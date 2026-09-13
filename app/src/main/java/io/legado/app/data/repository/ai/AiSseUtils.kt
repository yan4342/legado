package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Shared SSE parsing utilities used by all protocol handlers.
 */
internal suspend fun Response.readSseData(onData: suspend (String) -> Unit) {
    val source = body.source()
    val dataLines = mutableListOf<String>()
    // The SSE body is read with blocking Okio calls that a cancelled coroutine cannot
    // interrupt while the provider stays silent (AI clients use readTimeout=0). Register a
    // completion handler that closes the source on cancellation so the blocking read
    // unblocks and the CancellationException propagates promptly instead of hanging.
    val job = coroutineContext[Job]
    val cancellationHandle = job?.invokeOnCompletion { cause ->
        if (cause is CancellationException) {
            runCatching { source.close() }
        }
    }
    try {
        while (true) {
            coroutineContext.ensureActive()
            val line = try {
                source.readUtf8Line()
            } catch (e: IOException) {
                // Source was closed by the cancellation handler above — stop reading.
                if (job?.isCancelled == true) break
                throw e
            } ?: break
            when {
                line.isEmpty() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n").trim()
                        dataLines.clear()
                        if (data == "[DONE]") break
                        if (data.isNotEmpty()) onData(data)
                    }
                }
                line.startsWith("data:") -> {
                    dataLines += line.removePrefix("data:").trimStart()
                }
            }
        }
        if (dataLines.isNotEmpty()) {
            val data = dataLines.joinToString("\n").trim()
            if (data != "[DONE]" && data.isNotEmpty()) onData(data)
        }
    } finally {
        cancellationHandle?.dispose()
        source.close()
    }
}

internal fun String.toJsonObject(): JsonObject? {
    return runCatching {
        GSON.fromJson(this, JsonObject::class.java)
    }.getOrNull()
}

internal fun JsonObject.getString(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.getInt(name: String): Int? {
    return get(name)?.takeIf { !it.isJsonNull }?.asInt
}

internal fun JsonObject.extractApiErrorMessage(): String? {
    val error = get("error")?.asJsonObjectOrNull() ?: return null
    return error.getString("message") ?: error.getString("code") ?: "AI provider returned an error"
}

internal fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

internal fun JsonElement.asJsonArrayOrNull() = if (isJsonArray) asJsonArray else null

/**
 * Build a diagnostic error message for a non-2xx OkHttp response.
 * Includes the response URL and `x-request-id` (when present) so provider-side failures
 * can be correlated with upstream logs.
 */
internal fun okhttp3.Response.buildHttpError(): String {
    val errBody = body.string().take(1000)
    return buildHttpErrorDetail(code, message, errBody, request.url.toString(), headers["x-request-id"])
}

internal fun io.legado.app.help.http.StrResponse.buildHttpError(): String {
    val errBody = body ?: errorBody?.text() ?: ""
    return buildHttpErrorDetail(code(), message(), errBody, url(), headers()["x-request-id"])
}

private fun buildHttpErrorDetail(code: Int, message: String, errBody: String, url: String, requestId: String?): String {
    return buildString {
        append("HTTP $code: $message. Body: $errBody")
        append(" URL: $url")
        if (!requestId.isNullOrBlank()) append(" Request-Id: $requestId")
    }
}

/**
 * Check if the model supports reasoning capability.
 */
internal fun hasReasoningCapability(capabilities: Set<String>): Boolean {
    return AiCapability.REASONING in capabilities
}

/**
 * Map AiReasoningLevel to OpenAI-compatible reasoning_effort value.
 * OpenAI doesn't accept "none" — remap to "low".
 */
internal fun AiReasoningLevel.toOpenAiEffort(): String {
    return if (effort == "none") "low" else effort
}

/**
 * DeepSeek 模型检测 —— 按模型 id，而非 provider/baseUrl：
 * 其它 provider（聚合、代理、自建网关等）也会托管 deepseek 模型，baseUrl 判断会漏掉。
 * 与 AiModelRegistry 用 "deepseek" token 推断能力的方式一致。
 */
internal val AiModelConfig.isDeepSeek: Boolean
    get() = "deepseek" in modelId.lowercase()

/**
 * Resolve the wire-level reasoning_effort / output_config.effort value for a model.
 * DeepSeek only accepts low/high/max and translates per model tier
 * (official spec: v4-flash low→low / pro→high, xhigh→high/max). Non-DeepSeek
 * keeps legacy behavior (toOpenAiEffort: "none"→"low").
 */
internal fun AiReasoningLevel.toEffortFor(model: AiModelConfig): String {
    return if (model.isDeepSeek) toDeepSeekEffort(model.modelId) else toOpenAiEffort()
}

/**
 * DeepSeek mapping table. OFF/AUTO are gated out by callers; "low" for them
 * mirrors the existing "none 视为 low" convention.
 */
internal fun AiReasoningLevel.toDeepSeekEffort(modelId: String): String {
    val isPro = "pro" in modelId.lowercase()
    return when (this) {
        AiReasoningLevel.LOW -> if (isPro) "high" else "low"
        AiReasoningLevel.MEDIUM -> "high"      // 规格未列 medium（请求集为 low/high/xhigh/max）；映射为 high 与"默认 high"一致
        AiReasoningLevel.HIGH -> "high"
        AiReasoningLevel.MAX -> "max"
        else -> "low"          // OFF/AUTO
    }
}

/**
 * Responses API variant: OFF 的合法值是 "none"（关闭思考），不可被 toEffortFor 的 "low" 取代。
 */
internal fun AiReasoningLevel.toResponsesEffort(model: AiModelConfig): String {
    return if (this == AiReasoningLevel.OFF) "none" else toEffortFor(model)
}
