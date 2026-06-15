package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.asImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.target
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.help.CacheManager
import io.legado.app.help.DefaultData
import io.legado.app.help.coil.BlurTransformation
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.currentCoroutineContext
import splitties.init.appCtx

@Keep
@Suppress("ConstPropertyName")
object BookCover {

    private const val coverRuleConfigKey = "legadoCoverRuleConfig"
    const val configFileName = "coverRule.json"

    var drawBookName = true
        private set
    var drawBookAuthor = true
        private set
    lateinit var defaultDrawable: Drawable
        private set


    init {
        try {
            upDefaultCover()
        } catch (_: Throwable) {
            // Preview / test environment without Application context
            defaultDrawable = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor("#263238")
            )
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun upDefaultCover() {
        val isNightTheme = AppConfig.isNightTheme
        drawBookName = if (isNightTheme) {
            appCtx.getPrefBoolean(PreferKey.coverShowNameN, true)
        } else {
            appCtx.getPrefBoolean(PreferKey.coverShowName, true)
        }
        drawBookAuthor = if (isNightTheme) {
            appCtx.getPrefBoolean(PreferKey.coverShowAuthorN, true)
        } else {
            appCtx.getPrefBoolean(PreferKey.coverShowAuthor, true)
        }
        val key = if (isNightTheme) PreferKey.defaultCoverDark else PreferKey.defaultCover
        val path = appCtx.getPrefString(key)
        if (path.isNullOrBlank()) {
            defaultDrawable = appCtx.resources.getDrawable(R.drawable.image_cover_default, null)
            return
        }
        defaultDrawable = kotlin.runCatching {
            BitmapDrawable(appCtx.resources, BitmapUtils.decodeBitmap(path, 600, 900))
        }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
    }

    /** Safe access to AppConfig.useDefaultCover (Preview-compatible) */
    internal fun useDefaultCover(): Boolean = try {
        AppConfig.useDefaultCover
    } catch (_: Throwable) {
        false
    }

    /**
     * 构建封面加载 ImageRequest（Coil 版）
     */
    fun loadRequest(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): ImageRequest {
        if (useDefaultCover() || path == "use_default_cover") {
            return ImageRequest.Builder(context)
                .data(defaultDrawable)
                .build()
        }
        return ImageRequest.Builder(context)
            .data(path)
            .apply {
                extras[LegadoFetcher.loadOnlyWifiKey] = loadOnlyWifi
                if (sourceOrigin != null) {
                    extras[LegadoFetcher.sourceOriginKey] = sourceOrigin
                }
            }
            .placeholder(defaultDrawable.asImage())
            .error(defaultDrawable.asImage())
            .crossfade(true)
            .build()
    }

    /**
     * 构建模糊封面 ImageRequest（Coil 版）
     */
    fun loadBlurRequest(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): ImageRequest {
        // 启用默认封面 或 无封面路径时，使用默认封面并模糊
        if (useDefaultCover() || path.isNullOrBlank()) {
            return ImageRequest.Builder(context)
                .data(defaultDrawable)
                .transformations(BlurTransformation(25))
                .crossfade(1500)
                .build()
        }
        return ImageRequest.Builder(context)
            .data(path)
            .apply {
                extras[LegadoFetcher.loadOnlyWifiKey] = loadOnlyWifi
                if (sourceOrigin != null) {
                    extras[LegadoFetcher.sourceOriginKey] = sourceOrigin
                }
            }
            .error(defaultDrawable.asImage())
            .transformations(BlurTransformation(25))
            .crossfade(1500)
            .build()
    }

    /**
     * 构建漫画图片加载 ImageRequest（Coil 版）
     */
    fun loadMangaRequest(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(path)
            .apply {
                extras[LegadoFetcher.loadOnlyWifiKey] = loadOnlyWifi
                extras[LegadoFetcher.mangaKey] = true
                if (sourceOrigin != null) {
                    extras[LegadoFetcher.sourceOriginKey] = sourceOrigin
                }
            }
            .size(Size(Dimension(context.resources.displayMetrics.widthPixels), Dimension.Undefined))
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    /**
     * 同步获取封面位图（用于通知栏等场景）
     */
    suspend fun executeCoverBitmap(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): Bitmap? {
        val request = loadRequest(context, path, loadOnlyWifi, sourceOrigin)
        val result = SingletonImageLoader.get(context).execute(request)
        return result.image?.toBitmap()
    }

    fun getCoverRule(): CoverRule {
        return getConfig() ?: DefaultData.coverRule
    }

    fun getConfig(): CoverRule? {
        return GSON.fromJsonObject<CoverRule>(CacheManager.get(coverRuleConfigKey))
            .getOrNull()
    }

    suspend fun searchCover(book: Book): String? {
        val config = getCoverRule()
        if (!config.enable || config.searchUrl.isBlank() || config.coverRule.isBlank()) {
            return null
        }
        val analyzeUrl = AnalyzeUrl(
            config.searchUrl,
            book.name,
            source = config,
            coroutineContext = currentCoroutineContext(),
            hasLoginHeader = false
        )
        val res = analyzeUrl.getStrResponseAwait()
        val analyzeRule = AnalyzeRule(book)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setContent(res.body)
        analyzeRule.setRedirectUrl(res.url)
        return analyzeRule.getString(config.coverRule, isUrl = true)
    }

    fun saveCoverRule(config: CoverRule) {
        val json = GSON.toJson(config)
        saveCoverRule(json)
    }

    fun saveCoverRule(json: String) {
        CacheManager.put(coverRuleConfigKey, json)
    }

    fun delCoverRule() {
        CacheManager.delete(coverRuleConfigKey)
    }

    @Keep
    data class CoverRule(
        var enable: Boolean = true,
        var searchUrl: String,
        var coverRule: String,
        override var concurrentRate: String? = null,
        override var loginUrl: String? = null,
        override var loginUi: String? = null,
        override var header: String? = null,
        override var jsLib: String? = null,
        override var enabledCookieJar: Boolean? = false,
    ) : BaseSource {

        override fun getTag(): String {
            return searchUrl
        }

        override fun getKey(): String {
            return searchUrl
        }
    }

}