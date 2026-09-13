package io.legado.app.ui.ai.chat

import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import io.legado.app.domain.model.htmlAppRefParts
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =============================================================================
// HTML App Player：启动 / 关闭 / 反馈到对话 / __game_update__ 下行
// =============================================================================

/**
 * 打开某个消息引用的 HTML App 播放器。
 * [messageId] 对应含 [AiMessagePart.HtmlAppRef] 的 assistant 消息。
 */
internal fun AiChatViewModel.launchHtmlApp(messageId: String) {
    val cid = currentConversationId.value ?: return
    if (cid.isBlank()) return
    viewModelScope.launch {
        val msg = aiChatGateway.getMessage(messageId) ?: return@launch
        val ref = AiMessagePartJson.decode(msg.partsJson).htmlAppRefParts().firstOrNull()
            ?: return@launch
        val app = aiHtmlAppGateway.getById(ref.appId) ?: return@launch
        val entryPath = aiHtmlAppGateway.entryPath(ref.appId) ?: return@launch
        _uiState.update {
            it.copy(
                activeHtmlApp = HtmlAppPlayerState(
                    appId = ref.appId,
                    title = app.title,
                    entryPath = entryPath,
                ),
                gameUpdatePayload = null,
            )
        }
    }
}

/** 关闭播放器，清空下行载荷。 */
internal fun AiChatViewModel.dismissHtmlApp() {
    _uiState.update { it.copy(activeHtmlApp = null, gameUpdatePayload = null) }
}

/** 游戏经 GameBridge.sendToChat：作为用户消息插入对话（自动关闭播放器）。 */
internal fun AiChatViewModel.gameSendToChat(text: String) {
    dismissHtmlApp()
    sendMessage(text)
}

/** 游戏经 GameBridge.notifyContext：存静默上下文，注入下轮 AI 请求后清空。 */
internal fun AiChatViewModel.gameNotifyContext(json: String) {
    if (json.isBlank()) return
    _uiState.update { it.copy(silentGameContext = json) }
}

/** 构造播放器的 JS→Native 桥（Screen 在 remember 里调用一次）。 */
internal fun AiChatViewModel.createHtmlAppBridge(onExit: () -> Unit): HtmlAppBridge {
    val cid = currentConversationId.value.orEmpty()
    return HtmlAppBridge(
        conversationId = cid,
        worldBookGateway = aiWorldBookGateway,
        characterCardGateway = aiCharacterCardGateway,
        outlineGateway = aiOutlineGateway,
        chatGateway = aiChatGateway,
        stateManager = HtmlAppStateManager(aiMemoryTableGateway, cid),
        onSendToChat = { text -> onIntent(AiChatIntent.GameSendToChat(text)) },
        onNotifyContext = { json -> onIntent(AiChatIntent.GameNotifyContext(json)) },
        onExit = onExit,
    )
}

/**
 * assistant 消息落库后：回填 HtmlApp 的 messageId；若开启 auto-launch 则自动打开播放器。
 * 在 persist 的 finally/NonCancellable 区外调用（此处是普通 suspend）。
 */
internal suspend fun AiChatViewModel.postPersistHtmlApps(saved: AiChatMessage) {
    val appIds = AiMessagePartJson.decode(saved.partsJson).htmlAppRefParts().map { it.appId }
    if (appIds.isEmpty()) return
    appIds.forEach { appId ->
        runCatching { aiHtmlAppGateway.updateMessageId(appId, saved.id) }
    }
    if (AppConfig.aiHtmlAppAutoLaunch) {
        launchHtmlApp(saved.id)
    }
}

// =============================================================================
// __game_update__ 解析（AI→运行中游戏 下行通道）
// =============================================================================

private const val GAME_UPDATE_MARKER = "__game_update__"

/**
 * 从回复文本末尾提取 AI 夹带的 `{"__game_update__": {...}}` JSON 块。
 * @return (剥离后的正文, 提取到的更新 JSON 字符串)；未匹配返回 (原文本, null)。
 */
internal fun extractGameUpdate(text: String): Pair<String, String?> {
    if (text.isBlank()) return text to null
    val markerIdx = text.lastIndexOf(GAME_UPDATE_MARKER)
    if (markerIdx < 0) return text to null
    val braceStart = text.lastIndexOf('{', markerIdx)
    if (braceStart < 0) return text to null
    val tail = text.substring(braceStart).trim()
    val wrapper = runCatching { GSON.fromJson(tail, JsonObject::class.java) }.getOrNull()
        ?: return text to null
    val update = wrapper.get(GAME_UPDATE_MARKER) ?: return text to null
    if (update.isJsonNull) return text to null
    val cleanText = text.substring(0, braceStart).trimEnd()
    return cleanText to update.toString()
}

/** 从 publish_html_app 工具输出里解析 appId。 */
internal fun parseHtmlAppIdFromToolOutput(output: String): String? {
    if (output.isBlank()) return null
    return runCatching {
        GSON.fromJson(output, JsonObject::class.java)
            ?.get("appId")?.takeIf { !it.isJsonNull }?.asString
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
