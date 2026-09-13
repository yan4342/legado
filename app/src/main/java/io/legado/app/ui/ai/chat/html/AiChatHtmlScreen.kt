package io.legado.app.ui.ai.chat.html

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.base.LocalStatusBarTransparentHandle
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.ai.chat.AiChatDialogState
import io.legado.app.ui.ai.chat.AiChatEffect
import io.legado.app.ui.ai.chat.AiChatSheetHost
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.share
import kotlinx.coroutines.flow.collectLatest
import java.nio.charset.StandardCharsets

private const val AI_CHAT_ASSET_URL = AiChatHtmlThemeStore.ASSET_ENTRY_URL
private const val AI_CHAT_ASSET_PREFIX = AiChatHtmlThemeStore.ASSET_PREFIX
private const val AI_CHAT_THEME_CSS_SUFFIX = "/theme.css"
private const val AI_CHAT_APP_JS_SUFFIX = "/app.js"
private const val AI_CHAT_MD_JS_SUFFIX = "/md.js"
private const val AI_CHAT_SHARED_ENHANCE_JS_SUFFIX = "/shared-enhance.js"
private const val SHARED_ENHANCE_SCRIPT_TAG = "<script src=\"shared-enhance.js\"></script>"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiChatHtmlScreen(
    viewModel: AiChatViewModel,
    onBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val themeStore = remember { AiChatHtmlThemeStore(context.applicationContext) }
    val ds = remember { AiChatDialogState() }

    // Make status bar transparent so the WebView content (with its own CSS theme)
    // shows through the system status bar, instead of the Material theme primary color.
    val statusBarHandle = LocalStatusBarTransparentHandle.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLightStatusBar = !AppConfig.isNightTheme
    DisposableEffect(statusBarHandle) {
        statusBarHandle?.push()
        val activity = view.context.findActivity() as? Activity
        activity?.setLightStatusBar(isLightStatusBar)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activity?.setLightStatusBar(isLightStatusBar)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            statusBarHandle?.pop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var themeEpoch by remember { mutableStateOf(0) }
    var filesTouched by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editorSelection by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var shellRevision by remember { mutableStateOf(themeStore.activeShellRevision()) }
    var loadedShellRevision by remember { mutableStateOf<String?>(null) }
    // Reference trackers for the two-push strategy: full applyState is skipped while only
    // the streaming message (or small scalar flags) change, so per-token updates stay cheap.
    var trackedMessages by remember { mutableStateOf(state.messages) }
    var trackedConversations by remember { mutableStateOf(state.conversations) }
    var trackedToolConfs by remember { mutableStateOf(state.pendingToolConfs) }
    var trackedSuggestions by remember { mutableStateOf(state.suggestions) }
    var lastStreamingPayload by remember { mutableStateOf("") }
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnSettings by rememberUpdatedState(onNavigateToAiSettings)
    val latestOnIntent by rememberUpdatedState(viewModel::onIntent)

    val latestState by rememberUpdatedState(state)
    val latestMatchSlash by rememberUpdatedState { partial: String ->
        GSON.toJson(
            viewModel.matchSlashCommands(partial).map { cmd ->
                mapOf(
                    "id" to cmd.id,
                    "primary" to cmd.primary,
                    "insertText" to cmd.insertText,
                    "title" to cmd.title,
                    "description" to cmd.description,
                )
            },
        )
    }

    fun openSheet(sheet: String, id: String? = null) {
        ds.clearSheetTarget()
        when (sheet.trim().lowercase()) {
            "outline" -> ds.showOutlineSheet = true
            "workspace" -> latestOnIntent(io.legado.app.ui.ai.chat.AiChatIntent.ShowWorkspaceSheet)
            "characters", "character" -> ds.showCharacterMultiSelect = true
            "character_list" -> ds.showCharacterSheet = true
            "prompts", "prompt" -> ds.showPromptSheet = true
            "skills", "skill" -> ds.showSkillSheet = true
            "usercard", "user_card", "user" -> ds.showUserCardEditDialog = true
            "worldbook", "world_book" -> ds.showWorldBookSheet = true
            "memory", "memory_table" -> ds.showMemoryTableSheet = true
            "history", "execution" -> ds.showExecutionHistorySheet = true
            "compress", "compress_confirm", "compresscontext" -> ds.showCompressConfirm = true
            "context", "context_usage" -> ds.showContextUsageDialog = true
            "delete_conversation", "deleteconversation" -> {
                val convId = id?.trim().orEmpty()
                val conv = latestState.conversations.firstOrNull { it.id == convId }
                if (conv != null) ds.showDeleteConversationConfirm = conv
            }
            else -> Unit
        }
    }

    BackHandler { latestOnBack() }

    val outlineExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(state.outlineExportJson.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
    val outlineImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                if (!json.isNullOrBlank()) {
                    viewModel.onIntent(io.legado.app.ui.ai.chat.AiChatIntent.UpdateOutlineImportJson(json))
                    viewModel.onIntent(io.legado.app.ui.ai.chat.AiChatIntent.ConfirmOutlineImport)
                }
            }
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onIntent(io.legado.app.ui.ai.chat.AiChatIntent.AddAttachmentsFromUris(uris))
        }
    }
    var pickAttachments by remember { mutableStateOf({}) }
    pickAttachments = {
        attachmentLauncher.launch(
            arrayOf(
                "image/*",
                "application/pdf",
                "text/plain",
                "application/epub+zip",
            ),
        )
    }

    val bridge = remember {
        AiChatHtmlBridge(
            onIntent = { latestOnIntent(it) },
            onReady = { pageReady = true },
            onOpenSettings = { latestOnSettings() },
            onBack = { latestOnBack() },
            onListThemes = { GSON.toJson(themeStore.listThemes()) },
            onGetThemeId = { AppConfig.aiChatHtmlThemeId },
            onSetThemeId = { id ->
                if (themeStore.setTheme(id)) {
                    themeEpoch += 1
                    true
                } else {
                    false
                }
            },
            onOpenSheet = { sheet, id -> openSheet(sheet, id) },
            onGetFragments = { GSON.toJson(themeStore.readActiveFragments()) },
            onCopyText = { text -> context.sendToClip(text) },
            onPickAttachments = { pickAttachments() },
            onMatchSlashCommands = { partial -> latestMatchSlash(partial) },
            onSaveImage = { path ->
                android.util.Log.d("svg", "saveImage: called pathLen=${path.length} startsSvg=${path.startsWith("<svg", ignoreCase = true)}")
                runCatching {
                    val svgSource: String
                    if (path.startsWith("<svg", ignoreCase = true)) {
                        svgSource = io.legado.app.help.ai.AiChatAttachmentStore.sanitizeSvg(path)
                    } else {
                        val file = java.io.File(path)
                        if (!file.isFile) {
                            android.util.Log.w("svg", "saveImage: file not found $path")
                            return@runCatching
                        }
                        svgSource = file.readText()
                    }
                    // Render SVG to PNG bitmap
                    val bmp = renderSvgToBitmap(svgSource, context)
                    if (bmp == null) {
                        android.util.Log.w("svg", "saveImage: renderSvgToBitmap returned null")
                        return@runCatching
                    }
                    val dir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES,
                        ),
                        "legado",
                    )
                    dir.mkdirs()
                    val dest = java.io.File(dir, "ai_image_${System.currentTimeMillis()}.png")
                    dest.outputStream().use { os -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, os) }
                    bmp.recycle()
                    android.util.Log.d("svg", "saveImage: saved to ${dest.absolutePath} size=${dest.length()}")
                    Toast.makeText(context, "已保存到 " + dest.absolutePath, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    android.util.Log.e("svg", "saveImage: failed", e)
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectionChanged = { path, text, startLine, endLine ->
                editorSelection = mapOf(
                    "path" to path,
                    "text" to text,
                    "startLine" to startLine,
                    "endLine" to endLine,
                )
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiChatEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is AiChatEffect.SetInputText -> {
                    pushEffect(
                        webViewRef,
                        mapOf(
                            "type" to "SetInputText",
                            "text" to effect.text,
                            "forConversationId" to effect.forConversationId,
                        ),
                    )
                }
                is AiChatEffect.CopyToClipboard -> context.sendToClip(effect.text)
                is AiChatEffect.ShareText -> context.share(effect.text, effect.title)
            }
        }
    }

    LaunchedEffect(Unit) {
        AiChatHtmlThemeStore.changes.collect { paths ->
            themeEpoch += 1
            filesTouched = paths
            val next = themeStore.activeShellRevision()
            if (next != shellRevision) {
                shellRevision = next
            }
        }
    }

    // Full reload when user-pack index.html / app.js change (or WebView is recreated).
    LaunchedEffect(shellRevision, webViewRef) {
        val webView = webViewRef
        if (webView == null) {
            loadedShellRevision = null
            return@LaunchedEffect
        }
        if (loadedShellRevision == shellRevision) return@LaunchedEffect
        loadedShellRevision = shellRevision
        pageReady = false
        webView.loadUrl(themeStore.activeEntryUrl())
    }

    LaunchedEffect(state, pageReady, webViewRef) {
        if (!pageReady) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        // During streaming, if only the streaming message (or small flags) changed, the
        // StreamingUpdate channel handles it — skip re-serializing the whole conversation.
        val pureStream = state.streamingMessage != null &&
            state.messages === trackedMessages &&
            state.conversations === trackedConversations &&
            state.pendingToolConfs === trackedToolConfs &&
            state.suggestions === trackedSuggestions
        if (pureStream) return@LaunchedEffect
        trackedMessages = state.messages
        trackedConversations = state.conversations
        trackedToolConfs = state.pendingToolConfs
        trackedSuggestions = state.suggestions
        val json = AiChatHtmlStateMapper.toJson(state, themeStore)
        webView.evaluateJavascript(
            "window.LegadoAiChat && window.LegadoAiChat.applyState($json);",
            null,
        )
    }

    // Lightweight per-token streaming channel: push only the streaming message plus the
    // small scalar flags the JS composer depends on, so generation stays smooth.
    LaunchedEffect(state, pageReady, webViewRef) {
        if (!pageReady) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        val streamMsg = state.streamingMessage
        if (streamMsg == null) {
            lastStreamingPayload = ""
            return@LaunchedEffect
        }
        val json = AiChatHtmlStateMapper.streamingUpdateJson(state)
        if (json == lastStreamingPayload) return@LaunchedEffect
        lastStreamingPayload = json
        webView.evaluateJavascript(
            "window.LegadoAiChat && window.LegadoAiChat.applyEffect($json);",
            null,
        )
    }

    LaunchedEffect(themeEpoch, filesTouched, pageReady, webViewRef) {
        if (!pageReady) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        val payload = GSON.toJson(
            mapOf(
                "themeId" to AppConfig.aiChatHtmlThemeId,
                "themes" to themeStore.listThemes(),
                "cssUrl" to "theme.css?v=$themeEpoch",
                "fragments" to themeStore.readActiveFragments(),
                "filesTouched" to filesTouched.toList(),
                "editorSelection" to (editorSelection ?: emptyMap<String, Any?>()),
            ),
        )
        webView.evaluateJavascript(
            "window.LegadoAiChat && window.LegadoAiChat.applyTheme($payload);",
            null,
        )
        // Theme/fragments may remount sidebar; re-push state so conversation list is restored.
        val stateJson = AiChatHtmlStateMapper.toJson(state, themeStore)
        webView.evaluateJavascript(
            "window.LegadoAiChat && window.LegadoAiChat.applyState($stateJson);",
            null,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("LegadoBridge")
                stopLoading()
            }
            webViewRef = null
            pageReady = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                object : WebView(ctx) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE,
                            -> parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            -> parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        return super.onTouchEvent(event)
                    }
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isNestedScrollingEnabled = true
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                        setDarkeningAllowed(AppConfig.isNightTheme)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return !isAllowedAiChatUrl(url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return !isAllowedAiChatUrl(url.orEmpty())
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val url = request?.url?.toString().orEmpty()
                            if (url.contains(AI_CHAT_THEME_CSS_SUFFIX)) {
                                val css = themeStore.readActiveCss()
                                return WebResourceResponse(
                                    "text/css",
                                    StandardCharsets.UTF_8.name(),
                                    css.byteInputStream(StandardCharsets.UTF_8),
                                )
                            }
                            if (url.contains(AI_CHAT_APP_JS_SUFFIX) || url.endsWith("app.js")) {
                                val js = themeStore.readActiveAppJsOrNull()
                                    ?: themeStore.readEffectiveAppJs(
                                        AppConfig.aiChatHtmlThemeId.ifBlank {
                                            AiChatHtmlThemeStore.DEFAULT_THEME_ID
                                        },
                                    )
                                    .orEmpty()
                                return WebResourceResponse(
                                    "application/javascript",
                                    StandardCharsets.UTF_8.name(),
                                    js.byteInputStream(StandardCharsets.UTF_8),
                                )
                            }
                            if (url.contains(AI_CHAT_MD_JS_SUFFIX) || url.endsWith("md.js")) {
                                val mdJs = runCatching {
                                    context.assets.open("web/aichat/md.js")
                                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                                }.getOrNull().orEmpty()
                                return WebResourceResponse(
                                    "application/javascript",
                                    StandardCharsets.UTF_8.name(),
                                    mdJs.byteInputStream(StandardCharsets.UTF_8),
                                )
                            }
                            if (url.contains(AI_CHAT_SHARED_ENHANCE_JS_SUFFIX) ||
                                url.endsWith("shared-enhance.js")
                            ) {
                                val js = runCatching {
                                    context.assets.open("web/aichat/shared-enhance.js")
                                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                                }.getOrNull().orEmpty()
                                return WebResourceResponse(
                                    "application/javascript",
                                    StandardCharsets.UTF_8.name(),
                                    js.byteInputStream(StandardCharsets.UTF_8),
                                )
                            }
                            // User-pack index.html gets the shared enhancements injected in
                            // memory — the on-disk file is never modified.
                            if (isUserPackIndexUrl(url)) {
                                val themeId = AppConfig.aiChatHtmlThemeId.ifBlank {
                                    AiChatHtmlThemeStore.DEFAULT_THEME_ID
                                }
                                val userHtml = themeStore.readUserIndexHtml(themeId)
                                if (userHtml != null) {
                                    val injected = injectSharedEnhanceScript(userHtml)
                                    return WebResourceResponse(
                                        "text/html",
                                        StandardCharsets.UTF_8.name(),
                                        injected.byteInputStream(StandardCharsets.UTF_8),
                                    )
                                }
                            }
                            val mediaPath = mediaPathFromUrl(url)
                            if (mediaPath != null) {
                                val themeId = AppConfig.aiChatHtmlThemeId.ifBlank {
                                    AiChatHtmlThemeStore.DEFAULT_THEME_ID
                                }
                                val bytes = themeStore.readMediaBytes(themeId, mediaPath)
                                if (bytes != null) {
                                    return WebResourceResponse(
                                        AiChatHtmlThemeStore.mimeForMedia(mediaPath),
                                        null,
                                        bytes.inputStream(),
                                    )
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null && isAllowedAiChatUrl(url)) {
                                pageReady = true
                            }
                        }
                    }
                    addJavascriptInterface(bridge, "LegadoBridge")
                    // Initial load deferred to LaunchedEffect(shellRevision) once webViewRef is set.
                    webViewRef = this
                }
            },
            update = { view -> webViewRef = view },
        )

        AiChatSheetHost(
            ds = ds,
            viewModel = viewModel,
            state = state,
            onSaveOutlineExportFile = {
                outlineExportLauncher.launch("outline_export.json")
            },
            onOpenOutlineImportFile = {
                outlineImportLauncher.launch(arrayOf("application/json", "text/*"))
            },
        )
    }
}

