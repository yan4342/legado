package io.legado.app.ui.ai.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolGroupState
import io.legado.app.domain.model.textContent
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.GenerationOrchestrator
import io.legado.app.domain.usecase.InteractiveToolHandler
import io.legado.app.domain.usecase.ToolApprovalDecision
import io.legado.app.domain.usecase.ToolConfirmHandler
import io.legado.app.domain.usecase.ToolConfirmPolicy
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.UserQuestionsToolParser
import io.legado.app.domain.usecase.parseTodosJson
import io.legado.app.domain.usecase.renderTodosForPrompt
import io.legado.app.domain.usecase.ai.MsgTagParser
import io.legado.app.help.ai.AiChatAttachmentStore
import io.legado.app.help.ai.AiTokenEstimator
import io.legado.app.help.config.AppConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

// =============================================================================
// Generation: request building + prompt injection
// =============================================================================

internal suspend fun AiChatViewModel.buildGenerationRequest(
    userContent: String,
    history: List<AiChatMessageUi>,
    directedSpeakerCardId: String? = null,
    newUserParts: List<AiMessagePart>? = null,
): io.legado.app.domain.model.AiGenerateRequest {
    val state = _uiState.value
    val isWriting = state.conversationType == "writing"
    val isRoleplay = isWriting && state.writingSubMode == "roleplay"
    val modelHistory = historyForModel(history, isRoleplay)
    val toolGroupState = if (!isWriting) {
        val cid = currentConversationId.value
        if (cid != null) {
            conversationToolGroupStates.getOrPut(cid) { AiToolGroupState() }.also { generationToolGroupState = it }
        } else {
            AiToolGroupState().also { generationToolGroupState = it }
        }
    } else {
        generationToolGroupState = null
        null
    }
    // 任务清单渲染进 user 消息尾部（不动 SYSTEM 前缀，保持前缀缓存字节稳定；三模式通用）。
    val todosText = runCatching {
        val convId = currentConversationId.value
        if (convId == null) "" else renderTodosForPrompt(
            parseTodosJson(aiTodoGateway.getByConversation(convId)?.todosJson.orEmpty()),
        )
    }.getOrDefault("")
    val assemblyContext = if (isWriting) {
        buildWritingAssemblyContext(directedSpeakerCardId)
            .copy(history = modelHistory, newUserContent = userContent)
    } else {
        buildChatAssemblyContext(modelHistory, userContent)
            .copy(toolGroupState = toolGroupState)
    }.copy(todosText = todosText.takeIf { it.isNotBlank() })
    val request = generationUseCase.buildRequest(
        userContent = userContent,
        history = modelHistory,
        reasoningLevel = state.reasoningLevel,
        conversationId = currentConversationId.value,
        assemblyContext = assemblyContext,
        contextWindow = state.contextWindow,
        conversationType = state.conversationType,
        newUserParts = newUserParts,
    ).also { capturePendingPromptInjection() }
    // 追加到唯一 system 消息末尾，保持单条 SYSTEM、指令权重最高。
    // 计划指令仅 plan 模式；任务清单指令恒定注入（三模式通用）。两者均为常量文本，不破坏前缀缓存。
    val appendParts = buildList {
        if (state.outputMode == AiOutputMode.PLAN) {
            // 第一轮用计划指令；修订轮（planRevisionRound，提交意见后 pendingPlan 已清空）用修订指令。
            val isRevisionRound = planRevisionRound.also { planRevisionRound = false }
            val directiveKey = if (isRevisionRound) {
                AiPromptTemplate.PLAN_REVISION_DIRECTIVE
            } else {
                AiPromptTemplate.PLAN_MODE_SYSTEM_DIRECTIVE
            }
            promptTemplateGateway.getPrompt(directiveKey).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        } else {
            // 计划模式下专注写计划待审批，不建任务清单；批准后切 AUTO 再启用。
            promptTemplateGateway.getPrompt(AiPromptTemplate.TODO_SYSTEM_DIRECTIVE).trim()
                .takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }
    if (appendParts.isEmpty()) return request
    val append = appendParts.joinToString("\n\n")
    val messages = request.messages.toMutableList()
    val sysIdx = messages.indexOfFirst { it.role == AiMessageRole.SYSTEM }
    if (sysIdx >= 0) {
        val sys = messages[sysIdx]
        messages[sysIdx] = sys.copy(content = sys.content + "\n\n" + append)
    } else {
        messages.add(0, AiMessage(AiMessageRole.SYSTEM, append))
    }
    return request.copy(messages = messages)
}

internal fun AiChatViewModel.capturePendingPromptInjection() {
    pendingPromptInjection = generationUseCase.consumeLastPromptInjection()
        ?: pendingPromptInjection
}

// =============================================================================
// Generation: final assistant parts + reasoning + save text
// =============================================================================

internal fun AiChatViewModel.buildFinalAssistantParts(
    text: String,
    reasoning: String,
    toolTrace: ToolTraceBuilder,
    wasCancelled: Boolean,
): List<AiMessagePart> {
    toolTrace.pruneUnnamedPending()
    if (wasCancelled) {
        toolTrace.finalizeIncomplete(ToolTraceBuilder.RESULT_CANCELLED)
    } else if (toolTrace.hasIncompleteResults()) {
        toolTrace.finalizeIncomplete(ToolTraceBuilder.RESULT_NOT_COMPLETED)
    }
    val injection = pendingPromptInjection.also { pendingPromptInjection = null }
        ?: generationUseCase.consumeLastPromptInjection()
    val sideEffects = pendingSideEffects.toList().also { pendingSideEffects.clear() }
    val images = pendingStreamImages.toList().also { pendingStreamImages.clear() }
    // __game_update__ 下行：剥离尾部 JSON → 正文 + 更新载荷（推给运行中的游戏）。
    // 这里只设置非空载荷；清空由 persist 入口 / setStreamingPlaceholder 负责。
    val (cleanText, updateJson) = extractGameUpdate(text)
    if (updateJson != null) {
        _uiState.update { it.copy(gameUpdatePayload = updateJson) }
    }
    val base = generationUseCase.buildAssistantParts(
        cleanText,
        reasoning,
        toolTrace,
        injection,
        sideEffects,
        interleaveReasoning = preferInterleavedReasoning(),
    ) + images
    // publish_html_app 工具输出 → HtmlAppRef 轻量引用 part（消息 parts 里只存 appId）。
    val htmlAppRefs = base.filterIsInstance<AiMessagePart.Tool>()
        .filter { it.toolName == io.legado.app.domain.usecase.HtmlAppTools.TOOL_PUBLISH_HTML_APP }
        .mapNotNull { tool -> parseHtmlAppIdFromToolOutput(tool.output)?.let { AiMessagePart.HtmlAppRef(it) } }
    return if (htmlAppRefs.isEmpty()) base else base + htmlAppRefs
}

/** Chat mode interleaves reasoning with tools/text; writing keeps a single leading thinking block. */
internal fun AiChatViewModel.preferInterleavedReasoning(): Boolean =
    _uiState.value.conversationType != "writing"

/**
 * Text to persist for an assistant reply.
 * On stop: keep partials that have text, tools, and/or reasoning (no separate interrupted flag).
 */
internal fun AiChatViewModel.resolveAssistantSaveText(
    fullText: StringBuilder,
    wasCancelled: Boolean,
    errorMsg: String?,
    toolTrace: ToolTraceBuilder,
    fullReasoning: CharSequence = "",
): String? = when {
    fullText.isNotEmpty() -> fullText.toString()
    !wasCancelled && !errorMsg.isNullOrBlank() -> "Error: $errorMsg"
    wasCancelled && (toolTrace.size() > 0 || fullReasoning.isNotBlank()) -> ""
    else -> null
}

internal suspend fun AiChatViewModel.savePartialAssistantOnStop(
    convId: String,
    fullText: StringBuilder,
    fullReasoning: StringBuilder,
    toolTrace: ToolTraceBuilder,
    speakerCardId: String? = null,
    parentMessageId: String? = null,
) {
    val text = resolveAssistantSaveText(
        fullText,
        wasCancelled = true,
        errorMsg = null,
        toolTrace,
        fullReasoning,
    ) ?: return
    val speakerName = speakerNameForPersist(text, speakerCardId)
    runCatching {
        persistAssistantBubbles(
            convId = convId,
            parentMessageId = parentMessageId,
            rawText = text,
            reasoning = fullReasoning.toString(),
            toolTrace = toolTrace,
            wasCancelled = true,
            speakerId = speakerCardId,
            speakerName = speakerName,
            duration = 0,
        )
    }
}

// =============================================================================
// Generation: streaming
// =============================================================================

internal fun AiChatViewModel.setStreamingPlaceholder() {
    thinkingStartTime = 0L
    _uiState.update {
        it.copy(
            streamingMessage = AiChatMessageUi(
                id = "streaming_temp", role = AiMessageRole.ASSISTANT, content = "", createdAt = System.currentTimeMillis(),
            ),
            gameUpdatePayload = null,
        )
    }
}

internal fun AiChatViewModel.syncStreamingMessage(
    text: String,
    reasoning: String,
    toolTrace: ToolTraceBuilder,
    thinkingDuration: Int? = null,
) {
    // 流式展示也剥离尾部 __game_update__，避免 JSON 块闪现；解析成功即推送下行载荷。
    val (streamText, updateJson) = extractGameUpdate(text)
    if (updateJson != null) {
        _uiState.update { it.copy(gameUpdatePayload = updateJson) }
    }
    _uiState.update { s ->
        s.streamingMessage?.let { msg ->
            val stripTags = multiBubbleSplitEnabled()
            // Keep ContentSpan offsets on raw stream text; strip `<msg>` only for display.
            val parts = (toolTrace.toInterleavedParts(
                streamText,
                reasoning,
                interleaveReasoning = preferInterleavedReasoning(),
            ) + pendingStreamImages).map { part ->
                if (stripTags && part is AiMessagePart.Text) {
                    part.copy(text = MsgTagParser.stripAllMsgTags(part.text))
                } else {
                    part
                }
            }
            val rawText = parts.textContent()
            val resolvedSpeaker = resolveSpeakerCardId(rawText)
            val leadName = extractLeadingSpeaker(rawText)?.first.orEmpty()
            val speakerName = resolvedSpeaker?.let { sid ->
                speakerLookupCards().find { it.id == sid }?.name
            }.orEmpty().ifBlank { leadName }
            val displayText = cleanSpeakerPrefix(rawText, speakerName)
            s.copy(streamingMessage = msg.copy(
                content = displayText,
                speakerCardId = resolvedSpeaker,
                speakerName = speakerName,
                speakerColorIndex = (resolvedSpeaker?.hashCode() ?: speakerName.hashCode()).let {
                    kotlin.math.abs(it) % 8
                },
                reasoning = reasoning,
                toolTrace = toolTrace.toString(),
                parts = partsWithStrippedSpeaker(parts, speakerName).toImmutableList(),
                thinkingDuration = thinkingDuration ?: msg.thinkingDuration,
            ))
        } ?: s
    }
}

internal suspend fun AiChatViewModel.collectStream(
    request: io.legado.app.domain.model.AiGenerateRequest,
    fullText: StringBuilder,
    fullReasoning: StringBuilder,
    toolTrace: ToolTraceBuilder,
    conversationId: String? = currentConversationId.value,
) {
    pendingStreamImages.clear()
    pendingRawPromptEstimate = AiTokenEstimator.estimatePromptTokens(request)
    generationOrchestrator.streamInitial(
        request = request,
        toolTrace = toolTrace,
        callbacks = GenerationOrchestrator.StreamCallbacks(
            onContent = { delta ->
                if (fullText.isEmpty() && thinkingStartTime > 0) {
                    val duration = ((System.currentTimeMillis() - thinkingStartTime) / 1000).toInt()
                    fullText.append(delta)
                    syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace, duration)
                } else {
                    fullText.append(delta)
                    syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace)
                }
            },
            onReasoning = { delta ->
                if (fullReasoning.isEmpty()) thinkingStartTime = System.currentTimeMillis()
                fullReasoning.append(delta)
                syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace)
            },
            onToolTraceUpdate = { syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace) },
            onImage = { base64, mime, w, h ->
                runCatching {
                    val file = io.legado.app.help.ai.AiChatAttachmentStore.writeImageDataUri("data:$mime;base64,$base64")
                    if (file != null) {
                        val imagePart = AiMessagePart.Image(localPath = file.absolutePath, mimeType = mime, width = w, height = h)
                        pendingStreamImages.add(imagePart)
                        syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace)
                    }
                }
            },
            onUsage = { prompt, _, cacheHit ->
                AiChatGenerationUseCase.recordCacheResult(cacheHit, prompt)
                val raw = pendingRawPromptEstimate
                _uiState.update { current ->
                    val newScale = if (prompt > 0 && raw > 0) {
                        val measured = prompt.toFloat() / raw
                        (current.contextCalibrationScale * 0.7f + measured * 0.3f).coerceIn(0.5f, 2.5f)
                    } else {
                        current.contextCalibrationScale
                    }
                    current.copy(
                        contextTokensUsed = maxOf(prompt, current.contextTokensUsed),
                        contextTokensSource = AiChatUiState.CONTEXT_SOURCE_API,
                        contextInputBudget = generationUseCase.contextInputBudget(current.contextWindow)
                            .takeIf { it > 0 } ?: current.contextInputBudget,
                        contextCacheHitTokens = cacheHit,
                        cacheHitRatio = AiChatGenerationUseCase.cacheHitRatio,
                        contextCalibrationScale = newScale,
                    )
                }
                val snapshot = _uiState.value
                viewModelScope.launch {
                    persistContextUsage(
                        conversationId,
                        maxOf(prompt, snapshot.contextTokensUsed),
                        AiChatUiState.CONTEXT_SOURCE_API,
                        snapshot.contextCalibrationScale,
                    )
                }
            },
        ),
    )
}

