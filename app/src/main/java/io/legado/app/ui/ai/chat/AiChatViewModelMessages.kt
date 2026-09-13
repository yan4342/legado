package io.legado.app.ui.ai.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.WritingInputMode
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.domain.usecase.ToolApprovalDecision
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.ai.resolvedSubModelProfileId
import io.legado.app.help.ai.AiAttachmentContentResolver
import io.legado.app.help.ai.AiChatAttachmentStore
import io.legado.app.help.ai.AiTokenEstimator
import io.legado.app.utils.AiIdListCodec
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

// =============================================================================
// Message actions
// =============================================================================

internal fun AiChatViewModel.deleteMessage(messageId: String) {
    val idsToRemove = buildSet {
        add(messageId)
        allLoadedMessages
            .filter { it.parentMessageId == messageId }
            .forEach { add(it.id) }
    }
    pendingDeletedMessageIds.addAll(idsToRemove)
    recentMessages = recentMessages.filter { it.id !in idsToRemove }
    olderMessages = olderMessages.filter { it.id !in idsToRemove }
    _uiState.update { current ->
        current.copy(messages = allLoadedMessages.toImmutableList())
    }
    viewModelScope.launch {
        runCatching { aiChatGateway.deleteMessage(messageId) }
            .onFailure {
                pendingDeletedMessageIds.removeAll(idsToRemove)
                _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Delete failed"))
                _uiState.update { current ->
                    current.copy(messages = allLoadedMessages.toImmutableList())
                }
            }
        // 删除消息引用的 HTML App（实体 + 文件）清理。
        idsToRemove.forEach { id ->
            val app = runCatching { aiHtmlAppGateway.getByMessageId(id) }.getOrNull()
            if (app != null) runCatching { aiHtmlAppGateway.delete(app.id) }
        }
    }
}

internal fun AiChatViewModel.visibleMessages(messages: List<AiChatMessageUi>): List<AiChatMessageUi> {
    if (pendingDeletedMessageIds.isEmpty()) return messages
    return messages.filter { it.id !in pendingDeletedMessageIds }
}

internal fun AiChatViewModel.editMessage(messageId: String, newContent: String) {
    viewModelScope.launch {
        aiChatGateway.updateMessageParts(messageId, listOf(AiMessagePart.Text(newContent)))
        syncOpeningLineFromEditedMessage(messageId, newContent)
    }
}

internal fun AiChatViewModel.forkConversation(messageId: String) {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        runCatching {
            aiChatGateway.forkConversation(convId, messageId)
        }.onSuccess { newConv ->
            selectConversation(newConv.id)
            _effects.tryEmit(AiChatEffect.ShowMessage("Conversation forked"))
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Fork failed"))
        }
    }
}

internal fun AiChatViewModel.forkConversationToSingleChat(targetCardId: String) {
    val sourceConvId = currentConversationId.value ?: return
    viewModelScope.launch {
        try {
            val card = aiCharacterCardGateway.getById(targetCardId)
            val cardName = card?.name ?: "Unknown"
            // Create new single-character conversation
            val newConv = aiChatGateway.createConversation(title = cardName)
            aiChatGateway.updateConversationType(newConv.id, "writing")
            aiChatGateway.updateConversationWritingSubMode(
                newConv.id,
                normalizeWritingSubMode(_uiState.value.writingSubMode),
            )
            aiChatGateway.updateConversationCharacter(newConv.id, targetCardId)
            aiChatGateway.updateConversationCharacters(
                newConv.id,
                AiIdListCodec.toJsonArray(listOf(targetCardId)),
            )
            val sourceWs = aiWorkspaceGateway.getByConversationId(sourceConvId)
            val cardWb = card?.worldBookIds.orEmpty()
            if (sourceWs != null) {
                aiWorkspaceGateway.cloneWorkspaceToConversation(
                    sourceWorkspaceId = sourceWs.id,
                    targetConversationId = newConv.id,
                    name = cardName,
                )
                val clonedWs = aiWorkspaceGateway.getByConversationId(newConv.id)
                if (clonedWs != null) {
                    aiWorkspaceGateway.updateRefs(
                        workspaceId = clonedWs.id,
                        characterCardIds = targetCardId,
                        worldBookIds = AiIdListCodec.toCsv(
                            AiIdListCodec.parse(sourceWs.worldBookIds) +
                                AiIdListCodec.parse(cardWb),
                        ),
                    )
                }
            } else {
                aiWorkspaceGateway.ensureForConversation(
                    conversationId = newConv.id,
                    name = cardName,
                    characterCardIds = targetCardId,
                    worldBookIds = AiIdListCodec.toCsv(cardWb),
                    writingPromptIds = AiIdListCodec.toCsv(
                        _uiState.value.writingPrompts.filter { it.enabled }.map { it.id },
                    ),
                )
            }
            // Copy all messages from source (preserve speakerCardId for history display)
            val allMsgs = aiChatGateway.getContextMessages(sourceConvId, 500)
            // Copy messages with remapped IDs
            val idMap = mutableMapOf<String, String>()
            allMsgs.forEach { oldMsg ->
                val newId = "message_${java.util.UUID.randomUUID().toString().replace("-", "")}"
                idMap[oldMsg.id] = newId
                val newParentId = oldMsg.parentMessageId?.let { idMap[it] }
                val parts = io.legado.app.domain.model.AiMessagePartJson.decode(oldMsg.partsJson)
                aiChatGateway.saveMessage(
                    conversationId = newConv.id,
                    role = oldMsg.role,
                    parts = parts,
                    parentMessageId = newParentId,
                    thinkingDuration = oldMsg.thinkingDuration,
                    speakerCardId = oldMsg.speakerCardId,
                )
            }
            selectConversation(newConv.id)
            _effects.tryEmit(AiChatEffect.ShowMessage("已拆分为与 $cardName 的单聊"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "拆分失败"))
        }
    }
}

// =============================================================================
// History loading
// =============================================================================

