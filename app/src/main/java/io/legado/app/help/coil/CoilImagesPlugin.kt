package io.legado.app.help.coil

import android.content.Context
import android.graphics.drawable.Drawable
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.Disposable
import coil3.request.ImageRequest
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 Coil 3 的 Markwon 图片加载插件，替代 GlideImagesPlugin。
 *
 * 用法：
 * ```kotlin
 * Markwon.builder(context)
 *     .usePlugin(CoilImagesPlugin.create(context))
 *     .build()
 * ```
 */
object CoilImagesPlugin {

    @JvmStatic
    fun create(context: Context): MarkwonPlugin {
        return CoilImagesPluginImpl(context)
    }

    private class CoilImagesPluginImpl(
        private val context: Context,
    ) : AbstractMarkwonPlugin() {

        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            builder.asyncDrawableLoader(CoilAsyncDrawableLoader(context))
        }
    }
}

/**
 * 基于 Coil 3 的 AsyncDrawableLoader 实现。
 * 负责异步加载 Markdown 中的图片并设置到 AsyncDrawable。
 */
class CoilAsyncDrawableLoader(
    private val context: Context,
) : AsyncDrawableLoader() {

    /** 管理正在加载中的请求，用于取消 */
    private val loadingMap = ConcurrentHashMap<String, Disposable>()

    override fun load(drawable: AsyncDrawable) {
        val url = drawable.destination
        if (url.isNullOrBlank()) return

        val request = ImageRequest.Builder(context)
            .data(url)
            .target(
                onSuccess = { result ->
                    loadingMap.remove(url)
                    drawable.setResult(result.asDrawable(context.resources))
                },
                onError = { _ ->
                    loadingMap.remove(url)
                }
            )
            .build()

        loadingMap[url] = SingletonImageLoader.get(context).enqueue(request)
    }

    override fun cancel(drawable: AsyncDrawable) {
        loadingMap.remove(drawable.destination)?.dispose()
    }

    override fun placeholder(drawable: AsyncDrawable): Drawable? = null
}
