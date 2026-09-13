package io.legado.app.ui.ai.chat

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.toolParts
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.GSON

data class ToolBookNavTarget(
    val bookUrl: String,
    val name: String,
    val author: String,
)

/** Open in-app full-text search UI (SearchContentActivity) with optional prefilled hits. */
data class ToolContentSearchNav(
    val bookUrl: String,
    val name: String,
    val author: String,
    val query: String,
    val results: List<SearchResult>,
)

private val BOOK_DETAIL_NAV_TOOLS = setOf(
    AiToolRepository.TOOL_SEARCH_BOOKS,
    AiToolRepository.TOOL_SEARCH_BOOK_SOURCES,
    AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF,
    AiToolRepository.TOOL_GET_BOOK_DETAIL,
    AiToolRepository.TOOL_LIST_BOOK_CHAPTERS,
    AiToolRepository.TOOL_GET_CHAPTER_CONTENT,
)

fun isBookDetailNavTool(toolName: String): Boolean = toolName in BOOK_DETAIL_NAV_TOOLS

fun isContentSearchNavTool(toolName: String): Boolean =
    toolName == AiToolRepository.TOOL_SEARCH_BOOK_CONTENT

fun resolveToolBookNav(
    toolName: String,
    inputJson: String,
    outputJson: String,
): ToolBookNavTarget? {
    if (toolName !in BOOK_DETAIL_NAV_TOOLS) return null
    val output = parseJsonObject(outputJson) ?: return parseInputBookNav(inputJson)
    if (!output.string("error").isNullOrBlank()) return null
    output.getAsJsonObject("book")?.toBookNavTarget()?.let { return it }
    output.getAsJsonArray("books")?.let { books ->
        for (i in 0 until books.size()) {
            books.get(i).asJsonObjectOrNull()?.toBookNavTarget()?.let { return it }
        }
    }
    output.toBookNavTarget()?.let { return it }
    return parseInputBookNav(inputJson)
}

fun resolveToolContentSearchNav(
    toolName: String,
    inputJson: String,
    outputJson: String,
): ToolContentSearchNav? {
    if (!isContentSearchNavTool(toolName)) return null
    val output = parseJsonObject(outputJson) ?: return null
    if (!output.string("error").isNullOrBlank()) return null
    val book = output.getAsJsonObject("book") ?: return null
    val bookUrl = book.string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    val query = output.string("query").orEmpty().ifBlank {
        parseJsonObject(inputJson)?.string("query").orEmpty()
    }
    if (query.isBlank()) return null
    val hits = output.getAsJsonArray("hits")
    val results = buildList {
        if (hits != null) {
            for (i in 0 until hits.size()) {
                val hit = hits.get(i).asJsonObjectOrNull() ?: continue
                toSearchResult(hit, query, globalIndex = size)?.let { add(it) }
            }
        }
    }
    return ToolContentSearchNav(
        bookUrl = bookUrl,
        name = book.string("name").orEmpty(),
        author = book.string("author").orEmpty(),
        query = query,
        results = results,
    )
}

