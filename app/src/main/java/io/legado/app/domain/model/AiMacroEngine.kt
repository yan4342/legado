package io.legado.app.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiMacroContext(
    val userLabel: String = "用户",
    val characterNames: List<String> = emptyList(),
    val currentSpeaker: String = "",
    val lastUserMessage: String = "",
    val conversationTitle: String = "",
    val timeText: String = defaultTimeText(),
) {
    companion object {
        fun fromPersona(
            userName: String,
            userCardEnabled: Boolean,
            characterNames: List<String>,
            defaultUserLabel: String = "用户",
        ): AiMacroContext {
            val userLabel = if (userCardEnabled && userName.isNotBlank()) userName else defaultUserLabel
            return AiMacroContext(
                userLabel = userLabel,
                characterNames = characterNames,
                currentSpeaker = characterNames.firstOrNull().orEmpty(),
            )
        }

        fun defaultTimeText(): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
}

/**
 * Expands {{macro}} placeholders in prompt text.
 * Unknown macros are left unchanged.
 */
object AiMacroEngine {
    private val macroPattern = Regex("""\{\{([a-zA-Z0-9_]+)\}\}""")

    fun expand(text: String, ctx: AiMacroContext): String =
        macroPattern.replace(text) { match ->
            when (match.groupValues[1]) {
                "user" -> ctx.userLabel
                "char" -> ctx.characterNames.getOrElse(0) { "" }
                "currentSpeaker" -> ctx.currentSpeaker.ifBlank { ctx.characterNames.getOrElse(0) { "" } }
                "lastUserMessage" -> ctx.lastUserMessage
                "conversationTitle" -> ctx.conversationTitle
                "time" -> ctx.timeText.ifBlank { AiMacroContext.defaultTimeText() }
                else -> {
                    if (match.groupValues[1].startsWith("char")) {
                        val index = match.groupValues[1].removePrefix("char").toIntOrNull()?.minus(1)
                            ?: return@replace match.value
                        ctx.characterNames.getOrElse(index) { "" }
                    } else {
                        match.value
                    }
                }
            }
        }
}
