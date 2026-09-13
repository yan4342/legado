package io.legado.app.domain.model

/**
 * Shared AI tool name constants.
 * Packaged Skills live under `assets/ai_skills/{id}/SKILL.md` (+ optional resource `.md` files)
 * and are seeded by [io.legado.app.data.repository.AiSkillRepository].
 * Tool enable/disable remains in Ability Management.
 */
object BuiltinSkillCatalog {

    const val TOOL_EVAL_JS = "eval_js"
    const val SKILL_MIMO_TTS = "mimo-tts"

    /** Known tool names (legacy validation / reference). */
    val KNOWN_TOOLS: Set<String> = setOf(
        "search_books", "search_book_sources", "add_book_to_bookshelf",
        "get_book_detail", "list_book_chapters", "get_chapter_content", "search_book_content",
        "list_conversations", "read_conversation",
        "read_history_memory", "patch_history_memory",
        "read_outline", "patch_outline",
        "read_character_card", "patch_character_card", "list_character_cards",
        "read_user_card", "patch_user_card",
        "read_user_memory", "patch_user_memory",
        "read_world_book", "patch_world_book", "extract_world_book",
        "search_workspace",
        "web_search", "read_web_page",
        "read_web_search_page_config", "patch_web_search_page_config",
        "read_tts_config", "read_cloud_tts_engine", "patch_cloud_tts_engine",
        "list_cloud_tts_voices", "read_http_tts", "patch_http_tts", "test_tts",
        "set_default_tts_engine", "export_cloud_tts_as_http_tts",
        "ask_user_questions", "suggested_replies", "ai_help_reply", "post_edit", "galgame_hud",
        "list_html_chat_themes", "diff_html_chat_themes", "commit_html_chat_theme",
        "list_html_chat_theme_versions", "rollback_html_chat_theme", "set_html_chat_theme",
        "read_file", "edit_file", "write_file", "delete_file",
        "search_rule_help",
        "list_book_source_versions", "diff_book_source_version", "restore_book_source_version",
        "read_book_source", "fetch_page_snippet", "check_book_source", "debug_book_source",
        "patch_book_source", "write_book_source_from_url",
        TOOL_EVAL_JS,
    )

    const val SKILL_HTML_CHAT_THEME = "html-chat-theme"
}
