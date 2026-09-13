package io.legado.app.data.repository

import io.legado.app.data.dao.AiToolConfigDao
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.domain.model.AiStructuredToolNames

/**
 * One-time migration: seed canonical tool configs from legacy rows, then remove legacy rows.
 */
internal object AiToolConfigMigrator {

    private val canonicalLegacyGroups: Map<String, List<String>> = mapOf(
        AiStructuredToolNames.READ_HISTORY_MEMORY to listOf(
            "list_history_memory_tables", "get_history_memory_table",
        ),
        AiStructuredToolNames.PATCH_HISTORY_MEMORY to listOf(
            "add_history_table_row", "update_history_table_row", "delete_history_table_row",
            "update_history_memory_table", "generate_history_memory_table",
            "generate_single_history_table", "regenerate_history_memory_table",
        ),
        AiStructuredToolNames.READ_OUTLINE to listOf("get_outline"),
        AiStructuredToolNames.PATCH_OUTLINE to listOf("generate_outline"),
        AiStructuredToolNames.PATCH_CHARACTER_CARD to listOf("generate_character_card"),
        AiStructuredToolNames.READ_WORLD_BOOK to listOf("get_world_book"),
        AiStructuredToolNames.EXTRACT_WORLD_BOOK to listOf("create_world_book"),
        // Unified file CRUD replaces per-domain read/edit/write/delete tools.
        "read_file" to listOf(
            "read_html_chat_theme", "read_skill", "read_plan_file",
        ),
        "edit_file" to listOf(
            "edit_html_chat_theme", "edit_skill", "edit_plan_file",
        ),
        "write_file" to listOf(
            "write_html_chat_theme", "save_skill", "write_plan_file",
        ),
        "delete_file" to listOf(
            "delete_html_chat_theme_file", "delete_skill",
        ),
    )

    private val allLegacyToolNames: Set<String> =
        canonicalLegacyGroups.values.flatten().toSet()

    suspend fun migrateLegacyConfigsIfNeeded(dao: AiToolConfigDao) {
        val byName = dao.getAll().associateBy { it.toolName }
        for ((canonical, legacyNames) in canonicalLegacyGroups) {
            if (byName.containsKey(canonical)) continue
            val legacy = legacyNames.mapNotNull { byName[it] }
            if (legacy.isEmpty()) continue
            val subModelSource = legacy.firstOrNull { it.useSubModel } ?: legacy.first()
            dao.insert(
                AiToolConfig(
                    toolName = canonical,
                    displayName = legacy.firstOrNull { it.displayName.isNotBlank() }?.displayName
                        ?: AiStructuredToolNames.displayName(canonical),
                    description = legacy.firstOrNull { it.description.isNotBlank() }?.description.orEmpty(),
                    enabled = legacy.all { it.enabled },
                    useSubModel = legacy.any { it.useSubModel },
                    subModelProfileId = subModelSource.subModelProfileId,
                    subModelPrompt = subModelSource.subModelPrompt,
                    maxChars = subModelSource.maxChars,
                )
            )
        }
        for (legacyName in allLegacyToolNames) {
            dao.delete(legacyName)
        }
    }
}