private fun isAllowedAiChatUrl(url: String): Boolean {
    if (url.isBlank() || url == "about:blank") return true
    if (url.contains(AI_CHAT_THEME_CSS_SUFFIX)) return true
    if (url.contains(AI_CHAT_APP_JS_SUFFIX) || url.endsWith("app.js")) return true
    if (url.contains(AI_CHAT_MD_JS_SUFFIX) || url.endsWith("md.js")) return true
    if (url.contains(AI_CHAT_SHARED_ENHANCE_JS_SUFFIX) || url.endsWith("shared-enhance.js")) {
        return true
    }
    if (mediaPathFromUrl(url) != null) return true
    if (url == AI_CHAT_ASSET_URL ||
        url.startsWith(AI_CHAT_ASSET_PREFIX) ||
        url.startsWith("$AI_CHAT_ASSET_URL?")
    ) {
        return true
    }
    // User-pack shell loaded via file://…/aichat-themes/{id}/index.html
    if (url.startsWith("file://") && url.contains("/${AiChatHtmlThemeStore.USER_THEMES_DIR}/")) {
        return true
    }
    return false
}

private fun isUserPackIndexUrl(url: String): Boolean =
    url.endsWith("index.html") &&
        url.startsWith("file://") &&
        url.contains("/${AiChatHtmlThemeStore.USER_THEMES_DIR}/")