// =============================================================================
// Generation: tool rounds
// =============================================================================

internal suspend fun AiChatViewModel.continueToolRounds(
    convId: String?,
    request: io.legado.app.domain.model.AiGenerateRequest,
    fullText: StringBuilder,
    fullReasoning: StringBuilder,
    toolTrace: ToolTraceBuilder,
) {
    val state = _uiState.value
    val interactiveHandler = object : InteractiveToolHandler {
        override suspend fun awaitResponse(call: AiToolCall): String {
            val args = runCatching {
                com.google.gson.JsonParser.parseString(call.arguments).asJsonObject
            }.getOrNull() ?: return """{"error":"Invalid questions JSON"}"""
            val parsed = UserQuestionsToolParser.parseQuestionsFromArgs(args)
            if (parsed is UserQuestionsToolParser.ParseResult.Error) {
                return """{"error":"${parsed.message}"}"""
            }
            val questions = (parsed as UserQuestionsToolParser.ParseResult.Success).questions
            val pending = PendingUserQuestionsUi(
                callId = call.id,
                questions = questions.map { question ->
                    UserQuestionUi(
                        id = question.id,
                        prompt = question.prompt,
                        options = question.options.map { UserQuestionOptionUi(it.id, it.label) }.toImmutableList(),
                        allowMultiple = question.allowMultiple,
                    )
                }.toImmutableList(),
            )
            _uiState.update { it.copy(pendingUserQuestions = pending) }
            val deferred = CompletableDeferred<String>()
            userQuestionsDeferred = deferred
            val result = deferred.await()
            userQuestionsDeferred = null
            _uiState.update { it.copy(pendingUserQuestions = null) }
            return result
        }
    }
    val confirmHandler = object : ToolConfirmHandler {
        override suspend fun awaitApproval(toolCalls: List<AiToolCall>, round: Int): ToolApprovalDecision {
            val habitCalls = toolCalls.filter { it.name == AiToolRepository.TOOL_PATCH_USER_MEMORY }
            val otherCalls = toolCalls.filter { it.name != AiToolRepository.TOOL_PATCH_USER_MEMORY }
            val skippedFeedback = linkedMapOf<String, String>()
            val habitApproved = mutableListOf<AiToolCall>()
            for (call in habitCalls) {
                if (awaitHabitMemoryConfirm(call)) {
                    habitApproved.add(call)
                } else {
                    skippedFeedback[call.id] = appCtx.getString(R.string.ai_habit_memory_skipped)
                }
            }
            if (otherCalls.isEmpty()) {
                if (habitApproved.isEmpty() && habitCalls.isNotEmpty()) {
                    val marker = if (generationStopRequested) {
                        ToolTraceBuilder.RESULT_CANCELLED
                    } else {
                        ToolTraceBuilder.formatRejectedResult(
                            appCtx.getString(R.string.ai_habit_memory_skipped),
                        )
                    }
                    habitCalls.forEach { call ->
                        toolTrace.appendResult(call.id, marker)
                    }
                    syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace)
                }
                return ToolApprovalDecision(
                    approved = habitApproved,
                    skippedFeedback = skippedFeedback,
                )
            }
            // Auto-approve tools recently approved by the user within the trust window
            val now = System.currentTimeMillis()
            val autoApproveWindowMs = 30_000L
            val autoApproved = mutableListOf<AiToolCall>()
            val needsConfirm = mutableListOf<AiToolCall>()
            for (call in otherCalls) {
                val lastApproved = recentApprovals[call.name]
                if (lastApproved != null && (now - lastApproved) < autoApproveWindowMs) {
                    autoApproved.add(call)
                } else {
                    needsConfirm.add(call)
                }
            }
            // Record timestamps for auto-approved calls
            for (call in autoApproved) {
                recentApprovals[call.name] = now
            }
            if (needsConfirm.isEmpty()) {
                Log.d("AiTool", "continueToolRounds: auto-approved ${autoApproved.size} tools")
                return ToolApprovalDecision(
                    approved = habitApproved + autoApproved,
                    skippedFeedback = skippedFeedback,
                )
            }
            Log.d("AiTool", "continueToolRounds: awaiting user approval for ${needsConfirm.size} tools (${autoApproved.size} auto-approved)")
            val combinedAutoApproved = habitApproved + autoApproved
            val combinedSkipped = skippedFeedback.toMutableMap()
            approvedToolCalls = needsConfirm
            val secretFills = mutableListOf<PendingTtsSecretFillUi>()
            val pendingItems = buildList {
                for (call in needsConfirm) {
                    val args = runCatching { com.google.gson.JsonParser.parseString(call.arguments).asJsonObject }
                        .getOrNull() ?: com.google.gson.JsonObject()
                    val built = buildPendingToolCallUi(call, args, convId)
                    built.secretFill?.let { secretFills.add(it) }
                    add(built.ui)
                }
            }.toImmutableList()
            _uiState.update {
                it.copy(
                    pendingToolConfs = pendingItems.toImmutableList(),
                    pendingToolBatchFeedback = "",
                    pendingTtsSecretFills = secretFills.toImmutableList(),
                )
            }
            val deferred = CompletableDeferred<ToolApprovalDecision>()
            confirmationDeferred = deferred
            val decision = deferred.await()
            val rejectionFeedback = pendingRejectionFeedback
            pendingRejectionFeedback = null
            clearPendingToolApprovalUi()
            if (decision.approved.isEmpty()) {
                val marker = if (generationStopRequested) {
                    ToolTraceBuilder.RESULT_CANCELLED
                } else {
                    ToolTraceBuilder.formatRejectedResult(rejectionFeedback)
                }
                needsConfirm.forEach { call ->
                    toolTrace.appendResult(call.id, marker)
                }
                syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace)
            }
            return ToolApprovalDecision(
                approved = combinedAutoApproved + decision.approved,
                skippedFeedback = combinedSkipped + decision.skippedFeedback,
            )
        }
    }
    generationOrchestrator.runToolLoop(
        convId = convId,
        request = request,
        fullText = fullText,
        fullReasoning = fullReasoning,
        toolTrace = toolTrace,
        policy = ToolConfirmPolicy(
            requireMutationApproval = when (state.outputMode) {
                AiOutputMode.AUTO -> false
                AiOutputMode.ASK -> true
                AiOutputMode.PLAN -> true
            },
            outputMode = state.outputMode.name.lowercase(),
            conversationType = state.conversationType,
        ),
        confirmHandler = confirmHandler,
        maxRounds = AppConfig.aiMaxToolRounds,
        onStreamRound = { text, reasoning ->
            syncStreamingMessage(text, reasoning, toolTrace)
        },
        executeBatch = { cId, req, text, reasoning, trace, calls, round, roundToolCalls, committedTextLen, committedReasoningLen ->
            Log.d("AiTool", "continueToolRounds: round $round, executing ${calls.size} tools: ${calls.joinToString { it.name }}")
            val enriched = enrichToolCallsWithWritingSubMode(calls)
            generationUseCase.executeToolCalls(
                request = req,
                assistantContent = text.substring(committedTextLen.coerceAtMost(text.length)),
                toolTrace = trace,
                toolCalls = enriched,
                conversationId = cId,
                conversationType = state.conversationType,
                interactiveHandler = interactiveHandler,
                onToolTraceUpdate = { syncStreamingMessage(text.toString(), reasoning.toString(), trace) },
                assistantToolCalls = roundToolCalls,
                toolGroupState = generationToolGroupState,
                webSearchArmed = state.webSearchArmed,
                reasoning = reasoning.substring(committedReasoningLen.coerceAtMost(reasoning.length)),
            )
        },
        onMaxRoundsReached = { cId, req, text, reasoning, trace, remaining, committedTextLen, committedReasoningLen ->
            flushAtMaxRounds(cId, req, text, reasoning, trace, remaining, committedTextLen, committedReasoningLen)
        },
        appendRejectedRound = { _, req, text, reasoning, trace, calls, committedTextLen, committedReasoningLen ->
            generationUseCase.appendCompletedToolRound(
                request = req,
                assistantContent = text.substring(committedTextLen.coerceAtMost(text.length)),
                toolTrace = trace,
                toolCalls = calls,
                reasoning = reasoning.substring(committedReasoningLen.coerceAtMost(reasoning.length)),
            )
        },
        needsBookshelfApproval = { call ->
            val args = runCatching {
                com.google.gson.JsonParser.parseString(call.arguments).asJsonObject
            }.getOrNull() ?: com.google.gson.JsonObject()
            bookshelfAccessPreviewer.requiresBookshelfApproval(call.name, args)
        },
    )
    generationToolGroupState = null
}

