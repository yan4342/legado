package io.legado.app.domain.usecase.ai

import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole

/**
 * Helpers for splitting static instruction templates from per-call variable payloads
 * so provider prefix caches (Anthropic / DeepSeek / OpenAI) can hit on SYSTEM.
 */
object PromptRoleSplit {
    private val curlyPlaceholder = Regex("""\{[a-zA-Z][a-zA-Z0-9_]*\}""")
    private val doubleCurlyPlaceholder = Regex("""\{\{[a-zA-Z][a-zA-Z0-9_]*\}\}""")

    /** Remove `{name}` / `{{name}}` placeholders left in custom or legacy templates. */
    fun stripPlaceholders(template: String): String =
        template
            .replace(doubleCurlyPlaceholder, "")
            .replace(curlyPlaceholder, "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    fun messages(
        systemPrompt: String,
        userPrompt: String,
        maxUserChars: Int = Int.MAX_VALUE,
    ): List<AiMessage> = buildList {
        if (systemPrompt.isNotBlank()) {
            add(AiMessage(AiMessageRole.SYSTEM, systemPrompt))
        }
        add(AiMessage(AiMessageRole.USER, userPrompt.take(maxUserChars.coerceAtLeast(1))))
    }

    /** Build a USER payload from named variables; [contentKey] is appended last as raw body. */
    fun userFromVars(
        vars: Map<String, String>,
        contentKey: String = "content",
        labelMap: Map<String, String> = emptyMap(),
    ): String = buildString {
        vars.forEach { (key, value) ->
            if (key == contentKey || value.isBlank()) return@forEach
            val label = labelMap[key] ?: key
            append(label).append(": ").append(value.trim()).append('\n')
        }
        val body = vars[contentKey]?.trim().orEmpty()
        if (body.isNotEmpty()) {
            if (isNotEmpty()) append('\n')
            append(body)
        }
    }.trim()
}
