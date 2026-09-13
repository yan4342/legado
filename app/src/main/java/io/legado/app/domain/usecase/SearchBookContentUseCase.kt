package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keyword search over chapter text (local books + cached network chapters).
 * When [allowNetworkFetch] is true, may fetch uncached chapters via book source (budget-capped).
 *
 * Match modes: or (default) / and / phrase; quoted segments are phrases.
 * Returns a flat list of occurrence hits (Top-N per chapter), ranked by score.
 */
class SearchBookContentUseCase(
    private val bookChapterDao: BookChapterDao,
    private val resolveBookChapterContentUseCase: ResolveBookChapterContentUseCase,
) {

    data class Hit(
        val chapterIndex: Int,
        val chapterTitle: String,
        val charOffset: Int,
        val snippet: String,
        val matchCountInChapter: Int,
        val score: Int,
        val matchedKeywords: List<String>,
        val resultCountWithinChapter: Int = 0,
    )

    /**
     * Strong-typed search over chapter text. Returns the full ranked hit list
     * (not truncated to a returned slice) plus scan statistics.
     * Local books scan all chapters; network books scan cached chapters, and
     * when [allowNetworkFetch] is true may fetch up to [MAX_FETCH_PER_SEARCH]
     * uncached chapters via the book source.
     */
    data class SearchOutcome(
        val hits: List<Hit>,
        val scannedChapters: Int,
        val skippedUncached: Int,
        val fetchedCount: Int,
        val totalMatchCount: Int,
        val matchedChapterCount: Int,
        /** False when the book has no chapter list at all. */
        val chaptersExist: Boolean = true,
    )

    suspend fun search(
        book: Book,
        query: String,
        limit: Int = DEFAULT_LIMIT,
        chapterStart: Int? = null,
        chapterLimit: Int? = null,
        resultOffset: Int = 0,
        matchMode: ChapterContentWindow.MatchMode = ChapterContentWindow.MatchMode.OR,
        hitsPerChapter: Int = ChapterContentWindow.DEFAULT_HITS_PER_CHAPTER,
        allowNetworkFetch: Boolean = false,
    ): String {
        val key = query.trim()
        val parsed = ChapterContentWindow.parseQuery(key, matchMode)
        if (parsed.isEmpty) {
            return """{"error":"query is required"}"""
        }
        val cap = limit.coerceIn(1, MAX_LIMIT)
        val skip = resultOffset.coerceAtLeast(0)
        val perChapter = hitsPerChapter.coerceIn(1, ChapterContentWindow.MAX_HITS_PER_CHAPTER)

        val outcome = searchHits(
            book = book,
            query = key,
            chapterStart = chapterStart,
            chapterLimit = chapterLimit,
            matchMode = matchMode,
            hitsPerChapter = perChapter,
            allowNetworkFetch = allowNetworkFetch,
        )
        val ranked = outcome.hits
        if (ranked.isEmpty()) {
            val noChapters = !outcome.chaptersExist
            return GSON.toJson(
                mapOf(
                    "query" to key,
                    "keywords" to parsed.keywords,
                    "matchMode" to matchMode.name.lowercase(),
                    "book" to bookIdentity(book),
                    "scannedChapters" to outcome.scannedChapters,
                    "skippedUncached" to outcome.skippedUncached,
                    "fetchedCount" to outcome.fetchedCount,
                    "totalMatchCount" to outcome.totalMatchCount,
                    "matchedChapterCount" to outcome.matchedChapterCount,
                    "count" to 0,
                    "hasMore" to false,
                    "hits" to emptyList<Map<String, Any?>>(),
                    "note" to if (noChapters) {
                        "No chapters on bookshelf for this book."
                    } else {
                        buildNote(
                            isLocal = book.isLocal,
                            scanned = outcome.scannedChapters,
                            skippedUncached = outcome.skippedUncached,
                            fetchedCount = outcome.fetchedCount,
                            totalMatchCount = outcome.totalMatchCount,
                            matchedChapterCount = outcome.matchedChapterCount,
                            returnedCount = 0,
                            hasMore = false,
                            keywordCount = parsed.keywords.size,
                            matchMode = matchMode,
                        )
                    },
                    "workflow" to if (noChapters) {
                        "Import or open the book on the bookshelf first."
                    } else {
                        buildWorkflow(
                            isLocal = book.isLocal,
                            hitCount = 0,
                            hasMore = false,
                            nextResultOffset = null,
                            nextChapterStart = null,
                            skippedUncached = outcome.skippedUncached,
                            fetchedCount = outcome.fetchedCount,
                            keywordCount = parsed.keywords.size,
                            matchMode = matchMode,
                        )
                    },
                ),
            )
        }
        val returned = ranked.drop(skip).take(cap)
        val hasMore = ranked.size > skip + returned.size
        val nextResultOffset = if (hasMore) skip + returned.size else null
        val nextChapterStart = if (hasMore && returned.isNotEmpty()) {
            ranked.drop(skip + returned.size).minOfOrNull { it.chapterIndex }
        } else {
            null
        }

        val note = buildNote(
            isLocal = book.isLocal,
            scanned = outcome.scannedChapters,
            skippedUncached = outcome.skippedUncached,
            fetchedCount = outcome.fetchedCount,
            totalMatchCount = outcome.totalMatchCount,
            matchedChapterCount = outcome.matchedChapterCount,
            returnedCount = returned.size,
            hasMore = hasMore,
            keywordCount = parsed.keywords.size,
            matchMode = matchMode,
        )
        val workflow = buildWorkflow(
            isLocal = book.isLocal,
            hitCount = returned.size,
            hasMore = hasMore,
            nextResultOffset = nextResultOffset,
            nextChapterStart = nextChapterStart,
            skippedUncached = outcome.skippedUncached,
            fetchedCount = outcome.fetchedCount,
            keywordCount = parsed.keywords.size,
            matchMode = matchMode,
        )

        return GSON.toJson(
            buildMap {
                put("query", key)
                put("keywords", parsed.keywords)
                put("matchMode", matchMode.name.lowercase())
                put("hitsPerChapter", perChapter)
                put("book", bookIdentity(book))
                put("isLocal", book.isLocal)
                put("scannedChapters", outcome.scannedChapters)
                put("skippedUncached", outcome.skippedUncached)
                put("fetchedCount", outcome.fetchedCount)
                put("totalMatchCount", outcome.totalMatchCount)
                put("matchedChapterCount", outcome.matchedChapterCount)
                put("count", returned.size)
                put("hasMore", hasMore)
                put("limit", cap)
                put("resultOffset", skip)
                nextResultOffset?.let { put("nextResultOffset", it) }
                nextChapterStart?.let { put("nextChapterStart", it) }
                put("hits", returned.map { it.toMap() })
                put("note", note)
                put("workflow", workflow)
            },
        )
    }

    /**
     * 一次并行分片扫描的局部结果，用于跨协程合并。
     */
    private data class ChunkResult(
        val hits: List<Hit> = emptyList(),
        val scannedChapters: Int = 0,
        val totalMatchCount: Int = 0,
        val matchedChapterCount: Int = 0,
    ) {
        operator fun plus(other: ChunkResult): ChunkResult = ChunkResult(
            hits = hits + other.hits,
            scannedChapters = scannedChapters + other.scannedChapters,
            totalMatchCount = totalMatchCount + other.totalMatchCount,
            matchedChapterCount = matchedChapterCount + other.matchedChapterCount,
        )
    }

    /**
     * Core search: scans chapters (local: all; network: cached, plus optional
     * book-source fetch), returns the full ranked hit list without truncation.
     *
     * 已缓存/本地章节走并行分片扫描（CPU 密集的预处理与匹配在多个线程上并发）；
     * 未缓存章节保持串行（跳过或受 fetchBudget 限制联网抓取）。
     * 合并后统一 [rankHits]，结果与全串行扫描完全一致。
     */
    suspend fun searchHits(
        book: Book,
        query: String,
        chapterStart: Int? = null,
        chapterLimit: Int? = null,
        matchMode: ChapterContentWindow.MatchMode = ChapterContentWindow.MatchMode.OR,
        hitsPerChapter: Int = ChapterContentWindow.DEFAULT_HITS_PER_CHAPTER,
        allowNetworkFetch: Boolean = false,
    ): SearchOutcome {
        val key = query.trim()
        val parsed = ChapterContentWindow.parseQuery(key, matchMode)
        val perChapter = hitsPerChapter.coerceIn(1, ChapterContentWindow.MAX_HITS_PER_CHAPTER)
        if (parsed.isEmpty) {
            return SearchOutcome(emptyList(), 0, 0, 0, 0, 0)
        }
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) {
            return SearchOutcome(emptyList(), 0, 0, 0, 0, 0, chaptersExist = false)
        }

        val startIdx = (chapterStart ?: 0).coerceAtLeast(0)
        val endExclusive = when {
            chapterLimit != null && chapterLimit > 0 ->
                (startIdx + chapterLimit).coerceAtMost(chapters.size)
            else -> chapters.size
        }
        val window = chapters.filter { it.index >= startIdx && it.index < endExclusive }
            .ifEmpty {
                chapters.drop(startIdx).let { list ->
                    if (chapterLimit != null && chapterLimit > 0) list.take(chapterLimit) else list
                }
            }

        val processor = ContentProcessor.get(book.name, book.origin)
        val converterType = AppConfig.chineseConverterType

        // 一次划分缓存/未缓存，避免每章重复文件存在检查
        val cachedChapters = arrayListOf<BookChapter>()
        val uncachedChapters = arrayListOf<BookChapter>()
        for (chapter in window) {
            if (book.isLocal || BookHelp.hasContent(book, chapter)) {
                cachedChapters.add(chapter)
            } else {
                uncachedChapters.add(chapter)
            }
        }

        // 1) 已缓存/本地章节：并行分片扫描
        val cachedResult = scanCachedInParallel(
            chapters = cachedChapters,
            book = book,
            processor = processor,
            parsed = parsed,
            perChapter = perChapter,
            converterType = converterType,
        )

        // 2) 未缓存章节：串行扫描，保留 fetchBudget 联网补搜逻辑
        var skippedUncached = 0
        var fetchedCount = 0
        var fetchBudget = if (allowNetworkFetch) MAX_FETCH_PER_SEARCH else 0
        var uncachedScanned = 0
        var uncachedMatchCount = 0
        var uncachedMatchedChapters = 0
        val uncachedHits = mutableListOf<Hit>()
        for (chapter in uncachedChapters) {
            val raw: String? = when {
                allowNetworkFetch && fetchBudget > 0 -> {
                    when (
                        val resolved = resolveBookChapterContentUseCase.resolveRaw(
                            book, chapter, allowNetworkFetch = true,
                        )
                    ) {
                        is ResolveBookChapterContentUseCase.Result.Success -> {
                            fetchBudget--
                            fetchedCount++
                            resolved.content
                        }
                        is ResolveBookChapterContentUseCase.Result.Error -> {
                            skippedUncached++
                            null
                        }
                    }
                }
                else -> {
                    skippedUncached++
                    null
                }
            }
            if (raw.isNullOrEmpty()) {
                continue
            }
            uncachedScanned++
            val content = processor
                .getContent(
                    book,
                    chapter,
                    raw,
                    includeTitle = false,
                    useReplace = false,
                    chineseConvert = true,
                    reSegment = false,
                )
                .toString()
            val chapterMatch = ChapterContentWindow.matchChapter(
                content = content,
                parsed = parsed,
                chapterTitle = chapter.title,
                hitsPerChapter = perChapter,
                chineseConverterType = converterType,
            ) ?: continue
            uncachedMatchedChapters++
            uncachedMatchCount += chapterMatch.matchCount
            for (occ in chapterMatch.occurrences) {
                uncachedHits.add(
                    Hit(
                        chapterIndex = chapter.index,
                        chapterTitle = chapter.title,
                        charOffset = occ.charOffset,
                        snippet = occ.snippet,
                        matchCountInChapter = chapterMatch.matchCount,
                        score = occ.score,
                        matchedKeywords = occ.matchedKeywords,
                        resultCountWithinChapter = occ.resultCountWithinChapter,
                    ),
                )
            }
        }

        return SearchOutcome(
            hits = rankHits(cachedResult.hits + uncachedHits),
            scannedChapters = cachedResult.scannedChapters + uncachedScanned,
            skippedUncached = skippedUncached,
            fetchedCount = fetchedCount,
            totalMatchCount = cachedResult.totalMatchCount + uncachedMatchCount,
            matchedChapterCount = cachedResult.matchedChapterCount + uncachedMatchedChapters,
        )
    }

    /**
     * 并行扫描所有已缓存/本地章节，结果严格按章序合并。
     * 通过共享骨架 [ParallelChapterScanner] 分片并行，[scanChunk] 只读不写共享状态。
     */
    private suspend fun scanCachedInParallel(
        chapters: List<BookChapter>,
        book: Book,
        processor: ContentProcessor,
        parsed: ChapterContentWindow.ParsedQuery,
        perChapter: Int,
        converterType: Int,
    ): ChunkResult {
        var acc = ChunkResult()
        ParallelChapterScanner.scanInOrder(
            items = chapters,
            scanChunk = { chunk ->
                scanChunk(chunk, book, processor, parsed, perChapter, converterType)
            },
            onChunkResult = { acc = acc + it },
        )
        return acc
    }

    /**
     * 扫描单个分片（全部为已缓存/本地章节），返回局部结果。
     * 纯函数：不读写共享可变状态，可安全并发执行。
     */
    private suspend fun scanChunk(
        chunk: List<BookChapter>,
        book: Book,
        processor: ContentProcessor,
        parsed: ChapterContentWindow.ParsedQuery,
        perChapter: Int,
        converterType: Int,
    ): ChunkResult {
        var scanned = 0
        var totalMatchCount = 0
        var matchedChapterCount = 0
        val hits = mutableListOf<Hit>()
        for (chapter in chunk) {
            val raw = withContext(Dispatchers.IO) { BookHelp.getContent(book, chapter) }
            if (raw.isNullOrEmpty()) {
                continue
            }
            scanned++
            val content = processor
                .getContent(
                    book,
                    chapter,
                    raw,
                    includeTitle = false,
                    useReplace = false,
                    chineseConvert = true,
                    reSegment = false,
                )
                .toString()
            val chapterMatch = ChapterContentWindow.matchChapter(
                content = content,
                parsed = parsed,
                chapterTitle = chapter.title,
                hitsPerChapter = perChapter,
                chineseConverterType = converterType,
            ) ?: continue
            matchedChapterCount++
            totalMatchCount += chapterMatch.matchCount
            for (occ in chapterMatch.occurrences) {
                hits.add(
                    Hit(
                        chapterIndex = chapter.index,
                        chapterTitle = chapter.title,
                        charOffset = occ.charOffset,
                        snippet = occ.snippet,
                        matchCountInChapter = chapterMatch.matchCount,
                        score = occ.score,
                        matchedKeywords = occ.matchedKeywords,
                        resultCountWithinChapter = occ.resultCountWithinChapter,
                    ),
                )
            }
        }
        return ChunkResult(hits, scanned, totalMatchCount, matchedChapterCount)
    }

    private fun Hit.toMap(): Map<String, Any?> = mapOf(
        "chapterIndex" to chapterIndex,
        "chapterTitle" to chapterTitle,
        "charOffset" to charOffset,
        "snippet" to snippet,
        "matchCountInChapter" to matchCountInChapter,
        "score" to score,
        "matchedKeywords" to matchedKeywords,
        "resultCountWithinChapter" to resultCountWithinChapter,
    )

    private fun bookIdentity(book: Book): Map<String, Any?> = mapOf(
        "bookUrl" to book.bookUrl,
        "name" to book.name,
        "author" to book.author,
    )

    companion object {
        /** Max occurrence hits returned per call. */
        const val DEFAULT_LIMIT = 40
        const val MAX_LIMIT = 100
        const val MAX_FETCH_PER_SEARCH = 20

        fun rankHits(hits: List<Hit>): List<Hit> =
            hits.sortedWith(
                compareByDescending<Hit> { it.score }
                    .thenBy { it.chapterIndex }
                    .thenBy { it.charOffset },
            )

        fun buildNote(
            isLocal: Boolean,
            scanned: Int,
            skippedUncached: Int,
            totalMatchCount: Int,
            matchedChapterCount: Int,
            returnedCount: Int,
            hasMore: Boolean,
            keywordCount: Int = 1,
            fetchedCount: Int = 0,
            matchMode: ChapterContentWindow.MatchMode = ChapterContentWindow.MatchMode.OR,
        ): String = buildString {
            append("Scanned $scanned chapters")
            if (keywordCount > 1 || matchMode != ChapterContentWindow.MatchMode.OR) {
                append(" (${matchMode.name.lowercase()} on $keywordCount keywords)")
            }
            if (fetchedCount > 0) {
                append("; fetched $fetchedCount chapters from book source")
            }
            if (!isLocal && skippedUncached > 0) {
                append("; skipped $skippedUncached uncached network chapters")
            }
            append(". Found $totalMatchCount raw matches in $matchedChapterCount chapters")
            if (hasMore) {
                append("; returning $returnedCount hits (limit). Not limited by cache for local books.")
            } else {
                append(".")
            }
        }

        fun buildWorkflow(
            isLocal: Boolean,
            hitCount: Int,
            hasMore: Boolean,
            nextResultOffset: Int?,
            nextChapterStart: Int?,
            skippedUncached: Int,
            keywordCount: Int = 1,
            fetchedCount: Int = 0,
            matchMode: ChapterContentWindow.MatchMode = ChapterContentWindow.MatchMode.OR,
        ): String {
            val modeHint = when (matchMode) {
                ChapterContentWindow.MatchMode.AND ->
                    " matchMode=and requires every term/phrase in the chapter."
                ChapterContentWindow.MatchMode.PHRASE ->
                    " matchMode=phrase matches the whole query as one phrase."
                ChapterContentWindow.MatchMode.OR ->
                    if (keywordCount > 1) {
                        " Multi-keyword OR; use matchMode=and or quoted \"phrases\" to tighten."
                    } else {
                        ""
                    }
            }
            return when {
                hitCount == 0 && !isLocal && skippedUncached > 0 && fetchedCount == 0 ->
                    "No matches in cached chapters ($skippedUncached uncached skipped). " +
                        "Enable book-source fetch and confirm to search uncached chapters, or try another keyword."
                hitCount == 0 ->
                    "No matches in scanned chapters. Try a shorter keyword, matchMode=or, fewer terms, or a different chapterStart/chapterLimit range."
                hasMore && nextResultOffset != null ->
                    "Call get_chapter_content(chapterIndex, offset=charOffset, maxChars≈2000–4000) for a hit. " +
                        "More hits exist — call search_book_content again with resultOffset=$nextResultOffset. " +
                        (nextChapterStart?.let { "Optional chapterStart=$it to narrow the scan window. " } ?: "") +
                        "Low hit count is due to the return limit, not cache coverage." +
                        modeHint
                else ->
                    "Call get_chapter_content(chapterIndex, offset=charOffset, maxChars≈2000–4000) to read around a hit. " +
                        "Do not re-read whole chapters when a snippet is enough." +
                        modeHint
            }
        }
    }
}
