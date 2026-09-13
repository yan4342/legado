package io.legado.app.ui.ai.chat

import io.legado.app.domain.model.AiMessagePart

/** A single step within a thinking block. */
sealed interface AiThinkingStep {
    data class ReasoningStep(val text: String) : AiThinkingStep
    data class ToolStep(val tool: AiMessagePart.Tool) : AiThinkingStep
}

/** Blocks used to render a message's content in order. */
sealed interface AiMessagePartBlock {
    /** A group of consecutive reasoning/tool parts. */
    data class ThinkingBlock(
        val steps: List<AiThinkingStep>,
        val durationSeconds: Int = 0,
    ) : AiMessagePartBlock

    /** A non-thinking content part (usually Text or BookResult). */
    data class ContentBlock(
        val part: AiMessagePart,
        val index: Int,
    ) : AiMessagePartBlock
}

/**
 * Groups a flat list of [AiMessagePart] into render-order [AiMessagePartBlock]s.
 * Consecutive [AiMessagePart.Reasoning] and [AiMessagePart.Tool] parts are collapsed
 * into a single [ThinkingBlock]; all other parts become individual [ContentBlock]s.
 */
fun List<AiMessagePart>.groupMessageParts(): List<AiMessagePartBlock> {
    val blocks = mutableListOf<AiMessagePartBlock>()
    var pendingSteps = mutableListOf<AiThinkingStep>()
    var index = 0

    fun flushThinking() {
        if (pendingSteps.isNotEmpty()) {
            blocks.add(AiMessagePartBlock.ThinkingBlock(steps = pendingSteps.toList()))
            pendingSteps = mutableListOf()
        }
    }

    for (part in this) {
        when (part) {
            is AiMessagePart.Reasoning -> {
                pendingSteps.add(AiThinkingStep.ReasoningStep(part.text))
            }
            is AiMessagePart.Tool -> {
                pendingSteps.add(AiThinkingStep.ToolStep(part))
            }
            is AiMessagePart.PromptInjection -> {
                // Stored for execution history; not rendered in the chat bubble.
            }
            is AiMessagePart.SideEffect -> {
                // Stored for execution history; not rendered in the chat bubble.
            }
            else -> {
                flushThinking()
                blocks.add(AiMessagePartBlock.ContentBlock(part, index))
            }
        }
        index++
    }
    flushThinking()
    return blocks
}
