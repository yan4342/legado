package io.legado.app.ui.ai.chat

import androidx.lifecycle.viewModelScope
import android.util.Log
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiOutlineExport
import io.legado.app.data.entities.Book
import io.legado.app.domain.usecase.structured.OutlineMarkdownCodec
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun AiChatViewModel.saveOutline(content: String, enabled: Boolean) {
    viewModelScope.launch {
        val convId = currentConversationId.value ?: return@launch
        val bookState = _uiState.value
        val (beforeContent, _) = outlineMutator.getSnapshot(convId)
        // Persist directly — avoid applyPatch(Replace) which can race observeOutline
        // and reset enabled / book binding before this upsert.
        aiOutlineGateway.upsert(
            io.legado.app.data.entities.AiOutline(
                conversationId = convId,
                content = content,
                enabled = enabled,
                bookUrl = bookState.outlineBookUrl,
                bookName = bookState.outlineBookName,
                bookAuthor = bookState.outlineBookAuthor,
            )
        )
        mutationSnapshotService.captureOutlineVersion(
            conversationId = convId,
            source = "manual_save",
            beforeContent = beforeContent,
            afterContent = content,
            enabled = enabled,
        )
        markWorkspacePrefetchStale(convId)
        _uiState.update {
            it.copy(
                outlineContent = content,
                outlineEnabled = enabled,
                outlineDocument = OutlineMarkdownCodec.decode(content),
                outlineBranchChoice = resolveOutlineBranchChoiceUi(
                    content = content,
                    enabled = enabled,
                    writingSubMode = it.writingSubMode,
                ),
            )
        }
    }
}

/** Shared by ViewModel + outline extensions (package-visible resolver). */
internal fun resolveOutlineBranchChoiceUi(
    content: String,
    enabled: Boolean,
    writingSubMode: String,
): OutlineBranchChoiceUi? {
    if (!enabled || writingSubMode != "roleplay" || content.isBlank()) return null
    val decoded = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.decode(content)
    val graph = decoded.graph ?: return null
    // Only show the chooser when the gate is on — not for pre-planted future branches.
    if (!graph.awaitingChoice) return null
    val pending = io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine.findPendingBranch(graph)
        ?: return null
    return OutlineBranchChoiceUi(
        title = pending.first.title,
        options = pending.second
            .filter { it.selected != true }
            .map { OutlineBranchOptionUi(id = it.id, label = it.title) }
            .toImmutableList(),
    ).takeIf { it.options.isNotEmpty() }
}

internal fun AiChatViewModel.selectOutlineBranch(optionId: String) {
    viewModelScope.launch {
        maintainMutex.withLock {
            val convId = currentConversationId.value ?: return@withLock
            val result = outlineBranchChoiceUseCase.selectOption(
                conversationId = convId,
                optionId = optionId,
                writingSubMode = _uiState.value.writingSubMode,
            )
            if (!result.success) {
                val msg = when (result.message) {
                    "author_mode_no_branch" -> "作者模式不支持分支选择"
                    "option_not_found" -> "未找到该分支选项"
                    "no_outline" -> "当前没有大纲"
                    "invalid_args" -> "参数无效"
                    else -> result.message.ifBlank { "分支选择失败" }
                }
                _effects.tryEmit(AiChatEffect.ShowMessage(msg))
                return@withLock
            }
            // Update immediately so the chooser clears before Room observe catches up.
            _uiState.update {
                it.copy(
                    outlineContent = result.content,
                    outlineDocument = OutlineMarkdownCodec.decode(result.content),
                    outlineBranchChoice = resolveOutlineBranchChoiceUi(
                        content = result.content,
                        enabled = it.outlineEnabled,
                        writingSubMode = it.writingSubMode,
                    ),
                )
            }
            markWorkspacePrefetchStale(convId)
        }
    }
}

