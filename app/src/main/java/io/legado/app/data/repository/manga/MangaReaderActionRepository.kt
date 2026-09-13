package io.legado.app.data.repository.manga

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Book.ReadConfig
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.model.CacheBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** 漫画阅读器一次性操作仓储（换源/加书架/禁用源/保存图片等）。 */
class MangaReaderActionRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val imageLoader: ImageLoader,
) {
    suspend fun getBook(bookUrl: String): Book? = database.bookDao.getBook(bookUrl)

    suspend fun refreshSource(sourceOrigin: String?) =
        sourceOrigin?.let { database.bookSourceDao.getBookSource(it) }

    suspend fun disableSource(sourceOrigin: String?) {
        val source = sourceOrigin?.let { database.bookSourceDao.getBookSource(it) } ?: return
        source.enabled = false
        database.bookSourceDao.update(source)
    }

    suspend fun changeSource(currentBookUrl: String, book: Book, toc: List<BookChapter>) {
        // 主仓库 migrateTo 不带 defaultReplaceEnabled/chineseConverterType 参数，
        // 两者由 getUseReplaceRule()/getDisplayTitle() 内部读 AppConfig 同源设置。
        database.bookDao.getBook(currentBookUrl)?.migrateTo(
            newBook = book,
            toc = toc,
        )
        book.removeType(BookType.updateError)
        database.bookDao.getBook(currentBookUrl)?.delete()
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
        postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
    }

    suspend fun removeTemporaryBook(bookUrl: String) {
        database.bookDao.getBook(bookUrl)?.delete()
    }

    suspend fun addCurrentBookToShelf(bookUrl: String) {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        persistOnShelf(book, database.bookChapterDao.getChapterList(book.bookUrl))
    }

    suspend fun addToShelf(book: Book, toc: List<BookChapter>) = persistOnShelf(book, toc)

    private suspend fun persistOnShelf(book: Book, toc: List<BookChapter>) {
        book.removeType(BookType.notShelf)
        if (book.order == 0) book.order = database.bookDao.minOrder - 1
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
    }

    suspend fun updateReadConfig(bookUrl: String, update: ReadConfig.() -> Unit) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        book.readConfig = (book.readConfig ?: ReadConfig()).apply(update)
        database.bookDao.update(book)
    }

    suspend fun invalidateChapter(bookUrl: String, chapterIndex: Int) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        database.bookChapterDao.getChapter(bookUrl, chapterIndex)?.let {
            BookHelp.delContent(book, it)
        }
    }

    /**
     * 长按保存图片到相册。
     * 与漫画页请求一致：带上源站信息（Referer）与所属书籍，热链保护的图源才能抓到/解密。
     */
    suspend fun saveImage(
        url: String,
        bookUrl: String,
        sourceOrigin: String?,
        folderName: String = "Legado",
    ): Boolean = withContext(Dispatchers.IO) {
        val result = imageLoader.execute(
            ImageRequest.Builder(application)
                .data(url)
                .allowHardware(false)
                .apply {
                    extras[LegadoFetcher.mangaKey] = true
                    extras[LegadoFetcher.mangaBookUrlKey] = bookUrl
                    sourceOrigin?.let { extras[LegadoFetcher.sourceOriginKey] = it }
                }
                .build()
        )
        val bitmap = result.image?.toBitmap()
            ?: run {
                AppLog.put("漫画图片保存加载失败\n$url\n$result")
                throw NoStackTraceException(application.getString(R.string.manga_reader_save_failed))
            }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            output.toByteArray()
        }
        ImageSaveUtils.saveImageToGallery(application, bytes, folderName = folderName)
    }

    /**
     * 单页/双页合成图：加载 [urls] 对应图片，多张时横向拼接成一张 JPEG 缓存文件。
     * 分享/复制走该文件（FileProvider），保存走 [saveImages]。
     */
    suspend fun prepareImageFile(
        urls: List<String>,
        bookUrl: String,
        sourceOrigin: String?,
    ): File {
        require(urls.isNotEmpty())
        val bitmaps = urls.map { loadBitmap(it, bookUrl, sourceOrigin) }
        val bitmap = if (bitmaps.size == 1) bitmaps.single() else combineHorizontally(bitmaps)
        val directory = File(application.cacheDir, "manga-actions").apply { mkdirs() }
        return File(directory, "manga-${System.currentTimeMillis()}.jpg").also { file ->
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        }
    }

    /** 保存单页/双页合成图到相册 */
    suspend fun saveImages(urls: List<String>, bookUrl: String, sourceOrigin: String?): Boolean {
        val file = prepareImageFile(urls, bookUrl, sourceOrigin)
        return ImageSaveUtils.saveImageToGallery(
            application,
            file.readBytes(),
            folderName = "Legado"
        )
    }

    /** 把当前页写为书籍封面（下载到本地封面路径并更新 customCoverUrl） */
    suspend fun setBookCover(bookUrl: String, imageUrl: String) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        val sourceFile = prepareImageFile(listOf(imageUrl), book.bookUrl, book.origin)
        val coverFile = File(LocalBook.getCoverPath(book)).apply { parentFile?.mkdirs() }
        sourceFile.copyTo(coverFile, overwrite = true)
        book.customCoverUrl = coverFile.toURI().toString()
        database.bookDao.update(book)
    }

    private suspend fun loadBitmap(url: String, bookUrl: String, sourceOrigin: String?): Bitmap {
        val result = imageLoader.execute(
            ImageRequest.Builder(application)
                .data(url)
                .allowHardware(false)
                .apply {
                    extras[LegadoFetcher.mangaKey] = true
                    extras[LegadoFetcher.mangaBookUrlKey] = bookUrl
                    sourceOrigin?.let { extras[LegadoFetcher.sourceOriginKey] = it }
                }
                .build()
        )
        return result.image?.toBitmap()
            ?: throw NoStackTraceException(application.getString(R.string.manga_reader_save_failed))
    }

    private fun combineHorizontally(bitmaps: List<Bitmap>): Bitmap {
        val height = bitmaps.maxOf(Bitmap::getHeight)
        val widths = bitmaps.map { it.width * height / it.height }
        return Bitmap.createBitmap(widths.sum(), height, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = android.graphics.Canvas(output)
            var left = 0f
            bitmaps.forEachIndexed { index, bitmap ->
                val width = widths[index]
                canvas.drawBitmap(
                    bitmap,
                    null,
                    android.graphics.RectF(left, 0f, left + width, height.toFloat()),
                    null
                )
                left += width
            }
        }
    }

    /**
     * 离线缓存章节区间：走主仓库 CacheBookService 下载管线（内容+图片落盘），
     * 本地书没有书源管线，直接忽略。
     */
    suspend fun cacheChapters(bookUrl: String, start: Int, end: Int) {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        if (book.isLocal) return
        if (end < start) return
        CacheBook.start(application, book, start, end)
    }
}
