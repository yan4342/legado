package io.legado.app.ui.ai.chat

import android.util.Log
import io.legado.app.R
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiWorkspace
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.prompt.PromptAssemblyContext
import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.domain.usecase.AdoptDerivedToBookUseCase
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.WorkspacePrefetchCache
import io.legado.app.utils.AiIdListCodec
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// =============================================================================
// Writing sub-mode
// =============================================================================

internal fun AiChatViewModel.setWritingSubMode(subMode: String) {
    val normalized = normalizeWritingSubMode(subMode)
    _uiState.update {
        it.copy(
            writingSubMode = normalized,
            outlineBranchChoice = resolveOutlineBranchChoiceUi(
                content = it.outlineContent,
                enabled = it.outlineEnabled,
                writingSubMode = normalized,
            ),
        )
    }
    val cid = currentConversationId.value ?: return
    viewModelScope.launch {
        aiChatGateway.updateConversationWritingSubMode(cid, normalized)
        refreshMultiBubbleProtocolEnabled()
    }
}

internal fun AiChatViewModel.normalizeWritingSubMode(raw: String?): String =
    if (raw == "author") "author" else "roleplay"

// =============================================================================
// Writing mode
// =============================================================================

internal fun AiChatViewModel.switchMode(type: String) {
    val cid = currentConversationId.value ?: return
    _uiState.update {
        it.copy(
            conversationType = type,
            webSearchArmed = if (type == "chat") it.webSearchArmed else false,
            pendingAttachments = if (type == "writing") persistentListOf() else it.pendingAttachments,
            isProcessingAttachments = if (type == "writing") false else it.isProcessingAttachments,
        )
    }
    appCtx.putPrefBoolean(
        io.legado.app.constant.PreferKey.lastConversationType, type == "writing"
    )
    viewModelScope.launch {
        aiChatGateway.updateConversationType(cid, type)
        if (type == "writing") {
            runCatching { ensureWorkspaceForCurrent(cid) }
        }
        refreshMultiBubbleProtocolEnabled()
        refreshContextUsageEstimate(getContextUiMessages())
    }
}

// =============================================================================
// Workspace management
// =============================================================================

internal suspend fun AiChatViewModel.ensureWorkspaceForCurrent(conversationId: String): AiWorkspace {
    val state = _uiState.value
    val existing = aiWorkspaceGateway.getByConversationId(conversationId)
        ?.takeIf { it.conversationId == conversationId }
    if (existing != null) {
        _uiState.update {
            it.copy(
                workspaceId = existing.id,
                workspaceName = existing.name,
                workspaceCharacterCardIds = existing.characterCardIds,
                workspaceWorldBookIds = existing.worldBookIds,
                workspaceBookUrl = existing.bookUrl,
                workspaceExportJson = "",
            )
        }
        return existing
    }
    val cardIds = state.selectedCharacterCards.map { it.id }
        .ifEmpty { listOfNotNull(state.selectedCharacterCard?.id) }
    val cardDerivedWb = cardIds.flatMap { id ->
        AiIdListCodec.parse(aiCharacterCardGateway.getById(id)?.worldBookIds)
    }.distinct()
    val promptIds = state.writingPrompts.filter { it.enabled }.map { it.id }
    val conv = aiChatGateway.getConversation(conversationId)
    val ws = aiWorkspaceGateway.ensureForConversation(
        conversationId = conversationId,
        name = conv?.title.orEmpty().ifBlank { "Workspace" },
        characterCardIds = AiIdListCodec.toCsv(cardIds),
        worldBookIds = AiIdListCodec.toCsv(cardDerivedWb),
        writingPromptIds = AiIdListCodec.toCsv(
            promptIds.ifEmpty { AiIdListCodec.parse(conv?.promptIds) },
        ),
    )
    _uiState.update {
        it.copy(
            workspaceId = ws.id,
            workspaceName = ws.name,
            workspaceCharacterCardIds = ws.characterCardIds,
            workspaceWorldBookIds = ws.worldBookIds,
            workspaceBookUrl = ws.bookUrl,
        )
    }
    return ws
}

