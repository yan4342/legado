package io.legado.app.help.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiUsage

object AiTokenEstimator {

    /**
     * Vendor rule-of-thumb (e.g. DeepSeek docs): actual count is always from API usage.
     * 1 CJK char ≈ 0.6 token; 1 ASCII char ≈ 0.3 token.
     */
    private const val CJK_TOKENS_PER_CHAR = 0.6
    private const val ASCII_TOKENS_PER_CHAR = 0.3
    private const val OTHER_TOKENS_PER_CHAR = 0.4
    /** Chat-template / role framing overhead per message. */
    const val TOKENS_PER_MESSAGE = 4

    fun framingTokens(messageCount: Int = 1): Int =
        TOKENS_PER_MESSAGE * messageCount.coerceAtLeast(0)

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        var ascii = 0
        var other = 0
        for (ch in text) {
            when {
                ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF ||
                    ch.code in 0x20000..0x2A6DF || ch.code in 0xF900..0xFAFF ||
                    ch.code in 0x3040..0x309F || ch.code in 0x30A0..0x30FF ||
                    ch.code in 0xAC00..0xD7AF -> cjk++
                ch.code <= 0x7F -> ascii++
                else -> other++
            }
        }
        return (cjk * CJK_TOKENS_PER_CHAR + ascii * ASCII_TOKENS_PER_CHAR + other * OTHER_TOKENS_PER_CHAR)
            .toInt().coerceAtLeast(if (text.isNotBlank()) 1 else 0)
    }

    fun estimateMessageTokens(message: AiMessage): Int {
        var tokens = TOKENS_PER_MESSAGE
        val blocks = message.contentBlocks
        if (!blocks.isNullOrEmpty()) {
            tokens += blocks.sumOf { block ->
                when (block) {
                    is io.legado.app.domain.model.AiContentBlock.Text -> estimateTokens(block.text)
                    is io.legado.app.domain.model.AiContentBlock.Image ->
                        AiContentBlockMapper.IMAGE_TOKEN_ESTIMATE
                    is io.legado.app.domain.model.AiContentBlock.Document ->
                        AiContentBlockMapper.IMAGE_TOKEN_ESTIMATE * 2
                }
            }
        } else {
            tokens += estimateTokens(message.content)
        }
        tokens += message.toolCalls.sumOf { call ->
            estimateTokens(call.name) + estimateTokens(call.arguments)
        }
        message.name?.let { tokens += estimateTokens(it) }
        message.toolCallId?.let { tokens += estimateTokens(it) }
        return tokens
    }

    fun estimateMessageTokens(messages: List<AiMessage>): Int =
        messages.sumOf { estimateMessageTokens(it) }

    fun estimateToolsTokens(tools: List<AiToolDefinition>): Int =
        tools.sumOf { tool ->
            estimateTokens(tool.name) +
                estimateTokens(tool.description) +
                estimateTokens(tool.inputSchema.toString())
        }

    fun estimatePromptTokens(request: AiGenerateRequest): Int =
        estimateMessageTokens(request.messages) + estimateToolsTokens(request.tools)

    fun estimateUsage(request: AiGenerateRequest, outputText: String): AiUsage {
        val prompt = estimatePromptTokens(request)
        val completion = estimateTokens(outputText)
        return AiUsage(
            promptTokens = prompt,
            completionTokens = completion,
            totalTokens = prompt + completion,
        )
    }
}