internal fun AiChatViewModel.toggleOutlineEnabled() {
    viewModelScope.launch {
        val convId = currentConversationId.value ?: return@launch
        val existing = aiOutlineGateway.getByConversation(convId)
        val enabled = !(existing?.enabled ?: _uiState.value.outlineEnabled)
        if (existing != null) {
            aiOutlineGateway.upsert(existing.copy(enabled = enabled))
        } else {
            // No row yet: persist switch so it survives reopen / later generate.
            val bookState = _uiState.value
            aiOutlineGateway.upsert(
                io.legado.app.data.entities.AiOutline(
                    conversationId = convId,
                    content = bookState.outlineContent,
                    enabled = enabled,
                    bookUrl = bookState.outlineBookUrl,
                    bookName = bookState.outlineBookName,
                    bookAuthor = bookState.outlineBookAuthor,
                )
            )
        }
        markWorkspacePrefetchStale(convId)
        _uiState.update {
            it.copy(
                outlineEnabled = enabled,
                outlineBranchChoice = resolveOutlineBranchChoiceUi(
                    content = it.outlineContent,
                    enabled = enabled,
                    writingSubMode = it.writingSubMode,
                ),
            )
        }
    }
}

internal fun AiChatViewModel.deleteOutline() {
    viewModelScope.launch {
        val convId = currentConversationId.value ?: return@launch
        val existing = aiOutlineGateway.getByConversation(convId)
        if (existing == null) {
            _effects.tryEmit(AiChatEffect.ShowMessage("当前没有大纲"))
            return@launch
        }
        mutationSnapshotService.captureOutlineVersion(
            conversationId = convId,
            source = "delete",
            beforeContent = existing.content,
            afterContent = "",
            enabled = existing.enabled,
        )
        aiOutlineGateway.delete(convId)
        markWorkspacePrefetchStale(convId)
        _uiState.update {
            it.copy(
                outlineContent = "",
                outlineDocument = OutlineMarkdownCodec.decode(""),
                outlineEnabled = false,
                outlineBookUrl = "",
                outlineBookName = "",
                outlineBookAuthor = "",
                outlineBranchChoice = null,
            )
        }
        _effects.tryEmit(AiChatEffect.ShowMessage("大纲已删除"))
    }
}

internal fun AiChatViewModel.cleanupOrphanOutlines() {
    viewModelScope.launch {
        val count = runCatching { aiOutlineGateway.cleanupOrphanOutlines() }.getOrElse { 0 }
        _effects.tryEmit(
            AiChatEffect.ShowMessage(
                if (count > 0) "已清理 $count 份孤儿大纲" else "没有需要清理的孤儿大纲",
            ),
        )
    }
}

internal fun AiChatViewModel.showOutlineExportDialog() {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        val export = aiOutlineGateway.exportForConversation(convId)
        val json = GSON.toJson(export)
        _uiState.update { it.copy(showOutlineExportDialog = true, outlineExportJson = json) }
    }
}

internal fun AiChatViewModel.dismissOutlineExportDialog() {
    _uiState.update { it.copy(showOutlineExportDialog = false, outlineExportJson = "") }
}

internal fun AiChatViewModel.copyOutlineExport() {
    val json = _uiState.value.outlineExportJson
    if (json.isNotBlank()) {
        _effects.tryEmit(AiChatEffect.CopyToClipboard(json))
        _effects.tryEmit(AiChatEffect.ShowMessage("已复制到剪贴板"))
    }
}

internal fun AiChatViewModel.shareOutlineExport() {
    val json = _uiState.value.outlineExportJson
    if (json.isNotBlank()) {
        _effects.tryEmit(AiChatEffect.ShareText(json, "Outline Export"))
    }
}

internal fun AiChatViewModel.showOutlineImportDialog() {
    _uiState.update { it.copy(showOutlineImportDialog = true, outlineImportJson = "") }
}

internal fun AiChatViewModel.dismissOutlineImportDialog() {
    _uiState.update { it.copy(showOutlineImportDialog = false, outlineImportJson = "") }
}

internal fun AiChatViewModel.updateOutlineImportJson(json: String) {
    _uiState.update { it.copy(outlineImportJson = json) }
}

internal fun AiChatViewModel.confirmOutlineImport() {
    val json = _uiState.value.outlineImportJson
    if (json.isBlank()) {
        _effects.tryEmit(AiChatEffect.ShowMessage("请先粘贴导出的 JSON"))
        return
    }
    if (_uiState.value.outlineContent.isNotBlank()) {
        _uiState.update { it.copy(showOutlineOverwriteConfirm = true) }
        return
    }
    performOutlineJsonImport(json)
}