internal suspend fun AiChatViewModel.loadEarlierMessages() {
    try {
        val cid = currentConversationId.value ?: return
        val oldest = allLoadedMessages.firstOrNull() ?: return
        val older = runCatching {
            aiChatGateway.getSelectedMessagesBefore(cid, oldest.createdAt, oldest.id, 20)
        }.getOrNull() ?: return
        val olderUi = older.map { msg ->
            messageEntityToUi(msg)
        }
        if (olderUi.isEmpty()) {
            _uiState.update { it.copy(hasMoreHistory = false) }
            return
        }
        olderMessages = olderUi + olderMessages
        val allMsgs = allLoadedMessages
        _uiState.update { current ->
            val newLimit = allMsgs.size
            current.copy(
                messages = allMsgs.toImmutableList(),
                displayLimit = newLimit,
                hasMoreHistory = olderUi.size >= 20,
            )
        }
        refreshContextUsageEstimate(getContextUiMessages())
    } finally {
        _uiState.update { it.copy(isLoadingHistory = false) }
    }
}

internal suspend fun AiChatViewModel.getContextUiMessages(): List<AiChatMessageUi> {
    val cid = currentConversationId.value ?: return allLoadedMessages
    val msgs = runCatching {
        aiChatGateway.getContextMessages(cid, 80)
    }.getOrNull() ?: return allLoadedMessages
    val result = msgs.map { msg ->
        messageEntityToUi(msg)
    }
    // Merge with olderMessages (which are not in the context query if beyond 80).
    // olderMessages may still contain `/temp` notes loaded for UI — drop them here.
    val history = if (olderMessages.isNotEmpty()) {
        val resultIds = result.map { it.id }.toSet()
        olderMessages.filter { !it.excludeFromContext && it.id !in resultIds } + result
    } else {
        result
    }
    return withCompressionPrefix(history)
}

/**
 * Prepend the folded-history summary (persisted on the conversation) as a stable
 * cache prefix on every model request. The summary is synthetic (never persisted as
 * a message); the UI renders it via the compressed-context banner instead.
 */
internal fun AiChatViewModel.withCompressionPrefix(history: List<AiChatMessageUi>): List<AiChatMessageUi> {
    val summary = _uiState.value.compressedSummary
    if (summary.isBlank()) return history
    val prefix = listOf(
        AiChatMessageUi(
            id = "compress_summary_u", role = AiMessageRole.USER,
            content = "[前文摘要：$summary]", createdAt = 0L,
        ),
        AiChatMessageUi(
            id = "compress_summary_a", role = AiMessageRole.ASSISTANT,
            content = "已了解之前的对话内容。", createdAt = 1L,
        ),
    )
    return prefix + history
}

internal fun AiChatViewModel.loadMoreMessages() {
    val state = _uiState.value
    if (state.isLoadingHistory || !state.hasMoreHistory) return
    if (currentConversationId.value == null || allLoadedMessages.isEmpty()) return
    _uiState.update { it.copy(isLoadingHistory = true) }
    viewModelScope.launch { loadEarlierMessages() }
}

// =============================================================================
// Context compression
// =============================================================================

internal fun AiChatViewModel.compressContext() {
    val state = _uiState.value
    if (state.isSending || state.isCompressing) return
    val convId = currentConversationId.value ?: return
    _uiState.update { it.copy(isCompressing = true) }
    viewModelScope.launch {
        try {
            // Compress against the FULL selected context in the DB — not just the
            // in-memory window (recent 30 + paginated older) — because every request
            // reads up to 80 messages via getContextUiMessages()/getContextMessages().
            val msgs = aiChatGateway.getAllContextMessages(convId)
                .map { messageEntityToUi(it) }
            if (msgs.size <= 6) {
                _uiState.update { it.copy(isCompressing = false) }
                _effects.tryEmit(AiChatEffect.ShowMessage("Not enough messages to compress"))
                return@launch
            }
            val recent = msgs.takeLast(6)
            val old = msgs.dropLast(6)
            val oldText = old.joinToString("\n\n") { "[${it.role}] ${it.content}" }

            val summary = generationUseCase.compressHistory(oldText)
            // Persist so re-entry keeps the compressed context; fold ALL old messages
            // away in storage so subsequent requests only see summary + recent 6.
            aiChatGateway.updateConversationCompressedSummary(convId, summary)
            aiChatGateway.deleteContextMessagesExceptRecent(convId, 6)
            recentMessages = recent
            olderMessages = emptyList()
            _uiState.update {
                it.copy(
                    isCompressing = false,
                    compressedSummary = summary,
                    messages = allLoadedMessages.toImmutableList(),
                )
            }
            refreshContextUsageEstimate(getContextUiMessages(), forceEstimate = true)
            _effects.tryEmit(AiChatEffect.ShowMessage("Context compressed"))
        } catch (e: Exception) {
            _uiState.update { it.copy(isCompressing = false) }
            _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Compress failed"))
        }
    }
}

// =============================================================================
// Context usage
// =============================================================================

internal suspend fun AiChatViewModel.persistContextUsage(
    conversationId: String?,
    promptTokens: Int,
    source: String,
    calibrationScale: Float,
) {
    if (conversationId == null) return
    aiChatGateway.updateContextUsage(conversationId, promptTokens, source, calibrationScale)
}

