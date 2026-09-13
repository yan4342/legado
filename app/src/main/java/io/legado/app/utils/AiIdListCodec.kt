package io.legado.app.utils

/**
 * Shared ID-list codec for AI entities.
 *
 * Conversation.characterCardIds is stored as a JSON array string.
 * Workspace / prompt / world-book refs are stored as comma-separated IDs.
 * This codec accepts both shapes on read.
 */
object AiIdListCodec {

    fun parse(raw: String?): List<String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        if (text.startsWith("[")) {
            val fromJson = runCatching {
                GSON.fromJson(text, Array<String>::class.java)?.toList()
            }.getOrNull()
            if (fromJson != null) {
                return fromJson.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            }
        }
        return text.split(',', ';')
            .map { it.trim().trim('"', '\'', '[', ']') }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun toCsv(ids: Collection<String>): String =
        ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    fun toCsv(raw: String?): String = toCsv(parse(raw))

    fun toJsonArray(ids: Collection<String>): String =
        GSON.toJson(ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct())

    fun toJsonArray(raw: String?): String? {
        val ids = parse(raw)
        return if (ids.isEmpty()) null else toJsonArray(ids)
    }

    fun primaryId(raw: String?): String? = parse(raw).firstOrNull()
}