/** Sync card refs; union card-derived world books with existing workspace books. */
internal suspend fun AiChatViewModel.syncWorkspaceCardRefs(conversationId: String, cardIds: Set<String>) {
    val ws = ensureWorkspaceForCurrent(conversationId)
    val cardDerived = cardIds.flatMap { id ->
        AiIdListCodec.parse(aiCharacterCardGateway.getById(id)?.worldBookIds)
    }
    val mergedWb = (AiIdListCodec.parse(ws.worldBookIds) + cardDerived).distinct()
    syncWorkspaceRefs(
        conversationId = conversationId,
        characterCardIds = AiIdListCodec.toCsv(cardIds),
        worldBookIds = AiIdListCodec.toCsv(mergedWb),
    )
}

internal suspend fun AiChatViewModel.syncWorkspaceRefs(
    conversationId: String,
    characterCardIds: String? = null,
    worldBookIds: String? = null,
    writingPromptIds: String? = null,
) {
    val ws = ensureWorkspaceForCurrent(conversationId)
    val updated = aiWorkspaceGateway.updateRefs(
        workspaceId = ws.id,
        characterCardIds = characterCardIds,
        worldBookIds = worldBookIds,
        writingPromptIds = writingPromptIds,
    ) ?: return
    markWorkspacePrefetchStale(conversationId)
    _uiState.update {
        it.copy(
            workspaceId = updated.id,
            workspaceName = updated.name,
            workspaceCharacterCardIds = updated.characterCardIds,
            workspaceWorldBookIds = updated.worldBookIds,
        )
    }
}

internal fun AiChatViewModel.markWorkspacePrefetchStale(conversationId: String) {
    WorkspacePrefetchCache.markStale(conversationId)
}

internal fun AiChatViewModel.showWorkspaceSheet() {
    // Open immediately so the sheet is not blocked by ensureWorkspace I/O.
    _uiState.update { it.copy(showWorkspaceSheet = true, workspaceImportJson = "") }
    viewModelScope.launch {
        val cid = currentConversationId.value ?: return@launch
        runCatching { ensureWorkspaceForCurrent(cid) }
    }
}

internal fun AiChatViewModel.exportWorkspace() {
    viewModelScope.launch {
        val wsId = _uiState.value.workspaceId ?: run {
            val cid = currentConversationId.value ?: return@launch
            ensureWorkspaceForCurrent(cid).id
        }
        runCatching {
            val json = aiWorkspaceGateway.exportWorkspaceJson(wsId)
            _uiState.update { it.copy(workspaceExportJson = json) }
            _effects.emit(AiChatEffect.CopyToClipboard(json))
            _effects.emit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_workspace_export_done)))
        }.onFailure {
            _effects.emit(AiChatEffect.ShowMessage(it.message ?: "Export failed"))
        }
    }
}

internal fun AiChatViewModel.showWorkspaceClonePicker() {
    viewModelScope.launch {
        val currentId = currentConversationId.value
        val sources = _uiState.value.conversations
            .filter { it.type == "writing" && it.id != currentId }
            .toImmutableList()
        _uiState.update {
            it.copy(showWorkspaceClonePicker = true, workspaceCloneSources = sources)
        }
    }
}

