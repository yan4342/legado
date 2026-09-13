package io.legado.app.ui.config.ai

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.domain.model.AiToolGroup
import splitties.init.appCtx

@StringRes
fun toolCategoryRes(toolName: String): Int = when (AiToolGroup.ofTool(toolName)) {
    AiToolGroup.CORE -> R.string.ai_ability_category_core
    AiToolGroup.BOOKSHELF -> R.string.ai_ability_category_bookshelf
    AiToolGroup.BOOK_SOURCE -> R.string.ai_ability_category_book_source
    AiToolGroup.WORLD -> R.string.ai_ability_category_world_book
    AiToolGroup.STORY -> R.string.ai_ability_category_story
    AiToolGroup.SKILLS -> R.string.ai_ability_category_skills
    AiToolGroup.TTS -> R.string.ai_ability_category_tts
    AiToolGroup.HTML_THEME -> R.string.ai_ability_category_html_theme
    AiToolGroup.HTML_APP -> R.string.ai_ability_category_html_app
    AiToolGroup.FILE -> R.string.ai_ability_category_file
}

@StringRes
fun toolDisplayNameRes(toolName: String): Int = when (toolName) {
    "list_tool_groups" -> R.string.ai_tool_list_tool_groups
    "set_tool_groups" -> R.string.ai_tool_set_tool_groups
    "search_books" -> R.string.ai_tool_search_books
    "search_book_sources" -> R.string.ai_tool_search_book_sources
    "add_book_to_bookshelf" -> R.string.ai_tool_add_book_to_bookshelf
    "list_conversations" -> R.string.ai_tool_list_conversations
    "read_conversation" -> R.string.ai_tool_read_conversation
    "get_book_detail" -> R.string.ai_tool_get_book_detail
    "list_book_chapters" -> R.string.ai_tool_list_book_chapters
    "get_chapter_content" -> R.string.ai_tool_get_chapter_content
    "search_book_content" -> R.string.ai_tool_search_book_content
    "extract_world_book" -> R.string.ai_tool_extract_world_book
    "read_world_book" -> R.string.ai_tool_read_world_book
    "patch_world_book" -> R.string.ai_tool_patch_world_book
    "read_history_memory" -> R.string.ai_tool_read_history_memory
    "patch_history_memory" -> R.string.ai_tool_patch_history_memory
    "read_outline" -> R.string.ai_tool_read_outline
    "patch_outline" -> R.string.ai_tool_patch_outline
    "search_workspace" -> R.string.ai_tool_search_workspace
    "read_character_card" -> R.string.ai_tool_read_character_card
    "patch_character_card" -> R.string.ai_tool_patch_character_card
    "list_character_cards" -> R.string.ai_tool_list_character_cards
    "read_user_card" -> R.string.ai_tool_read_user_card
    "patch_user_card" -> R.string.ai_tool_patch_user_card
    "read_user_memory" -> R.string.ai_tool_read_user_memory
    "patch_user_memory" -> R.string.ai_tool_patch_user_memory
    "suggested_replies" -> R.string.ai_tool_suggested_replies
    "galgame_hud" -> R.string.ai_tool_galgame_hud
    "ai_help_reply" -> R.string.ai_tool_ai_help_reply
    "post_edit" -> R.string.ai_tool_post_edit
    "ask_user_questions" -> R.string.ai_tool_ask_user_questions
    "web_search" -> R.string.ai_tool_web_search
    "read_web_page" -> R.string.ai_tool_read_web_page
    "read_web_search_page_config" -> R.string.ai_tool_read_web_search_page_config
    "patch_web_search_page_config" -> R.string.ai_tool_patch_web_search_page_config
    "read_tts_config" -> R.string.ai_tool_read_tts_config
    "read_cloud_tts_engine" -> R.string.ai_tool_read_cloud_tts_engine
    "patch_cloud_tts_engine" -> R.string.ai_tool_patch_cloud_tts_engine
    "list_cloud_tts_voices" -> R.string.ai_tool_list_cloud_tts_voices
    "read_http_tts" -> R.string.ai_tool_read_http_tts
    "patch_http_tts" -> R.string.ai_tool_patch_http_tts
    "test_tts" -> R.string.ai_tool_test_tts
    "set_default_tts_engine" -> R.string.ai_tool_set_default_tts_engine
    "export_cloud_tts_as_http_tts" -> R.string.ai_tool_export_cloud_tts_as_http_tts
    "search_rule_help" -> R.string.ai_tool_search_rule_help
    "read_file" -> R.string.ai_tool_read_file
    "edit_file" -> R.string.ai_tool_edit_file
    "write_file" -> R.string.ai_tool_write_file
    "delete_file" -> R.string.ai_tool_delete_file
    "list_skills" -> R.string.ai_tool_list_skills
    "install_skill_from_url" -> R.string.ai_tool_install_skill_from_url
    "list_html_chat_themes" -> R.string.ai_tool_list_html_chat_themes
    "set_html_chat_theme" -> R.string.ai_tool_set_html_chat_theme
    "eval_js" -> R.string.ai_tool_eval_js
    "list_book_source_versions" -> R.string.ai_tool_list_book_source_versions
    "diff_book_source_version" -> R.string.ai_tool_diff_book_source_version
    "restore_book_source_version" -> R.string.ai_tool_restore_book_source_version
    "read_book_source" -> R.string.ai_tool_read_book_source
    "fetch_page_snippet" -> R.string.ai_tool_fetch_page_snippet
    "check_book_source" -> R.string.ai_tool_check_book_source
    "debug_book_source" -> R.string.ai_tool_debug_book_source
    "patch_book_source" -> R.string.ai_tool_patch_book_source
    "write_book_source_from_url" -> R.string.ai_tool_write_book_source_from_url
    else -> 0
}

