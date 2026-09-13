package io.legado.app.domain.usecase

import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Local URL fetch + readable text extraction for chat [read_web_page].
 * Not for book-source HTML debugging ([fetch_page_snippet]).
 */
class FetchWebPageUseCase(
    private val rateLimiter: WebSearchRateLimiter,
) {

    data class Result(
        val url: String,
        val title: String,
        val text: String,
        val chars: Int,
        val truncated: Boolean,
        val method: String,
        val warning: String? = null,
    )

    suspend fun fetch(
        url: String,
        conversationId: String?,
        maxChars: Int = DEFAULT_MAX_CHARS,
        recordUsage: Boolean = true,
    ): Result = withContext(Dispatchers.IO) {
        val target = url.trim()
        require(target.isNotBlank() && target.isAbsUrl()) {
            "url must be an absolute http(s) URL"
        }
        val capped = maxChars.coerceIn(MIN_CHARS, MAX_CHARS)

        when (val check = rateLimiter.checkReadConversation(conversationId)) {
            is WebSearchRateLimiter.CheckResult.Blocked ->
                error("${check.message}|quota=${check.layer}")
            WebSearchRateLimiter.CheckResult.Ok -> Unit
        }

        val response = AnalyzeUrl(mUrl = target).getStrResponseAwait(useWebView = false)
        val finalUrl = response.url.takeIf { it.isNotBlank() } ?: target
        val html = response.body.orEmpty()
        require(html.isNotBlank()) { "Empty response body" }

        val extracted = extractReadable(html, finalUrl)
        val truncated = extracted.text.length > capped
        val text = extracted.text.take(capped)
        val warning = when {
            text.length < SHORT_TEXT_THRESHOLD ->
                "Extracted text is very short — page may be SPA/client-rendered or blocked. Prefer another URL or rely on web_search snippets."
            else -> null
        }

        if (recordUsage) {
            rateLimiter.recordReadConversation(conversationId)
        }

        Result(
            url = finalUrl,
            title = extracted.title,
            text = text,
            chars = text.length,
            truncated = truncated,
            method = extracted.method,
            warning = warning,
        )
    }

    private data class Extracted(
        val title: String,
        val text: String,
        val method: String,
    )

    private fun extractReadable(html: String, baseUri: String): Extracted {
        val doc = Jsoup.parse(html, baseUri)
        doc.select("script, style, noscript, template, svg, iframe").remove()
        val title = doc.title().orEmpty().trim()

        val best = doc.select("article, main, [role=main]")
            .maxByOrNull { it.text().length }
        val useCandidate = best != null && best.text().length >= SHORT_TEXT_THRESHOLD
        val contentEl: Element = if (useCandidate) best!! else (doc.body() ?: doc)
        val method = when {
            !useCandidate -> "body"
            best!!.tagName().equals("article", ignoreCase = true) -> "article"
            best.tagName().equals("main", ignoreCase = true) -> "main"
            else -> "role=main"
        }
        return Extracted(
            title = title,
            text = normalizeText(contentEl.text()),
            method = method,
        )
    }

    private fun normalizeText(raw: String): String =
        raw
            .replace(WHITESPACE_RUN, " ")
            .replace(MULTI_NEWLINE, "\n\n")
            .trim()

    companion object {
        const val DEFAULT_MAX_CHARS = 6000
        const val MIN_CHARS = 500
        const val MAX_CHARS = 12_000
        const val SHORT_TEXT_THRESHOLD = 200
        private val WHITESPACE_RUN = Regex("[ \\t\\x0B\\f\\r]+")
        private val MULTI_NEWLINE = Regex("\\n{3,}")
    }
}