internal suspend fun AiChatViewModel.refreshContextUsageEstimate(
    history: List<AiChatMessageUi> = allLoadedMessages,
    pendingUserContent: String = "",
    forceEstimate: Boolean = false,
) {
    val state = _uiState.value
    val contextWindow = state.contextWindow
    if (contextWindow <= 0) {
        _uiState.update { it.copy(contextInputBudget = 0, contextTokensUsed = 0) }
        return
    }
    val isRoleplay = state.conversationType == "writing" && state.writingSubMode == "roleplay"
    val modelHistory = historyForModel(history, isRoleplay)
    val assemblyContext = when {
        state.conversationType == "writing" -> buildWritingAssemblyContext()
            .copy(history = modelHistory, newUserContent = pendingUserContent)
        else -> buildChatAssemblyContext(modelHistory, pendingUserContent)
    }
    val estimate = generationUseCase.estimateContextUsage(
        history = modelHistory,
        conversationId = state.currentConversationId,
        assemblyContext = assemblyContext,
        contextWindow = contextWindow,
        conversationType = state.conversationType,
        pendingUserContent = pendingUserContent,
        calibrationScale = state.contextCalibrationScale,
    )
    val keepApiUsage = !forceEstimate &&
        state.contextTokensSource == AiChatUiState.CONTEXT_SOURCE_API &&
        state.contextTokensUsed > estimate.promptTokens
    val resolvedTokens = if (keepApiUsage) state.contextTokensUsed else estimate.promptTokens
    val resolvedSource = if (keepApiUsage) state.contextTokensSource else AiChatUiState.CONTEXT_SOURCE_ESTIMATE
    _uiState.update {
        it.copy(
            contextTokensUsed = resolvedTokens,
            contextInputBudget = estimate.inputBudget,
            contextTokensSource = resolvedSource,
            contextUsageSlices = estimate.slices.map { slice ->
                AiContextUsageSliceUi(
                    category = when (slice.category) {
                        io.legado.app.domain.usecase.AiContextUsageCategory.SYSTEM ->
                            AiContextUsageCategoryUi.SYSTEM
                        io.legado.app.domain.usecase.AiContextUsageCategory.RULES ->
                            AiContextUsageCategoryUi.RULES
                        io.legado.app.domain.usecase.AiContextUsageCategory.CHARACTER ->
                            AiContextUsageCategoryUi.CHARACTER
                        io.legado.app.domain.usecase.AiContextUsageCategory.KNOWLEDGE ->
                            AiContextUsageCategoryUi.KNOWLEDGE
                        io.legado.app.domain.usecase.AiContextUsageCategory.HISTORY ->
                            AiContextUsageCategoryUi.HISTORY
                        io.legado.app.domain.usecase.AiContextUsageCategory.DRAFT ->
                            AiContextUsageCategoryUi.DRAFT
                        io.legado.app.domain.usecase.AiContextUsageCategory.TOOLS ->
                            AiContextUsageCategoryUi.TOOLS
                    },
                    tokens = slice.tokens,
                    detail = slice.detail,
                )
            }.toImmutableList(),
        )
    }
    persistContextUsage(
        state.currentConversationId,
        resolvedTokens,
        resolvedSource,
        state.contextCalibrationScale,
    )
}

// =============================================================================
// Draft management
// =============================================================================

internal fun AiChatViewModel.updateDraftInput(text: String) {
    currentDraftText = text
    scheduleDraftPersist(text)
    draftContextEstimateJob?.cancel()
    val state = _uiState.value
    if (state.contextWindow <= 0 || state.isSending) return
    draftContextEstimateJob = viewModelScope.launch {
        delay(300)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            refreshContextUsageEstimate(getContextUiMessages())
            return@launch
        }
        val pending = contentForModel(trimmed, state.conversationType == "writing")
        refreshContextUsageEstimate(getContextUiMessages(), pendingUserContent = pending)
    }
}

internal fun AiChatViewModel.scheduleDraftPersist(text: String) {
    val convId = currentConversationId.value ?: return
    draftPersistJob?.cancel()
    draftPersistJob = viewModelScope.launch {
        delay(400)
        runCatching { aiChatGateway.updateConversationDraft(convId, text) }
    }
}

internal suspend fun AiChatViewModel.clearConversationDraft(conversationId: String) {
    draftPersistJob?.cancel()
    draftPersistJob = null
    currentDraftText = ""
    runCatching { aiChatGateway.updateConversationDraft(conversationId, "") }
}

// =============================================================================
// Generation control
// =============================================================================

internal fun AiChatViewModel.stopGenerating() {
    generationStopRequested = true
    pendingRejectionFeedback = null
    confirmationDeferred?.complete(ToolApprovalDecision())
    confirmationDeferred = null
    cancelPendingUserQuestions(cancelled = true)
    cancelPendingHabitMemory(confirmed = false)
    _uiState.update {
        it.copy(
            pendingToolConfs = persistentListOf(),
            pendingToolBatchFeedback = "",
            pendingTtsSecretFills = persistentListOf(),
        )
    }
    streamingJob?.cancel()
}

// =============================================================================
// Regeneration
// =============================================================================

