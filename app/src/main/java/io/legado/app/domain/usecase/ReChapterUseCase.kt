package io.legado.app.domain.usecase

import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DefaultData
import io.legado.app.help.RuleBigDataHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.max
import kotlin.math.min

/**
 * 重新分章批次执行结果。
 * - [MERGED]: 成功产生了合并章
 * - [NONE]: 无可用缓存/无需处理/本地无法判定
 */
enum class ExecuteResult { MERGED, NONE }

/**
 * 网络书重新分章：把站点切分的约 2000 字子页，按正文中真实章节标题合并为逻辑章节。
 *
 * 纯映射方案——原子页缓存文件原样保留，合并章只把原子页映射写入
 * `BookChapter.variable["reChapter"]`，正文读取时按映射拼接。
 *
 * 分章判定以本地行首预筛 + txtTocRule 强规则为主体（规则选择）。
 */
class ReChapterUseCase(
    private val book: Book,
    private val bookSource: BookSource,
) {

    /**
     * 执行一个分章批次，从 [startIndex] 起处理 `AppConfig.preDownloadNum + 3` 个已缓存子页。
     */
    suspend fun execute(startIndex: Int): ExecuteResult {
        if (book.isLocal || book.isImage || book.isAudio) return ExecuteResult.NONE
        if (bookSource.getContentRule().content.isNullOrEmpty()) return ExecuteResult.NONE
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return ExecuteResult.NONE
        try {
            return withContext(Dispatchers.IO) {
                executeLocked(startIndex)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 重置当前书源已分章的结果并从头全量重新分章一次。
     * 合并章展开回原子页(展开原子页保留原始缓存文件名以还原缓存读取),
     * 清除进度标记与持久化记录,用覆盖全书的大窗口重新扫描。
     * 重分失败时还原原合并章目录。
     */
    suspend fun resetAndReChapter(): ExecuteResult {
        if (book.isLocal || book.isImage || book.isAudio) return ExecuteResult.NONE
        if (bookSource.getContentRule().content.isNullOrEmpty()) return ExecuteResult.NONE
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return ExecuteResult.NONE
        try {
            return withContext(Dispatchers.IO) {
                val oldList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val oldMark = book.reChapterMark
                val oldTotal = book.totalChapterNum
                RuleBigDataHelp.putBookVariable(book.bookUrl, persistKey, null)
                val expanded = expandMergedChapters(oldList)
                if (expanded != null) {
                    expanded.forEachIndexed { i, c -> c.index = i }
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*expanded.toTypedArray())
                    book.totalChapterNum = expanded.size
                }
                book.reChapterMark = null
                appDb.bookDao.update(book)
                val result = executeLocked(book.durChapterIndex, fullWindow = true)
                if (result != ExecuteResult.MERGED && expanded != null) {
                    // 重分失败:还原原合并章目录
                    oldList.forEachIndexed { i, c -> c.index = i }
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*oldList.toTypedArray())
                    book.reChapterMark = oldMark
                    book.totalChapterNum = oldTotal
                    appDb.bookDao.update(book)
                }
                result
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 从当前进度继续,用全书大窗口增量分章。不丢弃已有分章结果,
     * 仅处理已缓存到磁盘的子页,未缓存的随阅读进度自动跟上。
     */
    suspend fun fullBookReChapter(): ExecuteResult {
        if (book.isLocal || book.isImage || book.isAudio) return ExecuteResult.NONE
        if (bookSource.getContentRule().content.isNullOrEmpty()) return ExecuteResult.NONE
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return ExecuteResult.NONE
        try {
            return withContext(Dispatchers.IO) {
                restoreIfNeeded()
                executeLocked(book.durChapterIndex, fullWindow = true)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 关闭重新分章:优先从 [originalTocKey] 快照恢复书源原始目录,
     * 无快照(旧数据)时退化为展开合并章。清除标记与持久化结果。
     * @return true 表示执行了还原,false 表示无需还原(目录中无合并章)。
     */
    suspend fun disableAndRestore(): Boolean {
        if (book.isLocal || book.isImage || book.isAudio) return false
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return false
        try {
            return withContext(Dispatchers.IO) {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                var restored = restoreFromOriginalToc()
                if (restored == null) {
                    val expanded = expandMergedChapters(chapterList)
                    if (expanded != null) {
                        expanded.forEachIndexed { i, c -> c.index = i }
                        restored = expanded
                    }
                }
                if (restored != null) {
                    restored.forEachIndexed { i, c -> c.index = i }
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*restored.toTypedArray())
                    book.totalChapterNum = restored.size
                    val oldDurUrl = chapterList.getOrNull(
                        book.durChapterIndex.coerceIn(0, chapterList.size - 1)
                    )?.url
                    book.durChapterIndex = findNewIndex(oldDurUrl, restored)
                    restored.getOrNull(book.durChapterIndex)?.title
                        ?.takeIf { it.isNotBlank() }?.let {
                            book.durChapterTitle = it
                        }
                    val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
                    if (syncReadBook) {
                        io.legado.app.model.ReadBook.durChapterIndex = book.durChapterIndex
                    }
                }
                book.reChapterMark = null
                book.reChapterEnabled = false
                appDb.bookDao.update(book)
                RuleBigDataHelp.putBookVariable(book.bookUrl, persistKey, null)
                RuleBigDataHelp.putBookVariable(book.bookUrl, originalTocKey, null)
                postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
                io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)
                restored != null
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 首次分章前抓取书源原始目录快照(关闭分章时还原用)。
     * 仅当尚无快照且目录未被合并时写入,幂等。
     */
    private fun snapshotOriginalToc() {
        val existing = RuleBigDataHelp.getBookVariable(book.bookUrl, originalTocKey)
        if (!existing.isNullOrBlank()) return
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) return
        val entries = chapters.map {
            OriginalTocEntry(
                url = it.url,
                title = it.title,
                isVolume = it.isVolume,
                isVip = it.isVip,
                isPay = it.isPay,
                tag = it.tag,
                wordCount = it.wordCount,
                srcFile = it.getFileName(),
            )
        }
        RuleBigDataHelp.putBookVariable(book.bookUrl, originalTocKey, GSON.toJson(entries))
        AppLog.put("重新分章已快照原始目录 ${entries.size} 章")
    }

    /**
     * 从原始目录快照重建章节列表,用于关闭分章还原。无快照或解析失败返回 null。
     */
    private fun restoreFromOriginalToc(): List<BookChapter>? {
        val raw = RuleBigDataHelp.getBookVariable(book.bookUrl, originalTocKey)
        if (raw.isNullOrBlank()) return null
        val entries = GSON.fromJson(raw, Array<OriginalTocEntry>::class.java)?.toList()
        if (entries.isNullOrEmpty()) return null
        val baseUrl = appDb.bookChapterDao.getChapterList(book.bookUrl)
            .firstOrNull()?.baseUrl ?: book.tocUrl
        return entries.mapIndexed { index, e ->
            val chapter = BookChapter(
                url = e.url,
                title = e.title,
                isVolume = e.isVolume,
                isVip = e.isVip,
                isPay = e.isPay,
                tag = e.tag,
                wordCount = e.wordCount,
                baseUrl = baseUrl,
                bookUrl = book.bookUrl,
                index = index,
            )
            e.srcFile?.takeIf { it.isNotBlank() }?.let {
                chapter.putVariable(srcFileKey, it)
            }
            chapter
        }
    }

    /**
     * 对单个合并章重新分章:展开该章的原子页,在同窗口内重新检测边界并重建。
     * 可能分裂为多个章。非合并章或原子页少于 2 个返回 null。
     */
    suspend fun reChapterSingle(
        chapter: BookChapter,
    ): List<BookChapter>? {
        val subs = BookHelp.reChapterSubs(chapter) ?: return null
        if (subs.size < 2) return null
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return null
        try {
            return withContext(Dispatchers.IO) {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val chapterIndex = chapterList.indexOfFirst { it.url == chapter.url && it.bookUrl == chapter.bookUrl }
                if (chapterIndex < 0) return@withContext null

                // Collect atomic sub-pages for this chapter
                val windowAtomic = arrayListOf<BookChapter>()
                val windowContent = hashMapOf<String, String>()
                for (sub in subs) {
                    val content = BookHelp.getContentByFileName(book, sub.fileName)
                    if (content == null || content.isBlank()) return@withContext null
                    val atomic = BookChapter(
                        url = sub.url,
                        baseUrl = chapter.baseUrl,
                        bookUrl = book.bookUrl,
                        index = 0,
                    )
                    atomic.putVariable(srcFileKey, sub.fileName)
                    windowAtomic.add(atomic)
                    windowContent[sub.url] = content
                }

                // Scan for chapter boundaries within this window
                val detector = TitleDetector()
                val candidates = scanCandidates(windowAtomic, windowContent, detector)
                val volumeHits = candidates
                    .filter { it.isVolume }
                    .distinctBy { it.subIndex to it.line }
                val boundaryHits = dedupeTitleHits(candidates.filter { it.ruleMatch && !it.isVolume })
                if (boundaryHits.isEmpty()) return@withContext null

                // Build new merged chapters
                val merged = buildMergedChapters(windowAtomic, windowContent, boundaryHits, volumeHits)
                if (merged.isEmpty()) return@withContext null

                // Replace the old chapter with new merged chapters
                val newList = arrayListOf<BookChapter>()
                newList.addAll(chapterList.subList(0, chapterIndex))
                newList.addAll(merged)
                newList.addAll(chapterList.subList(chapterIndex + 1, chapterList.size))
                newList.forEachIndexed { i, c -> c.index = i }

                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*newList.toTypedArray())

                book.totalChapterNum = newList.size
                val oldDurUrl = chapterList.getOrNull(
                    book.durChapterIndex.coerceIn(0, chapterList.size - 1)
                )?.url
                book.durChapterIndex = findNewIndex(oldDurUrl, newList)
                book.durChapterTitle = newList.getOrNull(book.durChapterIndex)?.title
                    ?: book.durChapterTitle
                appDb.bookDao.update(book)
                val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
                if (syncReadBook) {
                    io.legado.app.model.ReadBook.durChapterIndex = book.durChapterIndex
                }
                io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)

                BookHelp.clearMergedContentCache(book)
                persistResult()
                postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
                AppLog.put("重新分章单章完成: ${chapter.title} -> ${merged.size} 章")
                merged
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 合并当前章到上一章:修复正文中某行被误识别为章标题导致的误拆。
     * 支持合并章与合并章,也支持上一章为原子页时将其包成单子页映射后合并;卷章除外。
     * 合并保留两侧共享边界子页各自的剥离映射,拼接后得到完整内容,边界标题行被丢弃。
     */
    suspend fun mergeWithPrevious(chapter: BookChapter): Boolean {
        val subs = BookHelp.reChapterSubs(chapter) ?: return false
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return false
        try {
            return withContext(Dispatchers.IO) {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val index = chapterList.indexOfFirst {
                    it.url == chapter.url && it.bookUrl == chapter.bookUrl
                }
                if (index <= 0) return@withContext false
                val prev = chapterList[index - 1]
                if (prev.isVolume) return@withContext false
                // 上一章是原子页时包装为单子页映射,使其能参与合并
                val prevSubs = BookHelp.reChapterSubs(prev) ?: listOf(
                    BookHelp.ReChapterSub(
                        fileName = prev.getFileName(),
                        url = prev.url,
                        title = prev.title,
                    )
                )

                // 拼接映射:prev 的边界页剥离尾部,当前章的边界页剥离首部,顺序拼接得到完整内容
                val mergedSubs = prevSubs.toMutableList()
                mergedSubs.addAll(subs)
                val merged = BookChapter(
                    url = prev.url,
                    title = prev.title,
                    isVolume = false,
                    baseUrl = prev.baseUrl,
                    bookUrl = book.bookUrl,
                    index = 0,
                )
                BookHelp.putReChapterSubs(merged, mergedSubs)

                // 重建目录:prev 替换为合并章,移除当前章
                val newList = arrayListOf<BookChapter>()
                newList.addAll(chapterList.subList(0, index - 1))
                newList.add(merged)
                newList.addAll(chapterList.subList(index + 1, chapterList.size))
                newList.forEachIndexed { i, c -> c.index = i }

                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*newList.toTypedArray())

                book.totalChapterNum = newList.size
                val oldDurUrl = chapterList.getOrNull(
                    book.durChapterIndex.coerceIn(0, chapterList.size - 1)
                )?.url
                book.durChapterIndex = findNewIndex(oldDurUrl, newList)
                book.durChapterTitle = newList.getOrNull(book.durChapterIndex)?.title
                    ?: book.durChapterTitle
                appDb.bookDao.update(book)
                val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
                if (syncReadBook) {
                    io.legado.app.model.ReadBook.durChapterIndex = book.durChapterIndex
                }
                io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)

                BookHelp.clearMergedContentCache(book)
                persistResult()
                postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
                AppLog.put("合并章到上一章: ${prev.title} <- ${chapter.title}")
                true
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 在合并章内按选中的标题行拆分章节:修复正文中漏识别的章节边界。
     * 在原子页中查找与 [titleText] 匹配的行,以其为新章边界拆分为两个章。
     * @return 拆分后的新章节列表,匹配失败或非合并章返回 null
     */
    suspend fun splitChapter(chapter: BookChapter, titleText: String): List<BookChapter>? {
        val subs = BookHelp.reChapterSubs(chapter) ?: return null
        if (subs.size < 2) return null
        val lock = batchLocks.getOrPut(book.bookUrl) { Mutex() }
        if (!lock.tryLock()) return null
        try {
            return withContext(Dispatchers.IO) {
                val target = titleText.trim()
                if (target.isBlank()) return@withContext null
                // 在原子页中查找选中标题行
                var splitSubIndex = -1
                var splitLine = -1
                var lineCount = 0
                for ((k, sub) in subs.withIndex()) {
                    val content = BookHelp.getContentByFileName(book, sub.fileName) ?: continue
                    val lines = content.lines()
                    val stripped = sub.stripLines?.toSet() ?: emptySet()
                    for ((li, line) in lines.withIndex()) {
                        if (li in stripped) continue
                        val t = line.trim()
                        if (t == target || (t.isNotEmpty() && (t.startsWith(target) || target.startsWith(t)))) {
                            splitSubIndex = k
                            splitLine = li
                            lineCount = lines.size
                            break
                        }
                    }
                    if (splitSubIndex >= 0) break
                }
                if (splitSubIndex < 0) {
                    AppLog.put("拆分章节失败:未在正文中找到 ${target.take(20)}")
                    return@withContext null
                }

                val k = splitSubIndex
                val part1Subs = arrayListOf<BookHelp.ReChapterSub>()
                for (j in 0 until k) part1Subs.add(subs[j])
                part1Subs.add(splitSub(subs[k], splitLine, lineCount, keepBefore = true))
                val part2Subs = arrayListOf<BookHelp.ReChapterSub>()
                part2Subs.add(splitSub(subs[k], splitLine, lineCount, keepBefore = false))
                for (j in k + 1 until subs.size) part2Subs.add(subs[j])

                val baseUrl = chapter.baseUrl
                val part1 = BookChapter(
                    url = subs.first().url,
                    title = chapter.title,
                    isVolume = false,
                    baseUrl = baseUrl,
                    bookUrl = book.bookUrl,
                    index = 0,
                )
                BookHelp.putReChapterSubs(part1, part1Subs)
                val part2 = BookChapter(
                    url = subs[k].url,
                    title = target.take(MAX_TITLE_LENGTH),
                    isVolume = false,
                    baseUrl = baseUrl,
                    bookUrl = book.bookUrl,
                    index = 0,
                )
                BookHelp.putReChapterSubs(part2, part2Subs)

                // 重建目录:当前章替换为 part1 + part2
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val chapterIndex = chapterList.indexOfFirst {
                    it.url == chapter.url && it.bookUrl == chapter.bookUrl
                }
                if (chapterIndex < 0) return@withContext null
                val newList = arrayListOf<BookChapter>()
                newList.addAll(chapterList.subList(0, chapterIndex))
                newList.add(part1)
                newList.add(part2)
                newList.addAll(chapterList.subList(chapterIndex + 1, chapterList.size))
                newList.forEachIndexed { i, c -> c.index = i }

                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*newList.toTypedArray())

                book.totalChapterNum = newList.size
                val oldDurUrl = chapterList.getOrNull(
                    book.durChapterIndex.coerceIn(0, chapterList.size - 1)
                )?.url
                book.durChapterIndex = findNewIndex(oldDurUrl, newList)
                book.durChapterTitle = newList.getOrNull(book.durChapterIndex)?.title
                    ?: book.durChapterTitle
                appDb.bookDao.update(book)
                val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
                if (syncReadBook) {
                    io.legado.app.model.ReadBook.durChapterIndex = book.durChapterIndex
                }
                io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)

                persistResult()
                postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
                AppLog.put("拆分章节: ${chapter.title} -> ${part1.title} / ${part2.title}")
                listOf(part1, part2)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 为拆分构造子页映射:保留 [splitLine] 之前(keepBefore=true)或之后(keepBefore=false)的行,
     * 原剥离行与新边界剥离行合并。
     */
    private fun splitSub(
        sub: BookHelp.ReChapterSub,
        splitLine: Int,
        lineCount: Int,
        keepBefore: Boolean,
    ): BookHelp.ReChapterSub {
        val strip = sub.stripLines?.toMutableSet() ?: hashSetOf()
        if (keepBefore) {
            // 保留 0..splitLine-1,剥离 splitLine..末尾(新章标题及之后留给新章)
            for (l in splitLine until lineCount) strip.add(l)
        } else {
            // 保留 splitLine+1..末尾,剥离 0..splitLine(含新章标题)
            for (l in 0..splitLine.coerceAtMost(lineCount - 1)) strip.add(l)
        }
        return sub.copy(stripLines = strip.takeIf { it.isNotEmpty() }?.toList())
    }

    /**
     * 把合并章展开为原子页列表。目录中无合并章时返回 null。
     * 展开原子页的 title 从 [BookHelp.ReChapterSub.title] 还原(合并时保存的原始标题);
     * 旧数据缺 title 时从缓存正文首行提取兜底。缓存读取依赖 [srcFileKey] 记录的原始文件名。
     */
    private fun expandMergedChapters(chapterList: List<BookChapter>): List<BookChapter>? {
        var hasMerged = false
        val expanded = arrayListOf<BookChapter>()
        for (chapter in chapterList) {
            val subs = BookHelp.reChapterSubs(chapter)
            if (subs == null) {
                expanded.add(chapter)
            } else {
                hasMerged = true
                for (sub in subs) {
                    // 旧数据反序列化时 title 可能为 null(GSON 无 Kotlin 适配器,绕过构造默认值)
                    var title = sub.title ?: ""
                    if (title.isBlank()) {
                        title = BookHelp.getContentByFileName(book, sub.fileName)
                            ?.lineSequence()?.firstOrNull { it.trim().isNotBlank() }?.trim()
                            ?: ""
                    }
                    val atomic = BookChapter(
                        url = sub.url,
                        title = title,
                        baseUrl = chapter.baseUrl,
                        bookUrl = book.bookUrl,
                        index = 0,
                    )
                    atomic.putVariable(srcFileKey, sub.fileName)
                    expanded.add(atomic)
                }
            }
        }
        return if (hasMerged) expanded else null
    }

    /**
     * 原子页缓存文件名:重新分章展开的原子页优先用记录的原始文件名,
     * 普通原子页按当前 chapter 计算。
     */
    private fun atomicFileName(ch: BookChapter): String {
        return ch.getVariable(srcFileKey).takeIf { it.isNotBlank() } ?: ch.getFileName()
    }

    private suspend fun executeLocked(
        startIndex: Int,
        fullWindow: Boolean = false,
    ): ExecuteResult {
        // 尝试恢复持久化的分章结果(换源切回原源等场景)。已恢复则幂等跳过重建;
        // 恢复重建后 reChapterMark 已指向最后一个合并章,下方扫描逻辑自动从其后继续增量分章。
        restoreIfNeeded()
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapterList.isEmpty()) return ExecuteResult.NONE
        // 首次分章(尚无进度标记)前快照书源原始目录,供关闭分章时还原
        if (book.reChapterMark == null) {
            snapshotOriginalToc()
        }
        AppLog.put(
            "重新分章 executeLocked startIndex=$startIndex fullWindow=$fullWindow " +
                "mark=${book.reChapterMark} 目录共${chapterList.size}章 dur=${book.durChapterIndex}"
        )

        var start = startIndex.coerceIn(0, chapterList.size - 1)
        if (!fullWindow) {
            book.reChapterMark?.let { mark ->
                val markIndex = chapterList.indexOfFirst { it.url == mark }
                if (markIndex >= 0) start = markIndex + 1
            }
            if (start >= chapterList.size) return ExecuteResult.NONE

            // 首次分章(尚无进度标记)时,把窗口向前扩几个子页,把当前章开头的标题纳入扫描。
            // 否则从章节中间的子页开始扫描,窗口内可能扫不到当前章的标题,
            // 导致判定不足而被跳过,或把当前阅读处错误并入后一章。
            if (book.reChapterMark == null) {
                start = max(0, start - LOOK_BACK)
            }
        } else {
            // 全量重新分章:从书开头扫到结尾
            start = 0
        }

        val batchSize = if (fullWindow) chapterList.size else AppConfig.preDownloadNum + BATCH_EXTRA
        val end = min(start + batchSize, chapterList.size)
        AppLog.put(
            "重新分章窗口 start=$start end=$end batchSize=$batchSize " +
                "预下载=${AppConfig.preDownloadNum} reChapterMark=${book.reChapterMark}"
        )

        // 只取窗口内已缓存、且尚未合并的原子页
        val windowAtomic = arrayListOf<BookChapter>()
        val windowContent = hashMapOf<String, String>()
        for (i in start until end) {
            val chapter = chapterList[i]
            if (chapter.isVolume) continue
            // 重新分章:已分章的合并章展开为原子页数据源(保留原始缓存文件名),重新扫描边界
            val subs = BookHelp.reChapterSubs(chapter)
            if (subs != null) {
                for (sub in subs) {
                    val content = BookHelp.getContentByFileName(book, sub.fileName)
                    if (content != null && content.isNotBlank()) {
                        val atomic = BookChapter(
                            url = sub.url,
                            baseUrl = chapter.baseUrl,
                            bookUrl = book.bookUrl,
                            index = 0,
                        )
                        atomic.putVariable(srcFileKey, sub.fileName)
                        windowAtomic.add(atomic)
                        windowContent[sub.url] = content
                    }
                }
                continue
            }
            // 重置分章时展开的原子页:缓存文件名记录在 srcFileKey 中,
            // 展开后 index/title 已变化,必须按原始文件名读取才能命中缓存
            val content = if (chapter.getVariable(srcFileKey).isNotBlank()) {
                BookHelp.getContentByFileName(book, atomicFileName(chapter))
            } else {
                BookHelp.getContent(book, chapter)
            }
            if (content != null && content.isNotBlank()) {
                windowAtomic.add(chapter)
                windowContent[chapter.url] = content
            }
        }
        if (windowAtomic.isEmpty()) {
            AppLog.put("重新分章窗口内无已缓存原子页,跳过")
            return ExecuteResult.NONE
        }

        // 行首预筛收集候选,再进强规则校验
        val detector = TitleDetector()
        val candidates = scanCandidates(windowAtomic, windowContent, detector)
        // 卷标题(第X卷/卷X)不作为章节边界,避免"第一卷"合并章把"第一章"覆盖;
        // 但卷标题行会作为独立卷章插入目录(卷首页),故需从合并章正文中剥离。
        // 同一子页内重复出现的卷标题只保留第一处。
        val volumeHits = candidates
            .filter { it.isVolume }
            .distinctBy { it.subIndex to it.line }
        // 边界:强规则命中、非卷标题。同一标题连续重复出现时(网页封面章名与正文中
        // 同名的真实章节标题、跨子页的"预告章名"等),只保留最后一个作为边界,
        // 避免真正的章节边界被开头的重复标题覆盖。
        val boundaryHits = dedupeTitleHits(candidates.filter { it.ruleMatch && !it.isVolume })
        if (volumeHits.isNotEmpty()) {
            AppLog.put(
                "重新分章检测到卷标题 ${volumeHits.size} 条:" +
                    volumeHits.joinToString(" | ") { "${it.subIndex}#${it.lineIndex} ${it.line}" }
            )
        }
        if (boundaryHits.size < 2) {
            // 本地判定不足:窗口内已缓存原子页太少则等后续下载,否则跳过本批。
            // 不推进进度标记,窗口随阅读位置前进后重试。
            AppLog.put(
                "重新分章本地边界不足(${boundaryHits.size} 条),窗口原子页${windowAtomic.size}个,等待下一批"
            )
            return ExecuteResult.NONE
        }

        val merged = buildMergedChapters(windowAtomic, windowContent, boundaryHits, volumeHits)
        if (merged.isEmpty()) {
            AppLog.put("重新分章构建合并章为空")
            return ExecuteResult.NONE
        }

        // 记录旧 index,用于重建后按 url 定位阅读位置、以及原子页缓存文件改名
        val oldIndexByUrl = chapterList.associate { it.url to it.index }
        val oldDurUrl = chapterList.getOrNull(
            book.durChapterIndex.coerceIn(0, chapterList.size - 1)
        )?.url

        // 重建目录:窗口前的章节 + 合并章 + 窗口内未处理的 + 窗口后的章节
        val newList = arrayListOf<BookChapter>()
        newList.addAll(chapterList.subList(0, start))
        newList.addAll(merged)
        // 窗口内重新扫描生成的卷章会替换原书源目录中的同名卷章(url 均为标题文本),
        // 避免"第一卷"同时出现两个条目
        val newVolumeUrls = merged.filter { it.isVolume }.map { it.url }.toSet()
        for (i in start until end) {
            val chapter = chapterList[i]
            if (windowAtomic.contains(chapter)) continue
            if (chapter.isVolume && chapter.url in newVolumeUrls) continue
            newList.add(chapter)
        }
        newList.addAll(chapterList.subList(end, chapterList.size))

        // 统一重排 index,并重命名被移动的原子页缓存文件,避免丢失已缓存内容
        newList.forEachIndexed { index, chapter ->
            val oldIndex = oldIndexByUrl[chapter.url]
            chapter.index = index
            if (oldIndex != null && oldIndex != index && !BookHelp.isReChaptered(chapter)) {
                BookHelp.renameContentFile(book, chapter, oldIndex, index)
            }
        }

        appDb.bookChapterDao.delByBook(book.bookUrl)
        appDb.bookChapterDao.insert(*newList.toTypedArray())

        // 更新书籍状态
        book.reChapterMark = merged.last().url
        book.totalChapterNum = newList.size
        val newDurIndex = findNewIndex(oldDurUrl, newList)
        book.durChapterIndex = newDurIndex
        book.durChapterTitle = newList.getOrNull(newDurIndex)?.title ?: book.durChapterTitle
        appDb.bookDao.update(book)
        val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
        if (syncReadBook) {
            io.legado.app.model.ReadBook.durChapterIndex = newDurIndex
        }
        AppLog.put(
            "重新分章重建:目录${newList.size}章(合并${merged.count { it.isVolume == false }}" +
                "卷章${merged.count { it.isVolume }}原子页${windowAtomic.size}) " +
                "oldDur=${oldDurUrl} newDurIndex=$newDurIndex 新标题=${newList.getOrNull(newDurIndex)?.title}" +
                " ReadBook同步=$syncReadBook"
        )
        // 重载当前/前后章节正文:重建目录后阅读器内存里的 curTextChapter 等还是旧的原子页内容,
        // 必须重载才能让正文与合并目录一致
        io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)

        // 重建目录后合并章的映射/剥离行可能变化,失效该书所有合并章 LRU 缓存,避免命中旧拼接内容
        BookHelp.clearMergedContentCache(book)

        // 全量持久化分章结果,换源切回原源时可恢复
        persistResult()

        postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        AppLog.put("重新分章完成 ${book.name} 共${newList.size}章")
        return ExecuteResult.MERGED
    }

    /**
     * 同一标题连续重复出现时只保留最后一个作为边界。
     * 网页常在子页开头重复一遍 TOC 章名(封面章名/加粗),或在前一子页末尾放"下一章预告",
     * 真正的章节边界在正文中;把连续出现的同标题合并为只保留最后一个是边界,
     * 避免真正的边界被开头的重复标题覆盖。
     */
    private fun dedupeTitleHits(hits: List<LineHit>): List<LineHit> {
        val sorted = hits.sortedWith(compareBy({ it.subIndex }, { it.lineIndex }))
        val result = arrayListOf<LineHit>()
        var i = 0
        while (i < sorted.size) {
            var j = i + 1
            while (j < sorted.size && sorted[j].line == sorted[i].line) {
                j++
            }
            // 连续出现的同一标题(中间没有其它标题)合并为最后一个
            result.add(sorted[j - 1])
            i = j
        }
        return result
    }

    /**
     * 扫描窗口内所有子页的行,收集候选标题行。[LineHit.ruleMatch] 为 true 表示命中强规则。
     */
    private fun scanCandidates(
        windowAtomic: List<BookChapter>,
        windowContent: Map<String, String>,
        detector: TitleDetector,
    ): List<LineHit> {
        val hits = arrayListOf<LineHit>()
        windowAtomic.forEachIndexed { subIndex, chapter ->
            val content = windowContent[chapter.url] ?: return@forEachIndexed
            content.lines().forEachIndexed { lineIndex, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.length > MAX_TITLE_LENGTH) return@forEachIndexed
                if (!detector.preFilterPasses(trimmed)) return@forEachIndexed
                hits.add(
                    LineHit(
                        subIndex = subIndex,
                        lineIndex = lineIndex,
                        line = trimmed,
                        ruleMatch = detector.strongRuleMatch(trimmed),
                        isVolume = detector.isVolumeTitle(trimmed),
                    )
                )
            }
        }
        return hits
    }

    /**
     * 按检测出的标题边界构建合并章。
     *
     * 子页可能横跨两章的边界(前一章结尾 + 本章标题 + 本章开头都在一个子页里),
     * 因此按**行级**切分:本章正文 = 边界标题行之后 到 下一个边界标题行之前的全部行,
     * 跨子页连续拼接。边界标题行本身不入正文(标题由阅读器从 chapter.title 展示)。
     *
     * 卷标题行不作为边界,但会作为独立卷章插入目录(卷首页),并从其所在子页正文中剥离;
     * 卷标题行到同子页内下一个章节边界之间的卷首内容(封面章名+文案)归入卷首页正文,
     * 同样从合并章正文中剥离。
     */
    private fun buildMergedChapters(
        windowAtomic: List<BookChapter>,
        windowContent: Map<String, String>,
        boundaryHits: List<LineHit>,
        volumeHits: List<LineHit>,
    ): List<BookChapter> {
        if (boundaryHits.isEmpty()) return emptyList()
        val result = arrayListOf<BookChapter>()
        val baseUrl = windowAtomic.firstOrNull()?.baseUrl ?: book.tocUrl

        // 卷标题行本身(已作为独立卷章标题展示,不能重复出现在合并章正文)
        // 以及卷标题行到下一个章节边界之间(封面章名+文案)归卷首页正文。
        // 边界可能与卷标题在不同子页:卷首内容跨子页拼接,并从合并章正文中剥离。
        val volumeStripBySub = hashMapOf<Int, MutableSet<Int>>()
        val volumeOpeners = hashMapOf<LineHit, String?>()
        for (vol in volumeHits) {
            val next = boundaryHits.firstOrNull {
                it.subIndex > vol.subIndex ||
                    (it.subIndex == vol.subIndex && it.lineIndex > vol.lineIndex)
            } ?: continue
            val endSub = next.subIndex.coerceIn(0, windowAtomic.size - 1)
            val endLine = next.lineIndex
            val sb = StringBuilder()
            val appendLine = { text: String ->
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(text)
            }
            // 卷标题所在子页:标题行本身 + 标题行之后的卷首内容(到本页末尾或同页边界)
            val volLines = windowContent[windowAtomic[vol.subIndex].url]?.lines() ?: continue
            val volStrip = volumeStripBySub.getOrPut(vol.subIndex) { hashSetOf() }
            val volPageEnd = if (endSub == vol.subIndex) endLine else volLines.size
            // 卷标题是该卷下一章边界的首个边界且同页时,卷标题之前的内容(书开头引子)也并入卷首页,
            // 否则这些行会被首章的 startSub 剥离逻辑吞掉(无前一章承接)
            val isFirstBoundarySamePage = endSub == vol.subIndex && next == boundaryHits.first()
            val stripStart = if (isFirstBoundarySamePage) 0 else vol.lineIndex
            for (l in stripStart until volPageEnd.coerceAtMost(volLines.size)) {
                volStrip.add(l)
                if (l != vol.lineIndex) appendLine(volLines[l])
            }
            // 跨子页:卷标题页之后的整页内容 + 边界所在页边界之前的卷首内容
            for (mid in vol.subIndex + 1 until endSub) {
                val midLines = windowContent[windowAtomic[mid].url]?.lines() ?: continue
                val midStrip = volumeStripBySub.getOrPut(mid) { hashSetOf() }
                for (l in 0 until midLines.size) {
                    midStrip.add(l)
                    appendLine(midLines[l])
                }
            }
            if (endSub > vol.subIndex) {
                val nextLines = windowContent[windowAtomic[endSub].url]?.lines() ?: continue
                val nextStrip = volumeStripBySub.getOrPut(endSub) { hashSetOf() }
                for (l in 0 until endLine.coerceAtMost(nextLines.size)) {
                    nextStrip.add(l)
                    appendLine(nextLines[l])
                }
            }
            // 卷首页正文 = 卷标题行之后的卷首内容(封面章名+文案),卷标题本身作为页面标题
            volumeOpeners[vol] = sb.toString().takeIf { it.isNotBlank() }
        }
        if (volumeOpeners.isNotEmpty()) {
            AppLog.put(
                "重新分章卷首页承载卷首内容:" +
                    volumeHits.joinToString(" | ") { vol ->
                        val opener = volumeOpeners[vol]
                        "${vol.subIndex}#${vol.lineIndex} ${vol.line}" +
                            (if (opener != null) "(含卷首${opener.length}字)" else "(无卷首)")
                    }
            )
        }

        // 先按边界构建合并章(记录每个合并章的起始子页,用于交错插入卷章)
        val mergedChapters = arrayListOf<BookChapter>()
        val mergedStarts = arrayListOf<Int>()
        boundaryHits.forEachIndexed { index, hit ->
            val startSub = hit.subIndex
            val startLine = hit.lineIndex
            val next = boundaryHits.getOrNull(index + 1)
            val endSub = (next?.subIndex ?: windowAtomic.size - 1)
                .coerceIn(0, windowAtomic.size - 1)
            val endLine = next?.lineIndex ?: Int.MAX_VALUE
            if (startSub > endSub) return@forEachIndexed
            // 同一子页内两个边界紧邻(中间无正文),无内容可并入
            if (startSub == endSub && startLine + 1 >= endLine) return@forEachIndexed

            val subs = arrayListOf<BookHelp.ReChapterSub>()
            var keptTotal = 0
            if (index == 0) {
                // 首个边界之前的悬垂子页原样并入首章
                for (i in 0 until startSub) {
                    val ch = windowAtomic[i]
                    val strip = volumeStripBySub[i] ?: emptySet()
                    val lineCount = windowContent[ch.url]?.lines()?.size ?: 0
                    keptTotal += lineCount - strip.size
                    subs.add(atomicSub(ch, i, strip))
                }
            }
            for (i in startSub..endSub) {
                val ch = windowAtomic[i]
                val strip = hashSetOf<Int>()
                volumeStripBySub[i]?.let { strip.addAll(it) }
                val lineCount = windowContent[ch.url]?.lines()?.size ?: 0
                if (i == startSub) {
                    // 剥离本章边界标题行及之前(上一章尾内容/卷首)的行
                    for (l in 0 until (startLine + 1).coerceAtMost(lineCount)) {
                        strip.add(l)
                    }
                }
                if (i == endSub && next != null) {
                    // 下一章边界在同一子页:剥离下一章标题行及之后的行(留给下一章)
                    for (l in endLine.coerceIn(0, lineCount) until lineCount) {
                        strip.add(l)
                    }
                }
                keptTotal += max(0, lineCount - strip.size)
                subs.add(atomicSub(ch, i, strip))
            }
            if (subs.isEmpty() || keptTotal <= 0) return@forEachIndexed

            AppLog.putDebug(
                "重新分章合并章[${hit.line.take(14)}] 子页${startSub}..$endSub " +
                    "行${startLine}..${if (next == null) "尾" else endLine} " +
                    "子页剥离行=${subs.joinToString(",") { "${it.stripLines?.size ?: 0}" }}"
            )

            val chapter = BookChapter(
                url = subs.first().url,
                title = hit.line.take(MAX_TITLE_LENGTH),
                isVolume = false,
                baseUrl = baseUrl,
                bookUrl = book.bookUrl,
                index = 0,
            )
            BookHelp.putReChapterSubs(chapter, subs)
            mergedChapters.add(chapter)
            mergedStarts.add(startSub)
        }

        // 卷标题行作为独立卷章:插到第一个 startSub >= 卷标题所在子页的合并章之前,
        // 保证卷首页出现在该卷第一章之前
        val volumes = volumeHits.sortedWith(compareBy({ it.subIndex }, { it.lineIndex }))
        var mi = 0
        for (vol in volumes) {
            while (mi < mergedChapters.size && mergedStarts[mi] < vol.subIndex) {
                result.add(mergedChapters[mi])
                mi++
            }
            result.add(buildVolumeChapter(vol, baseUrl, volumeOpeners[vol]))
        }
        while (mi < mergedChapters.size) {
            result.add(mergedChapters[mi])
            mi++
        }
        return result
    }

    /**
     * 构建一个原子页的映射记录,并把该原子页中标记的卷标题行/卷首区间行号记为剥离行。
     */
    private fun atomicSub(
        ch: BookChapter,
        subIndex: Int,
        stripLines: Set<Int>,
    ): BookHelp.ReChapterSub {
        val stripped = stripLines.takeIf { it.isNotEmpty() }?.toList()
        return BookHelp.ReChapterSub(
            fileName = atomicFileName(ch),
            url = ch.url,
            title = ch.title,
            stripLines = stripped,
        )
    }

    /**
     * 构建独立卷章:url 取标题本身,满足 isVolume && url.startsWith(title),
     * 阅读器正文规则不解析、返回卷首内容(tag),渲染时标题垂直居中独占一页(卷首页)。
     */
    private fun buildVolumeChapter(vol: LineHit, baseUrl: String, tag: String?): BookChapter {
        return buildVolumeChapter(vol.line.take(MAX_TITLE_LENGTH), baseUrl, tag)
    }

    /**
     * 从持久化记录构建独立卷章(恢复切源后的目录时用)。
     */
    private fun buildVolumeChapterFromRecord(vol: ReChapterRecord, baseUrl: String): BookChapter {
        return buildVolumeChapter(vol.title.take(MAX_TITLE_LENGTH), baseUrl, vol.content)
    }

    private fun buildVolumeChapter(title: String, baseUrl: String, tag: String? = null): BookChapter {
        return BookChapter(
            url = title,
            title = title,
            tag = tag,
            isVolume = true,
            baseUrl = baseUrl,
            bookUrl = book.bookUrl,
            index = 0,
        )
    }

    /**
     * 重建后按原阅读章节 url 定位新 index:先精确匹配,再查合并章映射,最后退回 Jaccard+序号映射。
     */
    private fun findNewIndex(oldDurUrl: String?, newList: List<BookChapter>): Int {
        if (oldDurUrl != null) {
            newList.indexOfFirst { it.url == oldDurUrl }.let { if (it >= 0) return it }
            newList.indexOfFirst { chapter ->
                BookHelp.reChapterSubs(chapter)?.any { it.url == oldDurUrl } == true
            }.let { if (it >= 0) return it }
        }
        return BookHelp.getDurChapter(book, newList)
    }

    /**
     * 单章刷新:删除该合并章全部原子页缓存后按映射重新下载并拼接。
     * 非合并章返回 null,由调用方走原有"删缓存重拉单章"逻辑。
     */
    suspend fun refreshChapter(chapter: BookChapter): String? {
        if (BookHelp.reChapterSubs(chapter) == null) return null
        return withContext(Dispatchers.IO) {
            BookHelp.delContent(book, chapter)
            BookHelp.downloadMappedContent(bookSource, book, chapter)
        }
    }

    /**
     * 把当前书源已分章的结果全量持久化(按 bookUrl 维度),
     * 换源切回原源时可据此恢复,不依赖 chapters 表(换源时会被清空)。
     * 只存标题 + 原子页 url 序列,fileName 在恢复时按新目录重算。
     * 卷章(isVolume)单独记录,anchorUrl 指向其后第一个合并章的首个子页 url。
     */
    private fun persistResult() {
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val records = arrayListOf<ReChapterRecord>()
        var nextMergedUrl: String? = null
        for (i in chapterList.indices.reversed()) {
            val chapter = chapterList[i]
            if (chapter.isVolume) {
                records.add(
                    ReChapterRecord(
                        title = chapter.title,
                        urls = emptyList(),
                        isVolume = true,
                        anchorUrl = nextMergedUrl,
                        content = chapter.tag,
                    )
                )
            } else {
                val subs = BookHelp.reChapterSubs(chapter)
                if (subs.isNullOrEmpty()) continue
                records.add(
                    ReChapterRecord(
                        title = chapter.title,
                        urls = subs.map { it.url },
                        stripLines = subs.map { it.stripLines },
                    )
                )
                nextMergedUrl = subs.first().url
            }
        }
        records.reverse()
        RuleBigDataHelp.putBookVariable(
            book.bookUrl,
            persistKey,
            GSON.toJson(records)
        )
    }

    /**
     * 用持久化的分章结果恢复当前目录(同书源切回等场景)。
     * 校验:每个合并章的原子页 url 必须在当前目录中存在、连续且不重叠;
     * 校验失败(如书源更新导致 url 变化)则放弃,走重新分章。
     * 目录已恢复过时幂等跳过,不重复重建。
     */
    private suspend fun restoreIfNeeded() {
        val raw = RuleBigDataHelp.getBookVariable(book.bookUrl, persistKey) ?: return
        if (raw.isBlank()) return
        // 过滤旧版本卷标题合并章记录:旧数据可能把"第一卷"当作合并章,恢复时不再重建,
        // 让卷标题覆盖的原子页还原到目录,避免"第一卷"继续覆盖"第一章"。
        // 新版本的独立卷章记录(isVolume = true)需保留。
        val records = GSON.fromJson(raw, Array<ReChapterRecord>::class.java)?.toList()
            ?.filter { it.isVolume || !volumeRegex.matches(it.title.trim()) }
        if (records == null || records.isEmpty()) {
            RuleBigDataHelp.putBookVariable(book.bookUrl, persistKey, null)
            return
        }
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapterList.isEmpty()) return

        val volumeRecords = records.filter { it.isVolume }
        val mergedRecords = records.filter { !it.isVolume }
        AppLog.put(
            "重新分章恢复检查:持久化记录 ${records.size} 条(合并${mergedRecords.size}卷章${volumeRecords.size}) " +
                "当前目录 ${chapterList.size} 章"
        )

        // 幂等:目录已按该持久化恢复过(合并章覆盖的原子页集合与记录一致)
        val expected = mergedRecords.flatMap { it.urls }.toSet()
        val existing = chapterList
            .filter { BookHelp.isReChaptered(it) }
            .flatMap { BookHelp.reChapterSubs(it)?.map { s -> s.url } ?: emptyList() }
            .toSet()
        if (existing.isNotEmpty() && existing == expected) {
            AppLog.put("重新分章目录已恢复过,幂等跳过")
            return
        }

        val urlToIndex = hashMapOf<String, Int>()
        chapterList.forEachIndexed { i, c -> urlToIndex[c.url] = i }
        val covered = hashSetOf<Int>()
        val parsed = arrayListOf<Pair<ReChapterRecord, IntRange>>()
        var prevRecordLast: Int? = null
        for (record in mergedRecords) {
            val indexes = record.urls.mapNotNull { urlToIndex[it] }
            if (indexes.size != record.urls.size) return
            // 原子页必须连续且有序(分章时合并章覆盖一段连续原子页)
            if (indexes.zipWithNext().any { (a, b) -> b != a + 1 }) return
            // 行级切分时相邻合并章共享一个边界子页(上一条记录最后一个子页 == 本条第一个子页),
            // 允许该共享子页被两条记录各占用一次
            val overlap = indexes.firstOrNull { it in covered }
            if (overlap != null && overlap != indexes.first()) return
            if (overlap != null && overlap != prevRecordLast) return
            indexes.forEach { covered.add(it) }
            prevRecordLast = indexes.last()
            parsed.add(record to (indexes.first()..indexes.last()))
        }
        // 按起点排序,并校验与目录顺序一致(允许相邻合并章共享边界子页)
        parsed.sortBy { it.second.first }
        for (i in 1 until parsed.size) {
            if (parsed[i].second.first < parsed[i - 1].second.last) return
        }

        // 重建目录:合并章替换其覆盖的原子页,其余原子页原样保留;卷章按 anchorUrl 插到对应合并章之前
        val baseUrl = chapterList.firstOrNull()?.baseUrl ?: book.tocUrl
        val newList = arrayListOf<BookChapter>()
        val volumeByAnchor = volumeRecords.groupBy { it.anchorUrl }
        // 恢复重建的卷章(url 为标题文本)会替换原书源目录中的同名卷章,避免重复条目
        val newVolumeUrls = volumeRecords.map { it.title }.toSet()
        var parsedIdx = 0
        for ((index, chapter) in chapterList.withIndex()) {
            if (parsedIdx < parsed.size && index == parsed[parsedIdx].second.first) {
                val (record, range) = parsed[parsedIdx]
                // 先插入 anchorUrl 指向该合并章首个子页的卷章
                volumeByAnchor[chapterList[range.first].url]?.forEach { vol ->
                    newList.add(buildVolumeChapterFromRecord(vol, baseUrl))
                }
                val subs = (range.first..range.last).map { i ->
                    val ch = chapterList[i]
                    val strip = record.stripLines?.getOrNull(i - range.first)
                    BookHelp.ReChapterSub(
                        fileName = ch.getFileName(),
                        url = ch.url,
                        title = ch.title,
                        stripLines = strip,
                    )
                }
                val merged = BookChapter(
                    url = subs.first().url,
                    title = record.title.take(MAX_TITLE_LENGTH),
                    isVolume = false,
                    baseUrl = baseUrl,
                    bookUrl = book.bookUrl,
                    index = 0,
                )
                BookHelp.putReChapterSubs(merged, subs)
                newList.add(merged)
                parsedIdx++
            } else if (!covered.contains(index)) {
                if (!(chapter.isVolume && chapter.url in newVolumeUrls)) {
                    newList.add(chapter)
                }
            }
        }
        // 末尾残留卷章(其后无合并章,anchorUrl 为 null)
        volumeByAnchor[null]?.forEach { vol ->
            newList.add(buildVolumeChapterFromRecord(vol, baseUrl))
        }
        if (newList.size >= chapterList.size) {
            AppLog.put("重新分章恢复后目录未减少(${newList.size}),放弃恢复")
            return
        }

        newList.forEachIndexed { i, c -> c.index = i }
        appDb.bookChapterDao.delByBook(book.bookUrl)
        appDb.bookChapterDao.insert(*newList.toTypedArray())

        // 更新书籍状态
        val oldDurUrl = chapterList.getOrNull(
            book.durChapterIndex.coerceIn(0, chapterList.size - 1)
        )?.url
        book.reChapterMark = parsed.last().second.run { chapterList[last].url }
        book.totalChapterNum = newList.size
        val newDur = findNewIndex(oldDurUrl, newList)
        book.durChapterIndex = newDur
        book.durChapterTitle = newList.getOrNull(newDur)?.title ?: book.durChapterTitle
        appDb.bookDao.update(book)
        val syncReadBook = io.legado.app.model.ReadBook.book?.bookUrl == book.bookUrl
        if (syncReadBook) {
            io.legado.app.model.ReadBook.durChapterIndex = newDur
        }
        // 恢复后同样重载正文,避免阅读器继续显示旧的原子页内容
        io.legado.app.model.ReadBook.onChapterListUpdated(book, loadContent = true)
        AppLog.put(
            "重新分章结果已恢复 ${book.name} 共${newList.size}章 " +
                "oldDur=${oldDurUrl} newDur=$newDur ReadBook同步=$syncReadBook"
        )
    }

    /**
     * 标题检测器:廉价行首预筛 + txtTocRule 强规则。
     */
    private inner class TitleDetector {
        private val strongRules: List<Pattern> = loadStrongRules()

        fun preFilterPasses(trimmed: String): Boolean {
            return preFilterRegex.any { it.containsMatchIn(trimmed) }
        }

        fun strongRuleMatch(trimmed: String): Boolean {
            for (pattern in strongRules) {
                if (pattern.matcher(trimmed).matches()) return true
            }
            return false
        }

        /**
         * 卷标题(第X卷/卷X/上中下卷/卷首语)识别。卷标题不作为重新分章的章节边界,
         * 避免"第一卷"这类卷级标题被当成章标题,把"第一章"的内容吞并覆盖。
         */
        fun isVolumeTitle(trimmed: String): Boolean {
            return volumeRegex.matches(trimmed)
        }
    }

    private fun loadStrongRules(): List<Pattern> {
        var rules: List<TxtTocRule> = appDb.txtTocRuleDao.enabled
        if (appDb.txtTocRuleDao.count == 0) {
            rules = DefaultData.txtTocRules.apply {
                appDb.txtTocRuleDao.insert(*this.toTypedArray())
            }.filter { it.enable }
        }
        return rules.mapNotNull { rule ->
            if (rule.rule.isBlank()) return@mapNotNull null
            try {
                rule.rule.toPattern(Pattern.MULTILINE)
            } catch (e: PatternSyntaxException) {
                null
            }
        }
    }

    private data class LineHit(
        val subIndex: Int,
        val lineIndex: Int,
        val line: String,
        val ruleMatch: Boolean,
        val isVolume: Boolean = false,
    )

    /**
     * 持久化的分章结果:一个合并章 = 标题 + 有序的原子页 url 序列;
     * 卷标题 = isVolume 标记 + 其后第一个合并章首个子页 url(anchorUrl)用于恢复定位,
     * content 保存卷首页正文(封面章名+文案),恢复目录时还原到卷章 tag。
     */
    @Keep
    private data class ReChapterRecord(
        val title: String,
        val urls: List<String>,
        val isVolume: Boolean = false,
        val stripLines: List<List<Int>?>? = null,
        val anchorUrl: String? = null,
        val content: String? = null,
    )

    /**
     * 原始目录快照条目(首次分章前抓取,关闭分章时还原书源原始数据)。
     * @param srcFile 该章节原始缓存文件名(%05d-{titleMD5}.nb),还原后写入 srcFileKey 保证缓存命中
     */
    @Keep
    private data class OriginalTocEntry(
        val url: String,
        val title: String,
        val isVolume: Boolean = false,
        val isVip: Boolean = false,
        val isPay: Boolean = false,
        val tag: String? = null,
        val wordCount: String? = null,
        val srcFile: String? = null,
    )

    companion object {
        /** 分章批窗口比预下载数量多出的额外子页数 */
        const val BATCH_EXTRA = 3
        /** 首次分章时窗口向前回看的子页数,覆盖当前章开头标题 */
        private const val LOOK_BACK = 6
        /** 分章结果持久化 key(book 维度,按 bookUrl 隔离,换源切回可恢复) */
        private const val persistKey = "reChapter"
        /** 关闭分章时还原的原始目录快照 key(首次分章前抓取书源原始目录) */
        private const val originalTocKey = "reChapterOriginalToc"
        /** 展开原子页时暂存的原始缓存文件名(用于重新分章时还原缓存读取) */
        private const val srcFileKey = "reChapterSrcFile"
        private const val MAX_TITLE_LENGTH = 50
        private val batchLocks = ConcurrentHashMap<String, Mutex>()

        /**
         * 该书是否正有分章批次在执行(用于 UI 提示,避免重复触发)
         */
        fun isRunning(bookUrl: String): Boolean = batchLocks[bookUrl]?.isLocked == true

        private val preFilterRegex = listOf(
            Regex("^[ 　\\t]{0,4}(?:第[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[章节卷篇回话])"),
            Regex("^[ 　\\t]{0,4}(?:序章|楔子|终章|后记|尾声|番外|前言|正文)"),
            Regex("^[ 　\\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|[Ee]pisode)"),
            Regex("^[ 　\\t]{0,4}\\d{1,5}[:：,.， 、_—\\-]"),
            Regex("^[ 　\\t]{0,4}[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 、_—\\-]"),
            Regex("^[ 　\\t]{0,4}[【〔〖［\\[][\\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节]"),
        )

        /**
         * 卷标题识别:第X卷、卷X、上/中/下卷、卷首语等,可带卷名(如"第一卷 风起云涌"、"卷五 开源盛世")。
         * 命中则不作为重新分章边界,避免卷标题把真实章节标题覆盖。
         */
        private val volumeRegex = Regex(
            "^[ 　\\t]{0,4}(?:第[\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}卷" +
                "|[卷][\\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}" +
                "|[上中下]卷|卷首语|卷末语)(?:[ 　\\t:：,，.。、_—\\-]+.*)?$"
        )
    }
}
