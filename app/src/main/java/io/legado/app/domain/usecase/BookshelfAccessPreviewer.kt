package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig

class BookshelfAccessPreviewer(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookSourceDao: BookSourceDao,
) {

    data class ChapterReadPreview(
        val bookName: String,
        val chapterTitles: List<String>,
        val indices: List<Int>,
        val cachedCount: Int,
        val needsFetchCount: Int,
        val sourceName: String?,
        val fetchEnabled: Boolean,
        val validationError: String? = null,
    )

    data class ContentSearchPreview(
        val bookName: String,
        val query: String,
        val cachedCount: Int,
        val needsFetchCount: Int,
        val sourceName: String?,
        val fetchEnabled: Boolean,
        val validationError: String? = null,
    )

    fun previewSearch(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim().ifBlank { "(recent books)" }
        val limit = args.int("limit", 8).coerceIn(1, 20)
        return "Search bookshelf: \"$query\" (limit $limit)"
    }

    fun previewSourceSearch(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 20).coerceIn(1, 30)
        if (!AppConfig.aiAllowBookSourceFetch) {
            return "Search book sources: disabled in AI settings"
        }
        if (query.isBlank()) {
            return "Search book sources: query required"
        }
        return "Search book sources: \"$query\" (limit $limit)"
    }

    suspend fun previewChapterRead(args: JsonObject, book: Book? = null): ChapterReadPreview {
        val resolved = book ?: resolveBook(args)
        if (resolved == null) {
            return ChapterReadPreview(
                bookName = args.string("bookName") ?: "?",
                chapterTitles = emptyList(),
                indices = emptyList(),
                cachedCount = 0,
                needsFetchCount = 0,
                sourceName = null,
                fetchEnabled = AppConfig.aiAllowBookSourceFetch,
                validationError = "Book not found",
            )
        }
        val parsed = BookshelfChapterIndices.parse(args, resolved.durChapterIndex)
        if (parsed is BookshelfChapterIndices.ParseResult.Error) {
            return ChapterReadPreview(
                bookName = resolved.name,
                chapterTitles = emptyList(),
                indices = emptyList(),
                cachedCount = 0,
                needsFetchCount = 0,
                sourceName = null,
                fetchEnabled = AppConfig.aiAllowBookSourceFetch,
                validationError = parsed.message,
            )
        }
        val indices = (parsed as BookshelfChapterIndices.ParseResult.Success).indices
        var cached = 0
        var needsFetch = 0
        val titles = indices.map { index ->
            val chapter = bookChapterDao.getChapter(resolved.bookUrl, index)
            if (chapter == null) {
                needsFetch++
                return@map "Chapter ${index + 1} (missing)"
            }
            when {
                resolved.isLocal || BookHelp.hasContent(resolved, chapter) -> {
                    cached++
                    chapter.title
                }
                else -> {
                    needsFetch++
                    chapter.title
                }
            }
        }
        val sourceName = if (needsFetch > 0) {
            bookSourceDao.getBookSource(resolved.origin)?.bookSourceName ?: resolved.originName
        } else null
        return ChapterReadPreview(
            bookName = resolved.name,
            chapterTitles = titles,
            indices = indices,
            cachedCount = cached,
            needsFetchCount = needsFetch,
            sourceName = sourceName,
            fetchEnabled = AppConfig.aiAllowBookSourceFetch,
        )
    }

    suspend fun anyNeedsNetworkFetch(book: Book, indices: List<Int>): Boolean {
        if (book.isLocal) return false
        return indices.any { index ->
            val chapter = bookChapterDao.getChapter(book.bookUrl, index) ?: return@any true
            !BookHelp.hasContent(book, chapter)
        }
    }

    /**
     * Preview for search_book_content when network fetch may be needed.
     */
    suspend fun previewContentSearch(args: JsonObject): ContentSearchPreview {
        val book = resolveBook(args)
        if (book == null) {
            return ContentSearchPreview(
                bookName = args.string("bookName") ?: "?",
                query = args.string("query").orEmpty().trim(),
                cachedCount = 0,
                needsFetchCount = 0,
                sourceName = null,
                fetchEnabled = AppConfig.aiAllowBookSourceFetch,
                validationError = "Book not found",
            )
        }
        val query = args.string("query").orEmpty().trim()
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
        val startIdx = (args.int("chapterStart", 0)).coerceAtLeast(0)
        val chapterLimit = args.get("chapterLimit")?.takeIf { !it.isJsonNull }?.asInt
        val window = when {
            chapterLimit != null && chapterLimit > 0 ->
                chapters.filter { it.index >= startIdx && it.index < startIdx + chapterLimit }
                    .ifEmpty { chapters.drop(startIdx).take(chapterLimit) }
            else -> chapters.filter { it.index >= startIdx }.ifEmpty { chapters.drop(startIdx) }
        }
        var cached = 0
        var needsFetch = 0
        for (chapter in window) {
            if (book.isLocal || BookHelp.hasContent(book, chapter)) {
                cached++
            } else {
                needsFetch++
            }
        }
        val sourceName = if (needsFetch > 0) {
            bookSourceDao.getBookSource(book.origin)?.bookSourceName ?: book.originName
        } else null
        return ContentSearchPreview(
            bookName = book.name,
            query = query,
            cachedCount = cached,
            needsFetchCount = needsFetch,
            sourceName = sourceName,
            fetchEnabled = AppConfig.aiAllowBookSourceFetch,
            validationError = if (query.isBlank()) "query is required" else null,
        )
    }

    fun formatContentSearchSummary(preview: ContentSearchPreview): String {
        preview.validationError?.let { return it }
        val q = preview.query.ifBlank { "(empty)" }
        val sb = StringBuilder("Search 《${preview.bookName}》 content: \"$q\"")
        if (preview.needsFetchCount > 0) {
            sb.append(" (${preview.cachedCount} cached")
            sb.append(", up to ${preview.needsFetchCount.coerceAtMost(SearchBookContentUseCase.MAX_FETCH_PER_SEARCH)} from source")
            preview.sourceName?.let { sb.append("「$it」") }
            sb.append(')')
        }
        return sb.toString()
    }

    fun formatContentSearchPreview(preview: ContentSearchPreview): String {
        preview.validationError?.let { return it }
        val sb = StringBuilder(formatContentSearchSummary(preview))
        sb.append('\n')
        if (preview.needsFetchCount > 0) {
            sb.append("${preview.cachedCount} chapters cached, ${preview.needsFetchCount} uncached")
            preview.sourceName?.let { sb.append(" — may fetch from \"$it\"") }
            sb.append(" (max ${SearchBookContentUseCase.MAX_FETCH_PER_SEARCH} fetches per call)")
            if (!preview.fetchEnabled) {
                sb.append("\nBook-source fetch is disabled — only cached chapters will be searched")
            }
        } else {
            sb.append("All chapters in scan window available locally")
        }
        return sb.toString().trim()
    }

    suspend fun requiresBookshelfApproval(toolName: String, args: JsonObject): Boolean {
        return when (toolName) {
            AiToolRepository.TOOL_SEARCH_BOOKS -> false
            AiToolRepository.TOOL_SEARCH_BOOK_SOURCES,
            AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF,
            AiToolRepository.TOOL_FETCH_PAGE_SNIPPET,
            AiToolRepository.TOOL_CHECK_BOOK_SOURCE,
            AiToolRepository.TOOL_DEBUG_BOOK_SOURCE ->
                AppConfig.aiAllowBookSourceFetch
            AiToolRepository.TOOL_WRITE_BOOK_SOURCE_FROM_URL ->
                AppConfig.aiAllowBookSourceFetch && writeNeedsNetwork(args)
            AiToolRepository.TOOL_PATCH_BOOK_SOURCE ->
                // Local field edits (recheck=false) skip network gate; mutation confirm still applies.
                AppConfig.aiAllowBookSourceFetch && args.bool("recheck", true)
            AiToolRepository.TOOL_GET_CHAPTER_CONTENT -> {
                val book = resolveBook(args) ?: return true
                val parsed = BookshelfChapterIndices.parse(args, book.durChapterIndex)
                if (parsed is BookshelfChapterIndices.ParseResult.Error) return true
                val indices = (parsed as BookshelfChapterIndices.ParseResult.Success).indices
                anyNeedsNetworkFetch(book, indices)
            }
            AiToolRepository.TOOL_SEARCH_BOOK_CONTENT -> {
                if (!AppConfig.aiAllowBookSourceFetch) return false
                val book = resolveBook(args) ?: return true
                if (book.isLocal) return false
                val preview = previewContentSearch(args)
                preview.needsFetchCount > 0
            }
            else -> false
        }
    }

    /** check / commit always hit network; upsert only when check=true (or stage=all). */
    fun writeNeedsNetwork(args: JsonObject): Boolean {
        val action = args.string("action")?.trim()?.lowercase().orEmpty().ifBlank { "upsert" }
        return when (action) {
            "check", "commit" -> true
            "upsert", "draft" -> {
                val stage = args.string("stage")?.trim()?.lowercase()
                args.bool("check", stage == "all")
            }
            else -> false
        }
    }

    fun previewAddBook(args: JsonObject): String {
        if (!AppConfig.aiAllowBookSourceFetch) {
            return "Add to bookshelf: disabled in AI settings"
        }
        val name = args.string("bookName")?.trim().orEmpty()
        val author = args.string("bookAuthor")?.trim().orEmpty()
        val url = args.string("bookUrl")?.trim().orEmpty()
        return when {
            name.isNotBlank() && author.isNotBlank() -> "Add to bookshelf: 《$name》 / $author"
            name.isNotBlank() -> "Add to bookshelf: 《$name》"
            url.isNotBlank() -> "Add to bookshelf: ${url.take(48)}"
            else -> "Add book to bookshelf (bookUrl or bookName required)"
        }
    }

    fun formatChapterReadSummary(preview: ChapterReadPreview): String {
        preview.validationError?.let { return it }
        val range = BookshelfChapterIndices.formatRange(preview.indices)
        val sb = StringBuilder("Read 《${preview.bookName}》 $range")
        if (preview.needsFetchCount > 0) {
            sb.append(" (${preview.cachedCount} cached")
            sb.append(", ${preview.needsFetchCount} from source")
            preview.sourceName?.let { sb.append("「$it」") }
            sb.append(')')
        }
        return sb.toString()
    }

    fun formatChapterReadPreview(preview: ChapterReadPreview): String {
        preview.validationError?.let { return it }
        val range = BookshelfChapterIndices.formatRange(preview.indices)
        val sb = StringBuilder("Read 《${preview.bookName}》 $range")
        sb.append("\n")
        preview.chapterTitles.forEachIndexed { i, title ->
            sb.append("  • ${preview.indices[i] + 1}. $title\n")
        }
        if (preview.needsFetchCount > 0) {
            sb.append("${preview.cachedCount} cached, ${preview.needsFetchCount} need download")
            preview.sourceName?.let { sb.append(" from \"$it\"") }
            if (!preview.fetchEnabled) {
                sb.append("\nBook-source fetch is disabled — only cached chapters will be read")
            }
        } else {
            sb.append("All chapters available locally")
        }
        return sb.toString().trim()
    }

    private fun resolveBook(args: JsonObject): Book? {
        args.string("bookUrl")?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.let { return it }
        }
        val name = args.string("bookName")?.trim().orEmpty()
        val author = args.string("bookAuthor")?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.let { return it }
        }
        if (name.isNotBlank()) {
            return bookDao.findByName(name).firstOrNull()
        }
        return bookDao.lastReadBook
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.bool(name: String, defaultValue: Boolean): Boolean {
        val el = get(name)?.takeIf { !it.isJsonNull } ?: return defaultValue
        return runCatching {
            when {
                el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                else -> el.asString.toBooleanStrictOrNull() ?: defaultValue
            }
        }.getOrDefault(defaultValue)
    }

    private fun JsonObject.int(name: String, defaultValue: Int): Int =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue
}