internal fun AiChatViewModel.cloneWorkspaceFromConversation(sourceConversationId: String) {
    viewModelScope.launch {
        val targetId = currentConversationId.value ?: return@launch
        runCatching {
            val sourceWs = aiWorkspaceGateway.getByConversationId(sourceConversationId)
                ?: throw IllegalArgumentException("Source workspace not found")
            val cloned = aiWorkspaceGateway.cloneWorkspaceToConversation(
                sourceWorkspaceId = sourceWs.id,
                targetConversationId = targetId,
            )
            val cardIds = workspaceIndexBuilder.parseIds(cloned.characterCardIds).toSet()
            if (cardIds.isNotEmpty()) {
                updateConversationCharacters(cardIds)
            }
            _uiState.update {
                it.copy(
                    workspaceId = cloned.id,
                    workspaceName = cloned.name,
                    workspaceCharacterCardIds = cloned.characterCardIds,
                    workspaceWorldBookIds = cloned.worldBookIds,
                    showWorkspaceClonePicker = false,
                    showWorkspaceSheet = false,
                )
            }
            _effects.emit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_workspace_cloned)))
        }.onFailure {
            _effects.emit(AiChatEffect.ShowMessage(it.message ?: "Clone failed"))
        }
    }
}

internal fun AiChatViewModel.importWorkspaceJson() {
    viewModelScope.launch {
        val cid = currentConversationId.value ?: return@launch
        val json = _uiState.value.workspaceImportJson
        if (json.isBlank()) {
            _effects.emit(AiChatEffect.ShowMessage("Empty JSON"))
            return@launch
        }
        runCatching {
            val ws = aiWorkspaceGateway.importWorkspaceJson(json, cid)
            val cardIds = workspaceIndexBuilder.parseIds(ws.characterCardIds).toSet()
            if (cardIds.isNotEmpty()) updateConversationCharacters(cardIds)
            _uiState.update {
                it.copy(
                    workspaceId = ws.id,
                    workspaceName = ws.name,
                    workspaceCharacterCardIds = ws.characterCardIds,
                    workspaceWorldBookIds = ws.worldBookIds,
                    workspaceImportJson = "",
                    showWorkspaceSheet = false,
                )
            }
            _effects.emit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_workspace_imported)))
        }.onFailure {
            _effects.emit(AiChatEffect.ShowMessage(it.message ?: "Import failed"))
        }
    }
}

internal fun AiChatViewModel.updateWorkspaceWorldBookIds(worldBookIds: String) {
    _uiState.update { it.copy(workspaceWorldBookIds = worldBookIds) }
    workspaceWorldBookSaveJob?.cancel()
    workspaceWorldBookSaveJob = viewModelScope.launch {
        delay(500)
        val cid = currentConversationId.value ?: return@launch
        if (_uiState.value.conversationType != "writing") return@launch
        syncWorkspaceRefs(cid, worldBookIds = AiIdListCodec.toCsv(worldBookIds))
    }
}

// =============================================================================
// Adopt bridge (derive -> canonical)
// =============================================================================

/** 采纳桥:衍生会话"写入本书"——先展示 diff 预览。 */
internal fun AiChatViewModel.showAdoptPreview() {
    val convId = currentConversationId.value ?: return
    _uiState.update { it.copy(adoptPreviewLoading = true, adoptPreview = null, adoptSelectedTypes = emptySet()) }
    viewModelScope.launch {
        runCatching { adoptUseCase.preview(convId) }
            .onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        adoptPreview = preview,
                        adoptPreviewLoading = false,
                        adoptSelectedTypes = preview.types,
                    )
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(adoptPreviewLoading = false) }
                _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "预览失败"))
            }
    }
}

/** 采纳桥:切换本次采纳的内容类型。 */
internal fun AiChatViewModel.toggleAdoptType(type: String) {
    _uiState.update { state ->
        val current = state.adoptSelectedTypes
        state.copy(adoptSelectedTypes = if (type in current) current - type else current + type)
    }
}