/**
 * Inject writingSubMode into patch_outline only in writing conversations
 * (sheet generate templates). Chat must omit so linear/branching follows the user request.
 */
internal fun AiChatViewModel.enrichToolCallsWithWritingSubMode(
    calls: List<io.legado.app.domain.model.AiToolCall>,
): List<io.legado.app.domain.model.AiToolCall> {
    val state = _uiState.value
    if (state.conversationType != "writing") return calls
    val subMode = state.writingSubMode.ifBlank { return calls }
    return calls.map { call ->
        if (call.name != AiToolRepository.TOOL_PATCH_OUTLINE) return@map call
        val args = runCatching {
            com.google.gson.JsonParser.parseString(call.arguments).asJsonObject
        }.getOrNull() ?: return@map call
        val existing = args.get("writingSubMode")?.asString
        if (!existing.isNullOrBlank()) return@map call
        args.addProperty("writingSubMode", subMode)
        call.copy(arguments = args.toString())
    }
}

internal suspend fun AiChatViewModel.flushAtMaxRounds(
    convId: String?,
    request: io.legado.app.domain.model.AiGenerateRequest,
    fullText: StringBuilder,
    fullReasoning: StringBuilder,
    toolTrace: ToolTraceBuilder,
    remaining: List<AiToolCall>,
    committedTextLen: Int,
    committedReasoningLen: Int,
) {
    val (mutationCalls, autoCalls) = remaining.partition { AiToolRepository.isMutationTool(it.name) }
    var currentRequest = request
    var committed = committedTextLen
    if (autoCalls.isNotEmpty()) {
        currentRequest = generationUseCase.executeToolCalls(
            request = currentRequest,
            assistantContent = fullText.substring(committed.coerceAtMost(fullText.length)),
            toolTrace = toolTrace,
            toolCalls = enrichToolCallsWithWritingSubMode(autoCalls),
            conversationId = convId,
            conversationType = _uiState.value.conversationType,
            onToolTraceUpdate = { syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace) },
            toolGroupState = generationToolGroupState,
            webSearchArmed = _uiState.value.webSearchArmed,
            reasoning = fullReasoning.substring(committedReasoningLen.coerceAtMost(fullReasoning.length)),
        )
        committed = fullText.length
    }
    if (mutationCalls.isEmpty()) return
    val isWriting = _uiState.value.conversationType == "writing"
    if (isWriting) {
        val warned = mutationCalls.map {
            it.copy(executionWarning = "max_tool_rounds_reached")
        }
        generationUseCase.executeToolCalls(
            request = currentRequest,
            assistantContent = fullText.substring(committed.coerceAtMost(fullText.length)),
            toolTrace = toolTrace,
            toolCalls = enrichToolCallsWithWritingSubMode(warned),
            conversationId = convId,
            conversationType = _uiState.value.conversationType,
            onToolTraceUpdate = { syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace) },
            toolGroupState = generationToolGroupState,
            webSearchArmed = _uiState.value.webSearchArmed,
            reasoning = fullReasoning.substring(committedReasoningLen.coerceAtMost(fullReasoning.length)),
        )
    } else {
        val errorJson = """{"error":"max_tool_rounds_reached"}"""
        generationUseCase.recordStaticToolResults(
            request = currentRequest,
            assistantContent = fullText.substring(committed.coerceAtMost(fullText.length)),
            toolTrace = toolTrace,
            toolCalls = mutationCalls,
            resultContent = errorJson,
            onToolTraceUpdate = { syncStreamingMessage(fullText.toString(), fullReasoning.toString(), toolTrace) },
            reasoning = fullReasoning.substring(committedReasoningLen.coerceAtMost(fullReasoning.length)),
        )
    }
}