internal fun AiChatViewModel.regenerateMessage(messageId: String) {
    val message = allLoadedMessages.find { it.id == messageId } ?: return
    if (message.role != AiMessageRole.ASSISTANT || message.parentMessageId == null) return
    val parentUserMsg = allLoadedMessages.find { it.id == message.parentMessageId } ?: return

    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        generationStopRequested = false
        refreshMultiBubbleProtocolEnabled()
        _uiState.update { it.copy(isSending = true, regeneratingMessageId = messageId) }
        val convId = currentConversationId.value ?: return@launch
        val contextMsgs = getContextUiMessages()
        val historyBeforeParent = contextMsgs.filter { it.createdAt < parentUserMsg.createdAt }
        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        val toolTrace = ToolTraceBuilder()
        var errorMsg: String? = null
        var wasCancelled = false
        try {
            val request = buildGenerationRequest(
                parentUserMsg.content,
                historyBeforeParent,
                newUserParts = parentUserMsg.parts,
            )
            setStreamingPlaceholder()
            try {
                collectStream(request, fullText, fullReasoning, toolTrace)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
            } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
            catch (e: Exception) { errorMsg = e.message }
        } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
        catch (e: Exception) { errorMsg = e.message; _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed")) }
        finally {
            val planToolsRan = logPlanToolsRan("regenerateMessage finally", wasCancelled, toolTrace)
            val text = resolveAssistantSaveText(
                fullText, wasCancelled, errorMsg, toolTrace, fullReasoning,
            )
            if (text != null || planToolsRan) {
                val finalText = if (planToolsRan && text == null) {
                    // 计划轮只调工具、无正文：空正文消息承载「📋 计划」角标，跳过 maybePostEdit。
                    ""
                } else if (wasCancelled) {
                    text.orEmpty()
                } else {
                    text?.let { maybePostEdit(it, fullReasoning.toString(), toolTrace) }.orEmpty()
                }
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                val speakerId = _uiState.value.streamingMessage?.speakerCardId
                    ?: resolveSpeakerCardId(finalText)
                val speakerName = speakerNameForPersist(finalText, speakerId)
                runCatching {
                    persistAssistantBubbles(
                        convId = convId,
                        parentMessageId = message.parentMessageId,
                        rawText = finalText,
                        reasoning = fullReasoning.toString(),
                        toolTrace = toolTrace,
                        wasCancelled = wasCancelled,
                        speakerId = speakerId,
                        speakerName = speakerName,
                        duration = duration,
                        useRegenerate = true,
                    )
                }
                if (planToolsRan) {
                    withContext(NonCancellable) {
                        val planMsgId = aiChatGateway.getContextMessages(convId, 1).firstOrNull()?.id
                        refreshPlanState(convId, planMsgId, fullReasoning.toString())
                    }
                }
                generateSuggestions()
                generateGalgameHud()
            }
            _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
            withContext(NonCancellable) { refreshContextUsageEstimate(getContextUiMessages()) }
            _uiState.update { it.copy(isSending = false) }
            refreshHabitMemories()
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
            if (text != null && _uiState.value.conversationType == "writing") {
                scheduleMaintainStructuredData(convId, toolTrace, force = true)
            }
        }
    }
}

internal fun AiChatViewModel.regenerateFromUserMessage(userMessageId: String) {
    val parentUserMsg = allLoadedMessages.find { it.id == userMessageId } ?: return
    if (parentUserMsg.role != AiMessageRole.USER) return

    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        generationStopRequested = false
        _uiState.update { it.copy(isSending = true) }
        val convId = currentConversationId.value ?: return@launch
        val contextMsgs = getContextUiMessages()
        val historyBeforeParent = contextMsgs.filter { it.createdAt < parentUserMsg.createdAt }
        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        val toolTrace = ToolTraceBuilder()
        var errorMsg: String? = null
        var wasCancelled = false
        try {
            val request = buildGenerationRequest(
                parentUserMsg.content,
                historyBeforeParent,
                newUserParts = parentUserMsg.parts,
            )
            setStreamingPlaceholder()
            try {
                collectStream(request, fullText, fullReasoning, toolTrace)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
            } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
            catch (e: Exception) { errorMsg = e.message }
        } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
        catch (e: Exception) { errorMsg = e.message; _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed")) }
        finally {
            val planToolsRan = logPlanToolsRan("regenerateFromUser finally", wasCancelled, toolTrace)
            val text = resolveAssistantSaveText(
                fullText, wasCancelled, errorMsg, toolTrace, fullReasoning,
            )
            if (text != null || planToolsRan) {
                val finalText = if (planToolsRan && text == null) {
                    // 计划轮只调工具、无正文：空正文消息承载「📋 计划」角标，跳过 maybePostEdit。
                    ""
                } else if (wasCancelled) {
                    text.orEmpty()
                } else {
                    text?.let { maybePostEdit(it, fullReasoning.toString(), toolTrace) }.orEmpty()
                }
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                val speakerId = _uiState.value.streamingMessage?.speakerCardId
                    ?: resolveSpeakerCardId(finalText)
                val speakerName = speakerNameForPersist(finalText, speakerId)
                runCatching {
                    persistAssistantBubbles(
                        convId = convId,
                        parentMessageId = parentUserMsg.id,
                        rawText = finalText,
                        reasoning = fullReasoning.toString(),
                        toolTrace = toolTrace,
                        wasCancelled = wasCancelled,
                        speakerId = speakerId,
                        speakerName = speakerName,
                        duration = duration,
                        useRegenerate = true,
                    )
                }
                if (planToolsRan) {
                    withContext(NonCancellable) {
                        val planMsgId = aiChatGateway.getContextMessages(convId, 1).firstOrNull()?.id
                        refreshPlanState(convId, planMsgId, fullReasoning.toString())
                    }
                }
                generateSuggestions()
                generateGalgameHud()
            }
            _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
            withContext(NonCancellable) { refreshContextUsageEstimate(getContextUiMessages()) }
            _uiState.update { it.copy(isSending = false) }
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
            if (text != null && _uiState.value.conversationType == "writing") {
                scheduleMaintainStructuredData(convId, toolTrace, force = true)
            }
        }
    }
}

// =============================================================================
// Branch switching
// =============================================================================

