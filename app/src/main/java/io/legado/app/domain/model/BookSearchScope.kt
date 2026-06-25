package io.legado.app.domain.model

import io.legado.app.utils.splitNotBlank

data class BookSearchScope(val raw: String) {

    private data class ParsedSearchScope(
        val groups: List<String> = emptyList(),
        val sources: List<ScopeSourceItem> = emptyList(),
    ) {
        val isAll: Boolean get() = groups.isEmpty() && sources.isEmpty()
    }

    data class ScopeSourceItem(val name: String, val url: String)

    private val parsed: ParsedSearchScope by lazy { parse(raw) }

    val isAll: Boolean get() = parsed.isAll
    val isSource: Boolean get() = parsed.sources.isNotEmpty()
    val groupNames: List<String> get() = parsed.groups
    val sourceUrls: List<String> get() = parsed.sources.map { it.url }

    private fun parse(raw: String): ParsedSearchScope {
        if (raw.isEmpty()) return ParsedSearchScope()
        parseJson(raw)?.let { return it }
        return parseLegacy(raw)
    }

    private fun parseJson(raw: String): ParsedSearchScope? {
        val json = raw.trim()
        if (!json.startsWith("{") || !json.endsWith("}")) return null
        return try {
            val scope = org.json.JSONObject(json)
            when (scope.optString("type")) {
                "source" -> {
                    val sources = scope.optJSONArray("sources") ?: return null
                    val items = (0 until sources.length()).mapNotNull { i ->
                        val item = sources.optJSONObject(i) ?: return@mapNotNull null
                        val url = item.optString("url", "")
                        if (url.isBlank()) null
                        else ScopeSourceItem(item.optString("name", ""), url)
                    }
                    ParsedSearchScope(sources = items)
                }
                "group" -> {
                    val groups = scope.optJSONArray("groups") ?: return null
                    val items = (0 until groups.length()).mapNotNull { i ->
                        groups.optString(i, "").takeIf { it.isNotBlank() }
                    }
                    ParsedSearchScope(groups = items)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacy(raw: String): ParsedSearchScope {
        val rawItems = raw.split(",").filter { it.isNotBlank() }
        val sourceItems = rawItems.mapNotNull { item ->
            val splitIndex = item.indexOf("::")
            if (splitIndex <= 0 || splitIndex >= item.lastIndex) null
            else ScopeSourceItem(item.substring(0, splitIndex), item.substring(splitIndex + 2))
        }
        if (rawItems.isNotEmpty() && sourceItems.size == rawItems.size) {
            return ParsedSearchScope(sources = sourceItems)
        }
        return ParsedSearchScope(groups = raw.splitNotBlank(",").toList())
    }
}
