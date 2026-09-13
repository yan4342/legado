package io.legado.app.help.ai

import android.net.Uri
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.AiAttachmentKind
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.EncodingDetect
import org.jsoup.Jsoup
import java.io.File

/**
 * Extracts plain text from chat attachments using the same LocalBook parsers as the reader.
 * Does **not** call [LocalBook.importFile] — no bookshelf / DB side effects.
 *
 * Callers must process attachments **serially** because [io.legado.app.model.localBook.EpubFile]
 * uses a process-wide companion cache.
 */
object AiAttachmentContentResolver {

    /** Plain-text cap after LocalBook extract (txt/epub). Larger novels are truncated. */
    const val MAX_EXTRACT_CHARS = 100_000

    data class ExtractResult(
        val text: String,
        val truncated: Boolean,
        val kind: String,
    )

    fun classifyKind(displayName: String, mimeType: String): String {
        val lower = displayName.lowercase()
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("image/") ||
                lower.endsWith(".png") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
                lower.endsWith(".webp") || lower.endsWith(".bmp") -> AiAttachmentKind.IMAGE
            mime == "application/pdf" || lower.endsWith(".pdf") -> AiAttachmentKind.DOCUMENT
            mime == "text/plain" || lower.endsWith(".txt") ||
                mime == "application/epub+zip" || lower.endsWith(".epub") ||
                mime.startsWith("text/") -> AiAttachmentKind.TEXT_EXTRACT
            else -> error("UNSUPPORTED")
        }
    }

    fun extractText(
        file: File,
        displayName: String,
        maxChars: Int = MAX_EXTRACT_CHARS,
    ): ExtractResult {
        val lower = displayName.lowercase()
        return when {
            lower.endsWith(".epub") || lower.endsWith(".txt") ->
                extractViaLocalBook(file, displayName, maxChars)
            else -> extractPlainBytes(file, maxChars)
        }
    }

    private fun extractViaLocalBook(
        file: File,
        displayName: String,
        maxChars: Int,
    ): ExtractResult {
        val book = tempBookFor(file, displayName)
        val chapters = runCatching { LocalBook.getChapterList(book) }
            .getOrElse { throw IllegalStateException("Cannot parse file: ${it.message}", it) }
        val sb = StringBuilder()
        var truncated = false
        for (chapter in chapters) {
            val raw = LocalBook.getContent(book, chapter) ?: continue
            val plain = toPlainText(raw).trim()
            if (plain.isBlank()) continue
            if (sb.isNotEmpty()) sb.append("\n\n")
            if (sb.length + plain.length > maxChars) {
                sb.append(plain.take((maxChars - sb.length).coerceAtLeast(0)))
                truncated = true
                break
            }
            sb.append(plain)
        }
        if (sb.isEmpty()) {
            // Fallback for odd txt files that fail TOC rules
            if (displayName.lowercase().endsWith(".txt")) {
                return extractPlainBytes(file, maxChars)
            }
            error("No extractable content")
        }
        return ExtractResult(
            text = sb.toString(),
            truncated = truncated,
            kind = AiAttachmentKind.TEXT_EXTRACT,
        )
    }

    private fun extractPlainBytes(file: File, maxChars: Int): ExtractResult {
        val bytes = file.readBytes()
        val charsetName = EncodingDetect.getEncode(bytes)
        val text = String(bytes, charset(charsetName))
        val truncated = text.length > maxChars
        return ExtractResult(
            text = if (truncated) text.take(maxChars) else text,
            truncated = truncated,
            kind = AiAttachmentKind.TEXT_EXTRACT,
        )
    }

    fun tempBookFor(file: File, displayName: String): Book = Book(
        type = BookType.text or BookType.local,
        bookUrl = Uri.fromFile(file).toString(),
        originName = displayName,
        name = displayName.substringBeforeLast('.').ifBlank { displayName },
        author = "",
    )

    fun toPlainText(htmlOrText: String): String {
        if (htmlOrText.isBlank()) return ""
        val trimmed = htmlOrText.trim()
        // LocalBook txt paths return plain text; epub returns HTML
        if (!looksLikeHtml(trimmed)) return trimmed
        return Jsoup.parse(trimmed).text()
    }

    private fun looksLikeHtml(text: String): Boolean {
        val sample = text.take(500).lowercase()
        return sample.contains("<html") || sample.contains("<body") ||
            sample.contains("<p") || sample.contains("<div") ||
            sample.contains("<br") || sample.contains("<h1") ||
            sample.contains("<h2") || sample.contains("<span")
    }
}