internal fun AiChatViewModel.switchBranch(messageId: String, direction: Int) {
    viewModelScope.launch {
        runCatching {
            val message = aiChatGateway.getMessage(messageId) ?: return@runCatching
            val parentId = message.parentMessageId ?: return@runCatching
            val siblings = aiChatGateway.getBranches(parentId)
            val byBranch = siblings.groupBy { it.branchIndex }.toSortedMap()
            if (byBranch.size <= 1) return@runCatching
            val branchIndexes = byBranch.keys.toList()
            val currentPos = branchIndexes.indexOf(message.branchIndex)
            if (currentPos < 0) return@runCatching
            val nextPos = currentPos + direction
            if (nextPos !in branchIndexes.indices) return@runCatching
            val nextBranchIndex = branchIndexes[nextPos]
            val currentGroupIds = byBranch.getValue(message.branchIndex).map { it.id }.toSet()
            val nextGroup = byBranch.getValue(nextBranchIndex)
            val totalBranches = byBranch.size
            val nextUis = nextGroup.map { ent ->
                messageEntityToUi(ent).copy(totalBranches = totalBranches)
            }

            fun swap(list: List<AiChatMessageUi>): List<AiChatMessageUi> {
                val out = ArrayList<AiChatMessageUi>(list.size - currentGroupIds.size + nextUis.size)
                var i = 0
                var replaced = false
                while (i < list.size) {
                    if (!replaced && list[i].id in currentGroupIds) {
                        while (i < list.size && list[i].id in currentGroupIds) i++
                        out.addAll(nextUis)
                        replaced = true
                        continue
                    }
                    out.add(list[i])
                    i++
                }
                return out
            }
            recentMessages = swap(recentMessages)
            olderMessages = swap(olderMessages)
            _uiState.update {
                it.copy(messages = allLoadedMessages.toImmutableList())
            }
            aiChatGateway.selectBranch(nextGroup.first().id)
        }.onFailure { _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed")) }
    }
}

// =============================================================================
// Send message
// =============================================================================

/**
 * 计划轮检测 + AiPlan 日志（发送 / 重新生成共用）。
 * 计划轮：模型按 directive 只调 write_file/edit_file("plan://…")、不输出正文，
 * 此时 resolveAssistantSaveText 返回 null，消息仍要以空正文落库（角标承载），
 * 并在收尾刷新审批面板。
 */
private fun AiChatViewModel.logPlanToolsRan(
    entry: String,
    wasCancelled: Boolean,
    toolTrace: ToolTraceBuilder,
): Boolean {
    val ran = !wasCancelled &&
        _uiState.value.outputMode == AiOutputMode.PLAN &&
        toolTrace.completedPlanFileCalls()
    Log.d(
        "AiPlan",
        "$entry outputMode=${_uiState.value.outputMode} wasCancelled=$wasCancelled " +
            "planToolsRan=$ran traceSize=${toolTrace.size()}",
    )
    return ran
}

internal fun AiChatViewModel.sendMessage(rawContent: String) {
    val trimmedRaw = rawContent.trim()
    val tempCommand = io.legado.app.domain.usecase.ai.TempSlashCommand.parse(trimmedRaw)
    if (tempCommand != null && tempCommand.body.isBlank()) {
        _effects.tryEmit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_temp_command_need_text)))
        return
    }
    val excludeFromContext = tempCommand != null
    val displayContent = tempCommand?.body ?: trimmedRaw
    val pendingAttachments = _uiState.value.pendingAttachments.filter { !it.isProcessing }
    if ((displayContent.isBlank() && pendingAttachments.isEmpty()) ||
        _uiState.value.isSending ||
        _uiState.value.isProcessingAttachments
    ) {
        return
    }
    val state = _uiState.value
    if (state.conversationType == "writing" && pendingAttachments.isNotEmpty()) {
        // Attachments are chat-mode only
        return
    }
    val isWriting = state.conversationType == "writing"
    val expandedContent = expandInputMacros(displayContent)
    var modelContent = contentForModel(displayContent, isWriting)
    // 游戏静默上下文（GameBridge.notifyContext）：注入下轮模型请求，不污染消息列表，用完即清空。
    val gameCtx = state.silentGameContext
    if (gameCtx.isNotBlank()) {
        modelContent = "[HTML App 当前状态]\n$gameCtx\n\n[用户消息]\n$modelContent"
        _uiState.update { it.copy(silentGameContext = "") }
    }
    val mentioned = findMentionedSpeakers(expandedContent)
    val directedSpeakerId = mentioned.firstOrNull()
    val attachmentParts = pendingAttachments.map { it.toAttachmentPart() }
    val userParts = buildList {
        addAll(attachmentParts)
        if (displayContent.isNotBlank()) add(AiMessagePart.Text(displayContent))
    }
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        // Capability check for images before clearing pending state
        if (attachmentParts.any { it.kind == io.legado.app.domain.model.AiAttachmentKind.IMAGE }) {
            val preset = aiProfileGateway.getTaskPreset(io.legado.app.domain.model.AiTaskType.CHAT)
            val caps = preset?.model?.capabilities.orEmpty()
            if (io.legado.app.domain.model.AiCapability.VISION !in caps) {
                _effects.tryEmit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_attachment_no_vision)))
                return@launch
            }
        }
        generationStopRequested = false
        refreshMultiBubbleProtocolEnabled()
        _uiState.update {
            it.copy(
                isSending = true,
                pendingAttachments = persistentListOf(),
            )
        }
        val historySnapshot = getContextUiMessages()
        var convId: String? = null
        var parentMessageId: String? = null
        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        val toolTrace = ToolTraceBuilder()
        var errorMsg: String? = null
        var wasCancelled = false
        try {
            convId = currentConversationId.value
                ?: aiChatGateway.createConversation().id.also { currentConversationId.value = it }
            clearConversationDraft(convId)
            val userMsg = aiChatGateway.saveMessage(
                conversationId = convId,
                role = AiMessageRole.USER,
                parts = userParts,
                excludeFromContext = excludeFromContext,
            )
            parentMessageId = userMsg.id
            val request = buildGenerationRequest(
                modelContent,
                historySnapshot,
                directedSpeakerId,
                newUserParts = userParts,
            )
            pendingRawPromptEstimate = AiTokenEstimator.estimatePromptTokens(request)
            _uiState.update { it.copy(contextTokensSource = AiChatUiState.CONTEXT_SOURCE_ESTIMATE) }
            refreshContextUsageEstimate(historySnapshot, pendingUserContent = modelContent)
            setStreamingPlaceholder()
            try {
                collectStream(request, fullText, fullReasoning, toolTrace, convId)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
            } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
            catch (e: Exception) {
                errorMsg = e.message
                if (fullText.isNotEmpty()) {
                    _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed"))
                }
            }
        } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
        catch (e: Exception) { errorMsg = e.message; _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed")) }
        finally {
            // 计划轮即使模型只调用 write_file 不输出正文（计划 directive 要求不把计划当文本），
            // 也要保存计划消息 + 刷新面板：否则计划消息不落库、planFileIdByMessageId 不回填，
            // 时间线上的「📋 计划」角标永远不出现，历史计划也无从点开。
            val planToolsRan = logPlanToolsRan("send finally", wasCancelled, toolTrace)
            val text = resolveAssistantSaveText(
                fullText, wasCancelled, errorMsg, toolTrace, fullReasoning,
            )
            if (convId != null && (text != null || planToolsRan)) {
                // 计划轮空正文：直接用空串保存（消息体由工具 parts + 角标承载），跳过 maybePostEdit。
                val finalText = if (planToolsRan && text == null) {
                    ""
                } else if (wasCancelled) {
                    text.orEmpty()
                } else {
                    text?.let { maybePostEdit(it, fullReasoning.toString(), toolTrace) }.orEmpty()
                }
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                val speakerId = directedSpeakerId
                    ?: _uiState.value.streamingMessage?.speakerCardId
                    ?: resolveSpeakerCardId(finalText)
                val speakerName = speakerNameForPersist(finalText, speakerId)
                val storageText = runCatching {
                    persistAssistantBubbles(
                        convId = convId,
                        parentMessageId = parentMessageId,
                        rawText = finalText,
                        reasoning = fullReasoning.toString(),
                        toolTrace = toolTrace,
                        wasCancelled = wasCancelled,
                        speakerId = speakerId,
                        speakerName = speakerName,
                        duration = duration,
                    )
                }.getOrElse {
                    finalizeAssistantBody(finalText, speakerId).first
                }
                Log.d("AiTool", "sendMessage save: textLen=${storageText.length} reasoningLen=${fullReasoning.length}")
                // Plan mode：仅当本轮真的调用了计划文件工具（write_file/edit_file 且 path 为 plan://）才刷新面板，
                // 避免计划模式下普通闲聊每轮都做无谓的文件读取 + pendingPlan 重建。
                if (planToolsRan) {
                    withContext(NonCancellable) {
                        val planMsgId = aiChatGateway.getContextMessages(convId, 1).firstOrNull()?.id
                        refreshPlanState(convId, planMsgId, fullReasoning.toString())
                    }
                }
                val conv = withContext(NonCancellable) { aiChatGateway.getConversation(convId) }
                if (conv != null && (conv.title == "New Chat" || conv.title.isBlank()))
                    generateConversationTitle(
                        convId,
                        displayContent.ifBlank {
                            pendingAttachments.joinToString { it.displayName }
                        },
                        storageText,
                    )
                generateSuggestions()
                generateGalgameHud()
                if (!wasCancelled && _uiState.value.outputMode != AiOutputMode.PLAN) {
                    autoContinueInterCharacter(convId, speakerId)
                    // Multi-mention: force-response for @mentioned characters that
                    // autoContinueInterCharacter didn't already trigger via its @-chain
                    if (mentioned.size > 1) {
                        val spokenIds = collectRecentSpeakerCardIds(mentioned.size * 2 + 4)
                        for (mentionedId in mentioned) {
                            if (mentionedId != speakerId && mentionedId !in spokenIds) {
                                forceCharacterResponse(convId, mentionedId)
                            }
                        }
                    }
                }
            }
            _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
            if (!isWriting && convId != null) {
                withContext(NonCancellable) { refreshUserCardState(convId) }
            }
            withContext(NonCancellable) { refreshContextUsageEstimate(getContextUiMessages()) }
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
            _uiState.update { it.copy(isSending = false) }
            refreshHabitMemories()
            if (isWriting && convId != null && text != null) {
                scheduleMaintainStructuredData(convId, toolTrace)
            }
        }
    }
}

