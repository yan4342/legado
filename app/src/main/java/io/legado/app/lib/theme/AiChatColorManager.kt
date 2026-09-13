package io.legado.app.lib.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.help.config.AiChatColorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class AiChatSemanticColors(
    val userBubbleBg: Color? = null,
    val userBubbleText: Color? = null,
    val userDialogue: Color? = null,
    val toolSearch: Color? = null,
    val toolRead: Color? = null,
    val toolExtract: Color? = null,
    val toolMemory: Color? = null,
    val toolMutation: Color? = null,
    val toolDestructive: Color? = null,
    val speakerColors: List<Color> = AiChatColorConfig.defaultSpeakerColors(),
) {
    companion object {
        val Empty = AiChatSemanticColors()
    }
}

val LocalAiChatSemanticColors = staticCompositionLocalOf { AiChatSemanticColors.Empty }

object AiChatColorManager {

    private val _colors = MutableStateFlow(AiChatSemanticColors.Empty)
    val colors: StateFlow<AiChatSemanticColors> = _colors.asStateFlow()

    fun refresh(context: Context) {
        _colors.value = AiChatSemanticColors(
            userBubbleBg = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatUserBubbleBg),
            userBubbleText = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatUserBubbleText),
            userDialogue = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatUserDialogue),
            toolSearch = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolSearch),
            toolRead = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolRead),
            toolExtract = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolExtract),
            toolMemory = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolMemory),
            toolMutation = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolMutation),
            toolDestructive = AiChatColorConfig.readColor(context, io.legado.app.constant.PreferKey.aiChatToolDestructive),
            speakerColors = AiChatColorConfig.resolveSpeakerColors(context),
        )
    }
}

@Composable
fun rememberAiChatSemanticColors(): AiChatSemanticColors {
    val colors by AiChatColorManager.colors.collectAsStateWithLifecycle()
    return colors
}

@Composable
fun ProvideAiChatSemanticColors(content: @Composable () -> Unit) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        AiChatColorManager.refresh(context.applicationContext)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAiChatSemanticColors provides rememberAiChatSemanticColors(),
        content = content,
    )
}
