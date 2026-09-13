package io.legado.app.domain.usecase

import com.script.ScriptException
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.WrappedException
import kotlin.coroutines.coroutineContext

/**
 * Structured single-source check extracted from [io.legado.app.service.CheckSourceService].
 * Optionally persists group/comment mutations on the source (same as the service).
 */
class CheckBookSourceUseCase {

    data class Options(
        val checkSearch: Boolean = true,
        val checkDiscovery: Boolean = false,
        val checkInfo: Boolean = true,
        val checkCategory: Boolean = true,
        val checkContent: Boolean = true,
        val keyword: String? = null,
        val timeoutMs: Long = 120_000L,
        /** When true, write groups/comment/respondTime back to DAO. */
        val persist: Boolean = true,
        /** MVP write path: search must pass; discovery off. */
        val requireSearch: Boolean = false,
        /** Stop after the first failed search/discovery/info/toc/content stage. */
        val stopOnFailure: Boolean = true,
        /** Image sources: probe the first two extracted <img> URLs for HTTP 2xx (default on). */
        val checkImages: Boolean = true,
    )

    data class StageResult(
        val stage: String,
        val ok: Boolean,
        val message: String,
    )

    data class Result(
        val bookSourceUrl: String,
        val bookSourceName: String,
        val success: Boolean,
        val stages: List<StageResult>,
        val groups: String,
        val respondTimeMs: Long,
        val logTail: List<String>,
        val error: String? = null,
        val cancelled: Boolean = false,
        val timedOut: Boolean = false,
    ) {
        fun allRequiredOk(requireSearch: Boolean): Boolean {
            val byStage = stages.associateBy { it.stage }
            val need = buildList {
                if (requireSearch || byStage.containsKey("search")) add("search")
                if (byStage.containsKey("info")) add("info")
                if (byStage.containsKey("toc")) add("toc")
                if (byStage.containsKey("content")) add("content")
            }.distinct()
            if (requireSearch && byStage["search"]?.ok != true) return false
            return need.all { byStage[it]?.ok == true }
        }

        fun toJson(): String = GSON.toJson(
            mapOf(
                "success" to success,
                "bookSourceUrl" to bookSourceUrl,
                "bookSourceName" to bookSourceName,
                "stages" to stages.map {
                    mapOf("stage" to it.stage, "ok" to it.ok, "message" to it.message)
                },
                "groups" to groups,
                "respondTimeMs" to respondTimeMs,
                "logTail" to logTail,
                "error" to error,
                "cancelled" to cancelled,
                "timedOut" to timedOut,
            ),
        )
    }

    suspend fun check(bookSourceUrl: String, options: Options = Options()): Result {
        val source = appDb.bookSourceDao.getBookSource(bookSourceUrl.trim())
            ?: return Result(
                bookSourceUrl = bookSourceUrl,
                bookSourceName = "",
                success = false,
                stages = emptyList(),
                groups = "",
                respondTimeMs = 0,
                logTail = emptyList(),
                error = "Book source not found: $bookSourceUrl",
            )
        return check(source, options)
    }

    suspend fun check(source: BookSource, options: Options = Options()): Result {
        val stages = mutableListOf<StageResult>()
        val logs = mutableListOf<String>()
        val working = source.copy()
        val start = System.currentTimeMillis()
        Debug.startChecking(working)
        working.removeInvalidGroups()
        working.removeErrorComment()

        try {
            var timedOut = false
            val outcome = try {
                withTimeout(options.timeoutMs.coerceAtLeast(5_000L)) {
                    doCheck(working, options, stages, logs)
                }
                ResultState.Ok
            } catch (e: TimeoutCancellationException) {
                timedOut = true
                working.addGroup("校验超时")
                logs += "timeout"
                stages += StageResult("timeout", false, e.message ?: "timeout")
                working.addErrorComment(e)
                Debug.updateFinalMessage(working.bookSourceUrl, "校验失败:timeout")
                ResultState.Failed(e)
            } catch (e: CancellationException) {
                logs += "cancelled"
                stages += StageResult("cancelled", false, "cancelled")
                Debug.updateFinalMessage(working.bookSourceUrl, "校验已取消")
                // Must not swallow structured cancellation — stop generation relies on this.
                throw e
            } catch (e: Exception) {
                when (e) {
                    is ScriptException, is WrappedException -> {
                        working.addGroup("js失效")
                        logs += "js: ${e.localizedMessage}"
                    }
                    is NoStackTraceException -> {
                        logs += e.localizedMessage.orEmpty()
                    }
                    else -> {
                        working.addGroup("网站失效")
                        logs += e.localizedMessage ?: e.toString()
                    }
                }
                working.addErrorComment(e)
                Debug.updateFinalMessage(working.bookSourceUrl, "校验失败:${e.localizedMessage}")
                ResultState.Failed(e)
            }

            val respondTime = System.currentTimeMillis() - start
            working.respondTime = respondTime
            if (outcome is ResultState.Ok) {
                Debug.updateFinalMessage(working.bookSourceUrl, "校验成功")
            }

            if (options.persist) {
                appDb.bookSourceDao.update(working)
            }

            val invalid = working.getInvalidGroupNames()
            val searchOk = stages.find { it.stage == "search" }?.ok
            val success = outcome is ResultState.Ok &&
                invalid.isBlank() &&
                (!options.requireSearch || searchOk == true) &&
                stages.filter { it.stage in REQUIRED_STAGES }.all { it.ok }

            return Result(
                bookSourceUrl = working.bookSourceUrl,
                bookSourceName = working.bookSourceName,
                success = success,
                stages = stages.toList(),
                groups = working.bookSourceGroup.orEmpty(),
                respondTimeMs = respondTime,
                logTail = logs.takeLast(40),
                error = if (success) {
                    null
                } else {
                    invalid.ifBlank {
                        (outcome as? ResultState.Failed)?.error?.localizedMessage
                    }
                },
                cancelled = false,
                timedOut = timedOut,
            )
        } finally {
            // Match CheckSourceService.onDestroy — otherwise book-source management keeps
            // showing "checking" after AI check returns.
            Debug.finishChecking()
        }
    }