// =============================================================================
// Attachments
// =============================================================================

internal fun AiChatViewModel.addAttachmentsFromUris(uris: List<android.net.Uri>) {
    if (_uiState.value.conversationType == "writing") return
    if (uris.isEmpty()) return
    viewModelScope.launch {
        val room = AiChatAttachmentStore.MAX_ATTACHMENTS_PER_SEND -
            _uiState.value.pendingAttachments.size
        if (room <= 0) {
            _effects.emit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_attachment_max_count)))
            return@launch
        }
        val toAdd = uris.take(room)
        if (uris.size > room) {
            _effects.emit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_attachment_max_count)))
        }
        _uiState.update { it.copy(isProcessingAttachments = true) }
        try {
            // Serial: EpubFile companion cache is process-wide
            for (uri in toAdd) {
                processOneAttachment(uri)
            }
        } finally {
            _uiState.update { it.copy(isProcessingAttachments = false) }
        }
    }
}

internal suspend fun AiChatViewModel.processOneAttachment(uri: android.net.Uri) {
    withContext(Dispatchers.IO) {
        runCatching {
            val copied = AiChatAttachmentStore.copyFromUri(uri)
            val kind = runCatching {
                AiAttachmentContentResolver.classifyKind(copied.displayName, copied.mimeType)
            }.getOrElse {
                AiChatAttachmentStore.deleteCopied(copied)
                throw it
            }
            var extractedPath: String? = null
            var truncated = false
            var preview: String? = null
            if (kind == io.legado.app.domain.model.AiAttachmentKind.TEXT_EXTRACT) {
                val result = AiAttachmentContentResolver.extractText(
                    copied.file,
                    copied.displayName,
                )
                val out = AiChatAttachmentStore.writeExtractedText(copied.id, result.text)
                extractedPath = out.absolutePath
                truncated = result.truncated
                preview = result.text.take(120)
            }
            val ui = AiChatPendingAttachmentUi(
                id = copied.id,
                localPath = copied.file.absolutePath,
                mimeType = copied.mimeType,
                displayName = copied.displayName,
                kind = kind,
                sizeBytes = copied.sizeBytes,
                extractedTextPath = extractedPath,
                truncated = truncated,
                previewText = preview,
                isProcessing = false,
            )
            _uiState.update { state ->
                state.copy(
                    pendingAttachments = (state.pendingAttachments + ui).toImmutableList(),
                )
            }
        }.onFailure { e ->
            val msg = when (e.message) {
                "FILE_TOO_LARGE" -> appCtx.getString(R.string.ai_attachment_too_large)
                "UNSUPPORTED" -> appCtx.getString(R.string.ai_attachment_unsupported)
                else -> e.message ?: appCtx.getString(R.string.ai_attachment_unsupported)
            }
            _effects.emit(AiChatEffect.ShowMessage(msg))
        }
    }
}