// =============================================================================
// Inter-character chat: speaker detection + auto-continue
// =============================================================================

internal suspend fun AiChatViewModel.collectRecentSpeakerCardIds(recentCount: Int): Set<String> =
    getContextUiMessages()
        .takeLast(recentCount)
        .mapNotNull { msg ->
            if (msg.role != AiMessageRole.ASSISTANT) null
            else msg.speakerCardId ?: resolveSpeakerCardId(msg.content)
        }
        .toSet()

/** After the assistant responds, if inter-character chat is enabled and the response
 *  @mentions another character, automatically generate a response for that character.
 *  Stops naturally when the response no longer @mentions anyone. */
internal suspend fun AiChatViewModel.autoContinueInterCharacter(
    convId: String,
    lastSpeakerId: String?,
) {
    if (!_uiState.value.interCharacterChatEnabled) {
        Log.d("AiChat", "autoContinue: interCharacterChat disabled, skipping")
        return
    }
    val history = getContextUiMessages()
    val lastMsg = history.lastOrNull()
    if (lastMsg == null) {
        Log.d("AiChat", "autoContinue: history is empty")
        return
    }
    if (lastMsg.role != AiMessageRole.ASSISTANT) {
        Log.d("AiChat", "autoContinue: lastMsg role=${lastMsg.role}, not ASSISTANT")
        return
    }
    val actualLastSpeaker = lastMsg.speakerCardId
        ?: resolveSpeakerCardId(lastMsg.content)
        ?: lastSpeakerId
    val mentioned = findMentionedSpeakers(lastMsg.content)
    Log.d("AiChat", "autoContinue: lastMsg content='${lastMsg.content.take(80)}', mentioned=$mentioned, actualLastSpeaker=$actualLastSpeaker")
    val next = mentioned.firstOrNull { it != actualLastSpeaker }
    if (next == null) {
        Log.d("AiChat", "autoContinue: no mentioned speaker different from actualLastSpeaker")
        return
    }

    val card = aiCharacterCardGateway.getById(next)
    val cardName = card?.name ?: return
    val prevName = actualLastSpeaker?.let { aiCharacterCardGateway.getById(it)?.name } ?: "对方"

    setStreamingPlaceholder()
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()
    val toolTrace = ToolTraceBuilder()
    try {
        val request = buildGenerationRequest(
            userContent = "（${prevName} 在发言中 @${cardName}，请以 ${cardName} 的身份接话。若需要让其他角色回应，请在发言末尾 @对方角色名。回复格式：[${cardName}]: 回复内容）",
            history = history,
            directedSpeakerCardId = next,
        )
        collectStream(request, fullText, fullReasoning, toolTrace)
        continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
    } catch (_: CancellationException) {
        _uiState.update { it.copy(streamingMessage = null) }
        savePartialAssistantOnStop(convId, fullText, fullReasoning, toolTrace, speakerCardId = next)
        return
    }

    val text = fullText.toString()
    if (text.isNotBlank()) {
        val finalText = maybePostEdit(text, fullReasoning.toString(), toolTrace)
        val speakerName = speakerNameForPersist(finalText, next)
        persistAssistantBubbles(
            convId = convId,
            parentMessageId = null,
            rawText = finalText,
            reasoning = fullReasoning.toString(),
            toolTrace = toolTrace,
            wasCancelled = false,
            speakerId = next,
            speakerName = speakerName,
            duration = 0,
        )
        autoContinueInterCharacter(convId, next)
    } else {
        _uiState.update { it.copy(streamingMessage = null) }
    }
}

