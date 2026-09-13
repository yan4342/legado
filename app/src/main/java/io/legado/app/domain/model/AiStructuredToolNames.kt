package io.legado.app.domain.model

/**
 * Canonical structured-data tool names exposed to the model and UI.
 */
object AiStructuredToolNames {

    const val READ_HISTORY_MEMORY = "read_history_memory"
    const val PATCH_HISTORY_MEMORY = "patch_history_memory"
    const val READ_OUTLINE = "read_outline"
    const val PATCH_OUTLINE = "patch_outline"
    const val READ_CHARACTER_CARD = "read_character_card"
    const val PATCH_CHARACTER_CARD = "patch_character_card"
    const val LIST_CHARACTER_CARDS = "list_character_cards"
    const val READ_USER_CARD = "read_user_card"
    const val PATCH_USER_CARD = "patch_user_card"
    const val READ_USER_MEMORY = "read_user_memory"
    const val PATCH_USER_MEMORY = "patch_user_memory"
    const val READ_WORLD_BOOK = "read_world_book"
    const val PATCH_WORLD_BOOK = "patch_world_book"
    const val EXTRACT_WORLD_BOOK = "extract_world_book"

    enum class StructuredResource {
        MEMORY_TABLE, OUTLINE, CHARACTER, USER, USER_MEMORY, WORLD_BOOK,
    }

    fun structuredResource(toolName: String): StructuredResource? = when (toolName) {
        READ_HISTORY_MEMORY, PATCH_HISTORY_MEMORY -> StructuredResource.MEMORY_TABLE
        READ_OUTLINE, PATCH_OUTLINE -> StructuredResource.OUTLINE
        READ_CHARACTER_CARD, PATCH_CHARACTER_CARD, LIST_CHARACTER_CARDS -> StructuredResource.CHARACTER
        READ_USER_CARD, PATCH_USER_CARD -> StructuredResource.USER
        READ_USER_MEMORY, PATCH_USER_MEMORY -> StructuredResource.USER_MEMORY
        READ_WORLD_BOOK, PATCH_WORLD_BOOK, EXTRACT_WORLD_BOOK -> StructuredResource.WORLD_BOOK
        else -> null
    }

    fun displayName(toolName: String): String = when (toolName) {
        READ_HISTORY_MEMORY -> "Read history memory"
        PATCH_HISTORY_MEMORY -> "Patch history memory"
        READ_OUTLINE -> "Read outline"
        PATCH_OUTLINE -> "Patch outline"
        READ_CHARACTER_CARD -> "Read character card"
        PATCH_CHARACTER_CARD -> "Patch character card"
        LIST_CHARACTER_CARDS -> "List character cards"
        READ_USER_CARD -> "Read user persona"
        PATCH_USER_CARD -> "Patch user persona"
        READ_USER_MEMORY -> "Read habit memory"
        PATCH_USER_MEMORY -> "Patch habit memory"
        READ_WORLD_BOOK -> "Read world book"
        PATCH_WORLD_BOOK -> "Patch world book"
        EXTRACT_WORLD_BOOK -> "Extract / create world book"
        "ask_user_questions" -> "Ask user questions"
        else -> toolName
    }
}