private fun toSearchResult(hit: JsonObject, fallbackQuery: String, globalIndex: Int): SearchResult? {
    val chapterIndex = hit.get("chapterIndex")?.takeIf { !it.isJsonNull }?.asInt ?: return null
    val chapterTitle = hit.string("chapterTitle").orEmpty()
    val snippet = hit.string("snippet").orEmpty().ifBlank { chapterTitle }
    val charOffset = hit.get("charOffset")?.takeIf { !it.isJsonNull }?.asInt ?: 0
    val matched = hit.getAsJsonArray("matchedKeywords")
        ?.mapNotNull { el ->
            el.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        }
        .orEmpty()
    val queryForHighlight = matched.firstOrNull { snippet.contains(it, ignoreCase = true) }
        ?: matched.firstOrNull()
        ?: fallbackQuery.split(Regex("\\s+")).map { it.trim('"', ' ') }.firstOrNull {
            it.isNotBlank() && snippet.contains(it, ignoreCase = true)
        }
        ?: fallbackQuery.trim().trim('"')
    val queryIndexInResult = snippet.indexOf(queryForHighlight, ignoreCase = true)
        .takeIf { it >= 0 } ?: 0
    // Use exact casing from the snippet so SearchResult HTML highlight won't miss.
    val queryExact = if (
        queryForHighlight.isNotEmpty() &&
        queryIndexInResult + queryForHighlight.length <= snippet.length
    ) {
        snippet.substring(queryIndexInResult, queryIndexInResult + queryForHighlight.length)
    } else {
        queryForHighlight
    }
    val withinChapter = hit.get("resultCountWithinChapter")?.takeIf { !it.isJsonNull }?.asInt ?: 0
    return SearchResult(
        resultCount = globalIndex,
        resultCountWithinChapter = withinChapter,
        resultText = snippet,
        chapterTitle = chapterTitle,
        query = queryExact,
        chapterIndex = chapterIndex,
        queryIndexInResult = queryIndexInResult,
        queryIndexInChapter = charOffset,
    )
}

private fun parseInputBookNav(inputJson: String): ToolBookNavTarget? {
    val input = parseJsonObject(inputJson.trim().ifBlank { "{}" }) ?: return null
    val bookUrl = input.string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return ToolBookNavTarget(
        bookUrl = bookUrl,
        name = input.string("name").orEmpty().ifBlank { input.string("bookName").orEmpty() },
        author = input.string("author").orEmpty(),
    )
}

private fun JsonObject.toBookNavTarget(): ToolBookNavTarget? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return ToolBookNavTarget(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
    )
}

private fun parseJsonObject(json: String): JsonObject? {
    val trimmed = json.trim()
    if (trimmed.isBlank()) return null
    return runCatching { GSON.fromJson(trimmed, JsonObject::class.java) }.getOrNull()
}

internal fun List<AiMessagePart>.extractBookResults(): List<AiChatBookResultUi> {
    val explicit = filterIsInstance<AiMessagePart.BookResult>().map {
        AiChatBookResultUi(
            bookUrl = it.bookUrl, name = it.name, author = it.author,
            origin = it.origin, coverPath = it.coverPath,
            latestChapterTitle = it.latestChapterTitle,
            currentChapterTitle = it.currentChapterTitle, intro = it.intro,
        )
    }
    if (explicit.isNotEmpty()) return explicit.distinctBy { it.bookUrl }
    val books = linkedMapOf<String, AiChatBookResultUi>()
    toolParts().forEach { tool ->
        val root = runCatching { GSON.fromJson(tool.output, JsonObject::class.java) }.getOrNull() ?: return@forEach
        root.getAsJsonArray("books")?.forEach { element ->
            element.asJsonObjectOrNull()?.toBookResultUi()?.let { books.putIfAbsent(it.bookUrl, it) }
        }
        root.getAsJsonObject("book")?.toBookResultUi()?.let {
            books.putIfAbsent(it.bookUrl, it)
        }
    }
    return books.values.toList()
}

private fun JsonObject.toBookResultUi(): AiChatBookResultUi? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    val coverFromJson = string("coverPath") ?: string("coverUrl")
    val coverPath = coverFromJson?.takeIf { it.isNotBlank() }
        ?: appDb.bookDao.getBook(bookUrl)?.getDisplayCover()
        ?: appDb.searchBookDao.getSearchBook(bookUrl)?.coverUrl
    return AiChatBookResultUi(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = coverPath,
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro"),
    )
}

private fun JsonObject.string(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString
private fun JsonObject.getAsJsonObject(name: String): JsonObject? =
    get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }
private fun JsonObject.getAsJsonArray(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()
private fun com.google.gson.JsonElement?.asJsonObjectOrNull(): JsonObject? =
    this?.takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