private fun injectSharedEnhanceScript(html: String): String {
    if (html.contains("shared-enhance.js")) return html
    val bodyClose = html.lowercase().lastIndexOf("</body>")
    return if (bodyClose >= 0) {
        html.substring(0, bodyClose) + SHARED_ENHANCE_SCRIPT_TAG + html.substring(bodyClose)
    } else {
        html + SHARED_ENHANCE_SCRIPT_TAG
    }
}

/** Resolve `media/{file}` from theme.css-relative or pack-relative URLs. */
private fun mediaPathFromUrl(url: String): String? {
    val marker = "/media/"
    val idx = url.indexOf(marker)
    if (idx < 0) return null
    val name = url.substring(idx + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore('/')
    if (!AiChatHtmlThemeStore.isAllowedMediaFileName(name)) return null
    return "media/$name"
}

private fun pushEffect(webView: WebView?, payload: Map<String, Any?>) {
    val view = webView ?: return
    val json = GSON.toJson(payload)
    view.post {
        view.evaluateJavascript(
            "window.LegadoAiChat && window.LegadoAiChat.applyEffect($json);",
            null,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun renderSvgToBitmap(svgSource: String, context: Context): android.graphics.Bitmap? {
    val svg = com.caverock.androidsvg.SVG.getFromString(svgSource) ?: return null
    val density = context.resources.displayMetrics.density
    val limitPx = (320f * density).toInt()
    val docW = svg.documentWidth.takeIf { it > 0f } ?: limitPx.toFloat()
    val docH = svg.documentHeight.takeIf { it > 0f } ?: limitPx.toFloat()
    val scale = minOf(limitPx / docW, limitPx / docH)
    val w = (docW * scale).toInt().coerceAtLeast(1)
    val h = (docH * scale).toInt().coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.scale(scale, scale)
    svg.renderToCanvas(canvas)
    return bmp
}
