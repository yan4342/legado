package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import coil3.asImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.fragment.app.DialogFragment
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/**
 * 显示图片（Compose 渲染，telephoto 可缩放）。
 * 阅读中已缓存的图片优先取 [ImageProvider]，其余按文件/网络加载；返回键关闭。
 */
class PhotoDialog() : DialogFragment() {

    constructor(src: String, sourceOrigin: String? = null) : this() {
        arguments = Bundle().apply {
            putString("src", src)
            putString("sourceOrigin", sourceOrigin)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val src = arguments?.getString("src")
        if (src == null) {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        val sourceOrigin = arguments?.getString("sourceOrigin")
        val cachedBitmap = ImageProvider.get(src)
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    PhotoDialogContent(
                        src = src,
                        sourceOrigin = sourceOrigin,
                        cachedBitmap = cachedBitmap,
                    )
                }
            }
        }
    }

}

@Composable
private fun PhotoDialogContent(
    src: String,
    sourceOrigin: String?,
    cachedBitmap: Bitmap?,
) {
    val context = LocalContext.current
    // E-Ink 模式避免整屏黑底，用主题弹窗底色兜底
    val scrimColor = if (AppConfig.isEInkMode) legadoPopupBackgroundColor() else Black.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor),
        contentAlignment = Alignment.Center,
    ) {
        val model = if (cachedBitmap != null) {
            cachedBitmap
        } else {
            rememberPhotoRequest(context, src, sourceOrigin)
        }
        ZoomableAsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun rememberPhotoRequest(
    context: android.content.Context,
    src: String,
    sourceOrigin: String?,
): Any {
    return remember(src, sourceOrigin) {
        val file = ReadBook.book?.let { book ->
            BookHelp.getImage(book, src)
        }
        if (file?.exists() == true) {
            ImageRequest.Builder(context)
                .data(file)
                .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY).asImage())
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(src)
                .apply {
                    sourceOrigin?.let { origin ->
                        extras[LegadoFetcher.sourceOriginKey] = origin
                    }
                }
                .error(BookCover.defaultDrawable.asImage())
                .build()
        }
    }
}