/** 采纳桥:确认后执行采纳(按所选类型写入正典层,快照可回滚)。 */
internal fun AiChatViewModel.confirmAdoptToBook() {
    val convId = currentConversationId.value ?: return
    val selected = _uiState.value.adoptSelectedTypes
    if (selected.isEmpty()) {
        _effects.tryEmit(AiChatEffect.ShowMessage("请至少选择一种内容"))
        return
    }
    viewModelScope.launch {
        val result = runCatching { adoptUseCase.adopt(convId, selected) }
            .getOrElse { AdoptDerivedToBookUseCase.AdoptResult(false, it.message ?: "采纳失败") }
        _uiState.update { it.copy(adoptPreview = null, adoptPreviewLoading = false, adoptSelectedTypes = emptySet()) }
        _effects.tryEmit(AiChatEffect.ShowMessage(result.message))
        if (result.success) markWorkspacePrefetchStale(convId)
    }
}

/** 工作区绑定本书:采纳桥需要 workspace.bookUrl 知道写回哪本书。 */
internal fun AiChatViewModel.showWorkspaceBookPicker() {
    viewModelScope.launch {
        val books = withContext(kotlinx.coroutines.Dispatchers.IO) {
            io.legado.app.data.appDb.bookDao.all.sortedByDescending { it.durChapterTime }
        }
        _uiState.update {
            it.copy(showWorkspaceBookPicker = true, workspaceBookshelfBooks = books.toImmutableList())
        }
    }
}

internal fun AiChatViewModel.selectWorkspaceBook(book: io.legado.app.data.entities.Book) {
    val wsId = _uiState.value.workspaceId ?: return
    viewModelScope.launch {
        runCatching {
            aiWorkspaceGateway.updateRefs(workspaceId = wsId, bookUrl = book.bookUrl)
        }.onSuccess {
            _uiState.update { state ->
                state.copy(
                    showWorkspaceBookPicker = false,
                    workspaceBookUrl = book.bookUrl,
                    adoptPreview = null,
                )
            }
            _effects.tryEmit(AiChatEffect.ShowMessage("已绑定本书，可写入本书（正典）"))
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "绑定失败"))
        }
    }
}

// =============================================================================
// Prompt assembly contexts
// =============================================================================

internal suspend fun AiChatViewModel.buildChatAssemblyContext(
    history: List<AiChatMessageUi>,
    newUserContent: String = "",
): PromptAssemblyContext {
    val state = _uiState.value
    val convId = currentConversationId.value
    val workspace = convId?.let { ensureWorkspaceForCurrent(it) }
    val cards = state.selectedCharacterCards.ifEmpty {
        state.selectedCharacterCard?.let { listOf(it) }.orEmpty()
    }
    val postHistory = cards.mapNotNull { card ->
        aiCharacterCardGateway.getById(card.id)?.postHistoryInstructions?.takeIf { it.isNotBlank() }
    }.joinToString("\n")
    return PromptAssemblyContext(
        pipelineMode = PromptPipelineMode.Chat,
        conversationId = convId,
        conversationType = state.conversationType,
        conversationTitle = state.conversations.find { it.id == convId }?.title.orEmpty(),
        history = history,
        newUserContent = newUserContent,
        contextWindow = state.contextWindow,
        characterCards = cards,
        userName = state.userName,
        userDescription = state.userDescription,
        userCardEnabled = state.userCardEnabled,
        workspace = workspace,
        postHistoryInstructions = postHistory,
        webSearchArmed = state.webSearchArmed,
        skillIds = state.conversationSkillIds,
        toolGroupState = null,
        outputMode = state.outputMode.name.lowercase(),
        hasActivePlan = planFileIdByMessageId.isNotEmpty(),
    )
}