internal fun AiChatViewModel.removePendingAttachment(id: String) {
    val removed = _uiState.value.pendingAttachments.find { it.id == id }
    _uiState.update { state ->
        state.copy(
            pendingAttachments = state.pendingAttachments.filter { it.id != id }.toImmutableList(),
        )
    }
    if (removed != null) {
        viewModelScope.launch(Dispatchers.IO) {
            AiChatAttachmentStore.deleteFile(removed.localPath)
            AiChatAttachmentStore.deleteFile(removed.extractedTextPath)
        }
    }
}

// =============================================================================
// Writing mode generation actions
// =============================================================================

internal fun AiChatViewModel.continueWriting() {
    val state = _uiState.value
    if (state.isSending) return
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        doWritingSend(
            userContent = WritingUserInput.CONTINUE_MODEL_CONTENT,
            displayContent = "(续写)",
        )
    }
}

internal fun AiChatViewModel.aiHelpReply(
    draftText: String,
    inputMode: WritingInputMode,
) {
    val state = _uiState.value
    if (state.isSending) return
    val trimmed = draftText.trim()
    val expandedDraft = expandInputMacros(trimmed)
    val isRoleplay = state.writingSubMode == "roleplay"
    val defaultRoleLabel = if (isRoleplay) "用户" else "主角"
    val roleLabel = if (state.userCardEnabled) {
        state.userName.takeIf { it.isNotBlank() } ?: defaultRoleLabel
    } else {
        defaultRoleLabel
    }
    val inputTypeHint = when (inputMode) {
        WritingInputMode.DIALOGUE ->
            "仅输出对白正文（不要动作描写、不要旁白）"
        WritingInputMode.ACTION ->
            "仅输出动作/神态描写（不要对白、不要引号台词）"
    }
    val inputTypeLabel = when (inputMode) {
        WritingInputMode.DIALOGUE -> "对白"
        WritingInputMode.ACTION -> "动作"
    }

    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        fun fillHelpReplyPlaceholders(raw: String): String =
            io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(
                raw.replace("{roleLabel}", roleLabel),
            )

        val instruction = if (trimmed.isNotBlank()) {
            fillHelpReplyPlaceholders(
                resolveWritingPromptContent(
                    AiWritingPrompt.WPROMPT_HELP_REPLY_WITH_DRAFT,
                    AiPromptTemplate.HELP_REPLY_WITH_DRAFT,
                ),
            )
        } else {
            fillHelpReplyPlaceholders(
                resolveWritingPromptContent(
                    AiWritingPrompt.WPROMPT_HELP_REPLY_EMPTY,
                    AiPromptTemplate.HELP_REPLY_EMPTY,
                ),
            )
        }
        // Keep the latest turn as a short, low-priority reference inside the user
        // message — not as a full chat history turn (that overweights NPC prose).
        val npcNames = state.selectedCharacterCards.ifEmpty {
            listOfNotNull(state.selectedCharacterCard)
        }.map { it.name.trim() }.filter { it.isNotEmpty() }
        val npcForbid = if (npcNames.isEmpty()) {
            "对方角色"
        } else {
            npcNames.joinToString("、")
        }
        val latestMsg = allLoadedMessages.lastOrNull()
        val referenceBlock = latestMsg?.let { msg ->
            val snippet = msg.content.trim().take(400)
            if (snippet.isEmpty()) null
            else {
                // Do not label as assistant/user turns — models otherwise continue that voice.
                "<reference priority=\"low\" pov=\"scene_only\">\n" +
                    "场景摘录（仅了解气氛；说话人不是你；禁止续写此段、禁止改用其人称）：\n" +
                    "$snippet\n" +
                    "</reference>"
            }
        }
        val userContent = buildString {
            append(instruction)
            if (trimmed.isNotBlank()) {
                append("\n\n草稿：\n")
                append(expandedDraft)
            }
            if (referenceBlock != null) {
                append("\n\n")
                append(referenceBlock)
            }
            // Variable suffix (input type) kept out of the static template for prefix cache.
            append("\n\n输出类型：$inputTypeHint")
            append("\n视角：必须以「$roleLabel」的用户侧视角输出。")
            append("\n禁止：扮演或模仿 $npcForbid；禁止输出对方的台词/内心/动作来顶替回复。")
            append("\n硬性要求：只输出「$inputTypeLabel」；参考不得改写输出类型或视角。")
        }
        val assemblyContext = buildWritingAssemblyContext(helpReply = true)
            .copy(
                history = emptyList(),
                newUserContent = userContent,
            )
        val systemPrompt = promptAssembler.assembleSystemPrompt(assemblyContext)

        _uiState.update { it.copy(isSending = true) }
        val buf = StringBuilder()
        try {
            // Check for sub-model delegation (skipped when sub-model == main chat model)
            val helpConfig = aiToolConfigGateway.getByToolName("ai_help_reply")
            val helpSubModelId = helpConfig.resolvedSubModelProfileId(aiProfileGateway)
            if (helpSubModelId != null) {
                // Sub-model path: build independent request, stream response
                val modelProfile = aiProfileGateway.getModel(helpSubModelId)
                val provider = modelProfile?.let { aiProfileGateway.getProvider(it.providerId) }
                if (modelProfile != null && provider != null) {
                    val messages = listOf(
                        AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                        AiMessage(AiMessageRole.USER, userContent),
                    )
                    val request = AiGenerateRequest(
                        model = AiModelConfig(
                            id = modelProfile.id,
                            provider = AiProviderConfig(
                                id = provider.id, name = provider.name, protocol = provider.protocol,
                                baseUrl = provider.baseUrl, apiKey = provider.apiKey,
                                modelsUrl = provider.modelsUrl,
                                chatPath = provider.chatPath ?: "/chat/completions",
                                responsesPath = provider.responsesPath ?: "/responses",
                                messagesPath = provider.messagesPath ?: "/v1/messages",
                                modelsPath = provider.modelsPath,
                                headers = emptyMap(), customHeaders = emptyMap()
                            ),
                            displayName = modelProfile.displayName,
                            modelId = modelProfile.modelId,
                            contextWindow = modelProfile.contextWindow,
                            maxOutputTokens = modelProfile.maxOutputTokens
                        ),
                        messages = messages,
                        params = AiGenerationParams(maxOutputTokens = 300),
                        callMeta = AiCallMeta(AiCallSource.HELP_REPLY, currentConversationId.value),
                    )
                    aiTextGateway.generateStream(request).collect { event ->
                        when (event) {
                            is AiStreamEvent.Content -> {
                                buf.append(event.text)
                                _effects.emit(AiChatEffect.SetInputText(buf.toString()))
                            }
                            else -> {}
                        }
                    }
                } else {
                    _effects.emit(AiChatEffect.ShowMessage("Help reply sub-model not configured"))
                }
            } else {
                // Default: use main chat model with maxOutputTokens limit
                val baseRequest = generationUseCase.buildRequest(
                    userContent = userContent,
                    history = emptyList(),
                    reasoningLevel = state.reasoningLevel,
                    conversationId = currentConversationId.value,
                    assemblyContext = assemblyContext,
                    contextWindow = state.contextWindow,
                    conversationType = state.conversationType,
                )
                val request = baseRequest.copy(
                    params = baseRequest.params.copy(maxOutputTokens = 300, reasoningLevel = AiReasoningLevel.OFF)
                )
                generationUseCase.collectStream(
                    request = request,
                    toolTrace = ToolTraceBuilder(),
                    onContent = { delta ->
                        buf.append(delta)
                        _effects.emit(AiChatEffect.SetInputText(buf.toString()))
                    },
                    onReasoning = {},
                    onToolTraceUpdate = {},
                )
            }
        } catch (_: CancellationException) {
            _effects.emit(AiChatEffect.SetInputText(buf.toString()))
        } catch (e: Exception) {
            if (buf.isNotEmpty()) {
                _effects.emit(AiChatEffect.SetInputText(buf.toString()))
            }
            _effects.emit(AiChatEffect.ShowMessage(e.message ?: "Failed"))
        } finally {
            _uiState.update { it.copy(isSending = false) }
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
        }
    }
}

