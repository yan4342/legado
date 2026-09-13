package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.usecase.structured.FieldChange
import io.legado.app.utils.GSON

/**
 * Performance / cast metadata (dramatic role, TTS voice, aliases) is for chat + book detail.
 * Writing mode must not read or write these fields via tools or manual card edits.
 *
 * [avatarPath] is always UI-only: never exposed to or writable by AI tools.
 */
object CharacterCardPerformancePolicy {

    private val PERFORMANCE_PATCH_FIELDS = setOf(
        "aliasesJson", "voiceGender", "voiceAgeBand", "dramaticRole",
    )

    private val PERFORMANCE_TOOL_SCHEMA_KEYS = setOf(
        "aliases", "aliasesJson", "voiceGender", "voiceAgeBand", "dramaticRole", "role",
    )

    private val PERFORMANCE_TOOL_MAP_KEYS = setOf(
        "aliases", "aliasesJson", "voiceGender", "voiceAgeBand", "dramaticRole",
    )

    /** Always hidden from AI tool IO (local image path only). */
    private val AI_HIDDEN_KEYS = setOf("avatarPath")

    private val PERFORMANCE_FIELD_CHANGES = PERFORMANCE_PATCH_FIELDS + "aliases"

    fun isWritingMode(conversationType: String?): Boolean = conversationType == "writing"

    fun stripPatch(patch: CharacterCardPatch): CharacterCardPatch = patch.copy(
        aliasesJson = null,
        voiceGender = null,
        voiceAgeBand = null,
        dramaticRole = null,
        avatarPath = null,
    )

    /** Strip fields the model must never see or set (avatar file path). */
    fun stripAiHiddenFromPatch(patch: CharacterCardPatch): CharacterCardPatch =
        patch.copy(avatarPath = null)

    fun stripToolMap(map: Map<String, Any?>): Map<String, Any?> =
        map.filterKeys { it !in PERFORMANCE_TOOL_MAP_KEYS && it !in AI_HIDDEN_KEYS }

    fun stripAiHiddenFromMap(map: Map<String, Any?>): Map<String, Any?> =
        map.filterKeys { it !in AI_HIDDEN_KEYS }

    fun stripToolJson(json: String): String {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map = runCatching { GSON.fromJson<Map<String, Any?>>(json, type) }.getOrNull()
            ?: return json
        return GSON.toJson(stripAiHiddenFromMap(stripToolMap(map)))
    }

    fun stripAiHiddenFromJson(json: String): String {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val map = runCatching { GSON.fromJson<Map<String, Any?>>(json, type) }.getOrNull()
            ?: return json
        return GSON.toJson(stripAiHiddenFromMap(map))
    }

    fun filterFieldChanges(changes: List<FieldChange>): List<FieldChange> =
        changes.filter { change ->
            PERFORMANCE_FIELD_CHANGES.none { change.path.endsWith(it) } &&
                AI_HIDDEN_KEYS.none { change.path.endsWith(it) }
        }

    fun adaptToolForMode(tool: AiToolDefinition, conversationType: String): AiToolDefinition {
        if (!isWritingMode(conversationType)) return tool
        if (tool.name != "read_character_card" && tool.name != "patch_character_card" &&
            tool.name != "list_character_cards"
        ) return tool
        return tool.copy(
            description = writingToolDescription(tool.name, tool.description),
            inputSchema = filterToolSchema(tool.inputSchema),
        )
    }

    private fun writingToolDescription(name: String, description: String): String = when (name) {
        "read_character_card" ->
            "Read one character card by cardId, or list book-bound cards for a bookshelf book. " +
                "Writing mode returns story fields only (no dramatic role, aliases, or voice metadata)."
        "patch_character_card" ->
            "Create or patch a character card for writing roleplay. Set mode=generate to AI-generate from " +
                "conversation; otherwise patch only provided story fields. " +
                "Dramatic role, aliases, and voice hints are not available in writing mode."
        "list_character_cards" ->
            "List character cards in the library (global catalog), optionally filtered by keyword or bound book. " +
                "Writing mode returns story fields only (no aliases or voice metadata)."
        else -> description
    }

    @Suppress("UNCHECKED_CAST")
    fun filterToolSchema(schema: Map<String, Any?>): Map<String, Any?> {
        val properties = schema["properties"] as? Map<String, Any?> ?: return schema
        val filtered = properties.filterKeys {
            it !in PERFORMANCE_TOOL_SCHEMA_KEYS && it !in AI_HIDDEN_KEYS
        }
        return schema.toMutableMap().apply { put("properties", filtered) }
    }

    fun stripPerformanceArgs(args: JsonObject): JsonObject {
        val copy = args.deepCopy()
        PERFORMANCE_TOOL_SCHEMA_KEYS.forEach { copy.remove(it) }
        AI_HIDDEN_KEYS.forEach { copy.remove(it) }
        return copy
    }

    fun stripAiHiddenFromArgs(args: JsonObject): JsonObject {
        val copy = args.deepCopy()
        AI_HIDDEN_KEYS.forEach { copy.remove(it) }
        return copy
    }
}
