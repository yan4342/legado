package io.legado.app.help.ai

import java.util.Base64
import io.legado.app.domain.model.AiAttachmentKind
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiContentBlock
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.attachmentParts
import io.legado.app.domain.model.textContent
import java.io.File

/**
 * Maps stored user message parts (text + attachments) into API [AiMessage] / [AiContentBlock]s.
 */
object AiContentBlockMapper {

    const val IMAGE_TOKEN_ESTIMATE = 1000

    sealed interface MapResult {
        data class Ok(val message: AiMessage) : MapResult
        data class Error(val reason: String) : MapResult
    }

    fun userPartsToAiMessage(
        parts: List<AiMessagePart>,
        capabilities: Set<String>,
        userTextOverride: String? = null,
    ): MapResult {
        val text = userTextOverride?.takeIf { it.isNotBlank() } ?: parts.textContent()
        val attachments = parts.attachmentParts()
        if (attachments.isEmpty()) {
            return MapResult.Ok(AiMessage(AiMessageRole.USER, text))
        }

        val blocks = mutableListOf<AiContentBlock>()
        var hasVisionRequired = false

        for (att in attachments) {
            when (att.kind) {
                AiAttachmentKind.IMAGE -> {
                    hasVisionRequired = true
                    val bytes = readBytes(att.localPath)
                        ?: return MapResult.Error("MISSING_ATTACHMENT")
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    blocks.add(AiContentBlock.Image(att.mimeType.ifBlank { "image/jpeg" }, b64))
                }
                AiAttachmentKind.DOCUMENT -> {
                    val bytes = readBytes(att.localPath)
                        ?: return MapResult.Error("MISSING_ATTACHMENT")
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    blocks.add(
                        AiContentBlock.Document(
                            mimeType = att.mimeType.ifBlank { "application/pdf" },
                            base64 = b64,
                            filename = att.displayName,
                        ),
                    )
                }
                AiAttachmentKind.TEXT_EXTRACT -> {
                    val extracted = AiChatAttachmentStore.readExtractedText(att.extractedTextPath)
                        ?: return MapResult.Error("MISSING_ATTACHMENT")
                    val label = "[附件: ${att.displayName}]"
                    val body = if (att.truncated) {
                        "$extracted\n\n…(truncated)"
                    } else {
                        extracted
                    }
                    blocks.add(AiContentBlock.Text("$label\n$body"))
                }
                else -> return MapResult.Error("UNSUPPORTED")
            }
        }

        if (hasVisionRequired && AiCapability.VISION !in capabilities) {
            return MapResult.Error("NO_VISION")
        }

        if (text.isNotBlank()) {
            blocks.add(AiContentBlock.Text(text))
        }

        if (blocks.isEmpty()) {
            return MapResult.Error("EMPTY")
        }

        // Prefer plain string when everything collapsed to a single text block (handlers stay simple)
        val onlyText = blocks.filterIsInstance<AiContentBlock.Text>()
        if (onlyText.size == blocks.size) {
            val joined = onlyText.joinToString("\n\n") { it.text }
            return MapResult.Ok(AiMessage(AiMessageRole.USER, joined))
        }

        val plainFallback = onlyText.joinToString("\n\n") { it.text }.ifBlank { text }
        return MapResult.Ok(
            AiMessage(
                role = AiMessageRole.USER,
                content = plainFallback,
                contentBlocks = blocks,
            ),
        )
    }

    fun estimateAttachmentTokens(parts: List<AiMessagePart>): Int {
        var tokens = 0
        for (att in parts.attachmentParts()) {
            when (att.kind) {
                AiAttachmentKind.IMAGE -> tokens += IMAGE_TOKEN_ESTIMATE
                AiAttachmentKind.DOCUMENT -> tokens += IMAGE_TOKEN_ESTIMATE * 2
                AiAttachmentKind.TEXT_EXTRACT -> {
                    val extracted = AiChatAttachmentStore.readExtractedText(att.extractedTextPath).orEmpty()
                    tokens += AiTokenEstimator.estimateTokens(extracted)
                }
            }
        }
        return tokens
    }

    fun formatAttachmentPreviewForPrompt(parts: List<AiMessagePart>): String {
        val names = parts.attachmentParts().map { it.displayName }
        if (names.isEmpty()) return ""
        return names.joinToString(", ") { "[附件: $it]" }
    }

    private fun readBytes(path: String): ByteArray? {
        val file = File(path)
        if (!file.isFile) return null
        return file.readBytes()
    }
}
