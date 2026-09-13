package io.legado.app.ui.ai.chat

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * HTML App 全屏播放器：独立沙箱 WebView，只注入 [HtmlAppBridge]（GameBridge）。
 *
 * - 经 `file://` 加载 index.html（源码文件是唯一真相；AI 反复改写后下次打开即新版本）。
 * - 网络：只放行图片和字体（按后缀白名单），其余 http/https 阻断。
 * - 文件访问收窄到本 app 目录内：拦截 file:// 越界读取。
 * - 无 app 能力：不注入 LegadoBridge。
 * - 下行通道：AI 回复里的 `__game_update__` 经 [gameUpdateState] 变化 → `window.updateGameState(json)`。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlAppPlayerScreen(
    entryUrl: String,
    conversationId: String,
    title: String,
    viewModel: AiChatViewModel,
    gameUpdateState: String?,
    onExit: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember(conversationId, viewModel) {
        viewModel.createHtmlAppBridge(onExit = onExit)
    }
    // 只允许 WebView 访问本 app 目录内的文件。
    val allowedDir = remember(entryUrl) {
        runCatching { Uri.parse(entryUrl).path }.getOrNull()
            ?.let { path -> File(path).parentFile?.absolutePath }
    }

    BackHandler { onExit() }

    // 卸载 WebView，避免残留 JS interface。
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("GameBridge")
                stopLoading()
            }
            webViewRef = null
        }
    }

    // 下行通道：AI 的 __game_update__ 推给运行中的游戏。
    LaunchedEffect(gameUpdateState) {
        val json = gameUpdateState ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript("window.updateGameState && window.updateGameState($json);", null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            // 游戏本体
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // file:// 加载需要；access-from-file 仍关闭（页面不能 XHR 其它 file://）。
                            allowFileAccess = true
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                        }
                        webViewClient = object : WebViewClient() {
                            private val blockedResponse = WebResourceResponse(
                                "text/plain",
                                StandardCharsets.UTF_8.name(),
                                ByteArrayInputStream(ByteArray(0)),
                            )

                            // 拦截网络；放开图片和字体（CDN / Google Fonts 等），其余阻断。
                            // file:// 只放行本 app 目录内。
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                return when (url.scheme) {
                                    "http", "https" -> {
                                        if (isAllowedMediaUrl(url)) null
                                        else blockedResponse
                                    }
                                    "file" -> {
                                        val path = url.path ?: return blockedResponse
                                        if (allowedDir != null && path.startsWith(allowedDir)) null
                                        else blockedResponse
                                    }
                                    else -> null
                                }
                            }

                            // 禁止导航离开 app 目录 / 联网。
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url ?: return false
                                return when (url.scheme) {
                                    "http", "https" -> true
                                    "file" -> {
                                        val path = url.path.orEmpty()
                                        allowedDir == null || !path.startsWith(allowedDir)
                                    }
                                    else -> false
                                }
                            }
                        }
                        addJavascriptInterface(bridge, "GameBridge")
                        // 同步实际视口尺寸（CSS px）给 GameBridge.getViewport()；横竖屏切换时更新。
                        val density = ctx.resources.displayMetrics.density
                        fun pushViewport() {
                            if (width > 0 && height > 0) {
                                bridge.updateViewport((width / density).toInt(), (height / density).toInt())
                            }
                        }
                        addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                            if (r - l > 0 && b - t > 0) pushViewport()
                        }
                        // 页面加载完成后补推一次，确保游戏启动即可查询到视口。
                        postDelayed({ pushViewport() }, 100)
                        loadUrl(entryUrl)
                        webViewRef = this
                    }
                },
            )
        }
    }
}

/** 放行图片和字体资源（按 URL 后缀或 Accept 头）。 */
private fun isAllowedMediaUrl(uri: android.net.Uri): Boolean {
    val path = uri.lastPathSegment?.lowercase().orEmpty()
    // 后缀白名单
    if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
        path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") ||
        path.endsWith(".ico") || path.endsWith(".bmp") ||
        path.endsWith(".woff") || path.endsWith(".woff2") ||
        path.endsWith(".ttf") || path.endsWith(".otf")
    ) {
        return true
    }
    // 含 data/image 参数（如 Google Fonts 的加权子集 URL）
    if (path.contains("image", ignoreCase = true) || path.contains("font", ignoreCase = true)) {
        return true
    }
    return false
}
