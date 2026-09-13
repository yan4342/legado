package io.legado.app.domain.usecase

import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WebSearchUseCase(
    private val rateLimiter: WebSearchRateLimiter,
) {

    data class Hit(
        val title: String,
        val url: String,
        val snippet: String,
    )

    data class Result(
        val provider: String,
        val query: String,
        val results: List<Hit>,
    )

    /**
     * Perform a web search. Checks rate limits first; records usage only on success.
     * Mode is mutual-exclusive: `page` (BackstageWebView) or `api` (Tavily/Brave).
     * Page mode uses conversation quota only (no week/month).
     */
    suspend fun search(
        query: String,
        conversationId: String?,
        limit: Int = DEFAULT_LIMIT,
        providerOverride: String? = null,
        apiKeyOverride: String? = null,
        recordUsage: Boolean = true,
    ): Result = withContext(Dispatchers.IO) {
        val key = query.trim()
        require(key.isNotBlank()) { "query is required" }

        val isPage = AppConfig.aiWebSearchMode == "page"
        when (
            val check = if (isPage) {
                rateLimiter.checkSearchConversation(conversationId)
            } else {
                rateLimiter.check(conversationId)
            }
        ) {
            is WebSearchRateLimiter.CheckResult.Blocked ->
                error("${check.message}|quota=${check.layer}")
            WebSearchRateLimiter.CheckResult.Ok -> Unit
        }

        val capped = limit.coerceIn(1, MAX_LIMIT)
        val result = if (isPage) {
            searchPage(key, capped)
        } else {
            searchApi(key, capped, providerOverride, apiKeyOverride)
        }
        if (recordUsage) {
            if (isPage) {
                rateLimiter.recordSearchConversation(conversationId)
            } else {
                rateLimiter.record(conversationId)
            }
        }
        result
    }

    /**
     * Config-page smoke test.
     * API mode: daily test cap + week/month.
     * Page mode: daily test cap only (no week/month).
     */
    suspend fun testConnection(): Result = withContext(Dispatchers.IO) {
        val isPage = AppConfig.aiWebSearchMode == "page"
        when (
            val check = if (isPage) {
                rateLimiter.checkTestDay()
            } else {
                rateLimiter.checkTest()
            }
        ) {
            is WebSearchRateLimiter.CheckResult.Blocked ->
                error("${check.message}|quota=${check.layer}")
            WebSearchRateLimiter.CheckResult.Ok -> Unit
        }
        val probe = "legado ebook reader"
        val result = if (isPage) {
            searchPage(probe, 1)
        } else {
            val apiKey = AppConfig.aiWebSearchApiKey.trim()
            require(apiKey.isNotBlank()) { "Web search API key is not configured" }
            searchApi(probe, 1, null, apiKey)
        }
        if (isPage) {
            rateLimiter.recordTestDay()
        } else {
            rateLimiter.recordTest()
        }
        result
    }

    private suspend fun searchApi(
        query: String,
        limit: Int,
        providerOverride: String?,
        apiKeyOverride: String?,
    ): Result {
        val provider = (providerOverride ?: AppConfig.aiWebSearchProvider).lowercase()
        val apiKey = (apiKeyOverride ?: AppConfig.aiWebSearchApiKey).trim()
        require(apiKey.isNotBlank()) { "Web search API key is not configured" }
        val hits = when (provider) {
            "brave" -> searchBrave(query, apiKey, limit)
            else -> searchTavily(query, apiKey, limit)
        }
        return Result(provider = provider, query = query, results = hits)
    }

    private suspend fun searchPage(query: String, limit: Int): Result {
        val template = AppConfig.aiWebSearchPageUrlTemplate
        require(template.contains("{{key}}") || template.contains("{{query}}")) {
            "Search page URL template must contain {{key}} or {{query}}"
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = template
            .replace("{{key}}", encoded)
            .replace("{{query}}", encoded)
            .trim()
        require(url.isAbsUrl()) { "Search page URL must be absolute http(s): $url" }

        val response = BackstageWebView(
            url = url,
            delayTime = AppConfig.aiWebSearchPageDelayMs.toLong(),
        ).getStrResponse()
        val html = response.body.orEmpty()
        require(html.isNotBlank()) { "Empty search page response" }

        val hits = parseSearchPageHtml(html, url, limit)
        require(hits.isNotEmpty()) { "No results parsed from search page" }
        return Result(provider = "page", query = query, results = hits)
    }

    private fun parseSearchPageHtml(html: String, pageUrl: String, limit: Int): List<Hit> {
        val doc = Jsoup.parse(html, pageUrl)
        val configured = parseConfiguredResults(doc)
        if (configured.isNotEmpty()) return configured.take(limit)
        return parseGenericResults(doc, pageUrl).take(limit)
    }

    private fun parseConfiguredResults(doc: org.jsoup.nodes.Document): List<Hit> {
        val resultSel = AppConfig.aiWebSearchPageResultSelector
        val titleSel = AppConfig.aiWebSearchPageTitleSelector
        val snippetSel = AppConfig.aiWebSearchPageSnippetSelector
        val results = runCatching { doc.select(resultSel) }.getOrDefault(org.jsoup.select.Elements())
        if (results.isEmpty()) return emptyList()
        val hits = ArrayList<Hit>()
        for (el in results) {
            val a = runCatching { el.selectFirst(titleSel) }.getOrNull() ?: continue
            val href = resolveResultUrl(a.absUrl("href").ifBlank { a.attr("href") })
            if (!isUsefulResultUrl(href)) continue
            val title = a.text().trim()
            if (title.isBlank()) continue
            val snippet = runCatching { el.selectFirst(snippetSel)?.text() }.getOrNull()
                .orEmpty()
                .trim()
                .take(SNIPPET_MAX)
            hits.add(Hit(title = title, url = href, snippet = snippet))
        }
        return hits.distinctBy { it.url }
    }

    private fun parseGenericResults(doc: org.jsoup.nodes.Document, pageUrl: String): List<Hit> {
        val pageHost = runCatching { pageUrl.toHttpUrl().host }.getOrNull().orEmpty()
        val hits = ArrayList<Hit>()
        val seen = HashSet<String>()
        for (a in doc.select("a[href]")) {
            val href = resolveResultUrl(a.absUrl("href").ifBlank { a.attr("href") })
            if (!isUsefulResultUrl(href, extraExcludeHost = pageHost)) continue
            if (!seen.add(href)) continue
            val title = a.text().trim()
            if (title.length < 3) continue
            val snippet = nearestSnippet(a).take(SNIPPET_MAX)
            hits.add(Hit(title = title, url = href, snippet = snippet))
            if (hits.size >= MAX_LIMIT * 2) break
        }
        return hits
    }

    private fun nearestSnippet(anchor: Element): String {
        val parent = anchor.parent() ?: return ""
        val text = parent.text().replace(anchor.text(), "").trim()
        return text.take(SNIPPET_MAX)
    }

    /**
     * Unwrap search-engine redirect wrappers using [AppConfig.aiWebSearchPageRedirectParam]
     * (e.g. DDG `uddg`) to the real destination before filtering.
     */
    private fun resolveResultUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val absolute = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
        val param = AppConfig.aiWebSearchPageRedirectParam.trim()
        if (param.isBlank()) return absolute
        val httpUrl = runCatching { absolute.toHttpUrl() }.getOrNull()
        if (httpUrl != null) {
            val unwrapped = httpUrl.queryParameter(param)
            if (!unwrapped.isNullOrBlank()) return unwrapped.trim()
        } else {
            val encoded = Regex("""[?&]${Regex.escape(param)}=([^&]+)""")
                .find(absolute)?.groupValues?.getOrNull(1)
            if (!encoded.isNullOrBlank()) {
                return runCatching {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                }.getOrDefault(encoded).trim()
            }
        }
        return absolute
    }

    private fun excludeHosts(): List<String> =
        AppConfig.aiWebSearchPageExcludeHosts
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

    private fun isUsefulResultUrl(url: String, extraExcludeHost: String = ""): Boolean {
        if (url.isBlank() || !url.isAbsUrl()) return false
        if (url.startsWith("javascript:", ignoreCase = true)) return false
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return false
        val host = httpUrl.host.lowercase()
        if (extraExcludeHost.isNotBlank() && host.contains(extraExcludeHost.lowercase())) {
            return false
        }
        for (excluded in excludeHosts()) {
            if (host.contains(excluded)) return false
            if (excluded == "google.com" && host.startsWith("www.google.")) return false
        }
        if (url.contains("/aclick")) return false
        return true
    }

    private suspend fun searchBrave(query: String, apiKey: String, limit: Int): List<Hit> {
        val endpoint = joinBaseUrl(AppConfig.aiWebSearchBraveBaseUrl, BRAVE_SEARCH_PATH)
        val httpUrl = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", limit.toString())
            .build()
        val response = okHttpClient.newCallStrResponse {
            url(httpUrl)
            addHeader("Accept", "application/json")
            addHeader("X-Subscription-Token", apiKey)
        }
        if (!response.isSuccessful()) {
            error("Brave search failed: HTTP ${response.code()} ${response.errorBody?.string() ?: response.body}")
        }
        val root = GSON.fromJsonObject<Map<String, Any?>>(response.body.orEmpty()).getOrNull()
            ?: error("Brave search: invalid JSON")
        @Suppress("UNCHECKED_CAST")
        val web = root["web"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = web?.get("results") as? List<Map<String, Any?>> ?: emptyList()
        return results.take(limit).map { item ->
            Hit(
                title = (item["title"] as? String).orEmpty(),
                url = (item["url"] as? String).orEmpty(),
                snippet = ((item["description"] as? String) ?: "").take(SNIPPET_MAX),
            )
        }.filter { it.url.isNotBlank() }
    }

    private suspend fun searchTavily(query: String, apiKey: String, limit: Int): List<Hit> {
        val bodyJson = GSON.toJson(
            mapOf(
                "query" to query,
                "max_results" to limit,
                "include_answer" to false,
            ),
        )
        val endpoint = joinBaseUrl(AppConfig.aiWebSearchTavilyBaseUrl, TAVILY_SEARCH_PATH)
        val response = okHttpClient.newCallStrResponse {
            url(endpoint)
            addHeader("Authorization", "Bearer $apiKey")
            postJson(bodyJson)
        }
        if (!response.isSuccessful()) {
            error("Tavily search failed: HTTP ${response.code()} ${response.errorBody?.string() ?: response.body}")
        }
        val root = GSON.fromJsonObject<Map<String, Any?>>(response.body.orEmpty()).getOrNull()
            ?: error("Tavily search: invalid JSON")
        @Suppress("UNCHECKED_CAST")
        val results = root["results"] as? List<Map<String, Any?>> ?: emptyList()
        return results.take(limit).map { item ->
            Hit(
                title = (item["title"] as? String).orEmpty(),
                url = (item["url"] as? String).orEmpty(),
                snippet = ((item["content"] as? String) ?: "").take(SNIPPET_MAX),
            )
        }.filter { it.url.isNotBlank() }
    }

    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 8
        const val SNIPPET_MAX = 200
        private const val BRAVE_SEARCH_PATH = "/res/v1/web/search"
        private const val TAVILY_SEARCH_PATH = "/search"

        /** Join API origin with path; if [base] already ends with the path, use as-is. */
        fun joinBaseUrl(base: String, path: String): String {
            val origin = base.trim().trimEnd('/')
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            if (origin.endsWith(normalizedPath)) return origin
            return origin + normalizedPath
        }
    }
}
