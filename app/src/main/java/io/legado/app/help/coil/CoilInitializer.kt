package io.legado.app.help.coil

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.util.DebugLogger
import io.legado.app.BuildConfig
import okio.Path.Companion.toOkioPath
import splitties.init.appCtx
import java.io.File

/**
 * Coil 3 全局 ImageLoader 配置。
 *
 * 替代 LegadoGlideModule，在 App.onCreate 中通过 CoilInitializer.init() 初始化。
 */
object CoilInitializer : SingletonImageLoader.Factory {

    private const val DISK_CACHE_DIR = "coil_image_cache"
    private const val DISK_CACHE_SIZE = 1024L * 1024 * 1024 // 1GB

    fun init() {
        SingletonImageLoader.setSafe(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // OkHttp 网络层（复用项目已有的 OkHttp 客户端）
                add(LegadoFetcher.Factory())
                // SVG decoder 通过 service-loader 自动注册
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appCtx.cacheDir, DISK_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(DISK_CACHE_SIZE)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
//            .apply {
//                if (!BuildConfig.DEBUG) {
//                    // Release 模式关闭日志
//                } else {
//                    logger(DebugLogger())
//                }
//            }
            .build()
    }
}
