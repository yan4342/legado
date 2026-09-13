package io.legado.app.data.entities

/**
 * Lightweight export/import container for per-conversation story outlines.
 */
data class AiOutlineExport(
    val type: String = TYPE,
    val version: Int = VERSION,
    val content: String,
    val enabled: Boolean = true,
    val sourceConversationId: String? = null,
) {
    companion object {
        const val TYPE = "legado_ai_outline"
        const val VERSION = 1
    }
}