    private sealed class ResultState {
        data object Ok : ResultState()
        data class Failed(val error: Throwable) : ResultState()
    }

    private suspend fun doCheck(
        source: BookSource,
        options: Options,
        stages: MutableList<StageResult>,
        logs: MutableList<String>,
    ) {
        var searchBook: Book? = null
        if (options.checkSearch) {
            coroutineContext.ensureActive()
            val keyword = source.getCheckKeyword(options.keyword ?: CheckSource.keyword)
            if (source.searchUrl.isNullOrBlank()) {
                source.addGroup("搜索链接规则为空")
                stages += StageResult("search", false, "searchUrl empty")
                logs += "searchUrl empty"
                if (options.requireSearch || options.stopOnFailure) return
            } else {
                source.removeGroup("搜索链接规则为空")
                val books = WebBook.searchBookAwait(source, keyword)
                coroutineContext.ensureActive()
                if (books.isEmpty()) {
                    source.addGroup("搜索失效")
                    stages += StageResult("search", false, "no results for \"$keyword\"")
                    logs += "search empty"
                    if (options.requireSearch || options.stopOnFailure) return
                } else {
                    source.removeGroup("搜索失效")
                    searchBook = books.first().toBook()
                    stages += StageResult("search", true, "hit ${books.size}, using ${searchBook.name}")
                    logs += "search ok: ${searchBook.name}"
                }
            }
        }

        if (options.checkDiscovery && !source.exploreUrl.isNullOrBlank()) {
            coroutineContext.ensureActive()
            val url = source.exploreKinds().firstOrNull { !it.url.isNullOrBlank() }?.url
            if (url.isNullOrBlank()) {
                source.addGroup("发现规则为空")
                stages += StageResult("discovery", false, "explore kinds empty")
                if (options.stopOnFailure && searchBook == null) return
            } else {
                source.removeGroup("发现规则为空")
                val exploreBooks = WebBook.exploreBookAwait(source, url)
                coroutineContext.ensureActive()
                if (exploreBooks.isEmpty()) {
                    source.addGroup("发现失效")
                    stages += StageResult("discovery", false, "no explore results")
                    if (options.stopOnFailure && searchBook == null) return
                } else {
                    source.removeGroup("发现失效")
                    stages += StageResult("discovery", true, "hit ${exploreBooks.size}")
                    if (searchBook == null) {
                        searchBook = exploreBooks.first().toBook()
                    }
                }
            }
        }

        val book = searchBook
        if (book != null && (options.checkInfo || options.checkCategory || options.checkContent)) {
            coroutineContext.ensureActive()
            checkBookStages(book, source, options, stages, logs, isSearchBook = true)
        } else if (options.checkInfo || options.checkCategory || options.checkContent) {
            if (stages.none { it.stage == "search" || it.stage == "discovery" }) {
                stages += StageResult("info", false, "no book to check info/toc/content")
            }
        }

        val finalCheckMessage = source.getInvalidGroupNames()
        if (finalCheckMessage.isNotBlank()) {
            throw NoStackTraceException(finalCheckMessage)
        }
    }

