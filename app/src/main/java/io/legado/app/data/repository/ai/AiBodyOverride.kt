package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.utils.GSON

/**
 * Parsed model request config: the typed common params (mapped to protocol keys by handlers)
 * plus provider-specific extras merged into the outgoing body verbatim.
 */
internal data class AiRequestConfig(
    val params: AiGenerationParams,
    val extraParams: Map<String, Any?>?,
)

/**
 * Keys the app owns per protocol (model, messages/input, stream, tools, reasoning, …).
 * They are never sent via extras and never overridden by the model's request-body JSON.
 */
private val HANDLER_OWNED_KEYS = setOf(
    "model", "messages", "input", "stream", "stream_options",
    "tools", "tool_choice", "system",
    "reasoning", "reasoning_effort", "thinking", "output_config",
)

/**
 * Splits the model edit page's raw request-body JSON into typed [AiGenerationParams] and
 * extras. Known keys map to typed params (also fixing the max_tokens → maxOutputTokens
 * alias); handler-owned keys are dropped; everything else becomes [extraParams] and is
 * merged into the request body without any further allow/deny list.
 */
internal fun parseRequestConfig(json: String?): AiRequestConfig {
    if (json.isNullOrBlank()) return AiRequestConfig(AiGenerationParams(), null)
    @Suppress("UNCHECKED_CAST")
    val map = runCatching {
        GSON.fromJson(json, Map::class.java) as Map<String, Any?>
    }.getOrNull() ?: return AiRequestConfig(AiGenerationParams(), null)
    var temperature: Float? = null
    var maxOutputTokens: Int? = null
    var topP: Float? = null
    var reasoningLevel: AiReasoningLevel = AiReasoningLevel.AUTO
    val extra = LinkedHashMap<String, Any?>()
    map.forEach { (key, value) ->
        when (key) {
            "temperature" -> temperature = (value as? Number)?.toFloat()
            "top_p" -> topP = (value as? Number)?.toFloat()
            "max_tokens", "max_output_tokens", "max_completion_tokens" ->
                maxOutputTokens = (value as? Number)?.toInt()
            "reasoningLevel" -> reasoningLevel = (value as? String)?.let {
                runCatching { AiReasoningLevel.valueOf(it) }.getOrNull()
            } ?: reasoningLevel
            in HANDLER_OWNED_KEYS -> Unit
            else -> extra[key] = value
        }
    }
    val params = AiGenerationParams(
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        topP = topP,
        reasoningLevel = reasoningLevel,
    )
    return AiRequestConfig(params, extra.ifEmpty { null })
}

/**
 * Merges provider-specific extras into the outgoing body. Extras are by construction keys the
 * app does not manage, so no deny list is needed here; existing handler-set keys still win.
 */
internal fun MutableMap<String, Any?>.mergeExtraParams(extra: Map<String, Any?>?) {
    if (extra.isNullOrEmpty()) return
    extra.forEach { (key, value) ->
        if (!containsKey(key)) put(key, value)
    }
}