/** Generate a response for a specific character, then chain via autoContinueInterCharacter. */
internal suspend fun AiChatViewModel.forceCharacterResponse(convId: String, cardId: String) {
    if (cardId in collectRecentSpeakerCardIds(6)) {
        Log.d("AiChat", "forceCharacterResponse: $cardId already spoke recently, skipping")
        return
    }
    val card = aiCharacterCardGateway.getById(cardId) ?: return
    val cardName = card.name

    setStreamingPlaceholder()
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()
    val toolTrace = ToolTraceBuilder()
    try {
        val request = buildGenerationRequest(
            userContent = "（请以 ${cardName} 的身份发言。若需要让其他角色回应，请在末尾 @对方角色名。回复格式：[${cardName}]: 回复内容）",
            history = getContextUiMessages(),
            directedSpeakerCardId = cardId,
        )
        collectStream(request, fullText, fullReasoning, toolTrace)
        continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
    } catch (_: CancellationException) {
        _uiState.update { it.copy(streamingMessage = null) }
        savePartialAssistantOnStop(convId, fullText, fullReasoning, toolTrace, speakerCardId = cardId)
        return
    }

    val text = fullText.toString()
    if (text.isNotBlank()) {
        val finalText = maybePostEdit(text, fullReasoning.toString(), toolTrace)
        val speakerName = speakerNameForPersist(finalText, cardId)
        persistAssistantBubbles(
            convId = convId,
            parentMessageId = null,
            rawText = finalText,
            reasoning = fullReasoning.toString(),
            toolTrace = toolTrace,
            wasCancelled = false,
            speakerId = cardId,
            speakerName = speakerName,
            duration = 0,
        )
        autoContinueInterCharacter(convId, cardId)
    } else {
        _uiState.update { it.copy(streamingMessage = null) }
    }
}