    private suspend fun checkBookStages(
        book: Book,
        source: BookSource,
        options: Options,
        stages: MutableList<StageResult>,
        logs: MutableList<String>,
        isSearchBook: Boolean,
    ) {
        val bookType = if (isSearchBook) "搜索" else "发现"
        try {
            if (options.checkInfo) {
                coroutineContext.ensureActive()
                if (book.tocUrl.isBlank()) {
                    WebBook.getBookInfoAwait(source, book)
                }
                stages += StageResult("info", true, "name=${book.name} tocUrl=${book.tocUrl.take(80)}")
                logs += "info ok"
            }
            if (!options.checkCategory || source.bookSourceType == BookSourceType.file) {
                source.removeGroup("${bookType}目录失效")
                source.removeGroup("${bookType}正文失效")
                return
            }
            coroutineContext.ensureActive()
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow().asSequence()
                .filter { !(it.isVolume && it.url.startsWith(it.title)) }
                .take(2)
                .toList()
            if (toc.isEmpty()) throw TocEmptyException("目录为空")
            stages += StageResult("toc", true, "chapters sample=${toc.size}, first=${toc.first().title}")
            logs += "toc ok"
            if (!options.checkContent) {
                source.removeGroup("${bookType}目录失效")
                source.removeGroup("${bookType}正文失效")
                return
            }
            coroutineContext.ensureActive()
            val nextChapterUrl = toc.getOrNull(1)?.url ?: toc.first().url
            val chapter = toc.first()
            val content = WebBook.getContentAwait(
                bookSource = source,
                book = book,
                bookChapter = chapter,
                nextChapterUrl = nextChapterUrl,
                needSave = false,
            )
            if (source.bookSourceType == BookSourceType.image && !chapter.isVolume) {
                // 漫画源：与阅读器 ReadManga 同用 BookHelp.flowImages 提取图片。
                // flowImages 为 0 时阅读器也会报"正文没有图片"，故此处判失败与阅读器对齐。
                val images = BookHelp.flowImages(chapter, content).toList()
                if (images.isEmpty()) {
                    val hint = if (content == chapter.url) {
                        " (ruleContent.content is empty → getContentAwait returned the chapter URL)"
                    } else {
                        " (ruleContent.content must yield <img> HTML, e.g. @css:img — raw URL lists like @css:img@src extract 0; " +
                            "if the site renders images via JS, chapter URLs must carry ,{\"webView\":true} so the page renders before parsing)"
                    }
                    source.addGroup("${bookType}正文失效")
                    stages += StageResult("content", false, "no images extracted, chars=${content.length}$hint")
                    logs += "content no images"
                    return
                }
                if (options.checkImages) {
                    val checked = BookHelp.probeImageUrls(images, source)
                    val msg = "images=${images.size}, sampled=${checked.joinToString("; ") { (u, ok) ->
                        "${if (ok) "OK" else "FAIL"} ${u.take(60)}"
                    }}"
                    if (checked.all { it.second }) {
                        source.removeGroup("${bookType}目录失效")
                        source.removeGroup("${bookType}正文失效")
                        stages += StageResult("content", true, msg)
                        logs += "content ok ($msg)"
                    } else {
                        source.addGroup("${bookType}正文失效")
                        stages += StageResult("content", false, msg)
                        logs += "content image probe failed"
                    }
                    return
                }
                source.removeGroup("${bookType}目录失效")
                source.removeGroup("${bookType}正文失效")
                stages += StageResult("content", true, "images=${images.size}, chars=${content.length}")
                logs += "content ok"
                return
            }
            stages += StageResult(
                "content",
                true,
                "chars=${content.length}, preview=${content.take(80).replace("\n", " ")}",
            )
            logs += "content ok"
            source.removeGroup("${bookType}目录失效")
            source.removeGroup("${bookType}正文失效")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ContentEmptyException) {
            source.addGroup("${bookType}正文失效")
            stages += StageResult("content", false, e.localizedMessage ?: "content empty")
        } catch (e: TocEmptyException) {
            source.addGroup("${bookType}目录失效")
            stages += StageResult("toc", false, e.localizedMessage ?: "toc empty")
        } catch (e: Exception) {
            val stage = when {
                stages.none { it.stage == "info" } -> "info"
                stages.none { it.stage == "toc" } -> "toc"
                else -> "content"
            }
            stages += StageResult(stage, false, e.localizedMessage ?: e.toString())
            if (!options.stopOnFailure) throw e
        }
    }

    companion object {
        private val REQUIRED_STAGES = setOf("search", "info", "toc", "content")

        /** Options for write-source MVP acceptance (four rings, search required). */
        fun mvpWriteOptions(keyword: String? = null, persist: Boolean = true) = Options(
            checkSearch = true,
            checkDiscovery = false,
            checkInfo = true,
            checkCategory = true,
            checkContent = true,
            keyword = keyword,
            timeoutMs = 120_000L,
            persist = persist,
            requireSearch = true,
            stopOnFailure = true,
        )
    }
}