internal fun AiChatViewModel.performOutlineJsonImport(json: String) {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        runCatching {
            val export = GSON.fromJson(json, AiOutlineExport::class.java)
            require(export.type == AiOutlineExport.TYPE && export.version == AiOutlineExport.VERSION) {
                "Invalid outline export"
            }
            aiOutlineGateway.importToConversation(convId, export)
        }.onSuccess {
            val convId = currentConversationId.value
            val imported = convId?.let { aiOutlineGateway.getByConversation(it) }
            _uiState.update {
                val content = imported?.content.orEmpty()
                val enabled = imported?.enabled == true
                it.copy(
                    showOutlineImportDialog = false,
                    outlineImportJson = "",
                    showOutlineOverwriteConfirm = false,
                    outlineContent = content,
                    outlineDocument = OutlineMarkdownCodec.decode(content),
                    outlineEnabled = enabled,
                    outlineBookUrl = imported?.bookUrl.orEmpty(),
                    outlineBookName = imported?.bookName.orEmpty(),
                    outlineBookAuthor = imported?.bookAuthor.orEmpty(),
                    outlineBranchChoice = resolveOutlineBranchChoiceUi(
                        content = content,
                        enabled = enabled,
                        writingSubMode = it.writingSubMode,
                    ),
                )
            }
            _effects.tryEmit(AiChatEffect.ShowMessage("大纲导入成功"))
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "大纲导入失败"))
        }
    }
}

internal fun AiChatViewModel.showOutlineConversationPicker(mode: String) {
    val currentId = currentConversationId.value ?: return
    _uiState.update {
        it.copy(
            showOutlineConversationPicker = true,
            outlineConversationPickerMode = mode,
        )
    }
    viewModelScope.launch {
        val conversations = aiChatGateway.listConversations(50)
            .filter { it.id != currentId }
        _uiState.update { state ->
            state.copy(
                outlinePickerConversations = conversations.map { conv ->
                    val existing = state.conversations.find { it.id == conv.id }
                    AiChatConversationUi(
                        id = conv.id,
                        title = conv.title,
                        updatedAt = conv.updatedAt,
                        isSelected = false,
                        providerName = existing?.providerName.orEmpty(),
                        modelName = existing?.modelName.orEmpty(),
                        type = conv.type,
                        characterCardId = conv.characterCardId,
                        promptIds = conv.promptIds,
                    )
                }.toImmutableList()
            )
        }
    }
}

internal fun AiChatViewModel.dismissOutlineConversationPicker() {
    _uiState.update {
        it.copy(
            showOutlineConversationPicker = false,
            outlinePickerConversations = persistentListOf(),
        )
    }
}

internal fun AiChatViewModel.selectConversationForOutlineTransfer(targetConversationId: String) {
    val state = _uiState.value
    val currentId = currentConversationId.value ?: return
    val mode = state.outlineConversationPickerMode
    if (mode == "import" && state.outlineContent.isNotBlank()) {
        _uiState.update {
            it.copy(
                showOutlineConversationPicker = false,
                outlinePickerConversations = persistentListOf(),
                showOutlineOverwriteConfirm = true,
                outlineOverwritePendingSourceId = targetConversationId,
            )
        }
        return
    }
    performOutlineConversationTransfer(
        fromId = if (mode == "import") targetConversationId else currentId,
        toId = if (mode == "import") currentId else targetConversationId,
        isImport = mode == "import",
    )
}

internal fun AiChatViewModel.performOutlineConversationTransfer(
    fromId: String,
    toId: String,
    isImport: Boolean,
) {
    viewModelScope.launch {
        runCatching {
            val success = aiOutlineGateway.copyOutlineToConversation(fromId, toId)
            if (!success) {
                throw IllegalStateException(
                    if (isImport) "源会话没有大纲" else "当前会话没有可导出的大纲"
                )
            }
        }.onSuccess {
            val imported = aiOutlineGateway.getByConversation(toId)
            _uiState.update {
                val content = imported?.content.orEmpty()
                val enabled = imported?.enabled == true
                it.copy(
                    showOutlineConversationPicker = false,
                    outlinePickerConversations = persistentListOf(),
                    showOutlineOverwriteConfirm = false,
                    outlineOverwritePendingSourceId = null,
                    outlineContent = content,
                    outlineDocument = OutlineMarkdownCodec.decode(content),
                    outlineEnabled = enabled,
                    outlineBookUrl = imported?.bookUrl.orEmpty(),
                    outlineBookName = imported?.bookName.orEmpty(),
                    outlineBookAuthor = imported?.bookAuthor.orEmpty(),
                    outlineBranchChoice = resolveOutlineBranchChoiceUi(
                        content = content,
                        enabled = enabled,
                        writingSubMode = it.writingSubMode,
                    ),
                )
            }
            _effects.tryEmit(AiChatEffect.ShowMessage("大纲转移成功"))
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "大纲转移失败"))
        }
    }
}

