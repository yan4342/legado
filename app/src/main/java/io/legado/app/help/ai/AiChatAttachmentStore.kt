package io.legado.app.help.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.domain.model.AiAttachmentKind
import splitties.init.appCtx
import java.io.File
import java.util.UUID

/**
 * Copies AI chat attachments into app-private storage so URIs remain durable.
 */
object AiChatAttachmentStore {

    /** Size cap for image / PDF (base64 multimodal). txt/epub have no byte cap — truncated by chars. */
    const val MAX_BINARY_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_ATTACHMENTS_PER_SEND = 5

    private const val DIR_NAME = "ai_chat_attachments"

    data class CopiedFile(
        val id: String,
        val file: File,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
    )

    fun attachmentsDir(context: Context = appCtx): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /**
     * @return null = no byte-size limit (text extract); otherwise max allowed bytes.
     */
    fun maxBytesFor(displayName: String, mimeType: String): Long? {
        return when (AiAttachmentContentResolver.classifyKind(displayName, mimeType)) {
            AiAttachmentKind.TEXT_EXTRACT -> null
            else -> MAX_BINARY_FILE_BYTES
        }
    }

    fun copyFromUri(uri: Uri, context: Context = appCtx): CopiedFile {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "attachment"
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { guessMime(displayName) }
        val maxBytes = maxBytesFor(displayName, mimeType)
        // Reject oversized binary before copying when ContentProvider reports size
        if (maxBytes != null) {
            querySize(context, uri)?.let { reported ->
                if (reported > maxBytes) error("FILE_TOO_LARGE")
            }
        }
        val id = UUID.randomUUID().toString()
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val dest = File(attachmentsDir(context), "${id}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open attachment")
        val size = dest.length()
        if (size <= 0L) {
            dest.delete()
            error("Empty attachment")
        }
        if (maxBytes != null && size > maxBytes) {
            dest.delete()
            error("FILE_TOO_LARGE")
        }
        return CopiedFile(
            id = id,
            file = dest,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = size,
        )
    }

    fun writeExtractedText(sourceId: String, text: String, context: Context = appCtx): File {
        val file = File(attachmentsDir(context), "$sourceId.extracted.txt")
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    fun readExtractedText(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8)
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun deleteCopied(copied: CopiedFile) {
        runCatching { copied.file.delete() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        return null
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".epub") -> "application/epub+zip"
            else -> "application/octet-stream"
        }
    }

    // ---- Image store (AI-generated / downloaded / inline SVG) ----

    private const val IMAGE_DIR_NAME = "ai_images"
    private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

    fun imagesDir(context: Context = appCtx): File =
        File(context.filesDir, IMAGE_DIR_NAME).also { it.mkdirs() }

    fun writeImageBytes(bytes: ByteArray, mimeType: String, context: Context = appCtx): File {
        require(bytes.size <= MAX_IMAGE_BYTES) { "Image exceeds $MAX_IMAGE_BYTES bytes" }
        val isSvg = mimeType.contains("svg", ignoreCase = true)
        val ext = when {
            isSvg -> "svg"
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            else -> "png"
        }
        val file = File(imagesDir(context), "${UUID.randomUUID()}.$ext")
        if (isSvg) {
            val sanitized = sanitizeSvg(bytes.toString(Charsets.UTF_8))
            file.writeText(sanitized, Charsets.UTF_8)
        } else {
            file.writeBytes(bytes)
        }
        return file
    }

    fun writeImageDataUri(dataUri: String, context: Context = appCtx): File? {
        val match = Regex("""^data:(image/[a-z+]+);base64,(.+)$""", RegexOption.IGNORE_CASE).find(dataUri.trim())
            ?: return null
        val mimeType = match.groupValues[1]
        val base64 = match.groupValues[2].replace(Regex("\\s"), "")
        val bytes = try { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) } catch (_: Exception) { return null }
        return writeImageBytes(bytes, mimeType, context)
    }

    /** Strip script elements, event handlers, and foreignObject from SVG text for XSS safety. */
    fun sanitizeSvg(svg: String): String {
        return svg
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<foreignObject[\s\S]*?</foreignObject>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bon\w+\s*=\s*"[^"]*"|\bon\w+\s*=\s*'[^']*'""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bon\w+\s*=\s*\S+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""javascript\s*:""", RegexOption.IGNORE_CASE), "")
    }
}