internal fun AiChatViewModel.doWritingSend(userContent: String, displayContent: String) {
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        generationStopRequested = false
        _uiState.update { it.copy(isSending = true) }
        val historySnapshot = getContextUiMessages()
        var convId: String? = null
        var parentMessageId: String? = null
        val fullText = StringBuilder()
        val fullReasoning = StringBuilder()
        val toolTrace = ToolTraceBuilder()
        var errorMsg: String? = null
        var wasCancelled = false
        try {
            convId = currentConversationId.value
                ?: aiChatGateway.createConversation().id.also { currentConversationId.value = it }
            val userMsg = aiChatGateway.saveMessage(convId, AiMessageRole.USER, listOf(AiMessagePart.Text(displayContent)))
            parentMessageId = userMsg.id
            val assemblyContext = buildWritingAssemblyContext()
                .copy(history = historySnapshot, newUserContent = userContent)
            val request = generationUseCase.buildRequest(
                userContent = userContent,
                history = historySnapshot,
                reasoningLevel = _uiState.value.reasoningLevel,
                conversationId = convId,
                assemblyContext = assemblyContext,
                contextWindow = _uiState.value.contextWindow,
                conversationType = _uiState.value.conversationType,
            ).also { capturePendingPromptInjection() }
            _uiState.update { it.copy(contextTokensSource = AiChatUiState.CONTEXT_SOURCE_ESTIMATE) }
            refreshContextUsageEstimate(historySnapshot, pendingUserContent = userContent)
            setStreamingPlaceholder()
            try {
                collectStream(request, fullText, fullReasoning, toolTrace, convId)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
            } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
            catch (e: Exception) { errorMsg = e.message }
        } catch (_: CancellationException) { wasCancelled = true; throw CancellationException("cancelled", null) }
        catch (e: Exception) { errorMsg = e.message; _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed")) }
        finally {
            val text = resolveAssistantSaveText(
                fullText, wasCancelled, errorMsg, toolTrace, fullReasoning,
            )
            if (convId != null && text != null) {
                val finalText = if (wasCancelled) {
                    text
                } else {
                    maybePostEdit(text, fullReasoning.toString(), toolTrace)
                }
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                val speakerId = _uiState.value.streamingMessage?.speakerCardId
                    ?: resolveSpeakerCardId(finalText)
                val speakerName = speakerNameForPersist(finalText, speakerId)
                val titleBody = runCatching {
                    persistAssistantBubbles(
                        convId = convId,
                        parentMessageId = parentMessageId,
                        rawText = finalText,
                        reasoning = fullReasoning.toString(),
                        toolTrace = toolTrace,
                        wasCancelled = wasCancelled,
                        speakerId = speakerId,
                        speakerName = speakerName,
                        duration = duration,
                    )
                }.getOrDefault(finalText)
                val conv = withContext(NonCancellable) { aiChatGateway.getConversation(convId) }
                if (conv != null && (conv.title == "New Chat" || conv.title.isBlank()))
                    generateConversationTitle(convId, userContent, titleBody)
            }
            _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
            withContext(NonCancellable) { refreshContextUsageEstimate(getContextUiMessages()) }
            _uiState.update { it.copy(isSending = false) }
            refreshHabitMemories()
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
            if (convId != null && text != null && _uiState.value.conversationType == "writing") {
                scheduleMaintainStructuredData(convId, toolTrace)
            }
        }
    }
}
