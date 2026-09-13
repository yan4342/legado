package io.legado.app.help.book

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.ImageUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.exists
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("unused", "ConstPropertyName")
object BookHelp {
    private val downloadDir: File = appCtx.externalFiles
    private const val cacheFolderName = "book_cache"
    private const val cacheImageFolderName = "images"
    private const val cacheEpubFolderName = "epub"
    private val downloadImages = ConcurrentHashMap<String, Mutex>()

    const val reChapterKey = "reChapter"

    // LRU cache for merged chapter content: key = "bookUrl/chapterUrl", value = concatenated content
    private const val MAX_CACHED_MERGED_CHAPTERS = 8
    private val mergedContentCache = object : LinkedHashMap<String, String>(
        16, 0.75f, true  // access-order for LRU eviction
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHED_MERGED_CHAPTERS
        }
    }

    @Synchronized
    private fun getCachedMergedContent(key: String): String? = mergedContentCache[key]

    @Synchronized
    private fun putCachedMergedContent(key: String, content: String) {
        mergedContentCache[key] = content
    }

    @Synchronized
    private fun removeCachedMergedContent(key: String) {
        mergedContentCache.remove(key)
    }

    @Synchronized
    fun invalidateMergedContentCache(book: Book, chapter: BookChapter) {
        mergedContentCache.remove("${book.bookUrl}/${chapter.url}")
    }

    /** 重分/换缓存后失效某本书的所有合并章缓存 */
    @Synchronized
    fun clearMergedContentCache(book: Book) {
        val prefix = "${book.bookUrl}/"
        val iterator = mergedContentCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.startsWith(prefix)) {
                iterator.remove()
            }
        }
    }

    val cachePath = FileUtils.getPath(downloadDir, cacheFolderName)

    fun clearCache() {
        synchronized(mergedContentCache) { mergedContentCache.clear() }
        FileUtils.delete(
            FileUtils.getPath(downloadDir, cacheFolderName)
        )
    }

    fun clearCache(book: Book) {
        clearMergedContentCache(book)
        val filePath = FileUtils.getPath(downloadDir, cacheFolderName, book.getFolderName())
        FileUtils.delete(filePath)
    }

    fun updateCacheFolder(oldBook: Book, newBook: Book) {
        val oldFolderName = oldBook.getFolderNameNoCache()
        val newFolderName = newBook.getFolderNameNoCache()
        if (oldFolderName == newFolderName) return
        val oldFolderPath = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            oldFolderName
        )
        val newFolderPath = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            newFolderName
        )
        FileUtils.move(oldFolderPath, newFolderPath)
    }

    /**
     * 清除已删除书的缓存 解压缓存
     */
    suspend fun clearInvalidCache() {
        withContext(IO) {
            val bookFolderNames = hashSetOf<String>()
            val originNames = hashSetOf<String>()
            appDb.bookDao.all.forEach {
                clearComicCache(it)
                bookFolderNames.add(it.getFolderName())
                if (it.isEpub) originNames.add(it.originName)
            }
            downloadDir.getFile(cacheFolderName)
                .listFiles()?.forEach { bookFile ->
                    if (!bookFolderNames.contains(bookFile.name)) {
                        FileUtils.delete(bookFile.absolutePath)
                    }
                }
            downloadDir.getFile(cacheEpubFolderName)
                .listFiles()?.forEach { epubFile ->
                    if (!originNames.contains(epubFile.name)) {
                        FileUtils.delete(epubFile.absolutePath)
                    }
                }
            FileUtils.delete(ArchiveUtils.TEMP_PATH)
            val filesDir = appCtx.filesDir
            FileUtils.delete("$filesDir/shareBookSource.json")
            FileUtils.delete("$filesDir/shareRssSource.json")
            FileUtils.delete("$filesDir/books.json")
        }
    }

    //清除已经看过的漫画数据
    private fun clearComicCache(book: Book) {
        //只处理漫画
        //为0的时候，不清除已缓存数据
        if (!book.isImage || AppConfig.imageRetainNum == 0) {
            return
        }
        //向前保留设定数量，向后保留预下载数量
        val startIndex = book.durChapterIndex - AppConfig.imageRetainNum
        val endIndex = book.durChapterIndex + AppConfig.preDownloadNum
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl, startIndex, endIndex)
        val imgNames = hashSetOf<String>()
        //获取需要保留章节的图片信息
        chapterList.forEach {
            val content = getContent(book, it)
            if (content != null) {
                val matcher = AppPattern.imgPattern.matcher(content)
                while (matcher.find()) {
                    val src = matcher.group(1) ?: continue
                    val mSrc = NetworkUtils.getAbsoluteURL(it.url, src)
                    imgNames.add("${MD5Utils.md5Encode16(mSrc)}.${getImageSuffix(mSrc)}")
                }
            }
        }
        downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            cacheImageFolderName
        ).listFiles()?.forEach { imgFile ->
            if (!imgNames.contains(imgFile.name)) {
                imgFile.delete()
            }
        }
    }

    suspend fun saveContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String
    ) {
        try {
            saveText(book, bookChapter, content)
            //saveImages(bookSource, book, bookChapter, content)
            postEvent(EventBus.SAVE_CONTENT, Pair(book, bookChapter))
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("保存正文失败 ${book.name} ${bookChapter.title}", e)
        }
    }

    fun saveText(
        book: Book,
        bookChapter: BookChapter,
        content: String
    ) {
        if (content.isEmpty()) return
        //保存文本
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName(),
        ).writeText(content)
        if (book.isOnLineTxt && AppConfig.tocCountWords) {
            val wordCount = StringUtils.wordCountFormat(content.length)
            bookChapter.wordCount = wordCount
            appDb.bookChapterDao.upWordCount(bookChapter.bookUrl, bookChapter.url, wordCount)
        }
    }

    fun flowImages(bookChapter: BookChapter, content: String): Flow<String> {
        return flow {
            val matcher = AppPattern.imgPattern.matcher(content)
            // 部分书源（如 tuku）的章节 url 是相对路径（/chapter345718/），直接拿它做 base 会
            // MalformedURLException 刷屏且相对图片 src 无法解析；用 getAbsoluteURL() 先按书 url 解析
            val baseUrl = bookChapter.getAbsoluteURL()
            while (matcher.find()) {
                val src = matcher.group(1) ?: continue
                val mSrc = NetworkUtils.getAbsoluteURL(baseUrl, src)
                emit(mSrc)
            }
        }
    }

    /**
     * 用书源 header（防盗链 UA/Referer/Cookie）探测前 limit 张图片是否 HTTP 2xx。
     * data:/blob: 视为天然可用。返回 (src, ok)。
     *
     * 聚合规则为严格（全部 2xx 才通过）：首章第一页 404 即阅读器已坏。
     * 如需宽松（任一 2xx 即可），调用处将 `.all { it.second }` 改为 `.count { it.second } > 0` 一行即可。
     */
    suspend fun probeImageUrls(
        images: List<String>,
        source: BookSource,
        limit: Int = 2,
    ): List<Pair<String, Boolean>> =
        images.take(limit).map { src ->          // 顺序探测（限速+礼貌）
            if (src.startsWith("data:") || src.startsWith("blob:")) {
                src to true
            } else {
                src to runCatching {
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = src,              // 原样传 flowImages 输出；paramPattern 剥离 ,{headers} 后缀并应用逐图 header
                        source = source,
                        baseUrl = source.bookSourceUrl,
                        readTimeout = 15_000L,
                        callTimeout = 30_000L,
                        coroutineContext = coroutineContext,
                    )
                    val response = analyzeUrl.getResponseAwait()
                    try {
                        response.code in 200..299
                    } finally {
                        response.close()
                    }
                }.getOrDefault(false)
            }
        }

    /**
     * 批量缓存章节图片到书目录。
     *
     * @return 失败的图片数量（0 表示全部成功或无需下载）
     */
    suspend fun saveImages(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String,
        concurrency: Int = AppConfig.threadCount,
    ): Int = coroutineScope {
        val imageUrls = flowImages(bookChapter, content).toList()
        val total = imageUrls.size
        if (total == 0) return@coroutineScope 0
        val progressMutex = Mutex()
        var failures = 0
        imageUrls.asFlow().onEachParallel(concurrency) { mSrc ->
            val ok = saveImage(bookSource, book, mSrc, bookChapter)
            progressMutex.withLock {
                if (!ok) failures++
            }
        }.collect()
        failures
    }

    /**
     * @return true 表示图片已存在，或本次下载且校验通过。
     * 数据异常时仍会落盘（避免反复拉坏图），但返回 false 以便调用方记失败。
     */
    suspend fun saveImage(
        bookSource: BookSource?,
        book: Book,
        src: String,
        chapter: BookChapter? = null
    ): Boolean {
        if (isImageExist(book, src)) {
            return true
        }
        val mutex = synchronized(this) {
            downloadImages.getOrPut(src) { Mutex() }
        }
        mutex.lock()
        try {
            if (isImageExist(book, src)) {
                return true
            }
            val analyzeUrl = AnalyzeUrl(
                src, source = bookSource, coroutineContext = coroutineContext
            )
            val bytes = analyzeUrl.getByteArrayAwait()
            if (ImageUtils.skipDecode(bookSource, isCover = false)) {
                writeImage(book, src, bytes)
                return true
            }
            //某些图片被加密，需要进一步解密
            val decoded = runScriptWithContext {
                ImageUtils.decode(
                    src, bytes, isCover = false, bookSource, book
                )
            }
            if (decoded == null) {
                AppLog.put("${book.name} ${chapter?.title} 图片 $src 下载失败 解码为空")
                return false
            }
            // 如果部分图片失效，每次进入正文都会花很长时间再次获取图片数据
            // 所以无论如何都要将数据写入到文件里；但仍记失败，避免章节被标为已缓存
            writeImage(book, src, decoded)
            if (!checkImage(decoded)) {
                AppLog.put("${book.name} ${chapter?.title} 图片 $src 下载错误 数据异常")
                return false
            }
            return true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val msg = "${book.name} ${chapter?.title} 图片 $src 下载失败\n${e.localizedMessage}"
            AppLog.put(msg, e)
            return false
        } finally {
            downloadImages.remove(src)
            mutex.unlock()
        }
    }

    fun getImage(book: Book, src: String): File {
        return downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            cacheImageFolderName,
            "${MD5Utils.md5Encode16(src)}.${getImageSuffix(src)}"
        )
    }

    @Synchronized
    fun writeImage(book: Book, src: String, bytes: ByteArray) {
        getImage(book, src).createFileIfNotExist().writeBytes(bytes)
    }

    @Synchronized
    fun isImageExist(book: Book, src: String): Boolean {
        return getImage(book, src).exists()
    }

    fun getImageSuffix(src: String): String {
        return UrlUtil.getSuffix(src, "jpg")
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun getEpubFile(book: Book): ZipFile {
        val uri = book.getLocalUri()
        if (uri.isContentScheme()) {
            FileUtils.createFolderIfNotExist(downloadDir, cacheEpubFolderName)
            val path = FileUtils.getPath(downloadDir, cacheEpubFolderName, book.originName)
            val file = File(path)
            val doc = DocumentFile.fromSingleUri(appCtx, uri)
                ?: throw IOException("文件不存在")
            if (!file.exists() || doc.lastModified() > book.latestChapterTime) {
                LocalBook.getBookInputStream(book).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            return ZipFile(file)
        }
        return ZipFile(uri.path)
    }

    /**
     * 获取本地书籍文件的ParcelFileDescriptor
     *
     * @param book
     * @return
     */
    @Throws(IOException::class, FileNotFoundException::class)
    fun getBookPFD(book: Book): ParcelFileDescriptor? {
        val uri = book.getLocalUri()
        return if (uri.isContentScheme()) {
            appCtx.contentResolver.openFileDescriptor(uri, "r")
        } else {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    fun getChapterFiles(book: Book): HashSet<String> {
        val fileNames = hashSetOf<String>()
        if (book.isLocalTxt) {
            return fileNames
        }
        FileUtils.createFolderIfNotExist(
            downloadDir,
            subDirs = arrayOf(cacheFolderName, book.getFolderName())
        ).list()?.let {
            fileNames.addAll(it)
        }
        return fileNames
    }

    /**
     * 重新分章(ReChapter)映射数据:一条原子页(子页)缓存记录。
     * @param fileName 原子页缓存文件名(%05d-{index}-{titleMD5}.nb),由原子页行 getFileName() 得到
     * @param url 原子页链接,用于刷新时重新下载
     * @param title 原子页原始标题,关闭分章还原目录时恢复
     * @param stripLines 该原子页正文中需剥离的卷标题行号(0-based,按 \n 切分),卷标题作为独立卷章展示,
     * 避免在合并章正文中重复出现
     */
    @Keep
    data class ReChapterSub(
        val fileName: String,
        val url: String,
        // 可空:旧版本(无 title 字段)持久化的 reChapter JSON 反序列化时字段缺失为 null(GSON 不走 Kotlin 构造默认值)
        val title: String? = null,
        val stripLines: List<Int>? = null,
    )

    /**
     * 解析章节的重新分章映射。映射以字符串数组形式存于 BookChapter.variable["reChapter"],
     * 不嵌套 JSON 对象,避免破坏 chapter.variableMap(HashMap<String,String>) 的解析。
     * 无映射返回 null(走原有单文件逻辑)。经 getVariable 读取以兼容大变量落盘路径。
     */
    fun reChapterSubs(chapter: BookChapter): List<ReChapterSub>? {
        val raw = chapter.getVariable(reChapterKey)
        if (raw.isBlank()) return null
        return GSON.fromJson(raw, Array<ReChapterSub>::class.java)?.toList()
    }

    /**
     * 该章节是否为重新分章合并章
     */
    fun isReChaptered(chapter: BookChapter): Boolean {
        return chapter.getVariable(reChapterKey).isNotBlank()
    }

    /**
     * 把原子页映射写入章节变量
     */
    fun putReChapterSubs(chapter: BookChapter, subs: List<ReChapterSub>) {
        chapter.putVariable(reChapterKey, GSON.toJson(subs))
    }

    /**
     * 重命名章节缓存文件。重新分章重建目录后,窗口内未合并的原子页索引会变化,
     * 需要把缓存文件同步改名,否则 getContent/hasContent 按新文件名找不到旧缓存。
     */
    fun renameContentFile(book: Book, chapter: BookChapter, oldIndex: Int, newIndex: Int) {
        if (oldIndex == newIndex) return
        val md5Part = chapter.getFileName().substringAfter('-')
        val oldFileName = String.format("%05d-%s", oldIndex, md5Part)
        val oldFile = downloadDir.getFile(cacheFolderName, book.getFolderName(), oldFileName)
        if (oldFile.exists()) {
            val newFile = downloadDir.getFile(
                cacheFolderName, book.getFolderName(), chapter.getFileName()
            )
            oldFile.renameTo(newFile)
        }
    }

    /**
     * 按固定文件名写入正文缓存(用于重新分章合并章按映射落盘原子页)
     */
    fun saveTextToFile(book: Book, fileName: String, content: String) {
        if (content.isEmpty()) return
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            fileName,
        ).writeText(content)
    }

    /**
     * 读取重新分章合并章内容:按映射逐个读取原子页缓存文件并拼接。
     * 任一原子页缺失返回 null,由调用方触发重下。
     */
    private fun getMappedContent(book: Book, subs: List<ReChapterSub>): String? {
        val firstSub = subs.firstOrNull() ?: return null
        val cacheKey = "${book.bookUrl}/${firstSub.url}"
        getCachedMergedContent(cacheKey)?.let {
            AppLog.putDebug("getMappedContent 命中缓存 ${subs.size} 个子页")
            return it
        }
        val contents = arrayListOf<String>()
        for (sub in subs) {
            val file = downloadDir.getFile(cacheFolderName, book.getFolderName(), sub.fileName)
            if (!file.exists()) {
                removeCachedMergedContent(cacheKey)
                return null
            }
            val text = file.readText()
            if (text.isEmpty()) {
                removeCachedMergedContent(cacheKey)
                return null
            }
            val stripped = stripSubLines(text, sub.stripLines)
            if (stripped.isNotEmpty()) {
                contents.add(stripped)
            }
        }
        if (contents.isEmpty()) {
            removeCachedMergedContent(cacheKey)
            return null
        }
        val result = contents.joinToString("\n")
        putCachedMergedContent(cacheKey, result)
        AppLog.putDebug(
            "getMappedContent 拼接 ${subs.size} 个子页 " +
                "剥离行=${subs.map { it.stripLines?.size ?: 0 }}"
        )
        return result
    }

    /**
     * 剥离原子页正文中标记为卷标题的行(重新分章后卷标题作为独立卷章展示)。
     * 未标记剥离行时原样返回。
     */
    private fun stripSubLines(text: String, stripLines: List<Int>?): String {
        if (stripLines.isNullOrEmpty()) return text
        val lines = text.lines()
        if (stripLines.all { it >= lines.size }) return text
        val kept = lines.filterIndexed { index, _ -> index !in stripLines }
        AppLog.putDebug("剥离原子页正文卷标题行 $stripLines,共${lines.size}行 -> ${kept.size}行")
        return kept.joinToString("\n")
    }

    /**
     * 按指定缓存文件名读取正文内容。
     * 重新分章重置时,展开的原子页 title 无法还原,缓存读取依赖原始文件名。
     */
    fun getContentByFileName(book: Book, fileName: String): String? {
        val file = downloadDir.getFile(cacheFolderName, book.getFolderName(), fileName)
        if (file.exists()) {
            val text = file.readText()
            return text.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * 重新分章合并章缺失原子页时,按 sub.url 逐个重新下载并写回原原子页缓存文件,然后拼接返回。
     */
    suspend fun downloadMappedContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
    ): String? {
        val subs = reChapterSubs(bookChapter) ?: return getContent(book, bookChapter)
        val contents = arrayListOf<String>()
        for (sub in subs) {
            val file = downloadDir.getFile(cacheFolderName, book.getFolderName(), sub.fileName)
            if (!file.exists()) {
                val tempChapter = BookChapter(
                    url = sub.url,
                    baseUrl = book.tocUrl,
                    bookUrl = book.bookUrl,
                    index = sub.fileName.substringBefore("-").toIntOrNull() ?: 0,
                )
                val content = WebBook.getContentAwait(
                    bookSource, book, tempChapter, needSave = false
                )
                if (content.isEmpty() || content.startsWith("获取正文失败")) return null
                saveTextToFile(book, sub.fileName, content)
                val stripped = stripSubLines(content, sub.stripLines)
                if (stripped.isNotEmpty()) {
                    contents.add(stripped)
                }
            } else {
                val text = file.readText()
                if (text.isEmpty()) return null
                val stripped = stripSubLines(text, sub.stripLines)
                if (stripped.isNotEmpty()) {
                    contents.add(stripped)
                }
            }
        }
        if (contents.isEmpty()) return null
        val result = contents.joinToString("\n")
        putCachedMergedContent("${book.bookUrl}/${bookChapter.url}", result)
        return result
    }

    /**
     * 检测该章节是否下载
     */
    fun hasContent(book: Book, bookChapter: BookChapter): Boolean {
        reChapterSubs(bookChapter)?.let { subs ->
            return subs.all { sub ->
                downloadDir.exists(cacheFolderName, book.getFolderName(), sub.fileName)
            }
        }
        return if (book.isLocalTxt ||
            (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title))
        ) {
            true
        } else {
            downloadDir.exists(
                cacheFolderName,
                book.getFolderName(),
                bookChapter.getFileName()
            )
        }
    }

    /**
     * UI/队列用：仅检查图片文件是否都存在，不做 bitmap 解码校验。
     * src 按 flowImages 同款规则解析为绝对 URL（tuku 等相对路径书源才能命中落盘 key）。
     */
    fun hasImageFilesCached(book: Book, bookChapter: BookChapter): Boolean {
        if (!hasContent(book, bookChapter)) {
            return false
        }
        var ret = true
        val baseUrl = bookChapter.getAbsoluteURL()
        getContent(book, bookChapter)?.let {
            val matcher = AppPattern.imgPattern.matcher(it)
            while (matcher.find()) {
                val src = matcher.group(1) ?: continue
                if (!isImageExist(book, NetworkUtils.getAbsoluteURL(baseUrl, src))) {
                    ret = false
                    break
                }
            }
        }
        return ret
    }

    /**
     * 一次图片缓存批次是否可视为章节图片缓存完成。
     */
    fun isChapterImageCacheComplete(book: Book, bookChapter: BookChapter, failures: Int): Boolean {
        return failures == 0 && hasImageFilesCached(book, bookChapter)
    }

    /**
     * 检测图片是否下载（含 bitmap 解码校验，用于实际下载决策）
     */
    fun hasImageContent(book: Book, bookChapter: BookChapter): Boolean {
        if (!hasContent(book, bookChapter)) {
            return false
        }
        var ret = true
        val op = BitmapFactory.Options()
        op.inJustDecodeBounds = true
        val baseUrl = bookChapter.getAbsoluteURL()
        getContent(book, bookChapter)?.let {
            val matcher = AppPattern.imgPattern.matcher(it)
            while (matcher.find()) {
                val src = matcher.group(1)!!
                val image = getImage(book, NetworkUtils.getAbsoluteURL(baseUrl, src))
                if (!image.exists()) {
                    ret = false
                    continue
                }
                BitmapFactory.decodeFile(image.absolutePath, op)
                if (op.outWidth < 1 && op.outHeight < 1) {
                    if (SvgUtils.getSize(image.absolutePath) != null) {
                        continue
                    }
                    ret = false
                    image.delete()
                }
            }
        }
        return ret
    }

    private fun checkImage(bytes: ByteArray): Boolean {
        val op = BitmapFactory.Options()
        op.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            return SvgUtils.getSize(ByteArrayInputStream(bytes)) != null
        }
        return true
    }

    /**
     * 读取章节内容
     */
    fun getContent(book: Book, bookChapter: BookChapter): String? {
        reChapterSubs(bookChapter)?.let { subs ->
            return getMappedContent(book, subs)
        }
        // 卷章(卷首页):正文即卷首内容(tag,封面章名+文案),没有正文缓存文件
        if (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title) &&
            !bookChapter.tag.isNullOrBlank()
        ) {
            return bookChapter.tag
        }
        val file = downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
        if (file.exists()) {
            val string = file.readText()
            if (string.isEmpty()) {
                return null
            }
            return string
        }
        if (book.isLocal) {
            val string = LocalBook.getContent(book, bookChapter)
            if (string != null && book.isEpub) {
                saveText(book, bookChapter, string)
            }
            return string
        }
        return null
    }

    /**
     * 删除章节内容
     */
    fun delContent(book: Book, bookChapter: BookChapter) {
        reChapterSubs(bookChapter)?.let { subs ->
            invalidateMergedContentCache(book, bookChapter)
            subs.forEach { sub ->
                FileUtils.createFileIfNotExist(
                    downloadDir,
                    cacheFolderName,
                    book.getFolderName(),
                    sub.fileName
                ).delete()
            }
            return
        }
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        ).delete()
    }

    /**
     * 设置是否禁用正文的去除重复标题,针对单个章节
     */
    fun setRemoveSameTitle(book: Book, bookChapter: BookChapter, removeSameTitle: Boolean) {
        val fileName = bookChapter.getFileName("nr")
        val contentProcessor = ContentProcessor.get(book)
        if (removeSameTitle) {
            val path = FileUtils.getPath(
                downloadDir,
                cacheFolderName,
                book.getFolderName(),
                fileName
            )
            contentProcessor.removeSameTitleCache.remove(fileName)
            File(path).delete()
        } else {
            FileUtils.createFileIfNotExist(
                downloadDir,
                cacheFolderName,
                book.getFolderName(),
                fileName
            )
            contentProcessor.removeSameTitleCache.add(fileName)
        }
    }

    /**
     * 获取是否去除重复标题
     */
    fun removeSameTitle(book: Book, bookChapter: BookChapter): Boolean {
        val path = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName("nr")
        )
        return !File(path).exists()
    }

    /**
     * 格式化书名
     */
    fun formatBookName(name: String): String {
        return name
            .replace(AppPattern.nameRegex, "")
            .trim { it <= ' ' }
    }

    /**
     * 格式化作者
     */
    fun formatBookAuthor(author: String): String {
        return author
            .replace(AppPattern.authorRegex, "")
            .trim { it <= ' ' }
    }

    private fun jaccardSimilarity(s1: String, s2: String): Double {
        val set1 = s1.toSet()
        val set2 = s2.toSet()
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    /**
     * 根据目录名获取当前章节
     */
    fun getDurChapter(
        oldDurChapterIndex: Int,
        oldDurChapterName: String?,
        newChapterList: List<BookChapter>,
        oldChapterListSize: Int = 0
    ): Int {
        if (oldDurChapterIndex <= 0) return 0
        if (newChapterList.isEmpty()) return oldDurChapterIndex
        val oldChapterNum = getChapterNum(oldDurChapterName)
        val oldName = getPureChapterName(oldDurChapterName)
        val newChapterSize = newChapterList.size
        val durIndex =
            if (oldChapterListSize == 0) oldDurChapterIndex
            else oldDurChapterIndex * oldChapterListSize / newChapterSize
        val min = max(0, min(oldDurChapterIndex, durIndex) - 10)
        val max = min(newChapterSize - 1, max(oldDurChapterIndex, durIndex) + 10)
        var nameSim = 0.0
        var newIndex = 0
        var newNum = 0
        if (oldName.isNotEmpty()) {
            for (i in min..max) {
                val newName = getPureChapterName(newChapterList[i].title)
                val temp = jaccardSimilarity(oldName, newName)
                if (temp > nameSim) {
                    nameSim = temp
                    newIndex = i
                }
            }
        }
        if (nameSim < 0.96 && oldChapterNum > 0) {
            for (i in min..max) {
                val temp = getChapterNum(newChapterList[i].title)
                if (temp == oldChapterNum) {
                    newNum = temp
                    newIndex = i
                    break
                } else if (abs(temp - oldChapterNum) < abs(newNum - oldChapterNum)) {
                    newNum = temp
                    newIndex = i
                }
            }
        }
        return if (nameSim > 0.96 || abs(newNum - oldChapterNum) < 1) {
            newIndex
        } else {
            min(max(0, newChapterList.size - 1), oldDurChapterIndex)
        }
    }

    fun getDurChapter(
        oldBook: Book,
        newChapterList: List<BookChapter>
    ): Int {
        return oldBook.run {
            getDurChapter(durChapterIndex, durChapterTitle, newChapterList, totalChapterNum)
        }
    }

    private val chapterNamePattern1 by lazy {
        Pattern.compile(
            ".*?第([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话]"
        )
    }

    @Suppress("RegExpSimplifiable")
    private val chapterNamePattern2 by lazy {
        Pattern.compile(
            "^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、]|\\.[^\\d])"
        )
    }

    private val regexA by lazy {
        return@lazy "\\s".toRegex()
    }

    private fun getChapterNum(chapterName: String?): Int {
        chapterName ?: return -1
        val chapterName1 = StringUtils.fullToHalf(chapterName).replace(regexA, "")
        return StringUtils.stringToInt(
            (
                    chapterNamePattern1.matcher(chapterName1).takeIf { it.find() }
                        ?: chapterNamePattern2.matcher(chapterName1).takeIf { it.find() }
                    )?.group(1)
                ?: "-1"
        )
    }

    private val regexOther by lazy {
        // 所有非字母数字中日韩文字 CJK区+扩展A-F区
        @Suppress("RegExpDuplicateCharacterInClass")
        return@lazy "[^\\w\\u4E00-\\u9FEF〇\\u3400-\\u4DBF\\u20000-\\u2A6DF\\u2A700-\\u2EBEF]".toRegex()
    }

    @Suppress("RegExpUnnecessaryNonCapturingGroup", "RegExpSimplifiable")
    private val regexB by lazy {
        //章节序号，排除处于结尾的状况，避免将章节名替换为空字串
        return@lazy "^.*?第(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话](?!$)|^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、](?!$)|\\.(?=[^\\d]))".toRegex()
    }

    private val regexC by lazy {
        //前后附加内容，整个章节名都在括号中时只剔除首尾括号，避免将章节名替换为空字串
        return@lazy "(?!^)(?:[〖【《〔\\[{(][^〖【《〔\\[{()〕》】〗\\]}]+)?[)〕》】〗\\]}]$|^[〖【《〔\\[{(](?:[^〖【《〔\\[{()〕》】〗\\]}]+[〕》】〗\\]})])?(?!$)".toRegex()
    }

    private fun getPureChapterName(chapterName: String?): String {
        return if (chapterName == null) "" else StringUtils.fullToHalf(chapterName)
            .replace(regexA, "")
            .replace(regexB, "")
            .replace(regexC, "")
            .replace(regexOther, "")
    }

}