// =============================================================================
// Persist: assistant bubble save + multi-bubble split
// =============================================================================

/**
 * Persist assistant reply; when multi-bubble is on, split `<msg>` into sibling messages
 * (same parent) with staggered save delay. Tools/reasoning stay on the first bubble,
 * in the same interleaved order as streaming.
 * @return display text of the first bubble (for titles / follow-ups)
 */
internal suspend fun AiChatViewModel.persistAssistantBubbles(
    convId: String,
    parentMessageId: String?,
    rawText: String,
    reasoning: String,
    toolTrace: ToolTraceBuilder,
    wasCancelled: Boolean,
    speakerId: String?,
    speakerName: String,
    duration: Int,
    useRegenerate: Boolean = false,
): String = withContext(NonCancellable) {
    // Runs in NonCancellable: this is called from a finally/catch after the streaming job
    // was cancelled (stop / leave screen), so plain suspending Room calls would otherwise
    // throw CancellationException and drop the partially streamed reply entirely.
    persistAssistantBubblesInternal(
        convId, parentMessageId, rawText, reasoning, toolTrace,
        wasCancelled, speakerId, speakerName, duration, useRegenerate,
    )
}

internal suspend fun AiChatViewModel.persistAssistantBubblesInternal(
    convId: String,
    parentMessageId: String?,
    rawText: String,
    reasoning: String,
    toolTrace: ToolTraceBuilder,
    wasCancelled: Boolean,
    speakerId: String?,
    speakerName: String,
    duration: Int,
    useRegenerate: Boolean = false,
): String {
    // 剥离尾部 __game_update__，保证正文 + 多气泡切分都用干净文本。
    val (cleanRawText, updateJson) = extractGameUpdate(rawText)
    if (updateJson != null) {
        _uiState.update { it.copy(gameUpdatePayload = updateJson) }
    }
    val fullParts = assembleAssistantPartsForPersist(
        cleanRawText, reasoning, toolTrace, wasCancelled, speakerName,
    )
    val bubbles = if (multiBubbleSplitEnabled()) {
        MsgTagParser.split(cleanRawText).ifEmpty {
            listOf(MsgTagParser.Bubble(MsgTagParser.stripAllMsgTags(cleanRawText)))
        }
    } else {
        listOf(
            MsgTagParser.Bubble(
                MsgTagParser.stripAllMsgTags(cleanRawText).ifBlank { cleanRawText },
            ),
        )
    }
    val cleanedBubbles = bubbles.map { b ->
        val t = cleanSpeakerPrefix(b.text, speakerName).ifBlank { b.text.trim() }
        b.copy(text = t)
    }.filter { it.text.isNotBlank() || it.longForm }
    val effective = cleanedBubbles.ifEmpty {
        listOf(MsgTagParser.Bubble(partsWithStrippedSpeaker(fullParts, speakerName).textContent()))
    }

    // Do not clear streamingMessage before the saved row is reflected in UI — that gap
    // shrinks displayMessages to the previous item and LazyColumn stick-to-bottom
    // (esp. writing mode) rolls the viewport back one message.

    suspend fun saveFirst(parts: List<AiMessagePart>): io.legado.app.data.entities.AiChatMessage {
        return if (useRegenerate && parentMessageId != null) {
            aiChatGateway.saveRegeneratedMessage(
                convId, AiMessageRole.ASSISTANT, parts, parentMessageId, duration,
                speakerCardId = speakerId,
            )
        } else {
            aiChatGateway.saveMessage(
                convId, AiMessageRole.ASSISTANT, parts, parentMessageId, duration,
                speakerCardId = speakerId,
                branchIndex = 0,
            )
        }
    }

    if (effective.size <= 1) {
        // Prefer in-place tag strip so interleaved R/T/Text order matches the stream UI.
        val parts = partsWithStrippedMsgTags(fullParts).ifEmpty { fullParts }
        val saved = runCatching { saveFirst(parts) }.getOrNull()
        if (saved != null) {
            replaceStreamingWithSaved(saved)
            // 回填 HtmlApp messageId；开启 auto-launch 则自动打开播放器。
            runCatching { postPersistHtmlApps(saved) }
        } else {
            _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
        }
        return effective.firstOrNull()?.text.orEmpty().ifBlank { parts.textContent() }
    }

    var turnBranchIndex = 0
    for ((index, bubble) in effective.withIndex()) {
        if (index > 0) kotlinx.coroutines.delay(bubbleRevealDelayMs(bubble.text))
        val parts = if (index == 0) {
            partsWithSingleTextBubble(fullParts, bubble.text)
        } else {
            listOf(AiMessagePart.Text(bubble.text))
        }
        if (index == 0) {
            // The streaming placeholder still carries the full reply text while the
            // multi-bubble reveal saves the first bubble. Shrink the placeholder to
            // this bubble so displayMessages' dedup (content equality) hides it the
            // moment the saved row lands — otherwise the full text flashes beside the
            // partial bubble as a duplicate ("每条正文都比上一条多出一段").
            _uiState.update { s ->
                s.streamingMessage?.let { msg ->
                    s.copy(streamingMessage = msg.copy(
                        content = bubble.text,
                        parts = parts.toImmutableList(),
                    ))
                } ?: s
            }
            val saved = runCatching { saveFirst(parts) }.getOrNull()
            turnBranchIndex = saved?.branchIndex ?: 0
            if (saved != null) {
                replaceStreamingWithSaved(saved)
                runCatching { postPersistHtmlApps(saved) }
            } else {
                _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
            }
        } else {
            val saved = runCatching {
                aiChatGateway.saveMessage(
                    convId, AiMessageRole.ASSISTANT, parts, parentMessageId, 0,
                    speakerCardId = speakerId,
                    branchIndex = turnBranchIndex,
                )
            }.getOrNull()
            if (saved != null) appendSavedAssistantMessage(saved)
        }
    }
    return effective.first().text
}