/** Localized tool title for bubbles / approval panel (falls back to English default). */
fun resolvedToolDisplayName(toolName: String): String {
    val res = toolDisplayNameRes(toolName)
    return if (res != 0) appCtx.getString(res) else AiAbilityManagementViewModel.defaultDisplayName(toolName)
}

@StringRes
fun toolDescriptionRes(toolName: String): Int = when (toolName) {
    "search_books" -> R.string.ai_tool_desc_search_books
    "search_book_sources" -> R.string.ai_tool_desc_search_book_sources
    "add_book_to_bookshelf" -> R.string.ai_tool_desc_add_book_to_bookshelf
    "list_conversations" -> R.string.ai_tool_desc_list_conversations
    "read_conversation" -> R.string.ai_tool_desc_read_conversation
    "get_book_detail" -> R.string.ai_tool_desc_get_book_detail
    "list_book_chapters" -> R.string.ai_tool_desc_list_book_chapters
    "get_chapter_content" -> R.string.ai_tool_desc_get_chapter_content
    "search_book_content" -> R.string.ai_tool_desc_search_book_content
    "extract_world_book" -> R.string.ai_tool_desc_extract_world_book
    "read_world_book" -> R.string.ai_tool_desc_read_world_book
    "patch_world_book" -> R.string.ai_tool_desc_patch_world_book
    "read_history_memory" -> R.string.ai_tool_desc_read_history_memory
    "patch_history_memory" -> R.string.ai_tool_desc_patch_history_memory
    "read_outline" -> R.string.ai_tool_desc_read_outline
    "patch_outline" -> R.string.ai_tool_desc_patch_outline
    "search_workspace" -> R.string.ai_tool_desc_search_workspace
    "read_character_card" -> R.string.ai_tool_desc_read_character_card
    "patch_character_card" -> R.string.ai_tool_desc_patch_character_card
    "list_character_cards" -> R.string.ai_tool_desc_list_character_cards
    "read_user_card" -> R.string.ai_tool_desc_read_user_card
    "patch_user_card" -> R.string.ai_tool_desc_patch_user_card
    "read_user_memory" -> R.string.ai_tool_desc_read_user_memory
    "patch_user_memory" -> R.string.ai_tool_desc_patch_user_memory
    "suggested_replies" -> R.string.ai_tool_desc_suggested_replies
    "galgame_hud" -> R.string.ai_tool_desc_galgame_hud
    "ai_help_reply" -> R.string.ai_tool_desc_ai_help_reply
    "post_edit" -> R.string.ai_tool_desc_post_edit
    "ask_user_questions" -> R.string.ai_tool_desc_ask_user_questions
    "web_search" -> R.string.ai_tool_desc_web_search
    "read_web_page" -> R.string.ai_tool_desc_read_web_page
    "read_web_search_page_config" -> R.string.ai_tool_desc_read_web_search_page_config
    "patch_web_search_page_config" -> R.string.ai_tool_desc_patch_web_search_page_config
    "read_tts_config" -> R.string.ai_tool_desc_read_tts_config
    "read_cloud_tts_engine" -> R.string.ai_tool_desc_read_cloud_tts_engine
    "patch_cloud_tts_engine" -> R.string.ai_tool_desc_patch_cloud_tts_engine
    "list_cloud_tts_voices" -> R.string.ai_tool_desc_list_cloud_tts_voices
    "read_http_tts" -> R.string.ai_tool_desc_read_http_tts
    "patch_http_tts" -> R.string.ai_tool_desc_patch_http_tts
    "test_tts" -> R.string.ai_tool_desc_test_tts
    "set_default_tts_engine" -> R.string.ai_tool_desc_set_default_tts_engine
    "export_cloud_tts_as_http_tts" -> R.string.ai_tool_desc_export_cloud_tts_as_http_tts
    "search_rule_help" -> R.string.ai_tool_desc_search_rule_help
    "read_file" -> R.string.ai_tool_desc_read_file
    "edit_file" -> R.string.ai_tool_desc_edit_file
    "write_file" -> R.string.ai_tool_desc_write_file
    "delete_file" -> R.string.ai_tool_desc_delete_file
    "list_skills" -> R.string.ai_tool_desc_list_skills
    "install_skill_from_url" -> R.string.ai_tool_desc_install_skill_from_url
    "list_html_chat_themes" -> R.string.ai_tool_desc_list_html_chat_themes
    "set_html_chat_theme" -> R.string.ai_tool_desc_set_html_chat_theme
    "eval_js" -> R.string.ai_tool_desc_eval_js
    "list_book_source_versions" -> R.string.ai_tool_desc_list_book_source_versions
    "diff_book_source_version" -> R.string.ai_tool_desc_diff_book_source_version
    "restore_book_source_version" -> R.string.ai_tool_desc_restore_book_source_version
    "read_book_source" -> R.string.ai_tool_desc_read_book_source
    "fetch_page_snippet" -> R.string.ai_tool_desc_fetch_page_snippet
    "check_book_source" -> R.string.ai_tool_desc_check_book_source
    "debug_book_source" -> R.string.ai_tool_desc_debug_book_source
    "patch_book_source" -> R.string.ai_tool_desc_patch_book_source
    "write_book_source_from_url" -> R.string.ai_tool_desc_write_book_source_from_url
    "list_tool_groups" -> R.string.ai_tool_desc_list_tool_groups
    "set_tool_groups" -> R.string.ai_tool_desc_set_tool_groups
    "diff_html_chat_themes" -> R.string.ai_tool_desc_diff_html_chat_themes
    "list_html_chat_theme_versions" -> R.string.ai_tool_desc_list_html_chat_theme_versions
    "commit_html_chat_theme" -> R.string.ai_tool_desc_commit_html_chat_theme
    "rollback_html_chat_theme" -> R.string.ai_tool_desc_rollback_html_chat_theme
    "publish_html_app" -> R.string.ai_tool_desc_publish_html_app
    "commit_html_app" -> R.string.ai_tool_desc_commit_html_app
    "rollback_html_app" -> R.string.ai_tool_desc_rollback_html_app
    "list_html_app_versions" -> R.string.ai_tool_desc_list_html_app_versions
    "list_html_apps" -> R.string.ai_tool_desc_list_html_apps
    else -> 0
}
