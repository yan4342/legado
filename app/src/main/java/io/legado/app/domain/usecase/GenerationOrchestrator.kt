package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.prompt.PromptAssembler
import io.legado.app.domain.prompt.PromptAssemblyContext

/**
 * Coordinates prompt assembly, streaming, tool loop, and post-edit.
 * Side effects (title, suggestions, maintain) remain in the ViewModel.
 */
class GenerationOrchestrator(
    private val generationUseCase: AiChatGenerationUseCase,
    private val promptAssembler: PromptAssembler,
    private val toolAgentLoop: ToolAgentLoop,
) {
    data class StreamCallbacks(
        val onContent: suspend (String) -> Unit,
        val onReasoning: suspend (String) -> Unit,
        val onToolTraceUpdate: suspend () -> Unit,
        val onUsage: suspend (Int, Int, Int) -> Unit = { _, _, _ -> },
        val onImage: suspend (base64: String, mimeType: String, width: Int?, height: Int?) -> Unit = { _, _, _, _ -> },
    )

    data class GenerationResult(
        val text: String,
        val reasoning: String,
        val parts: List<AiMessagePart>,
        val request: AiGenerateRequest,
        val toolTrace: ToolTraceBuilder,
    )

    suspend fun assembleSystemPrompt(ctx: PromptAssemblyContext): String =
        promptAssembler.assembleSystemPrompt(ctx)

    suspend fun streamInitial(
        request: AiGenerateRequest,
        toolTrace: ToolTraceBuilder,
        callbacks: StreamCallbacks,
    ) {
        generationUseCase.collectStream(
            request = request,
            toolTrace = toolTrace,
            onContent = callbacks.onContent,
            onReasoning = callbacks.onReasoning,
            onToolTraceUpdate = callbacks.onToolTraceUpdate,
            onUsage = callbacks.onUsage,
            onImage = callbacks.onImage,
        )
    }

    suspend fun runToolLoop(
        convId: String?,
        request: AiGenerateRequest,
        fullText: StringBuilder,
        fullReasoning: StringBuilder,
        toolTrace: ToolTraceBuilder,
        policy: ToolConfirmPolicy,
        confirmHandler: ToolConfirmHandler?,
        maxRounds: Int,
        onStreamRound: suspend (text: String, reasoning: String) -> Unit,
        executeBatch: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            calls: List<io.legado.app.domain.model.AiToolCall>,
            round: Int,
            roundToolCalls: List<io.legado.app.domain.model.AiToolCall>?,
            committedTextLen: Int,
            committedReasoningLen: Int,
        ) -> AiGenerateRequest,
        needsBookshelfApproval: suspend (io.legado.app.domain.model.AiToolCall) -> Boolean = { false },
        onMaxRoundsReached: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            remaining: List<io.legado.app.domain.model.AiToolCall>,
            committedTextLen: Int,
            committedReasoningLen: Int,
        ) -> Unit = { _, _, _, _, _, _, _, _ -> },
        appendRejectedRound: suspend (
            convId: String?,
            request: AiGenerateRequest,
            fullText: StringBuilder,
            fullReasoning: StringBuilder,
            toolTrace: ToolTraceBuilder,
            rejectedCalls: List<io.legado.app.domain.model.AiToolCall>,
            committedTextLen: Int,
            committedReasoningLen: Int,
        ) -> AiGenerateRequest = { _, req, _, _, _, _, _, _ -> req },
    ): ToolAgentLoop.LoopResult {
        val result = toolAgentLoop.run(
            initialRequest = request,
            initialText = fullText.toString(),
            initialReasoning = fullReasoning.toString(),
            toolTrace = toolTrace,
            policy = policy,
            confirmHandler = confirmHandler,
            maxRounds = maxRounds,
            convId = convId,
            onMaxRoundsReached = onMaxRoundsReached,
            appendRejectedRound = appendRejectedRound,
            onStreamRound = { req, text, reasoning, trace ->
                generationUseCase.collectStream(
                    request = req,
                    toolTrace = trace,
                    onContent = { delta ->
                        text.append(delta)
                        // Keep streaming UI in sync (same as the initial round); otherwise the
                        // pre-tool bubble stays visible beside the saved full reply after tools.
                        onStreamRound(text.toString(), reasoning.toString())
                    },
                    onReasoning = { delta ->
                        reasoning.append(delta)
                        onStreamRound(text.toString(), reasoning.toString())
                    },
                    onToolTraceUpdate = { onStreamRound(text.toString(), reasoning.toString()) },
                    onImage = { base64, mime, w, h -> onStreamRound(text.toString(), reasoning.toString()) },
                )
            },
            executeBatch = executeBatch,
            needsBookshelfApproval = needsBookshelfApproval,
        )
        fullText.clear()
        fullText.append(result.fullText)
        fullReasoning.clear()
        fullReasoning.append(result.fullReasoning)
        return result
    }

    fun finalizeText(
        reasoning: String,
        toolTrace: ToolTraceBuilder,
        polishedText: String,
    ): Pair<String, List<AiMessagePart>> {
        val parts = generationUseCase.buildAssistantParts(polishedText, reasoning, toolTrace)
        return polishedText to parts
    }
}