/**
 * Atomically swap the streaming placeholder for the persisted assistant row so
 * [displayMessages] never briefly ends on the previous message.
 */
internal suspend fun AiChatViewModel.replaceStreamingWithSaved(saved: io.legado.app.data.entities.AiChatMessage) {
    val ui = messageEntityToUi(saved).let { msg ->
        if (msg.role == AiMessageRole.ASSISTANT && msg.parentMessageId != null) {
            val counts = currentConversationId.value?.let { cid ->
                runCatching { aiChatGateway.getBranchCounts(cid) }.getOrNull()
            }
            msg.copy(totalBranches = counts?.get(msg.parentMessageId) ?: 1)
        } else {
            msg
        }
    }
    val regenId = _uiState.value.regeneratingMessageId
    recentMessages = buildList {
        for (m in recentMessages) {
            if (m.id == regenId || m.id == saved.id) continue
            add(m)
        }
        add(ui)
    }
    olderMessages = olderMessages.filter { it.id != saved.id && it.id != regenId }
    _uiState.update {
        it.copy(
            messages = allLoadedMessages.toImmutableList(),
            streamingMessage = null,
            regeneratingMessageId = null,
        )
    }
}

internal suspend fun AiChatViewModel.appendSavedAssistantMessage(saved: io.legado.app.data.entities.AiChatMessage) {
    val ui = messageEntityToUi(saved).let { msg ->
        if (msg.role == AiMessageRole.ASSISTANT && msg.parentMessageId != null) {
            val counts = currentConversationId.value?.let { cid ->
                runCatching { aiChatGateway.getBranchCounts(cid) }.getOrNull()
            }
            msg.copy(totalBranches = counts?.get(msg.parentMessageId) ?: msg.totalBranches.coerceAtLeast(1))
        } else {
            msg
        }
    }
    // 不能先守卫再追加:saveMessage 落库后 DB Flow 可能已把同 id 行合入 recentMessages,
    // 而守卫与追加之间隔着 messageEntityToUi 的挂起点,竞态会让同 id 出现两次。
    // 改为剔除-再-追加,即使 Flow 已合入也只会被替换而非重复。
    recentMessages = buildList {
        for (m in recentMessages) {
            if (m.id == saved.id) continue
            add(m)
        }
        add(ui)
    }
    olderMessages = olderMessages.filter { it.id != saved.id }
    _uiState.update {
        it.copy(messages = allLoadedMessages.toImmutableList())
    }
}

