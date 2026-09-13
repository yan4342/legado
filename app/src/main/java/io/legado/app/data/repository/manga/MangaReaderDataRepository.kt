package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.DailyReadRecord
import io.legado.app.data.entities.HourlyReadRecord
import io.legado.app.data.entities.ReadRecord
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.MangaReaderDataGateway
import io.legado.app.domain.model.manga.MangaBookPresentation
import io.legado.app.domain.model.manga.MangaBookState
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.OpenedMangaBook
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MangaReaderDataRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val uploadReadingProgressUseCase: UploadReadingProgressUseCase,
    private val backupSettingsGateway: BackupSettingsGateway,
) : MangaReaderDataGateway {

    private var readingStartedAt: Long? = null

    // ---- A 档预下载状态（自 ReadManga 迁入；session 每实例独立，openBook 时重置）----
    private val downloadedChapters = hashSetOf<Int>()
    private val downloadFailChapters = hashMapOf<Int, Int>()
    private val prefetchingChapters = hashSetOf<Int>()
    // 限制预下载网络阶段（正文抓取 + 图片落盘）并发 ≤2，对齐旧 ReadManga.preDownloadSemaphore
    private val preDownloadSemaphore = Semaphore(2)

    override fun observeBookPresentation(bookUrl: String) =
        database.bookDao.flowGetBook(bookUrl)
            .map { book ->
                MangaBookPresentation(
                    scrollMode = book?.readConfig?.mangaScrollMode,
                    sidePaddingDp = book?.readConfig?.webtoonSidePaddingDp,
                )
            }
            .distinctUntilChanged()

    override suspend fun openBook(
        bookUrl: String?,
        inBookshelf: Boolean,
        chapterChanged: Boolean,
    ): OpenedMangaBook {
        downloadedChapters.clear()
        downloadFailChapters.clear()
        prefetchingChapters.clear()
        val book = bookUrl?.takeIf(String::isNotEmpty)?.let(database.bookDao::getBook)
            ?: database.bookDao.lastReadBook
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        if (book.isLocal && !localBookExists(book)) {
            throw NoStackTraceException(application.getString(R.string.no_book))
        }
        val source = database.bookSourceDao.getBookSource(book.origin)
        if (!book.isLocal && book.tocUrl.isEmpty()) {
            val activeSource = source ?: throw NoStackTraceException(
                application.getString(R.string.manga_reader_details_failed),
            )
            val oldBook = book.copy()
            WebBook.getBookInfoAwait(activeSource, book, canReName = false)
            if (oldBook.bookUrl == book.bookUrl) database.bookDao.update(book)
            else {
                database.bookDao.replace(oldBook, book)
                BookHelp.updateCacheFolder(oldBook, book)
            }
        }
        var chapterCount = database.bookChapterDao.getChapterCount(book.bookUrl)
        if (chapterCount == 0 || book.isLocalModified()) {
            if (book.isLocal) {
                val chapters = LocalBook.getChapterList(book)
                database.bookChapterDao.delByBook(book.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                database.bookDao.update(book)
                chapterCount = chapters.size
            } else {
                val activeSource = source ?: throw NoStackTraceException(
                    application.getString(R.string.error_load_toc),
                )
                val oldBook = book.copy()
                val chapters = WebBook.getChapterListAwait(activeSource, book, true).getOrThrow()
                if (oldBook.bookUrl == book.bookUrl) {
                    database.bookDao.update(book)
                } else {
                    database.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                database.bookChapterDao.delByBook(oldBook.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                chapterCount = chapters.size
            }
        }
        if (chapterCount == 0) chapterCount = database.bookChapterDao.getChapterCount(book.bookUrl)
        val simulatedCount = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else chapterCount
        val safeChapter = book.durChapterIndex.coerceIn(0, (simulatedCount - 1).coerceAtLeast(0))
        val newerProgress = if (!chapterChanged) findNewerProgress(book) else null
        return OpenedMangaBook(
            book = MangaBookState(
                bookUrl = book.bookUrl,
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                customCoverUrl = book.customCoverUrl,
                sourceOrigin = book.origin,
                sourceName = source?.bookSourceName.orEmpty(),
                sourceType = source?.getSourceType(),
                inBookshelf = inBookshelf,
                scrollMode = book.readConfig?.mangaScrollMode,
                sidePaddingDp = book.readConfig?.webtoonSidePaddingDp,
                chapterTitles = database.bookChapterDao.getChapterList(book.bookUrl).map { it.title },
            ),
            chapterIndex = safeChapter,
            pageIndex = book.durChapterPos.coerceAtLeast(0),
            chapterCount = simulatedCount,
            newerProgress = newerProgress,
        )
    }

    override suspend fun loadChapter(bookUrl: String, chapterIndex: Int): MangaChapterContent {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        val chapter = database.bookChapterDao.getChapter(bookUrl, chapterIndex)
            ?: throw NoStackTraceException(application.getString(R.string.error_load_toc))
        val cached = BookHelp.getContent(book, chapter)
        val content = cached ?: run {
            val source = database.bookSourceDao.getBookSource(book.origin)
                ?: throw NoStackTraceException("加载内容失败 没有书源")
            val nextUrl = database.bookChapterDao.getChapter(bookUrl, chapterIndex + 1)?.url
            WebBook.getContentAwait(source, book, chapter, nextUrl)
        }
        if (content.isEmpty() && !chapter.isVolume) {
            throw NoStackTraceException("正文内容为空")
        }
        val imageUrls = BookHelp.flowImages(chapter, content)
            .distinctUntilChanged()
            .toList()
        if (imageUrls.isEmpty() && !chapter.isVolume) {
            throw NoStackTraceException("正文没有图片")
        }
        return MangaChapterContent(
            chapterIndex = chapterIndex,
            chapterTitle = chapter.title,
            chapterUrl = chapter.url,
            pages = imageUrls.mapIndexed { index, url ->
                MangaPageContent(url, index, imageUrls.size)
            },
            isVolume = chapter.isVolume,
        )
    }

    /**
     * A 档预下载：内容缓存 + 图片落盘书目录。
     * 保留主仓库 ReadManga.preDownload 的语义——完成度校验、失败重试 ≤3、信号量限并发。
     */
    override suspend fun prefetchChapter(bookUrl: String, chapterIndex: Int) {
        if (downloadedChapters.contains(chapterIndex)) return
        if ((downloadFailChapters[chapterIndex] ?: 0) >= 3) return
        if (!prefetchingChapters.add(chapterIndex)) return
        try {
            val ok = runCatching {
                preDownloadSemaphore.withPermit {
                    loadChapter(bookUrl, chapterIndex)
                    cacheChapterImagesIfNeeded(bookUrl, chapterIndex)
                }
            }.getOrDefault(false)
            if (ok) {
                downloadedChapters.add(chapterIndex)
                downloadFailChapters.remove(chapterIndex)
            } else {
                downloadFailChapters[chapterIndex] =
                    (downloadFailChapters[chapterIndex] ?: 0) + 1
            }
        } finally {
            prefetchingChapters.remove(chapterIndex)
        }
    }

    /**
     * @return true 表示非图片书，或图片已齐全 / 本次补全成功（对齐 ReadManga.cacheChapterImagesIfNeeded）
     */
    private suspend fun cacheChapterImagesIfNeeded(bookUrl: String, chapterIndex: Int): Boolean {
        val book = database.bookDao.getBook(bookUrl) ?: return false
        if (!book.isImage) return true
        val chapter = database.bookChapterDao.getChapter(bookUrl, chapterIndex) ?: return false
        val source = database.bookSourceDao.getBookSource(book.origin) ?: return false
        if (BookHelp.hasImageContent(book, chapter)) return true
        val content = BookHelp.getContent(book, chapter) ?: return false
        val failures = BookHelp.saveImages(source, book, chapter, content)
        return BookHelp.isChapterImageCacheComplete(book, chapter, failures)
    }

    override suspend fun persistProgress(bookUrl: String, chapterIndex: Int, pageIndex: Int) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        book.durChapterIndex = chapterIndex
        book.durChapterPos = pageIndex
        book.durChapterTime = System.currentTimeMillis()
        database.bookChapterDao.getChapter(bookUrl, chapterIndex)?.let {
            book.durChapterTitle = it.title
        }
        database.bookDao.update(book)
    }

    override suspend fun applyProgress(bookUrl: String, progress: MangaProgressState) {
        persistProgress(bookUrl, progress.chapterIndex, progress.pageIndex)
    }

    override suspend fun resume(bookUrl: String) {
        if (readingStartedAt == null) readingStartedAt = System.currentTimeMillis()
    }

    override suspend fun pause(bookUrl: String, inBookshelf: Boolean) {
        val start = readingStartedAt ?: return
        readingStartedAt = null
        val end = System.currentTimeMillis()
        val book = database.bookDao.getBook(bookUrl) ?: return
        val elapsed = end - start
        if (elapsed >= 10_000 && AppConfig.enableReadRecord) {
            // 主仓库阅读记录：ReadRecord + Daily + Hourly（对齐 ReadManga.upReadTime 落库方式，
            // readTime 必须读库累加，不能 REPLACE 覆盖历史累计时长）
            val record = ReadRecord(
                bookName = book.name,
                readTime = (database.readRecordDao.getReadTime(book.name) ?: 0) + elapsed,
                lastRead = end,
            )
            database.readRecordDao.insert(record)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = sdf.format(Date(end))
            val existing = database.dailyReadRecordDao.getReadTime(today, book.name) ?: 0
            database.dailyReadRecordDao.insert(DailyReadRecord(today, book.name, existing + elapsed))
            val sdfHour = SimpleDateFormat("yyyy-MM-dd HH", Locale.getDefault())
            val dateHour = sdfHour.format(Date(end))
            val existingHourly = database.hourlyReadRecordDao.getReadTime(dateHour, book.name) ?: 0
            database.hourlyReadRecordDao.insert(HourlyReadRecord(dateHour, book.name, existingHourly + elapsed))
        }
        if (backupSettingsGateway.currentSettings.syncBookProgressPlus) {
            uploadReadingProgressUseCase.execute(book.toProgressState())
        }
        if (inBookshelf && !BuildConfig.DEBUG) Backup.autoBack(application)
    }

    override suspend fun syncProgress(bookUrl: String): MangaProgressState? {
        if (!backupSettingsGateway.currentSettings.syncBookProgressPlus) return null
        val book = database.bookDao.getBook(bookUrl) ?: return null
        return syncOrUpload(book)
    }

    private suspend fun findNewerProgress(book: Book): MangaProgressState? {
        if (!backupSettingsGateway.currentSettings.syncBookProgress) return null
        return syncOrUpload(book)
    }

    private suspend fun syncOrUpload(book: Book): MangaProgressState? {
        val newer = compareProgress(
            book,
            getReadingProgressUseCase.execute(book.name, book.author),
        )
        if (newer == null) uploadReadingProgressUseCase.execute(book.toProgressState())
        return newer
    }

    private fun compareProgress(
        book: Book,
        progress: io.legado.app.domain.model.ReadingProgress?,
    ): MangaProgressState? {
        progress ?: return null
        val remoteIsNewer = progress.durChapterIndex > book.durChapterIndex ||
            progress.durChapterIndex == book.durChapterIndex &&
            progress.durChapterPos > book.durChapterPos
        return progress.takeIf { remoteIsNewer }?.let {
            MangaProgressState(
                bookName = it.name,
                bookAuthor = it.author,
                chapterIndex = it.durChapterIndex,
                pageIndex = it.durChapterPos,
                chapterTitle = it.durChapterTitle,
                updatedAt = it.durChapterTime,
            )
        }
    }

    private fun Book.toProgressState() = io.legado.app.domain.model.ReadingProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle,
    )

    private fun localBookExists(book: Book): Boolean = runCatching {
        LocalBook.getBookInputStream(book)
    }.isSuccess
}
