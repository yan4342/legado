package io.legado.app.ui.ai.chat

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.textContent
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

/**
 * HTML App 播放器的 JS → Native 桥。
 *
 * 独立于聊天 shell 的 [io.legado.app.ui.ai.chat.html.AiChatHtmlBridge]：只暴露白名单
 * 的上下文读取 / 状态持久化 / 反馈到聊天的方法，不暴露 postIntent、openSheet、剪贴板等
 * 任何 app 能力。WebView 只注入这一个 interface（名字 "GameBridge"）。
 */
internal class HtmlAppBridge(
    private val conversationId: String,
    private val worldBookGateway: AiWorldBookGateway,
    private val characterCardGateway: AiCharacterCardGateway,
    private val outlineGateway: AiOutlineGateway,
    private val chatGateway: AiChatGateway,
    private val stateManager: HtmlAppStateManager,
    private val onSendToChat: (String) -> Unit,
    private val onNotifyContext: (String) -> Unit,
    private val onExit: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- 视口（Screen 在 WebView 布局/尺寸变化时更新） ----
    @Volatile
    private var viewportCssWidth: Int = 0
    @Volatile
    private var viewportCssHeight: Int = 0

    /** Screen 回调：记录 WebView 实际 CSS 像素视口（横竖屏切换 / 尺寸变化时更新）。 */
    fun updateViewport(widthCss: Int, heightCss: Int) {
        viewportCssWidth = widthCss
        viewportCssHeight = heightCss
    }

    /** 游戏运行时查询设备屏幕 / WebView 视口尺寸。返回 CSS 像素 + density + 物理像素。 */
    @JavascriptInterface
    fun getViewport(): String {
        val dm = appCtx.resources.displayMetrics
        val density = dm.density
        val cssW = if (viewportCssWidth > 0) viewportCssWidth
        else (dm.widthPixels / density).toInt()
        val cssH = if (viewportCssHeight > 0) viewportCssHeight
        else (dm.heightPixels / density).toInt()
        return GSON.toJson(
            mapOf(
                "width" to cssW,            // CSS px（WebView 可用宽度）
                "height" to cssH,           // CSS px（WebView 可用高度，不含播放器顶栏）
                "screenWidth" to (dm.widthPixels / density).toInt(),   // 整屏 CSS px
                "screenHeight" to (dm.heightPixels / density).toInt(), // 整屏 CSS px（含系统栏）
                "density" to density,
                "pixelWidth" to cssW * density,
                "pixelHeight" to cssH * density,
            ),
        )
    }

    // ---- 上下文读取（只读，走 runBlocking 包住挂起 gateway 调用） ----

    /** 当前启用的世界书 JSON 数组。 */
    @JavascriptInterface
    fun getWorldBooks(): String = runBlocking {
        val books = worldBookGateway.getEnabled()
        GSON.toJson(books.map { book ->
            mapOf(
                "id" to book.id,
                "name" to book.name,
                "writingStyle" to book.writingStyle,
                "grammar" to book.grammar,
                "plotSummary" to book.plotSummary,
                "representativeDialogues" to book.representativeDialogues,
                "representativeProse" to book.representativeProse,
            )
        })
    }

    /** 当前会话绑定的角色卡 JSON 数组。 */
    @JavascriptInterface
    fun getCharacters(): String = runBlocking {
        val cardIds = chatGateway.getConversation(conversationId)?.characterCardIds.orEmpty()
        val cards = AiIdListCodec.parse(cardIds).mapNotNull { id ->
            characterCardGateway.getById(id)
        }
        GSON.toJson(cards.map { card ->
            mapOf(
                "id" to card.id,
                "name" to card.name,
                "description" to card.description,
                "personality" to card.personality,
                "scenario" to card.scenario,
                "exampleDialogues" to card.exampleDialogues,
                "openingLine" to card.openingLine,
            )
        })
    }

    /** 当前大纲内容（markdown 字符串）。 */
    @JavascriptInterface
    fun getOutline(): String = runBlocking {
        outlineGateway.getByConversation(conversationId)?.content.orEmpty()
    }

    /** 最近 [n] 条对话消息 `[{role, content}]`。 */
    @JavascriptInterface
    fun getRecentMessages(n: Int): String = runBlocking {
        val count = n.coerceIn(1, 50)
        val messages = chatGateway.getContextMessages(conversationId, count)
        GSON.toJson(messages.map { msg ->
            mapOf(
                "role" to when (msg.role) {
                    AiMessageRole.USER -> "user"
                    AiMessageRole.ASSISTANT -> "assistant"
                    AiMessageRole.SYSTEM -> "system"
                    AiMessageRole.TOOL -> "tool"
                    else -> "user"
                },
                "content" to io.legado.app.domain.model.AiMessagePartJson.decode(msg.partsJson).textContent(),
            )
        })
    }

    // ---- 游戏状态持久化（映射到 AiMemoryTable） ----

    /** 读取上次保存的游戏状态 JSON；无返回 null。 */
    @JavascriptInterface
    fun loadGameState(): String? = runBlocking { stateManager.loadGameState() }

    /** 保存游戏状态 JSON。 */
    @JavascriptInterface
    fun saveGameState(json: String?) {
        val value = json.orEmpty()
        if (value.isBlank()) return
        runBlocking { stateManager.saveGameState(value) }
    }

    // ---- 反馈到 AI 对话 ----

    /** 作为用户消息插入对话。 */
    @JavascriptInterface
    fun sendToChat(message: String?) {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return
        mainHandler.post { onSendToChat(text) }
    }

    /** 静默注入下轮 AI 请求上下文（不显示为消息）。 */
    @JavascriptInterface
    fun notifyContext(json: String?) {
        val value = json.orEmpty()
        if (value.isBlank()) return
        mainHandler.post { onNotifyContext(value) }
    }

    // ---- 生命周期 ----

    @JavascriptInterface
    fun exit() {
        mainHandler.post { onExit() }
    }
}