// =============================================================================
// Multi-bubble protocol: split + strip + reveal delay
// =============================================================================

/**
 * `<msg>` strip/split follows the prompt pipeline block only (chat + writing roleplay).
 */
internal fun AiChatViewModel.multiBubbleSplitEnabled(): Boolean =
    _uiState.value.multiBubbleProtocolEnabled

internal suspend fun AiChatViewModel.refreshMultiBubbleProtocolEnabled() {
    val enabled = resolveMultiBubbleProtocolEnabled()
    _uiState.update { it.copy(multiBubbleProtocolEnabled = enabled) }
}

internal suspend fun AiChatViewModel.resolveMultiBubbleProtocolEnabled(): Boolean {
    val s = _uiState.value
    val mode = when {
        s.conversationType == "chat" -> PromptPipelineMode.Chat
        s.conversationType == "writing" && s.writingSubMode == "roleplay" ->
            PromptPipelineMode.WritingRoleplay
        else -> return false
    }
    val preset = promptPipelineGateway.getPresetForMode(mode) ?: return false
    return preset.blocks.any {
        it.id == PromptBlockId.MultiBubbleProtocol && it.enabled
    }
}

/**
 * Keep Reasoning/Tool (and other non-text) in their interleaved positions.
 * Put [text] at the first Text slot and drop subsequent Text parts so multi-bubble
 * siblings do not duplicate body while tools stay on the first message.
 */
internal fun AiChatViewModel.partsWithSingleTextBubble(
    fullParts: List<AiMessagePart>,
    text: String,
): List<AiMessagePart> {
    var textPlaced = false
    val out = ArrayList<AiMessagePart>(fullParts.size)
    for (part in fullParts) {
        when (part) {
            is AiMessagePart.Text -> {
                if (!textPlaced) {
                    if (text.isNotBlank()) out.add(AiMessagePart.Text(text))
                    textPlaced = true
                }
            }
            else -> out.add(part)
        }
    }
    if (!textPlaced && text.isNotBlank()) out.add(AiMessagePart.Text(text))
    return out
}

/**
 * Strip `<msg>` tags inside each Text part without changing part order
 * (preserves thinking → tool → text interleaving).
 */
internal fun AiChatViewModel.partsWithStrippedMsgTags(fullParts: List<AiMessagePart>): List<AiMessagePart> =
    fullParts.mapNotNull { part ->
        when (part) {
            is AiMessagePart.Text -> {
                val cleaned = MsgTagParser.stripAllMsgTags(part.text)
                if (cleaned.isBlank()) null else part.copy(text = cleaned)
            }
            else -> part
        }
    }

internal fun AiChatViewModel.bubbleRevealDelayMs(text: String): Long =
    (80L + text.length.coerceAtMost(120) * 2L).coerceIn(80L, 350L)
