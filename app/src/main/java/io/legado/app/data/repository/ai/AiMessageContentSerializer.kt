package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiContentBlock
import io.legado.app.domain.model.AiMessage

/**
 * Shared multimodal content serialization for OpenAI Chat / Responses and Anthropic.
 */
internal object AiMessageContentSerializer {

    /** OpenAI Chat Completions: string or array of {type:text|image_url|file}. */
    fun toOpenAiChatContent(message: AiMessage): Any {
        val blocks = message.contentBlocks
        if (blocks.isNullOrEmpty()) return message.content
        return blocks.mapNotNull { block ->
            when (block) {
                is AiContentBlock.Text -> mapOf(
                    "type" to "text",
                    "text" to block.text,
                )
                is AiContentBlock.Image -> mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf(
                        "url" to "data:${block.mimeType};base64,${block.base64}",
                    ),
                )
                is AiContentBlock.Document -> mapOf(
                    "type" to "file",
                    "file" to mapOf(
                        "filename" to block.filename,
                        "file_data" to "data:${block.mimeType};base64,${block.base64}",
                    ),
                )
            }
        }.ifEmpty { message.content }
    }

    /** OpenAI Responses API input content. */
    fun toOpenAiResponsesContent(message: AiMessage): Any {
        val blocks = message.contentBlocks
        if (blocks.isNullOrEmpty()) return message.content
        return blocks.mapNotNull { block ->
            when (block) {
                is AiContentBlock.Text -> mapOf(
                    "type" to "input_text",
                    "text" to block.text,
                )
                is AiContentBlock.Image -> mapOf(
                    "type" to "input_image",
                    "image_url" to "data:${block.mimeType};base64,${block.base64}",
                )
                is AiContentBlock.Document -> mapOf(
                    "type" to "input_file",
                    "filename" to block.filename,
                    "file_data" to "data:${block.mimeType};base64,${block.base64}",
                )
            }
        }.ifEmpty { message.content }
    }

    /** Anthropic Messages API content blocks. */
    fun toAnthropicContent(message: AiMessage): Any {
        val blocks = message.contentBlocks
        if (blocks.isNullOrEmpty()) return message.content
        return blocks.mapNotNull { block ->
            when (block) {
                is AiContentBlock.Text -> mapOf(
                    "type" to "text",
                    "text" to block.text,
                )
                is AiContentBlock.Image -> mapOf(
                    "type" to "image",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to block.mimeType,
                        "data" to block.base64,
                    ),
                )
                is AiContentBlock.Document -> mapOf(
                    "type" to "document",
                    "source" to mapOf(
                        "type" to "base64",
                        "media_type" to block.mimeType,
                        "data" to block.base64,
                    ),
                )
            }
        }.ifEmpty { message.content }
    }

    fun hasMultimodalBlocks(message: AiMessage): Boolean =
        !message.contentBlocks.isNullOrEmpty()
}
