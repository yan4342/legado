package io.legado.app.ui.ai.chat

import android.util.Log
import com.google.gson.reflect.TypeToken
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.utils.GSON

internal fun historyForModel(
    history: List<AiChatMessageUi>,
    roleplay: Boolean,
): List<AiChatMessageUi> {
    if (!roleplay) return history
    return history.map { msg ->
        if (msg.role != AiMessageRole.ASSISTANT || msg.speakerName.isBlank()) return@map msg
        val trimmed = msg.content.trimStart()
        val prefixes = listOf(
            "[${msg.speakerName}]:",
            "[${msg.speakerName}]：",
            "${msg.speakerName}：",
            "${msg.speakerName}:",
        )
        if (prefixes.any { trimmed.startsWith(it) }) return@map msg
        msg.copy(content = "[${msg.speakerName}]: ${msg.content}")
    }
}

internal fun buildToolRejectionFeedback(
    items: List<PendingToolCallUi>,
    batchFeedback: String,
): String? {
    val parts = buildList {
        if (batchFeedback.isNotBlank()) add(batchFeedback.trim())
        items.forEach { item ->
            item.argsValidationError?.takeIf { it.isNotBlank() }?.let { err ->
                add("${item.displayName}: $err")
            }
            item.validationError
                ?.takeIf { it.isNotBlank() && it != item.argsValidationError }
                ?.let { err ->
                    add("${item.displayName}: $err")
                }
            item.subItems.forEach { sub ->
                sub.validationError?.takeIf { it.isNotBlank() }?.let { err ->
                    add("${item.displayName} · ${sub.opLabel}: $err")
                }
            }
            item.feedback.takeIf { it.isNotBlank() }?.let { fb ->
                add("${item.displayName}: $fb")
            }
        }
    }
    return parts.joinToString("\n").takeIf { it.isNotBlank() }
}

internal fun parseSuggestionsJson(text: String): List<String> {
    val cleaned = text.trim()
        .removeSurrounding("```json", "```")
        .removeSurrounding("```", "```")
        .trim()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        GSON.fromJson(cleaned, type) ?: emptyList()
    } catch (e: Exception) {
        Log.d("AiChat", "parseSuggestionsJson failed: ${e.message}, raw=$cleaned")
        emptyList()
    }
}