internal fun AiChatViewModel.confirmOutlineOverwrite() {
    val state = _uiState.value
    val pendingSourceId = state.outlineOverwritePendingSourceId
    if (pendingSourceId != null) {
        val currentId = currentConversationId.value ?: return
        performOutlineConversationTransfer(
            fromId = pendingSourceId,
            toId = currentId,
            isImport = true,
        )
        return
    }
    performOutlineJsonImport(state.outlineImportJson)
}

internal fun AiChatViewModel.dismissOutlineOverwriteConfirm() {
    _uiState.update {
        it.copy(
            showOutlineOverwriteConfirm = false,
            outlineOverwritePendingSourceId = null,
        )
    }
}

internal fun AiChatViewModel.generateOutlineFromSheet(
    supplement: Boolean,
    explicitContent: String? = null,
    outlineKind: String = "",
) {
    val convId = currentConversationId.value ?: return
    // 扩写优先用 sheet 当前工作内容（Markdown 草稿/编辑中的大纲），否则退回已持久化内容。
    val existingContent = explicitContent?.takeIf { it.isNotBlank() }
        ?: _uiState.value.outlineContent
    _uiState.update {
        it.copy(
            suggestionsLoading = true,
            outlineGenerationPreview = "",
            outlineGenerationReasoning = "",
        )
    }
    viewModelScope.launch {
        try {
            val result = outlineMutator.generateFromConversation(
                conversationId = convId,
                supplement = supplement,
                existingContent = existingContent,
                writingSubMode = _uiState.value.writingSubMode,
                outlineKind = outlineKind,
                onPartial = { content, reasoning ->
                    _uiState.update {
                        it.copy(
                            outlineGenerationPreview = content,
                            outlineGenerationReasoning = reasoning.orEmpty(),
                        )
                    }
                },
            )
            if (result.success) {
                val existing = aiOutlineGateway.getByConversation(convId)
                val beforeContent = existing?.content.orEmpty()
                var content = result.content
                if (_uiState.value.writingSubMode == "roleplay") {
                    content = io.legado.app.domain.usecase.structured.OutlineAwaitingChoicePolicy
                        .normalizeAwaitingFrontMatter(content)
                }
                // 校验：模型输出必须是可解析的 outline_format:2，否则不覆盖已有大纲。
                val normalized = outlineMutator.normalizeGeneratedOutline(content)
                if (normalized == null) {
                    _effects.tryEmit(
                        AiChatEffect.ShowMessage(
                            (if (supplement) "扩写" else "生成") + "结果不是合法的大纲格式（outline_format:2），未保存",
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            suggestionsLoading = false,
                            outlineGenerationPreview = "",
                            outlineGenerationReasoning = "",
                        )
                    }
                    return@launch
                }
                content = normalized
                // Preserve switch when a row already exists; first create defaults on.
                val enabled = existing?.enabled ?: true
                mutationSnapshotService.captureOutlineVersion(
                    conversationId = convId,
                    source = "generate",
                    beforeContent = beforeContent,
                    afterContent = content,
                    enabled = enabled,
                )
                aiOutlineGateway.upsert(
                    io.legado.app.data.entities.AiOutline(
                        conversationId = convId,
                        content = content,
                        enabled = enabled,
                        bookUrl = _uiState.value.outlineBookUrl,
                        bookName = _uiState.value.outlineBookName,
                        bookAuthor = _uiState.value.outlineBookAuthor,
                    )
                )
                markWorkspacePrefetchStale(convId)
                _uiState.update {
                    it.copy(
                        outlineContent = content,
                        outlineDocument = OutlineMarkdownCodec.decode(content),
                        outlineEnabled = enabled,
                        outlineBranchChoice = resolveOutlineBranchChoiceUi(
                            content = content,
                            enabled = enabled,
                            writingSubMode = it.writingSubMode,
                        ),
                        suggestionsLoading = false,
                        outlineGenerationPreview = "",
                        outlineGenerationReasoning = "",
                    )
                }
            } else {
                _effects.tryEmit(AiChatEffect.ShowMessage(result.message))
                _uiState.update {
                    it.copy(
                        suggestionsLoading = false,
                        outlineGenerationPreview = "",
                        outlineGenerationReasoning = "",
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AiTool", "generateOutlineFromSheet: exception", e)
            _uiState.update {
                it.copy(
                    suggestionsLoading = false,
                    outlineGenerationPreview = "",
                    outlineGenerationReasoning = "",
                )
            }
            _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Generation failed"))
        }
    }
}

internal fun AiChatViewModel.showOutlineVersionHistory() {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        val versions = mutationSnapshotService.listOutlineSnapshots(convId)
        _uiState.update { it.copy(outlineVersions = versions.toImmutableList(), showOutlineVersionHistory = true) }
    }
}

internal fun AiChatViewModel.dismissOutlineVersionHistory() {
    _uiState.update { it.copy(showOutlineVersionHistory = false) }
}

internal suspend fun AiChatViewModel.loadOutlineSnapshotContent(snapshotId: String): String? =
    mutationSnapshotService.snapshotContent(snapshotId)?.content

internal fun AiChatViewModel.restoreOutlineVersion(snapshotId: String) {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        val current = outlineMutator.getSnapshot(convId)
        mutationSnapshotService.captureOutlineVersion(
            conversationId = convId,
            source = "before_restore",
            beforeContent = current.first,
            afterContent = current.first,
            enabled = current.second,
        )
        val result = mutationSnapshotService.restore(snapshotId)
        if (result.success) {
            val outline = aiOutlineGateway.getByConversation(convId)
            val content = outline?.content.orEmpty()
            markWorkspacePrefetchStale(convId)
            _uiState.update {
                it.copy(
                    outlineContent = content,
                    outlineDocument = OutlineMarkdownCodec.decode(content),
                    outlineEnabled = outline?.enabled == true,
                    outlineBookUrl = outline?.bookUrl.orEmpty(),
                    outlineBookName = outline?.bookName.orEmpty(),
                    outlineBookAuthor = outline?.bookAuthor.orEmpty(),
                    showOutlineVersionHistory = false,
                    outlineBranchChoice = resolveOutlineBranchChoiceUi(
                        content = content,
                        enabled = outline?.enabled == true,
                        writingSubMode = it.writingSubMode,
                    ),
                )
            }
        }
        _effects.tryEmit(AiChatEffect.ShowMessage(result.message))
    }
}

internal fun AiChatViewModel.showOutlineBookPicker() {
    viewModelScope.launch {
        val books = withContext(Dispatchers.IO) {
            appDb.bookDao.all.sortedByDescending { it.durChapterTime }
        }
        _uiState.update { it.copy(showOutlineBookPicker = true, outlineBookshelfBooks = books.toImmutableList()) }
    }
}

internal fun AiChatViewModel.dismissOutlineBookPicker() {
    _uiState.update { it.copy(showOutlineBookPicker = false) }
}

internal fun AiChatViewModel.selectOutlineBook(book: Book) {
    _uiState.update {
        it.copy(
            showOutlineBookPicker = false,
            outlineBookUrl = book.bookUrl,
            outlineBookName = book.name,
            outlineBookAuthor = book.author,
        )
    }
}

internal fun AiChatViewModel.clearOutlineBookSource() {
    _uiState.update {
        it.copy(
            outlineBookUrl = "",
            outlineBookName = "",
            outlineBookAuthor = "",
        )
    }
}

internal suspend fun AiChatViewModel.getOutlineBookForConversation(convId: String): Triple<String, String, String> {
    val outline = aiOutlineGateway.getByConversation(convId)
    return Triple(
        outline?.bookUrl.orEmpty(),
        outline?.bookName.orEmpty(),
        outline?.bookAuthor.orEmpty(),
    )
}