internal suspend fun AiChatViewModel.buildWritingAssemblyContext(
    directedSpeakerCardId: String? = null,
    helpReply: Boolean = false,
): PromptAssemblyContext {
    val state = _uiState.value
    val convId = currentConversationId.value
    val mode = when {
        helpReply -> PromptPipelineMode.WritingHelpReply
        state.writingSubMode == "author" -> PromptPipelineMode.WritingAuthor
        else -> PromptPipelineMode.WritingRoleplay
    }
    val workspace = convId?.let { ensureWorkspaceForCurrent(it) }
    val postHistory = writingCharacterCards().mapNotNull { card ->
        aiCharacterCardGateway.getById(card.id)?.postHistoryInstructions?.takeIf { it.isNotBlank() }
    }.joinToString("\n")
    return PromptAssemblyContext(
        pipelineMode = mode,
        conversationId = convId,
        conversationType = state.conversationType,
        conversationTitle = state.conversations.find { it.id == convId }?.title.orEmpty(),
        history = recentMessages,
        contextWindow = state.contextWindow,
        writingSubMode = state.writingSubMode,
        directedSpeakerCardId = directedSpeakerCardId,
        characterCards = writingCharacterCards(),
        userName = state.userName,
        userDescription = state.userDescription,
        userCardEnabled = state.userCardEnabled,
        writingPrompts = state.writingPrompts,
        workspace = workspace,
        postHistoryInstructions = postHistory,
        skillIds = state.conversationSkillIds,
        hasActivePlan = planFileIdByMessageId.isNotEmpty(),
    )
}

// =============================================================================
// Structured data maintain helpers
// =============================================================================

internal suspend fun AiChatViewModel.countWritingUserTurns(conversationId: String): Long {
    return aiChatGateway.getContextMessages(conversationId, 500)
        .count { it.role == AiMessageRole.USER }
        .toLong()
}

internal fun AiChatViewModel.toolTracePatchedMemory(toolTrace: ToolTraceBuilder): Boolean =
    toolTrace.completedToolNames().any { it == AiToolRepository.TOOL_PATCH_HISTORY_MEMORY }

internal fun AiChatViewModel.toolTracePatchedOutline(toolTrace: ToolTraceBuilder): Boolean =
    toolTrace.completedToolNames().any { it == AiToolRepository.TOOL_PATCH_OUTLINE }

/**
 * Fire-and-forget memory/outline maintain. Does not block [isSending].
 * Serialized via [maintainMutex]; DB writes continue even if user switches chats.
 */
internal fun AiChatViewModel.scheduleMaintainStructuredData(
    convId: String,
    toolTrace: ToolTraceBuilder,
    force: Boolean = false,
) {
    if (_uiState.value.conversationType != "writing") return
    if (!_uiState.value.structuredAutoMaintainEnabled) {
        Log.d("StructuredMaintain", "skip: auto-maintain disabled")
        return
    }
    val skipMemory = toolTracePatchedMemory(toolTrace)
    val skipOutline = toolTracePatchedOutline(toolTrace)
    val outlineEnabled = _uiState.value.outlineEnabled
    maintainJob = viewModelScope.launch {
        maintainMutex.withLock {
            val showUi = currentConversationId.value == convId
            if (showUi) {
                _uiState.update { it.copy(isMaintainingStructuredData = true) }
            }
            try {
                val userTurns = countWritingUserTurns(convId)
                if (currentConversationId.value == convId) {
                    _uiState.update { it.copy(writingRoundCount = userTurns) }
                }
                val result = structuredMaintainUseCase.maintainIfNeeded(
                    conversationId = convId,
                    writingRoundCount = userTurns,
                    outlineEnabled = outlineEnabled,
                    writingSubMode = _uiState.value.writingSubMode,
                    skipMemory = skipMemory,
                    skipOutline = skipOutline,
                    force = force,
                )
                if (result.memoryUpdated || result.outlineUpdated) {
                    markWorkspacePrefetchStale(convId)
                }
                Log.d(
                    "StructuredMaintain",
                    "done: conv=$convId memory=${result.memoryUpdated} outline=${result.outlineUpdated}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("StructuredMaintain", "failed: ${e.message}")
            } finally {
                if (currentConversationId.value == convId) {
                    _uiState.update { it.copy(isMaintainingStructuredData = false) }
                }
            }
        }
    }
}
