package io.legado.app.help.coil

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.okHttpClientManga
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.ImageUtils
import io.legado.app.utils.isWifiConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.buffer
import splitties.init.appCtx

/**
 * Coil Fetcher，替代 Glide 的 OkHttpModelLoader + OkHttpStreamFetcher。
 *
 * 负责：
 * 1. 通过 AnalyzeUrl 处理 URL（书源 URL 选项、Cookie）
 * 2. 注入自定义请求头
 * 3. WiFi 限制检查
 * 4. 图片解密（ImageUtils.decode）
 * 5. 使用正确的 OkHttp 客户端（普通/漫画）
 */
class LegadoFetcher private constructor(
    private val url: String,
    private val options: Options,
) {

    // Coil Extras keys
    companion object {
        val sourceOriginKey = Extras.Key<String>("")
        val loadOnlyWifiKey = Extras.Key<Boolean>(false)
        val mangaKey = Extras.Key<Boolean>(false)
        /** 漫画图片所属书籍。图片解密/本地优先必须显式携带，不能读取全局阅读会话。 */
        val mangaBookUrlKey = Extras.Key<String>("")
    }

    suspend fun fetch(): FetchResult {
        val loadOnlyWifi = options.extras[loadOnlyWifiKey] ?: false
        if (loadOnlyWifi && !appCtx.isWifiConnect) {
            throw NoStackTraceException("只在wifi加载图片")
        }

        val sourceOrigin = options.extras[sourceOriginKey]
        val isManga = options.extras[mangaKey] ?: false

        var source: BaseSource? = null
        if (sourceOrigin != null) {
            source = SourceHelp.getSource(sourceOrigin)
        }

        // 漫画图片所属书籍：显式从请求携带，不读取全局阅读会话（ReadManga 已删除）
        val mangaBook = options.extras[mangaBookUrlKey]
            ?.takeIf(String::isNotEmpty)
            ?.let { bookUrl -> kotlinx.coroutines.withContext(IO) { appDb.bookDao.getBook(bookUrl) } }

        val coroutineContext = SupervisorJob()

        // 漫画本地优先：书目录已有该图（预下载/长按保存落盘）则纯本地解码显示，不发网络。
        // 请求 url 与 BookHelp.saveImages 的落盘 key 同为 flowImages 输出原文，命中即一致。
        if (isManga) {
            mangaBook?.let { book ->
                if (BookHelp.isImageExist(book, url)) {
                    try {
                        val bytes = kotlinx.coroutines.withContext(IO) {
                            BookHelp.getImage(book, url).readBytes()
                        }
                        val localSource: Buffer = if (!ImageUtils.skipDecode(source, isCover = false)) {
                            val decoded = runScriptWithContext(coroutineContext) {
                                ImageUtils.decode(
                                    url, bytes, isCover = false, source, book
                                )?.inputStream()
                            }
                            if (decoded == null) {
                                throw NoStackTraceException("本地图片解密失败")
                            }
                            Buffer().readFrom(decoded)
                        } else {
                            Buffer().write(bytes)
                        }
                        return SourceFetchResult(
                            source = ImageSource(localSource, FileSystem.SYSTEM),
                            mimeType = "image/*",
                            dataSource = DataSource.DISK,
                        )
                    } catch (e: Exception) {
                        // 本地读取/解码失败：回退网络，不阻塞显示
                        AppLog.put("读取本地缓存图片失败，回退网络: $url\n$e")
                    }
                }
            }
        }

        val analyzedUrl = AnalyzeUrl(
            url,
            source = source,
            coroutineContext = coroutineContext
        )

        val requestBuilder = Request.Builder().url(analyzedUrl.getGlideUrl().toStringUrl())
        analyzedUrl.getGlideUrl().headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        // 打上书源 tag：okHttpClientManga 的并发限速器按源共享
        source?.let { requestBuilder.tag(BaseSource::class.java, it) }
        val request = requestBuilder.build()

        val call = if (isManga) {
            okHttpClientManga.newCall(request)
        } else {
            okHttpClient.newCall(request)
        }

        val response = call.execute()
        if (!response.isSuccessful) {
            // 记录实际发出的 URL 与请求头，便于定位 403/防盗链类失败
            val headerLog = request.headers.toMultimap().entries.joinToString("; ") { (k, v) ->
                "$k=${v.joinToString(",")}"
            }
            AppLog.put(
                "图片加载失败 ${response.code} isManga=$isManga\n" +
                    "URL: ${request.url}\nHeaders: $headerLog"
            )
            throw NoStackTraceException("图片加载失败: ${response.code}")
        }

        val responseBody = response.body ?: throw NoStackTraceException("响应体为空")

        // 图片解密
        val finalSource: Buffer = if (!ImageUtils.skipDecode(source, !isManga)) {
            val decoded = kotlinx.coroutines.withContext(IO) {
                runScriptWithContext(coroutineContext) {
                    if (isManga) {
                        ImageUtils.decode(
                            url,
                            responseBody.bytes(),
                            isCover = false,
                            source,
                            mangaBook
                        )?.inputStream()
                    } else {
                        ImageUtils.decode(
                            analyzedUrl.getGlideUrl().toStringUrl(),
                            responseBody.byteStream(),
                            isCover = true,
                            source
                        )
                    }
                }
            }
            val decodedStream = decoded as? java.io.InputStream
            if (decodedStream == null) {
                throw NoStackTraceException("图片解密失败")
            }
            val decodedBytes = decodedStream.readBytes()
            // 写穿：已成功解码的漫画网络图同步落盘书目录（A 档缓存）。
            // 换源再换回时本地优先直接命中，不被 Coil 磁盘缓存（256MB）淘汰导致重下。
            persistViewedImage(isManga, mangaBook, url, decodedBytes)
            Buffer().write(decodedBytes)
        } else {
            val rawBytes = responseBody.bytes()
            persistViewedImage(isManga, mangaBook, url, rawBytes)
            Buffer().write(rawBytes)
        }

        return SourceFetchResult(
            source = ImageSource(finalSource, FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.NETWORK,
        )
    }

    /**
     * 漫画网络图写穿书目录：与 A 档预下载同 key（BookHelp.saveImages 的落盘规则，
     * 见 [BookHelp.getImage]），显示过的图也进书目录缓存。
     * 写穿失败只记日志，不影响本次显示。
     */
    private fun persistViewedImage(
        isManga: Boolean,
        mangaBook: io.legado.app.data.entities.Book?,
        url: String,
        bytes: ByteArray,
    ) {
        if (!isManga || mangaBook == null) return
        runCatching {
            BookHelp.writeImage(mangaBook, url, bytes)
        }.onFailure { e ->
            AppLog.put("漫画图片写穿书目录失败: $url\n$e")
        }
    }

    class Factory : coil3.fetch.Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): coil3.fetch.Fetcher? {
            // coil3 会把 String 数据经 StringMapper 映射成 coil3.Uri 后才匹配 fetcher，
            // 内置 OkHttpNetworkFetcher 是 <coil3.Uri> 类型（priority 2），若这里类型仍是 <String>
            // 则永远匹配不上，所有网络图片都会走内置 fetcher（不带书源 UA/Referer/Cookie → 防盗链 403）。
            val scheme = data.scheme
            if (scheme != "http" && scheme != "https") return null
            // Uri.toString() 原样返回原始字符串（不重新编码），与直接传 String 完全一致
            return Fetcher { LegadoFetcher(data.toString(), options).fetch() }
        }
    }
}

private fun Fetcher(block: suspend () -> FetchResult): coil3.fetch.Fetcher {
    return object : coil3.fetch.Fetcher {
        override suspend fun fetch(): FetchResult = block()
    }
}
