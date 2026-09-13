package io.legado.app.domain.usecase

import android.util.Log
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.model.AiBuiltinTool
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.toolParts
import io.legado.app.domain.model.textContent
import io.legado.app.domain.model.reasoningContent
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.prompt.ContextBudgeter
import io.legado.app.domain.prompt.InChatInjection
import io.legado.app.domain.prompt.PromptAssembler
import io.legado.app.domain.prompt.PromptAssemblyContext
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import io.legado.app.help.ai.AiTokenEstimator
import kotlinx.collections.immutable.toImmutableList
import splitties.init.appCtx
import kotlin.coroutines.cancellation.CancellationException

data class AiContextUsageEstimate(
    val promptTokens: Int,
    val rawPromptTokens: Int,
    val inputBudget: Int,
    val contextWindow: Int,
    val slices: List<AiContextUsageSlice> = emptyList(),
) {
    val usageRatio: Float
        get() = if (inputBudget > 0) (promptTokens.toFloat() / inputBudget).coerceIn(0f, 1f) else 0f
}

enum class AiContextUsageCategory {
    SYSTEM,
    RULES,
    CHARACTER,
    KNOWLEDGE,
    HISTORY,
    DRAFT,
    TOOLS,
}

data class AiContextUsageSlice(
    val category: AiContextUsageCategory,
    val tokens: Int,
    /** Optional detail, e.g. block ids joined. */
    val detail: String = "",
)

/**
 * Encapsulates chat generation logic: request building, streaming, tool execution loop.
 * ViewModel only handles UI state updates.
 */
class AiChatGenerationUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiMemoryGateway: AiMemoryGateway,
    private val aiWorldBookGateway: AiWorldBookGateway,
    private val aiMemoryTableGateway: AiMemoryTableGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val promptAssembler: PromptAssembler,
) {

    /** Last prompt-injection snapshot from assembly; cleared via [consumeLastPromptInjection]. */
    @Volatile
    var lastPromptInjection: AiMessagePart.PromptInjection? = null
        private set

    /**
     * Byte-stable SYSTEM prefix (after approval-policy decoration) of the most recent
     * assembled chat request. Reused by [compressHistory] so the auxiliary compression
     * call shares the same system prefix as follow-up main requests and the provider's
     * KV / prompt cache hits on it (DSH "aux call reuses the routed request's prefix").
     */
    @Volatile
    private var lastSystemPrefix: String? = null

    fun consumeLastPromptInjection(): AiMessagePart.PromptInjection? {
        val value = lastPromptInjection
        lastPromptInjection = null
        return value
    }

    suspend fun buildRequestWithAssembly(
        assemblyContext: PromptAssemblyContext,
        userContent: String,
        history: List<AiChatMessageUi>,
        reasoningLevel: AiReasoningLevel,
        conversationId: String? = assemblyContext.conversationId,
        newUserParts: List<AiMessagePart>? = null,
    ): AiGenerateRequest {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: error("Please configure a default AI model first")
        return AiGenerateRequest(
            model = preset.model,
            messages = buildRequestMessagesFromAssembly(
                assemblyContext,
                userContent,
                history,
                newUserParts = newUserParts,
                capabilities = preset.model.capabilities,
                modelCapabilities = preset.model.capabilities,
            ),
            params = preset.model.defaultParams.copy(reasoningLevel = reasoningLevel),
            tools = aiToolGateway.availableTools(
                assemblyContext.conversationType,
                assemblyContext.webSearchArmed,
                activeToolGroupsFor(assemblyContext),
                modelCapabilities = preset.model.capabilities,
                outputMode = assemblyContext.outputMode,
                hasActivePlan = assemblyContext.hasActivePlan,
            ),
            builtinTools = nativeWebSearchBuiltinTools(preset.model),
            callMeta = AiCallMeta(AiCallSource.CHAT, conversationId),
        )
    }

    suspend fun buildRequest(
        userContent: String,
        history: List<AiChatMessageUi>,
        reasoningLevel: AiReasoningLevel,
        conversationId: String? = null,
        systemPromptOverride: String? = null,
        assemblyContext: PromptAssemblyContext? = null,
        contextWindow: Int = 0,
        conversationType: String = "chat",
        webSearchArmed: Boolean = false,
        newUserParts: List<AiMessagePart>? = null,
    ): AiGenerateRequest {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: error("Please configure a default AI model first")
        val armed = assemblyContext?.webSearchArmed ?: webSearchArmed
        val type = assemblyContext?.conversationType ?: conversationType
        return AiGenerateRequest(
            model = preset.model,
            messages = buildRequestMessages(
                newContent = userContent,
                history = history,
                conversationId = conversationId,
                systemPromptOverride = systemPromptOverride,
                assemblyContext = assemblyContext,
                contextWindow = contextWindow,
                conversationType = type,
                webSearchArmed = armed,
                newUserParts = newUserParts,
                capabilities = preset.model.capabilities,
                modelCapabilities = preset.model.capabilities,
            ),
            params = preset.model.defaultParams.copy(reasoningLevel = reasoningLevel),
            tools = aiToolGateway.availableTools(
                type,
                armed,
                activeToolGroupsFor(assemblyContext, type),
                modelCapabilities = preset.model.capabilities,
                outputMode = assemblyContext?.outputMode ?: "ask",
                hasActivePlan = assemblyContext?.hasActivePlan ?: false,
            ),
            builtinTools = nativeWebSearchBuiltinTools(preset.model),
            callMeta = AiCallMeta(AiCallSource.CHAT, conversationId),
        )
    }

    suspend fun collectStream(
        request: AiGenerateRequest,
        toolTrace: ToolTraceBuilder,
        onContent: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
        onToolTraceUpdate: suspend () -> Unit,
        onUsage: suspend (promptTokens: Int, completionTokens: Int, cacheHitTokens: Int) -> Unit = { _, _, _ -> },
        onImage: suspend (base64: String, mimeType: String, width: Int?, height: Int?) -> Unit = { _, _, _, _ -> },
    ) {
        Log.d(TAG, "collectStream: start, traceSize=${toolTrace.size()}")
        Log.d(
            TAG,
            "toolsOffered=${request.tools.size} names=${request.tools.joinToString { it.name }}",
        )
        aiTextGateway.generateStream(request).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> {
                    toolTrace.recordTextDelta(event.text.length)
                    onContent(event.text)
                }
                is AiStreamEvent.Reasoning -> {
                    toolTrace.recordReasoning(event.text)
                    onReasoning(event.text)
                }
                is AiStreamEvent.Image -> {
                    toolTrace.recordImage(event.mimeType)
                    onImage(event.base64, event.mimeType, event.width, event.height)
                }
                is AiStreamEvent.ToolCallDelta -> {
                    toolTrace.append(event)
                    // Log only the tool's first appearance (name + id); argument deltas arrive in
                    // hundreds of chunks and would spam logcat.
                    if (event.name?.isNotBlank() == true) {
                        Log.d(TAG, "collectStream: ToolCallDelta id=${event.id} index=${event.index} name=${event.name}")
                    }
                    onToolTraceUpdate()
                }
                is AiStreamEvent.Usage -> onUsage(event.promptTokens, event.completionTokens, event.cacheHitTokens)
            }
        }
        Log.d(TAG, "collectStream: end, traceSize=${toolTrace.size()} pending=${toolTrace.pendingToolCalls().size}")
    }

    suspend fun executeToolCalls(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        conversationId: String? = null,
        conversationType: String? = null,
        interactiveHandler: InteractiveToolHandler? = null,
        onToolTraceUpdate: suspend () -> Unit,
        /**
         * Tool calls to put on the assistant message. When partially approving a batch,
         * pass the full original list so skipped tools still have matching results.
         */
        assistantToolCalls: List<AiToolCall>? = null,
        toolGroupState: io.legado.app.domain.model.AiToolGroupState? = null,
        webSearchArmed: Boolean = false,
        /** Reasoning text emitted in THIS round (before its tool calls). DeepSeek Responses
         *  requires it to be passed back on tool-call rounds in thinking mode. */
        reasoning: String = "",
    ): AiGenerateRequest {
        Log.d(TAG, "executeToolCalls: executing ${toolCalls.size} tools: ${toolCalls.joinToString { "${it.name}(${it.id})" }}")
        val batchId = java.util.UUID.randomUUID().toString()
        val pendingNormal = mutableListOf<AiToolCall>()
        suspend fun flushNormal() {
            if (pendingNormal.isEmpty()) return
            val batch = pendingNormal.toList()
            pendingNormal.clear()
            coroutineScope {
                batch.map { toolCall ->
                    async {
                        executeSingleToolCall(
                            toolCall, batchId, conversationId, conversationType,
                            interactiveHandler, toolTrace, onToolTraceUpdate, toolGroupState,
                        )
                    }
                }.awaitAll()
            }
        }
        for (toolCall in toolCalls) {
            if (AiToolRepository.isInteractiveTool(toolCall.name)) {
                flushNormal()
                executeSingleToolCall(
                    toolCall, batchId, conversationId, conversationType,
                    interactiveHandler, toolTrace, onToolTraceUpdate, toolGroupState,
                )
            } else {
                pendingNormal.add(toolCall)
            }
        }
        flushNormal()
        val forAssistant = assistantToolCalls ?: toolCalls
        val toolResultMessages = forAssistant.map { call ->
            val content = toolTrace.resultOf(call.id).orEmpty().ifBlank {
                ToolTraceBuilder.RESULT_REJECTED
            }
            AiMessage(
                role = AiMessageRole.TOOL,
                content = content,
                toolCallId = call.id,
                name = call.name,
            )
        }
        val withMessages = request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = forAssistant,
                    reasoning = reasoning,
                ) +
                toolResultMessages
        )
        return refreshToolsAfterGroupChange(
            request = withMessages,
            conversationType = conversationType,
            webSearchArmed = webSearchArmed,
            toolGroupState = toolGroupState,
        )
    }

    private suspend fun executeSingleToolCall(
        toolCall: AiToolCall,
        batchId: String,
        conversationId: String?,
        conversationType: String?,
        interactiveHandler: InteractiveToolHandler?,
        toolTrace: ToolTraceBuilder,
        onToolTraceUpdate: suspend () -> Unit,
        toolGroupState: io.legado.app.domain.model.AiToolGroupState? = null,
    ): AiMessage {
        var call = if (toolCall.sourceConversationId == null && conversationId != null) {
            toolCall.copy(sourceConversationId = conversationId)
        } else toolCall
        call = call.copy(batchId = batchId, conversationType = conversationType)
        Log.d(TAG, "executeToolCalls: [${call.name}] id=${call.id} convId=${call.sourceConversationId} args=${call.arguments.take(200)}")
        toolTrace.setExecuting(call.id)
        onToolTraceUpdate()
        val startMs = System.currentTimeMillis()
        return try {
            val truncated = if (AiToolRepository.isInteractiveTool(call.name)) {
                val args = runCatching {
                    com.google.gson.JsonParser.parseString(call.arguments).asJsonObject
                }.getOrElse {
                    return toolErrorMessage(call, """{"error":"Invalid questions JSON"}""", toolTrace, onToolTraceUpdate, startMs)
                }
                when (val parsed = UserQuestionsToolParser.parseQuestionsFromArgs(args)) {
                    is UserQuestionsToolParser.ParseResult.Error ->
                        return toolErrorMessage(call, """{"error":"${parsed.message}"}""", toolTrace, onToolTraceUpdate, startMs)
                    is UserQuestionsToolParser.ParseResult.Success -> {
                        val handler = interactiveHandler
                            ?: return toolErrorMessage(call, """{"error":"Interactive tool unavailable"}""", toolTrace, onToolTraceUpdate, startMs)
                        handler.awaitResponse(call).truncateToolOutput()
                    }
                }
            } else {
                val result = aiToolGateway.execute(
                    call,
                    onProgress = { fraction, label ->
                        toolTrace.setProgress(call.id, fraction, label)
                        onToolTraceUpdate()
                    },
                    toolGroupState = toolGroupState,
                )
                val elapsed = System.currentTimeMillis() - startMs
                Log.d(TAG, "executeToolCalls: [${toolCall.name}] done in ${elapsed}ms, resultLen=${result.content.length}")
                // B1: spill oversized full-text tool results to disk, keep only a bounded head/tail
                // + locator in-context (mirrors DSH spill-policy). Preview is then truncated as usual.
                io.legado.app.help.ai.ToolResultSpillStore
                    .maybeSpill(result.content, "tool_${call.id}")
                    .truncateToolOutput()
            }
            // B2: repeat-tool reminder (DSH guard/repeat-tool-reminder). Prepends escalating
            // guidance when the same (tool, normalized args) fires consecutively in one chat.
            val reminded = if (conversationType == "chat") {
                RepeatToolReminder.maybeRemind(
                    content = truncated,
                    conversationId = call.sourceConversationId,
                    tool = call.name,
                    args = call.arguments,
                )
            } else {
                truncated
            }
            toolTrace.appendResult(call.id, reminded)
            onToolTraceUpdate()
            AiMessage(
                role = AiMessageRole.TOOL,
                content = reminded,
                toolCallId = call.id,
                name = call.name,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Error: ${e.message ?: e.javaClass.simpleName}"
            toolErrorMessage(call, message, toolTrace, onToolTraceUpdate, startMs, logAsWarning = true)
        }
    }

    /**
     * Provider-native built-in tools for the request, driven by the model capability (chat only).
     * Tuning fields ride [io.legado.app.domain.model.AiBuiltinTool.params] and are sent only when
     * explicitly configured — defaults keep the bare `{"type":"web_search"}` tool object, so
     * unconfigured users send byte-identical requests to before.
     */
    private fun nativeWebSearchBuiltinTools(model: AiModelConfig): List<AiBuiltinTool> {
        if (AiCapability.WEB_SEARCH !in model.capabilities) return emptyList()
        val params = LinkedHashMap<String, Any?>()
        io.legado.app.help.config.AppConfig.aiNativeSearchMaxUses
            .takeIf { it > 0 }
            ?.let { params["max_uses"] = it }
        io.legado.app.help.config.AppConfig.aiNativeSearchContextSize
            .takeIf { it.isNotBlank() }
            ?.let { params["search_context_size"] = it }
        val domains = io.legado.app.help.config.AppConfig.aiNativeSearchAllowedDomains
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (domains.isNotEmpty()) params["filters"] = mapOf("allowed_domains" to domains)
        return listOf(AiBuiltinTool(type = "web_search", params = params))
    }

    private fun activeToolGroupsFor(
        assemblyContext: PromptAssemblyContext?,
        conversationType: String = assemblyContext?.conversationType ?: "chat",
    ): Set<String>? {
        if (conversationType != "chat") return null
        return assemblyContext?.toolGroupState?.snapshot()
            ?: io.legado.app.domain.model.AiToolGroup.DEFAULT_ACTIVE_IDS
    }

    private fun refreshToolsAfterGroupChange(
        request: AiGenerateRequest,
        conversationType: String?,
        webSearchArmed: Boolean,
        toolGroupState: io.legado.app.domain.model.AiToolGroupState?,
    ): AiGenerateRequest {
        if (conversationType != "chat" || toolGroupState == null) return request
        return request.copy(
            tools = aiToolGateway.availableTools(
                conversationType,
                webSearchArmed,
                toolGroupState.snapshot(),
                modelCapabilities = request.model.capabilities,
            ),
        )
    }

    private suspend fun toolErrorMessage(
        call: AiToolCall,
        message: String,
        toolTrace: ToolTraceBuilder,
        onToolTraceUpdate: suspend () -> Unit,
        startMs: Long,
        logAsWarning: Boolean = false,
    ): AiMessage {
        val elapsed = System.currentTimeMillis() - startMs
        if (logAsWarning) {
            Log.w(TAG, "executeToolCalls: [${call.name}] failed in ${elapsed}ms: $message")
        }
        toolTrace.appendResult(call.id, message)
        onToolTraceUpdate()
        return AiMessage(
            role = AiMessageRole.TOOL,
            content = message,
            toolCallId = call.id,
            name = call.name,
        )
    }

    suspend fun recordStaticToolResults(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        resultContent: String,
        onToolTraceUpdate: suspend () -> Unit,
        /** Reasoning text that accompanied this assistant message (pass back in thinking mode). */
        reasoning: String = "",
    ): AiGenerateRequest {
        val toolResultMessages = toolCalls.map { call ->
            toolTrace.setExecuting(call.id)
            onToolTraceUpdate()
            toolTrace.appendResult(call.id, resultContent)
            onToolTraceUpdate()
            AiMessage(
                role = AiMessageRole.TOOL,
                content = resultContent,
                toolCallId = call.id,
                name = call.name,
            )
        }
        return request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = toolCalls,
                    reasoning = reasoning,
                ) +
                toolResultMessages
        )
    }

    /** Append assistant tool-call round + recorded trace results to the request (e.g. after user rejection). */
    fun appendCompletedToolRound(
        request: AiGenerateRequest,
        assistantContent: String,
        toolTrace: ToolTraceBuilder,
        toolCalls: List<AiToolCall>,
        /** Reasoning text that accompanied this assistant message (pass back in thinking mode). */
        reasoning: String = "",
    ): AiGenerateRequest {
        val toolResultMessages = toolCalls.map { call ->
            val content = toolTrace.resultOf(call.id).orEmpty().ifBlank { ToolTraceBuilder.RESULT_REJECTED }
            AiMessage(
                role = AiMessageRole.TOOL,
                content = content,
                toolCallId = call.id,
                name = call.name,
            )
        }
        return request.copy(
            messages = request.messages +
                AiMessage(
                    role = AiMessageRole.ASSISTANT,
                    content = assistantContent,
                    toolCalls = toolCalls,
                    reasoning = reasoning,
                ) +
                toolResultMessages
        )
    }

    fun buildAssistantParts(
        text: String,
        reasoning: String,
        toolTrace: ToolTraceBuilder,
        promptInjection: AiMessagePart.PromptInjection? = null,
        sideEffects: List<AiMessagePart.SideEffect> = emptyList(),
        interleaveReasoning: Boolean = false,
    ): List<AiMessagePart> {
        val parts = toolTrace.toInterleavedParts(text, reasoning, interleaveReasoning)
        val prefix = buildList {
            if (promptInjection != null && promptInjection.blocks.isNotEmpty()) {
                add(promptInjection)
            }
            addAll(sideEffects)
        }
        return if (prefix.isEmpty()) parts else prefix + parts
    }

    suspend fun generateTitle(
        userContent: String,
        assistantContent: String,
        reasoningLevel: AiReasoningLevel
    ): String {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured")
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(
            promptTemplateGateway.getPrompt(AiPromptTemplate.TITLE_GENERATION_PROMPT),
        )
        val user = buildString {
            append("用户：").append(userContent.take(500)).append('\n')
            append("助手：").append(assistantContent.take(500))
        }
        val request = AiGenerateRequest(
            model = preset.model,
            messages = io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(system, user),
            params = preset.model.defaultParams.copy(reasoningLevel = reasoningLevel),
            callMeta = AiCallMeta(AiCallSource.TITLE),
        )
        val result = aiTextGateway.generate(request)
        return result.getOrNull()?.text?.trim()?.take(30) ?: userContent.take(20)
    }

    /** Estimate prompt tokens for the next request, using the same trim rules as [buildRequest]. */
    suspend fun estimateContextUsage(
        history: List<AiChatMessageUi>,
        conversationId: String? = null,
        systemPromptOverride: String? = null,
        assemblyContext: PromptAssemblyContext? = null,
        contextWindow: Int = 0,
        conversationType: String = "chat",
        pendingUserContent: String = "",
        calibrationScale: Float = 1f,
    ): AiContextUsageEstimate {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
        val effectiveAssembly = assemblyContext ?: if (
            systemPromptOverride == null && conversationType == "chat"
        ) {
            PromptAssemblyContext(
                pipelineMode = PromptPipelineMode.Chat,
                conversationId = conversationId,
                conversationType = conversationType,
                history = history,
                newUserContent = pendingUserContent,
                contextWindow = contextWindow,
            )
        } else {
            null
        }

        val slices = mutableListOf<AiContextUsageSlice>()
        val messages: List<AiMessage>
        val tools = aiToolGateway.availableTools(
            conversationType,
            effectiveAssembly?.webSearchArmed == true,
            activeToolGroupsFor(effectiveAssembly, conversationType),
            modelCapabilities = preset?.model?.capabilities.orEmpty(),
        )

        if (effectiveAssembly != null) {
            val assembled = promptAssembler.assemble(effectiveAssembly)
            lastPromptInjection = assembled.toPromptInjectionPart()
            val system = withToolApprovalSystemPolicy(
                assembled.systemPrompt,
                effectiveAssembly.conversationType,
                tools.map { it.name },
                effectiveAssembly.outputMode,
            )
            val systemTokens = estimateTokens(system) + AiTokenEstimator.framingTokens()
            val effectivePending = mergeUserPrefix(assembled.userPrefix, pendingUserContent)
            val newContentTokens = estimateTokens(effectivePending) +
                if (effectivePending.isNotBlank()) AiTokenEstimator.framingTokens() else 0
            val toolsTokens = AiTokenEstimator.estimateToolsTokens(tools)
            val availableTokens = if (effectiveAssembly.contextWindow > 0) {
                (ContextBudgeter.trimBudget(effectiveAssembly.contextWindow) -
                    systemTokens - newContentTokens - toolsTokens).coerceAtLeast(0)
            } else {
                Int.MAX_VALUE
            }
            val trimmedHistory = history.trimForContext(availableTokens, effectiveAssembly.conversationType)
            // Inject in-chat at AiChatMessageUi level (same as buildRequestMessagesFromAssembly)
            val injectedHistory = injectInChatMessagesUi(trimmedHistory, assembled.inChatInjections)
            val historyMessages = injectedHistory.flatMap { msg ->
                when (msg.role) {
                    AiMessageRole.USER -> listOf(
                        AiMessage(
                            AiMessageRole.USER,
                            formatUserContentForModel(msg.content, effectiveAssembly.conversationType),
                        ),
                    )
                    AiMessageRole.ASSISTANT -> msg.toRequestMessages()
                    else -> null
                }.orEmpty()
            }
            messages = listOf(AiMessage(AiMessageRole.SYSTEM, system)) +
                historyMessages +
                if (effectivePending.isNotBlank()) {
                    listOf(
                        AiMessage(
                            AiMessageRole.USER,
                            formatUserContentForModel(effectivePending, effectiveAssembly.conversationType),
                        ),
                    )
                } else {
                    emptyList()
                }

            // Block-level slices (Cursor-like: system / rules / character / knowledge)
            assembled.blocks
                .groupBy { it.spec.id.toContextUsageCategory() }
                .entries
                .sortedBy { it.key.ordinal }
                .forEach { (category, blocks) ->
                    val tokens = blocks.sumOf { it.estimatedTokens }
                    if (tokens > 0) {
                        slices += AiContextUsageSlice(
                            category = category,
                            tokens = tokens,
                            detail = blocks.joinToString(", ") { it.spec.id.name },
                        )
                    }
                }

            val historyTokens = AiTokenEstimator.estimateMessageTokens(historyMessages)
            if (historyTokens > 0) {
                slices += AiContextUsageSlice(
                    category = AiContextUsageCategory.HISTORY,
                    tokens = historyTokens,
                    detail = "${historyMessages.size} msgs",
                )
            }
            if (effectivePending.isNotBlank() && newContentTokens > 0) {
                slices += AiContextUsageSlice(
                    category = AiContextUsageCategory.DRAFT,
                    tokens = newContentTokens,
                )
            }
            if (toolsTokens > 0) {
                slices += AiContextUsageSlice(
                    category = AiContextUsageCategory.TOOLS,
                    tokens = toolsTokens,
                    detail = "${tools.size} tools",
                )
            }
        } else {
            messages = buildRequestMessages(
                newContent = pendingUserContent,
                history = history,
                conversationId = conversationId,
                systemPromptOverride = systemPromptOverride,
                assemblyContext = null,
                contextWindow = contextWindow,
                conversationType = conversationType,
                modelCapabilities = preset?.model?.capabilities.orEmpty(),
            )
            val toolsTokens = AiTokenEstimator.estimateToolsTokens(tools)
            val msgTokens = AiTokenEstimator.estimateMessageTokens(messages)
            if (msgTokens > 0) {
                slices += AiContextUsageSlice(AiContextUsageCategory.HISTORY, msgTokens)
            }
            if (toolsTokens > 0) {
                slices += AiContextUsageSlice(
                    AiContextUsageCategory.TOOLS,
                    toolsTokens,
                    detail = "${tools.size} tools",
                )
            }
        }

        val raw = AiTokenEstimator.estimateMessageTokens(messages) +
            AiTokenEstimator.estimateToolsTokens(tools)
        val scaled = (raw * calibrationScale.coerceIn(0.5f, 2.5f)).toInt()
        val scale = if (raw > 0) scaled.toFloat() / raw else 1f
        val scaledSlices = slices
            .map { it.copy(tokens = (it.tokens * scale).toInt().coerceAtLeast(if (it.tokens > 0) 1 else 0)) }
            .filter { it.tokens > 0 }

        return AiContextUsageEstimate(
            promptTokens = scaled,
            rawPromptTokens = raw,
            inputBudget = contextInputBudget(contextWindow),
            contextWindow = contextWindow,
            slices = scaledSlices,
        )
    }

    private fun PromptBlockId.toContextUsageCategory(): AiContextUsageCategory = when (this) {
        PromptBlockId.Main,
        PromptBlockId.Persona,
        PromptBlockId.LocalTime,
        PromptBlockId.WritingSubmode,
        PromptBlockId.WritingIdentity,
        PromptBlockId.WritingInputFormat,
        PromptBlockId.WritingXmlNotice,
        PromptBlockId.HelpReplySystem,
        PromptBlockId.MultiBubbleProtocol,
        PromptBlockId.SkillsCatalog,
        -> AiContextUsageCategory.SYSTEM

        PromptBlockId.WritingStyleGuide,
        PromptBlockId.WritingActionContinue,
        PromptBlockId.PostHistory,
        -> AiContextUsageCategory.RULES

        PromptBlockId.Character,
        PromptBlockId.MultiCharacter,
        PromptBlockId.CurrentSpeaker,
        PromptBlockId.UserCard,
        -> AiContextUsageCategory.CHARACTER

        PromptBlockId.WorldCatalog,
        PromptBlockId.WorldEntries,
        PromptBlockId.MemoryTables,
        PromptBlockId.CharacterCardsCatalog,
        PromptBlockId.UserMemory,
        PromptBlockId.WorkspaceIndex,
        PromptBlockId.WorkspacePrefetch,
        -> AiContextUsageCategory.KNOWLEDGE
    }

    fun contextInputBudget(contextWindow: Int): Int =
        ContextBudgeter.displayBudget(contextWindow)

    /** Summarize old conversation messages into a concise paragraph for context compression. */
    suspend fun compressHistory(oldMessagesText: String): String {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured")
        val compressInstruction = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(
            promptTemplateGateway.getPrompt(AiPromptTemplate.COMPRESS_HISTORY_PROMPT),
        )
        // Reuse the byte-stable SYSTEM prefix of the most recent routed chat request
        // (when available) so this AUX call shares the same system bytes as follow-up
        // main requests → provider KV / prompt cache hits on the SYSTEM (DSH pattern).
        val chatSystem: String? = lastSystemPrefix
        val messages = buildList {
            if (chatSystem.isNullOrBlank()) {
                // Legacy path: compress instruction is the SYSTEM.
                addAll(
                    io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(
                        compressInstruction, oldMessagesText,
                    )
                )
            } else {
                add(AiMessage(AiMessageRole.SYSTEM, chatSystem))
                add(
                    AiMessage(
                        AiMessageRole.USER,
                        listOfNotNull(
                            compressInstruction.takeIf { it.isNotBlank() },
                            oldMessagesText,
                        ).joinToString("\n\n"),
                    )
                )
            }
        }
        val request = AiGenerateRequest(
            model = preset.model,
            messages = messages,
            params = preset.model.defaultParams,
            callMeta = AiCallMeta(AiCallSource.COMPRESS),
        )
        val result = aiTextGateway.generate(request)
        return result.getOrThrow().text.trim().take(9000)
    }

    private suspend fun buildRequestMessagesFromAssembly(
        assemblyContext: PromptAssemblyContext,
        newContent: String,
        history: List<AiChatMessageUi>,
        newUserParts: List<AiMessagePart>? = null,
        capabilities: Set<String> = emptySet(),
        modelCapabilities: Set<String> = emptySet(),
    ): List<AiMessage> {
        val assembled = promptAssembler.assemble(assemblyContext)
        lastPromptInjection = assembled.toPromptInjectionPart()
        val tools = aiToolGateway.availableTools(
            assemblyContext.conversationType,
            assemblyContext.webSearchArmed,
            activeToolGroupsFor(assemblyContext),
            modelCapabilities = modelCapabilities,
        )
        val system = withToolApprovalSystemPolicy(
            assembled.systemPrompt,
            assemblyContext.conversationType,
            tools.map { it.name },
            assemblyContext.outputMode,
        )
        lastSystemPrefix = system
        val systemTokens = estimateTokens(system) + AiTokenEstimator.framingTokens()
        val effectiveNewContent = mergeUserPrefix(assembled.userPrefix, newContent)
        val newUserMessage = userMessageForRequest(
            content = effectiveNewContent,
            parts = newUserParts,
            conversationType = assemblyContext.conversationType,
            capabilities = capabilities,
        )
        // 任务清单追加到最后一个 user 消息尾部：不动 SYSTEM 前缀（保持前缀缓存字节稳定），
        // 且位于 prompt 最末，仅 todos 段未命中缓存。
        val todosText = assemblyContext.todosText?.takeIf { it.isNotBlank() }
        val finalUserMessage = if (todosText != null) {
            AiMessage(AiMessageRole.USER, newUserMessage.content + "\n\n" + todosText)
        } else {
            newUserMessage
        }
        val newContentTokens = AiTokenEstimator.estimateMessageTokens(newUserMessage)
        val toolsTokens = AiTokenEstimator.estimateToolsTokens(tools)
        val availableTokens = if (assemblyContext.contextWindow > 0) {
            (ContextBudgeter.trimBudget(assemblyContext.contextWindow) -
                systemTokens - newContentTokens - toolsTokens).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val mergedHistory = history.mergeSiblingAssistantBubbles()
        val trimmedHistory = mergedHistory.trimForContext(availableTokens, assemblyContext.conversationType)
        // Inject in-chat content at AiChatMessageUi level (before toRequestMessages expansion)
        // so non-zero depth never splits tool_calls/tool_result pairs.
        val injectedHistory = injectInChatMessagesUi(trimmedHistory, assembled.inChatInjections)
        val historyMessages = injectedHistory.flatMap { msg ->
            when (msg.role) {
                AiMessageRole.USER -> listOf(
                    userMessageForRequest(
                        content = msg.content,
                        parts = msg.parts,
                        conversationType = assemblyContext.conversationType,
                        capabilities = capabilities,
                    ),
                )
                AiMessageRole.ASSISTANT -> msg.toRequestMessages()
                else -> null
            }.orEmpty()
        }
        return listOf(AiMessage(AiMessageRole.SYSTEM, system)) +
            historyMessages +
            finalUserMessage
    }

    private fun userMessageForRequest(
        content: String,
        parts: List<AiMessagePart>?,
        conversationType: String,
        capabilities: Set<String>,
    ): AiMessage {
        val formatted = formatUserContentForModel(content, conversationType)
        val hasAttachments = parts?.any { it is AiMessagePart.Attachment } == true
        if (!hasAttachments || parts == null) {
            return AiMessage(AiMessageRole.USER, formatted)
        }
        return when (
            val mapped = io.legado.app.help.ai.AiContentBlockMapper.userPartsToAiMessage(
                parts = parts,
                capabilities = capabilities,
                userTextOverride = formatted,
            )
        ) {
            is io.legado.app.help.ai.AiContentBlockMapper.MapResult.Ok -> mapped.message
            is io.legado.app.help.ai.AiContentBlockMapper.MapResult.Error ->
                AiMessage(AiMessageRole.USER, formatted)
        }
    }

    private fun mergeUserPrefix(prefix: String?, content: String): String {
        val p = prefix?.trim().orEmpty()
        val c = content.trim()
        return when {
            p.isEmpty() -> content
            c.isEmpty() -> p
            else -> "$p\n\n$c"
        }
    }

    private fun injectInChatMessages(
        historyMessages: List<AiMessage>,
        injections: List<InChatInjection>,
    ): List<AiMessage> {
        if (injections.isEmpty()) return historyMessages
        val result = historyMessages.toMutableList()
        injections.sortedWith(compareBy({ it.depth }, { it.order })).forEach { injection ->
            val index = (result.size - injection.depth).coerceIn(0, result.size)
            result.add(
                index,
                AiMessage(role = injection.role, content = injection.content),
            )
        }
        return result
    }

    /**
     * Inject in-chat content at AiChatMessageUi level (before toRequestMessages expansion)
     * so non-zero depth never splits tool_calls/tool_result pairs.
     */
    private fun injectInChatMessagesUi(
        history: List<AiChatMessageUi>,
        injections: List<InChatInjection>,
    ): List<AiChatMessageUi> {
        if (injections.isEmpty()) return history
        val result = history.toMutableList()
        injections.sortedWith(compareBy({ it.depth }, { it.order })).forEach { injection ->
            val index = (result.size - injection.depth).coerceIn(0, result.size)
            result.add(
                index,
                AiChatMessageUi(
                    id = "inject_${injection.hashCode()}_$index",
                    role = injection.role,
                    content = injection.content,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }

    private suspend fun buildRequestMessages(
        newContent: String,
        history: List<AiChatMessageUi>,
        conversationId: String? = null,
        systemPromptOverride: String? = null,
        assemblyContext: PromptAssemblyContext? = null,
        contextWindow: Int = 0,
        conversationType: String = "chat",
        webSearchArmed: Boolean = false,
        newUserParts: List<AiMessagePart>? = null,
        capabilities: Set<String> = emptySet(),
        modelCapabilities: Set<String> = emptySet(),
    ): List<AiMessage> {
        if (assemblyContext != null) {
            return buildRequestMessagesFromAssembly(
                assemblyContext = assemblyContext,
                newContent = newContent,
                history = history,
                newUserParts = newUserParts,
                capabilities = capabilities,
                modelCapabilities = modelCapabilities,
            )
        }
        if (systemPromptOverride == null && conversationType == "chat") {
            return buildRequestMessagesFromAssembly(
                assemblyContext = PromptAssemblyContext(
                    pipelineMode = PromptPipelineMode.Chat,
                    conversationId = conversationId,
                    conversationType = conversationType,
                    history = history,
                    newUserContent = newContent,
                    contextWindow = contextWindow,
                    webSearchArmed = webSearchArmed,
                ),
                newContent = newContent,
                history = history,
                newUserParts = newUserParts,
                capabilities = capabilities,
                modelCapabilities = modelCapabilities,
            )
        }
        val tools = aiToolGateway.availableTools(
            conversationType,
            webSearchArmed,
            activeToolGroupsFor(null, conversationType),
            modelCapabilities = modelCapabilities,
            outputMode = assemblyContext?.outputMode ?: "ask",
            hasActivePlan = assemblyContext?.hasActivePlan ?: false,
        )
        val system = withToolApprovalSystemPolicy(
            systemPromptOverride ?: buildLegacySystemPrompt(conversationId, conversationType),
            conversationType,
            tools.map { it.name },
        )
        val systemTokens = estimateTokens(system) + AiTokenEstimator.framingTokens()
        val newUserMessage = userMessageForRequest(
            content = newContent,
            parts = newUserParts,
            conversationType = conversationType,
            capabilities = capabilities,
        )
        val newContentTokens = AiTokenEstimator.estimateMessageTokens(newUserMessage)
        val toolsTokens = AiTokenEstimator.estimateToolsTokens(tools)
        val availableTokens = if (contextWindow > 0) {
            (ContextBudgeter.trimBudget(contextWindow) -
                systemTokens - newContentTokens - toolsTokens).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val mergedHistory = history.mergeSiblingAssistantBubbles()
        val trimmedHistory = mergedHistory.trimForContext(availableTokens, conversationType)
        val messages = trimmedHistory.flatMap { msg ->
            when (msg.role) {
                AiMessageRole.USER -> listOf(
                    userMessageForRequest(
                        content = msg.content,
                        parts = msg.parts,
                        conversationType = conversationType,
                        capabilities = capabilities,
                    ),
                )
                AiMessageRole.ASSISTANT -> msg.toRequestMessages()
                else -> null
            }.orEmpty()
        }
        return listOf(AiMessage(AiMessageRole.SYSTEM, system)) +
            messages +
            newUserMessage
    }

    /**
     * Collapse consecutive assistant bubbles that share the same [AiChatMessageUi.parentMessageId]
     * into one request message so multi-bubble UI does not fake extra turns for the model.
     */
    internal fun List<AiChatMessageUi>.mergeSiblingAssistantBubbles(): List<AiChatMessageUi> {
        if (isEmpty()) return this
        val out = ArrayList<AiChatMessageUi>(size)
        var i = 0
        while (i < size) {
            val msg = this[i]
            val parent = msg.parentMessageId
            if (msg.role != AiMessageRole.ASSISTANT || parent.isNullOrBlank()) {
                out.add(msg)
                i++
                continue
            }
            val group = mutableListOf(msg)
            var j = i + 1
            while (j < size) {
                val next = this[j]
                if (next.role != AiMessageRole.ASSISTANT || next.parentMessageId != parent) break
                // Keep tool-bearing follow-ups as separate turns
                if (next.parts.any { it is AiMessagePart.Tool }) break
                group.add(next)
                j++
            }
            if (group.size == 1) {
                out.add(msg)
            } else {
                val mergedText = group.joinToString("\n") { it.content.trim() }
                    .trim()
                val head = group.first()
                val nonText = head.parts.filter { it !is AiMessagePart.Text }
                val mergedParts = (nonText + AiMessagePart.Text(mergedText)).toImmutableList()
                out.add(
                    head.copy(
                        content = mergedText,
                        parts = mergedParts,
                    ),
                )
            }
            i = j
        }
        return out
    }

    private fun formatUserContentForModel(content: String, conversationType: String): String {
        if (conversationType != "writing") return content
        return WritingUserInput.toModelContent(content)
    }

    private fun withToolApprovalSystemPolicy(
        system: String,
        conversationType: String,
        availableToolNames: List<String>,
        outputMode: String = "ask",
    ): String {
        val requireConfirm = outputMode != "auto"
        val parts = buildList {
            AiToolRepository.toolGroupSystemPolicy(conversationType, availableToolNames)?.let { add(it) }
            AiToolRepository.toolApprovalSystemPolicy(conversationType, requireConfirm)?.let { add(it) }
            AiToolRepository.toolAutoContinuePolicy(
                conversationType, io.legado.app.help.config.AppConfig.aiMaxToolRounds
            )?.let { add(it) }
        }
        if (parts.isEmpty()) return system
        var out = system
        for (part in parts) {
            val marker = part.take(24)
            if (out.contains(marker)) continue
            out = out.trimEnd() + "\n\n" + part
        }
        return out
    }

    private fun List<AiChatMessageUi>.trimForContext(availableTokens: Int, conversationType: String = "chat"): List<AiChatMessageUi> {
        if (availableTokens == Int.MAX_VALUE) {
            if (size <= FALLBACK_MAX_MESSAGES) return this
            return takeLast(FALLBACK_MAX_MESSAGES)
        }
        // Cache prefix: pick stable plain-text messages (skip tool results which change every request)
        val stablePrefix = filter { msg ->
            msg.parts.none { it is AiMessagePart.Tool }
        }.take(CACHE_PREFIX_MESSAGES)
        val prefix = if (stablePrefix.size >= CACHE_PREFIX_MESSAGES) stablePrefix
            else take(CACHE_PREFIX_MESSAGES)  // fallback if not enough clean messages
        val prefixTokens = prefix.sumOf { msgTokens(it) }
        val remainingTokens = availableTokens - prefixTokens
        if (remainingTokens <= 0) return prefix

        // Trim the middle: candidates = messages not in the stable prefix
        val prefixIdSet = prefix.map { it.id }.toSet()
        val tail = filter { it.id !in prefixIdSet }
        val keptTail = mutableListOf<AiChatMessageUi>()
        var effectiveTokens = 0
        val droppedMsgs = mutableListOf<AiChatMessageUi>()
        for (msg in tail.reversed()) {
            val t = msgTokens(msg)
            val weight = msg.weight(conversationType)
            val cost = (t / weight).toInt().coerceAtLeast(1)
            if (effectiveTokens + cost > remainingTokens && keptTail.isNotEmpty()) {
                droppedMsgs.add(msg)
                continue
            }
            effectiveTokens += cost
            keptTail.add(msg)
        }

        val kept = prefix + keptTail.reversed()
        if (droppedMsgs.isEmpty()) return kept

        // Insert a summary marker for dropped messages in the middle
        val summary = droppedMsgs.droppedSummary(conversationType)
        val summaryMsg = AiChatMessageUi(
            id = "trim_summary", role = AiMessageRole.USER,
            content = summary, createdAt = System.currentTimeMillis(),
        )
        return prefix + listOf(summaryMsg) + keptTail.reversed()
    }

    private fun AiChatMessageUi.weight(conversationType: String): Float {
        val hasTools = parts.any { it is AiMessagePart.Tool }
        val isWriting = conversationType == "writing"
        return when {
            isWriting && hasTools -> 3f
            isWriting && content.length > 200 -> 2f
            isWriting && role == AiMessageRole.ASSISTANT && content.length > 100 -> 2f
            isWriting -> 1f
            hasTools -> 3f
            role == AiMessageRole.USER && content.length > 100 -> 2f
            content.length < 10 -> 0.5f
            else -> 1f
        }
    }

    private fun List<AiChatMessageUi>.droppedSummary(conversationType: String): String {
        return if (conversationType == "writing") "[前文概要：已省略中间对话内容]"
            else "[已省略中间对话内容]"
    }

    private fun msgTokens(msg: AiChatMessageUi): Int {
        val text = msg.content.ifBlank { msg.parts.textContent() }
        val tools = msg.parts.filterIsInstance<AiMessagePart.Tool>()
        val toolTokens = tools.sumOf {
            estimateTokens(it.toolName) + estimateTokens(it.input) + estimateTokens(it.output)
        }
        val attachmentTokens = io.legado.app.help.ai.AiContentBlockMapper.estimateAttachmentTokens(msg.parts)
        // Matches toRequestMessages(): assistant(+toolCalls) + one TOOL message per completed tool.
        val completedTools = tools.count { it.output.isNotBlank() }
        val requestMessageCount = when {
            msg.role == AiMessageRole.ASSISTANT && completedTools > 0 -> 1 + completedTools
            else -> 1
        }
        return estimateTokens(text) + toolTokens + attachmentTokens + AiTokenEstimator.framingTokens(requestMessageCount)
    }

    private fun estimateTokens(text: String): Int = AiTokenEstimator.estimateTokens(text)

    private fun AiChatMessageUi.toRequestMessages(): List<AiMessage> {
        // Provider-native web search is a built-in tool; never re-send it as a function call.
        val tools = parts.toolParts().filterNot { it.rawType.contains("web_search_call") }
        val toolCalls = tools.filter { it.output.isNotBlank() }.map {
            AiToolCall(id = it.toolCallId, name = it.toolName, arguments = it.input)
        }
        val toolResults = tools.filter { it.output.isNotBlank() }.map {
            AiMessage(
                role = AiMessageRole.TOOL,
                content = it.output,
                toolCallId = it.toolCallId,
                name = it.toolName
            )
        }
        return listOf(
            AiMessage(
                role = AiMessageRole.ASSISTANT,
                content = content,
                toolCalls = toolCalls,
                // Persisted reasoning parts (AiMessagePart.Reasoning) must be passed back in
                // thinking mode (DeepSeek Responses requires reasoning_text on tool rounds).
                reasoning = parts.reasoningContent().orEmpty(),
            )
        ) + toolResults
    }

    private suspend fun buildLegacySystemPrompt(
        conversationId: String? = null,
        conversationType: String? = null,
    ): String {
        val base = promptTemplateGateway.getPrompt(AiPromptTemplate.CHAT_SYSTEM_PROMPT)

        val parts = mutableListOf(base)

        // 搜索工具按模式命名:chat 中性名 search_context,写作 search_workspace。
        val searchTool = if (conversationType == "writing") "search_workspace" else "search_context"

        // World books: inject enabled ones as a brief catalog so the AI knows what's available
        val enabledWorldBooks = aiWorldBookGateway.getEnabled()
        if (enabledWorldBooks.isNotEmpty()) {
            val catalog = enabledWorldBooks.joinToString("\n") { wb ->
                val preview = wb.writingStyle.take(80).replace("\n", " ")
                "- **${wb.name}** (id: `${wb.id}`" +
                    if (wb.bookName.isNotBlank()) ", source: ${wb.bookName}" else "" +
                    "): $preview"
            }
            val catalogContent = "These writing-style references are available. Prefer `$searchTool` for keywords, then `read_world_book` (use entryId from hits for a single entry).\n$catalog"
            val catalogHash = catalogContent.hashCode().toString(16)
            parts.add("\n<world_books_catalog hash=\"$catalogHash\">\n" +
                catalogContent + "\n" +
                "</world_books_catalog>")
        }

        // Memory tables: inject enabled tables for the current conversation
        if (!conversationId.isNullOrBlank()) {
            val enabledTables = aiMemoryTableGateway.getEnabledForConversation(conversationId)
            if (enabledTables.isNotEmpty()) {
                val tableCatalog = enabledTables.joinToString("\n") { table ->
                    val columns = runCatching {
                        com.google.gson.Gson().fromJson(table.columns, Array<String>::class.java).joinToString(", ")
                    }.getOrNull() ?: table.columns
                    "- **${table.name}** (id: `${table.id}`): $columns"
                }
                val tableContent = "These structured tables store story history data for this conversation. Prefer `$searchTool` for keywords, then `read_history_memory` (rowIds) or `patch_history_memory` for changes.\n$tableCatalog"
                val tableHash = tableContent.hashCode().toString(16)
                parts.add("\n<history_memory_tables_catalog hash=\"$tableHash\">\n" +
                    tableContent + "\n" +
                    "</history_memory_tables_catalog>")
            }
        }

        if (conversationId != null) {
            UserMemoryTools.buildPromptBlock(
                conversationType = "chat",
                conversationId = conversationId,
                gateway = aiMemoryGateway,
            )?.let { parts.add(it) }
        }

        return parts.joinToString("\n")
    }

    companion object {
        const val TAG = "AiTool"

        /** Keep the first N messages (system + first exchanges) stable as a cache prefix
         *  so DeepSeek/Anthropic prefix-caching can hit. Trim only the middle. */
        const val CACHE_PREFIX_MESSAGES = 4
        /** Fallback when contextWindow is not configured (0). */
        const val FALLBACK_MAX_MESSAGES = 12
        // const val MAX_TOOL_OUTPUT_CHARS = 12_000 //已弃用
        // const val MAX_TOOL_ROUNDS = 5 //已弃用

        /** Sliding window of recent cache token samples (latest at end). */
        private data class CacheTokenSample(val hitTokens: Int, val promptTokens: Int)

        private val cacheHitHistory = ArrayDeque<CacheTokenSample>(20)
        /** Token-weighted cache hit ratio over [cacheHitHistory]. */
        @Volatile
        var cacheHitRatio: Float = 0f
            private set

        /**
         * Record API-reported cache hit vs prompt tokens for the sliding ratio.
         * Ignores samples with [promptTokens] <= 0.
         */
        fun recordCacheResult(cacheHitTokens: Int, promptTokens: Int) {
            if (promptTokens <= 0) return
            val hit = cacheHitTokens.coerceIn(0, promptTokens)
            synchronized(cacheHitHistory) {
                if (cacheHitHistory.size >= 20) cacheHitHistory.removeFirst()
                cacheHitHistory.addLast(CacheTokenSample(hit, promptTokens))
                val totalHit = cacheHitHistory.sumOf { it.hitTokens }
                val totalPrompt = cacheHitHistory.sumOf { it.promptTokens }
                cacheHitRatio = if (totalPrompt <= 0) 0f
                else totalHit.toFloat() / totalPrompt.toFloat()
            }
        }
    }
}

/**
 * Accumulates streaming tool call deltas into complete [AiMessagePart.Tool] parts.
 */
class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexToId = mutableMapOf<Int, String>()

    /** Records where each tool first appeared in the text stream for interleaved parts ordering. */
    private val toolInsertions = mutableListOf<ToolInsertionPoint>()

    /**
     * Chronological stream markers for chat-mode interleaving of reasoning / content / tools.
     * Writing mode ignores this and keeps a single leading [AiMessagePart.Reasoning].
     */
    private val timeline = mutableListOf<TimelineEntry>()

    data class ToolInsertionPoint(
        val textPosition: Int,
        val callId: String
    )

    private sealed interface TimelineEntry {
        class ReasoningSeg(val text: StringBuilder = StringBuilder()) : TimelineEntry
        class ContentSpan(var start: Int, var end: Int) : TimelineEntry
        data class ToolStart(val callId: String) : TimelineEntry
    }

    /** Accumulated text and reasoning lengths, synced from stream callbacks. */
    var textLength: Int = 0
        private set
    var reasoningLength: Int = 0
        private set
    var imageCount: Int = 0
        private set

    fun recordTextDelta(deltaLen: Int) {
        if (deltaLen <= 0) return
        val start = textLength
        textLength += deltaLen
        val last = timeline.lastOrNull()
        if (last is TimelineEntry.ContentSpan) {
            last.end = textLength
        } else {
            timeline.add(TimelineEntry.ContentSpan(start, textLength))
        }
    }

    fun recordReasoning(delta: String) {
        if (delta.isEmpty()) return
        reasoningLength += delta.length
        val last = timeline.lastOrNull()
        if (last is TimelineEntry.ReasoningSeg) {
            last.text.append(delta)
        } else {
            timeline.add(TimelineEntry.ReasoningSeg(StringBuilder(delta)))
        }
    }

    /** @deprecated Prefer [recordReasoning] so chat mode can interleave thinking segments. */
    fun recordReasoningDelta(deltaLen: Int) {
        if (deltaLen <= 0) return
        reasoningLength += deltaLen
        // Length-only updates cannot rebuild text; keep a placeholder span so timeline stays ordered.
        val last = timeline.lastOrNull()
        if (last !is TimelineEntry.ReasoningSeg) {
            timeline.add(TimelineEntry.ReasoningSeg())
        }
    }

    /** Image received from stream (statistics only; actual data stored by ViewModel). */
    fun recordImage(mimeType: String) {
        imageCount++
    }

    fun size(): Int = calls.size

    fun beginResponse() {
        Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: beginResponse clearing ${calls.size} calls")
        calls.clear()
        indexToId.clear()
        toolInsertions.clear()
        timeline.clear()
        textLength = 0
        reasoningLength = 0
    }

    fun append(event: AiStreamEvent.ToolCallDelta) {
        val id = resolveId(event)
        if (event.name == "web_search" || event.rawType.contains("web_search_call", ignoreCase = true)) {
            Log.d(
                AiChatGenerationUseCase.TAG,
                "ToolTrace.append web_search event id=$id index=${event.index} name=${event.name} rawType=${event.rawType}",
            )
        }
        // Disambiguate only when a previous round already finished with a real result.
        // EXECUTING_PLACEHOLDER and blank are still in-flight and must reuse the same id.
        val resolvedId = if (hasCompletedResult(calls[id]?.result)) {
            var suffix = 2
            var candidate: String
            do { candidate = "${id}_r$suffix"; suffix++ } while (calls.containsKey(candidate))
            Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: collision $id -> $candidate")
            candidate
        } else id
        // Record insertion point for new tools so toInterleavedParts can split text correctly
        val isNewTool = !calls.containsKey(resolvedId)
        if (isNewTool) {
            toolInsertions.add(ToolInsertionPoint(textLength, resolvedId))
            timeline.add(TimelineEntry.ToolStart(resolvedId))
        }
        // Record index→id mapping so subsequent deltas (which only carry index) find the right entry
        if (event.index != null && event.index >= 0) {
            indexToId[event.index] = resolvedId
        }
        val call = calls.getOrPut(resolvedId) { ToolCallTrace(id = resolvedId) }
        if (event.rawType.contains("web_search_call", ignoreCase = true)) {
            // Provider-native web search: visible in the bubble/history but never executed.
            Log.d(AiChatGenerationUseCase.TAG, "ToolTrace.append builtin-marked id=$resolvedId rawType=${event.rawType}")
            call.isBuiltinSearch = true
            call.name = "web_search"
            call.result = ""
            parseWebSearchData(event.webSearchData)?.let { data ->
                call.webSearchQueries = data.queries
                call.webSearchQuery = data.query
                if (data.sources.isNotEmpty()) {
                    call.result = com.google.gson.Gson().toJson(
                        mapOf("results" to data.sources.map {
                            val hit = linkedMapOf(
                                "title" to it.title,
                                "url" to it.url,
                                "snippet" to "",
                            )
                            it.publishedAt?.let { date -> hit["publishedAt"] = date }
                            hit
                        })
                    )
                }
            }
        }
        event.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        event.argumentsDelta?.takeIf { it.isNotEmpty() }?.let { delta ->
            // Responses API sends argument *deltas* then a *.done / output_item.done snapshot
            // with the full JSON. Appending the snapshot would corrupt args and break tools.
            if (isToolArgumentsSnapshot(event.rawType)) {
                val current = call.arguments.toString()
                when {
                    current.isEmpty() -> call.arguments.append(delta)
                    delta == current -> Unit
                    delta.startsWith(current) -> {
                        call.arguments.clear()
                        call.arguments.append(delta)
                    }
                    else -> Unit
                }
            } else if (!call.arguments.isCompleteJson()) {
                // OpenAI streams deltas until a .done snapshot; DeepSeek instead sends the full
                // JSON on output_item.added and then *re-sends* it as delta chunks. Once the
                // accumulated args already parse as a complete object, ignore further deltas —
                // appending them concatenates two JSON documents and breaks tool parsing.
                call.arguments.append(delta)
            }
        }
    }

    /** True if the accumulated arguments already form a valid standalone JSON object. */
    private fun StringBuilder.isCompleteJson(): Boolean = runCatching {
        val parsed = com.google.gson.JsonParser.parseString(toString())
        parsed.isJsonObject || parsed.isJsonArray
    }.getOrDefault(false)

    private fun isToolArgumentsSnapshot(rawType: String): Boolean {
        if (rawType.isBlank()) return false
        return rawType.endsWith(".done") ||
            rawType == "response.output_item.done" ||
            rawType.contains("arguments.done", ignoreCase = true)
    }

    /** Resolve the canonical ID for a delta, preferring the index→id map when id is absent. */
    private fun resolveId(event: AiStreamEvent.ToolCallDelta): String {
        // If the delta carries an explicit id, use it directly
        event.id?.takeIf { it.isNotBlank() }?.let { return it }
        // Otherwise look up by index — the first chunk for this tool already registered the id
        event.index?.let { idx ->
            indexToId[idx]?.let { return it }
        }
        // Fallback: generate a synthetic id
        return event.index?.let { "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
    }

    fun appendResult(id: String, result: String) {
        Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: appendResult id=$id resultLen=${result.length} found=${calls.containsKey(id)}")
        calls[id]?.let { call ->
            call.result = result
            call.progressFraction = null
            call.progressLabel = null
        }
    }

    fun resultOf(id: String): String? = calls[id]?.result

    private fun hasCompletedResult(result: String?): Boolean {
        if (result == null || result == EXECUTING_PLACEHOLDER) return false
        return true
    }

    private fun isPendingResult(result: String?): Boolean =
        result == null || result == EXECUTING_PLACEHOLDER

    /** Set an interim status before the tool is actually executed. */
    fun setExecuting(id: String) {
        val call = calls[id]
        Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: setExecuting id=$id found=${call != null} existingResult=${call?.result != null}")
        if (call != null && call.result.isNullOrBlank()) {
            call.result = EXECUTING_PLACEHOLDER
            call.progressFraction = 0f
            call.progressLabel = null
        }
    }

    /** Update determinate progress while [EXECUTING_PLACEHOLDER] is showing. */
    fun setProgress(id: String, fraction: Float, label: String = "") {
        val call = calls[id] ?: return
        if (!isPendingResult(call.result)) return
        call.result = EXECUTING_PLACEHOLDER
        call.progressFraction = fraction.coerceIn(0f, 1f)
        call.progressLabel = label.trim().takeIf { it.isNotEmpty() }
    }

    /** Mark tools that never finished so saved messages do not show perpetual "Running". */
    fun finalizeIncomplete(message: String = RESULT_NOT_COMPLETED) {
        calls.values.forEach { call ->
            if (!call.isBuiltinSearch && isPendingResult(call.result)) {
                call.result = message
            }
        }
    }

    fun hasIncompleteResults(): Boolean =
        calls.values.any { call -> isPendingResult(call.result) }

    /** Drop unnamed tool stubs that never received a tool name from the stream. */
    fun pruneUnnamedPending() {
        val removeIds = calls.filterValues { call ->
            call.name.isBlank() && isPendingResult(call.result)
        }.keys
        removeIds.forEach { id ->
            calls.remove(id)
            toolInsertions.removeAll { it.callId == id }
            timeline.removeAll { it is TimelineEntry.ToolStart && it.callId == id }
            indexToId.entries.removeAll { it.value == id }
        }
    }

    fun pendingToolCalls(): List<AiToolCall> {
        val pending = calls.values.filter { call -> !call.isBuiltinSearch && isPendingResult(call.result) }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(id = call.id, name = name, arguments = call.arguments.toString().ifBlank { "{}" })
        }
        Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: pendingToolCalls total=${calls.size} pending=${pending.size} " +
            "calls=${calls.values.joinToString { "${it.name}/${it.id}: result=${it.result?.take(50) ?: "null"}" }}")
        return pending
    }

    /** Names of tools that finished with a real (non-placeholder) result. */
    fun completedToolNames(): Set<String> =
        calls.values
            .filter { hasCompletedResult(it.result) && it.name.isNotBlank() }
            .map { it.name }
            .toSet()

    /** True if a write_file/edit_file targeting a plan:// path completed with a real result. */
    fun completedPlanFileCalls(): Boolean {
        val matched = calls.values.filter { call ->
            hasCompletedResult(call.result) &&
                (call.name == io.legado.app.data.repository.AiToolRepository.TOOL_WRITE_FILE ||
                    call.name == io.legado.app.data.repository.AiToolRepository.TOOL_EDIT_FILE) &&
                call.arguments.contains("plan://")
        }
        Log.d(
            AiChatGenerationUseCase.TAG,
            "completedPlanFileCalls: matched=${matched.size} all=${calls.values.joinToString { "${it.name}:args=${it.arguments.take(80)}:hasResult=${it.result != null}" }}",
        )
        return matched.isNotEmpty()
    }

    fun toParts(): List<AiMessagePart> {
        val parts = calls.values.mapNotNull { call ->
            // 幽灵原生搜索不渲染（同 buildToolPart）。
            if (call.isBuiltinSearch &&
                call.webSearchQuery.isNullOrBlank() &&
                call.result.isNullOrBlank()
            ) {
                return@mapNotNull null
            }
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: ""
            )
        }
        Log.d(AiChatGenerationUseCase.TAG, "ToolTrace: toParts count=${parts.size} " +
            parts.joinToString { "${it.toolName}: outLen=${it.output.length}" })
        return parts
    }

    /**
     * Build [AiMessagePart] list for display/persistence.
     *
     * @param interleaveReasoning when true (chat), reasoning segments follow stream order among
     * text/tools. when false (writing), keep legacy: one leading Reasoning, then text/tools.
     */
    fun toInterleavedParts(
        fullText: String,
        fullReasoning: String,
        interleaveReasoning: Boolean = false,
    ): List<AiMessagePart> {
        if (interleaveReasoning && timeline.isNotEmpty()) {
            return buildPartsFromTimeline(fullText, fullReasoning)
        }
        return buildPartsLegacy(fullText, fullReasoning)
    }

    private fun buildPartsFromTimeline(fullText: String, fullReasoning: String): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        val emittedToolIds = mutableSetOf<String>()
        for (entry in timeline) {
            when (entry) {
                is TimelineEntry.ReasoningSeg -> {
                    val text = entry.text.toString()
                    if (text.isNotBlank()) parts.add(AiMessagePart.Reasoning(text))
                }
                is TimelineEntry.ContentSpan -> {
                    val start = entry.start.coerceIn(0, fullText.length)
                    val end = entry.end.coerceIn(start, fullText.length)
                    if (end > start) {
                        val segment = fullText.substring(start, end)
                        if (segment.isNotBlank()) parts.add(AiMessagePart.Text(segment))
                    }
                }
                is TimelineEntry.ToolStart -> {
                    val call = calls[entry.callId]
                    if (call != null) {
                        buildToolPart(call)?.let {
                            parts.add(it)
                            emittedToolIds.add(entry.callId)
                        }
                    }
                }
            }
        }
        // Tools that never got a timeline marker (shouldn't happen) — append at end.
        calls.values.forEach { call ->
            if (call.id !in emittedToolIds) {
                buildToolPart(call)?.let { parts.add(it) }
            }
        }
        // If timeline had only tools/content but reasoning arrived via length-only API, fall back.
        if (parts.none { it is AiMessagePart.Reasoning } && fullReasoning.isNotBlank()) {
            return listOf(AiMessagePart.Reasoning(fullReasoning)) + parts
        }
        return parts
    }

    private fun buildPartsLegacy(fullText: String, fullReasoning: String): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()

        if (fullReasoning.isNotBlank()) {
            parts.add(AiMessagePart.Reasoning(fullReasoning))
        }

        if (toolInsertions.isEmpty()) {
            // No timing data — put all text first, then all tools.
            if (fullText.isNotBlank()) {
                parts.add(AiMessagePart.Text(fullText))
            }
            calls.values.forEach { call ->
                if (call.name.isNotBlank()) {
                    buildToolPart(call)?.let { parts.add(it) }
                }
            }
            return parts
        }

        // Text↔tool interleaving only (reasoning stays leading).
        var textPos = 0
        for (insertion in toolInsertions) {
            if (insertion.textPosition > textPos) {
                val segment = fullText.substring(
                    textPos, insertion.textPosition.coerceAtMost(fullText.length)
                )
                if (segment.isNotBlank()) {
                    parts.add(AiMessagePart.Text(segment))
                }
            }
            val call = calls[insertion.callId]
            if (call != null) {
                buildToolPart(call)?.let { parts.add(it) }
            }
            textPos = insertion.textPosition.coerceAtMost(fullText.length)
        }

        if (textPos < fullText.length) {
            val remaining = fullText.substring(textPos)
            if (remaining.isNotBlank()) {
                parts.add(AiMessagePart.Text(remaining))
            }
        }

        return parts
    }

    private fun buildToolPart(call: ToolCallTrace): AiMessagePart.Tool? {
        // 幽灵原生搜索：provider 在每个 function call 后自动附带一个无内容的 web_search_call
        // item（无 query、无结果），纯噪音，不渲染成工具气泡；真实搜索（有 query 或结果）保留可见。
        if (call.isBuiltinSearch &&
            call.webSearchQuery.isNullOrBlank() &&
            call.result.isNullOrBlank()
        ) {
            Log.d(
                AiChatGenerationUseCase.TAG,
                "ToolTrace: skip ghost builtin web_search id=${call.id} (no query/result)",
            )
            return null
        }
        val metadata = call.progressFraction?.let { fraction ->
            io.legado.app.domain.model.AiToolProgress(
                fraction = fraction,
                label = call.progressLabel.orEmpty(),
            ).toMetadataJson()
        }
        val input = if (call.isBuiltinSearch) {
            val inputMap = linkedMapOf<String, Any?>()
            call.webSearchQuery?.takeIf { it.isNotBlank() }?.let { inputMap["query"] = it }
            if (call.webSearchQueries.size > 1) inputMap["queries"] = call.webSearchQueries
            if (inputMap.isEmpty()) "{}" else com.google.gson.Gson().toJson(inputMap)
        } else {
            call.arguments.toString().ifBlank { "{}" }
        }
        return AiMessagePart.Tool(
            toolCallId = call.id,
            toolName = call.name.ifBlank { call.id },
            input = input,
            output = call.result ?: "",
            metadata = metadata,
            rawType = if (call.isBuiltinSearch) "web_search_call" else "tool_call",
        )
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                append(call.name.ifBlank { call.id })
                append(" (")
                append(call.id)
                append(')')
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append("Args: ")
                    append(call.arguments.toString().take(500))
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }

    companion object {
        const val EXECUTING_PLACEHOLDER = "[Executing...]"
        const val RESULT_CANCELLED = "[Cancelled]"
        const val RESULT_NOT_COMPLETED = "[Not completed]"
        const val RESULT_REJECTED = "User rejected"

        fun formatRejectedResult(feedback: String?): String {
            if (feedback.isNullOrBlank()) return RESULT_REJECTED
            return com.google.gson.JsonObject().apply {
                addProperty("status", "rejected")
                addProperty("feedback", feedback.trim())
            }.toString()
        }

        /** Partial approval: tool was in the batch but the user did not check it. */
        fun formatSkippedResult(feedback: String?): String {
            return com.google.gson.JsonObject().apply {
                addProperty("status", "skipped")
                addProperty(
                    "feedback",
                    feedback?.trim()?.takeIf { it.isNotBlank() }
                        ?: "User did not approve this tool call",
                )
            }.toString()
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var result: String? = null,
    /** 0f..1f while executing; cleared when a final result is set. */
    var progressFraction: Float? = null,
    var progressLabel: String? = null,
    /** Provider-native built-in call (e.g. web_search) — rendered but never executed by the app. */
    var isBuiltinSearch: Boolean = false,
    /** Native web search query (first of search_queries), for the bubble preview when no sources. */
    var webSearchQuery: String? = null,
    /** All native search terms of this call, in provider order. */
    var webSearchQueries: List<String> = emptyList(),
)

internal data class WebSearchTraceData(
    val queries: List<String>,
    val sources: List<WebSearchSourceItem>,
) {
    /** First search term, kept for the single-query bubble preview. */
    val query: String? get() = queries.firstOrNull()
}

internal data class WebSearchSourceItem(
    val title: String,
    val url: String,
    val publishedAt: String? = null,
)

/** Parse compact webSearchData JSON from [AiStreamEvent.ToolCallDelta.webSearchData]. */
internal fun parseWebSearchData(json: String?): WebSearchTraceData? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val o = com.google.gson.JsonParser.parseString(json).asJsonObject
        val queries = o.getAsJsonArray("queries")
            ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val legacyQuery = o.get("query")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        val sources = o.getAsJsonArray("sources")?.mapNotNull { el ->
            val s = el.asJsonObject
            WebSearchSourceItem(
                title = s.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                url = s.get("url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                publishedAt = s.get("publishedAt")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() },
            )
        }?.filter { it.url.isNotBlank() } ?: emptyList()
        WebSearchTraceData(
            queries.ifEmpty { listOfNotNull(legacyQuery?.takeIf { it.isNotEmpty() }) },
            sources,
        )
    }.getOrNull()
}

internal fun String.truncateToolOutput(): String {
    val maxChars = io.legado.app.help.config.AppConfig.aiMaxToolOutputChars
    if (length <= maxChars) return this
    val trimmed = runCatching {
        val obj = com.google.gson.JsonParser.parseString(this).asJsonObject
        if (obj.has("rows") && obj.get("rows").isJsonArray) {
            val rows = obj.getAsJsonArray("rows")
            val keep = (maxChars / 200).coerceIn(5, 40)
            if (rows.size() > keep) {
                val subset = com.google.gson.JsonArray()
                for (i in 0 until keep) subset.add(rows[i])
                obj.add("rows", subset)
                obj.addProperty("truncated", true)
                obj.addProperty("hasMore", true)
                obj.addProperty("totalRows", rows.size())
                obj.addProperty("truncatedFrom", length)
                return obj.toString()
            }
        }
        null
    }.getOrNull()
    if (trimmed != null) return trimmed
    return take(maxChars) + "\n\n[...truncated from ${length} chars]"
}
