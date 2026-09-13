package io.legado.app.domain.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** In-flight tool execution progress shown in the chat tool bubble. */
data class AiToolProgress(
    /** 0f..1f */
    val fraction: Float,
    val label: String = "",
) {
    fun toMetadataJson(): String = JsonObject().apply {
        addProperty(KEY_PROGRESS, fraction.coerceIn(0f, 1f))
        if (label.isNotBlank()) addProperty(KEY_LABEL, label)
    }.toString()

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_LABEL = "label"

        fun fromMetadata(metadata: String?): AiToolProgress? {
            if (metadata.isNullOrBlank()) return null
            return runCatching {
                val obj = JsonParser.parseString(metadata).asJsonObject
                if (!obj.has(KEY_PROGRESS)) return@runCatching null
                val fraction = obj.get(KEY_PROGRESS).asFloat.coerceIn(0f, 1f)
                val label = obj.get(KEY_LABEL)?.asString.orEmpty()
                AiToolProgress(fraction, label)
            }.getOrNull()
        }
    }
}

/** Suspendable progress callback for long-running tools (e.g. install_skill_from_url). */
fun interface AiToolProgressCallback {
    suspend fun onProgress(fraction: Float, label: String)
}
