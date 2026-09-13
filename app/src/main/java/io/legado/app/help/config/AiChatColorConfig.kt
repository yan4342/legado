package io.legado.app.help.config

import android.content.Context
import androidx.compose.ui.graphics.Color
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt

object AiChatColorConfig {

    const val UNSET = -1

    val speakerColorKeys = listOf(
        PreferKey.aiChatSpeakerColor0,
        PreferKey.aiChatSpeakerColor1,
        PreferKey.aiChatSpeakerColor2,
        PreferKey.aiChatSpeakerColor3,
        PreferKey.aiChatSpeakerColor4,
        PreferKey.aiChatSpeakerColor5,
        PreferKey.aiChatSpeakerColor6,
        PreferKey.aiChatSpeakerColor7,
    )

    val allColorKeys = listOf(
        PreferKey.aiChatUserBubbleBg,
        PreferKey.aiChatUserBubbleText,
        PreferKey.aiChatUserDialogue,
        PreferKey.aiChatToolSearch,
        PreferKey.aiChatToolRead,
        PreferKey.aiChatToolExtract,
        PreferKey.aiChatToolMemory,
        PreferKey.aiChatToolMutation,
        PreferKey.aiChatToolDestructive,
    ) + speakerColorKeys

    fun defaultSpeakerColors(): List<Color> = listOf(
        Color(0xFFE57373),
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DD0E1),
        Color(0xFFF06292),
        Color(0xFFAED581),
    )

    fun readColor(context: Context, key: String): Color? {
        val value = context.getPrefInt(key, UNSET)
        return if (value != UNSET) Color(value) else null
    }

    fun writeColor(context: Context, key: String, color: Color) {
        context.putPrefInt(key, color.toArgb())
    }

    fun resetKey(context: Context, key: String) {
        context.putPrefInt(key, UNSET)
    }

    fun resetAll(context: Context) {
        allColorKeys.forEach { key -> resetKey(context, key) }
    }

    fun resolveSpeakerColors(context: Context): List<Color> {
        val defaults = defaultSpeakerColors()
        return speakerColorKeys.mapIndexed { index, key ->
            readColor(context, key) ?: defaults[index]
        }
    }

    private fun Color.toArgb(): Int {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
