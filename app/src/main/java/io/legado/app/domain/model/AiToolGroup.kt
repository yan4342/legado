package io.legado.app.domain.model

/**
 * Chat-mode runtime tool groups. Writing mode ignores groups.
 * Default active set is [CORE] only; the model opens more via set_tool_groups.
 */
enum class AiToolGroup(
    val id: String,
    /** Short English blurb for list_tool_groups. */
    val summary: String,
    /** User-intent keywords (中文+English) that should cause this group to be enabled via set_tool_groups. */
    val triggers: String,
) {
    CORE(
        "core",
        "Conversations, user card/habit memory, ask_user_questions, web config, tool-group meta tools",
        "",
    ),
    BOOKSHELF(
        "bookshelf",
        "Local bookshelf search, chapters, content, add-from-sources",
        "书架, 找书, 加书, 搜索书籍, 章节, 正文内容, 缓存章节, bookshelf, search books, add book, chapters",
    ),
    BOOK_SOURCE(
        "book_source",
        "Book-source agent: read/debug/patch/eval_js/versions",
        "书源, 漫画源, 图源, 修源, 修书源, 源坏了, 书源失效, 校验书源, 写书源, 调试书源, 书源规则, book source, manga source, comic source, fix/check/repair source, source rule",
    ),
    WORLD(
        "world",
        "World book read/patch/extract",
        "世界书, 世界观, 设定集, world book, worldbuilding",
    ),
    STORY(
        "story",
        "Outline, character cards, history memory table, workspace search",
        "大纲, 人物卡, 剧情, 历史记忆, 角色, 作品设定, outline, character card, story, history memory",
    ),
    SKILLS(
        "skills",
        "List / install skills (file CRUD lives in the file group)",
        "技能, skills, 安装技能",
    ),
    TTS(
        "tts",
        "Cloud/HttpTTS config and test",
        "朗读, 语音, 发音, 播报, TTS, 有声",
    ),
    HTML_THEME(
        "html_theme",
        "HTML AI-chat theme packs: list/diff/commit/rollback/set (file CRUD lives in the file group)",
        "主题, 聊天主题, HTML主题, chat theme, html theme",
    ),
    FILE(
        "file",
        "Generic file CRUD: read_file / edit_file / write_file / delete_file (theme:// skill:// plan://)",
        "文件, 读取文件, 编辑文件, file, read/edit/write file",
    ),
    HTML_APP(
        "html_app",
        "HTML app publishing, version control: list/publish/commit/rollback (file CRUD lives in the file group)",
        "HTML应用, 网页应用, 发布应用, html app, publish app",
    ),
    ;

    companion object {
        val DEFAULT_ACTIVE_IDS: Set<String> = setOf(CORE.id)

        fun fromId(id: String): AiToolGroup? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

        /** Resolve runtime group for a tool name. Unknown → CORE (still gated by enabled/mode). */
        fun ofTool(toolName: String): AiToolGroup = when (toolName) {
            "list_tool_groups", "set_tool_groups",
            "list_conversations", "read_conversation",
            "ask_user_questions",
            "read_user_card", "patch_user_card",
            "read_user_memory", "patch_user_memory",
            "web_search", "read_web_page",
            "read_web_search_page_config", "patch_web_search_page_config",
            "suggested_replies", "ai_help_reply", "post_edit",
            "update_todos",
            -> CORE

            "search_books", "search_book_sources", "add_book_to_bookshelf",
            "get_book_detail", "list_book_chapters", "get_chapter_content", "search_book_content",
            -> BOOKSHELF

            "search_rule_help", "eval_js",
            "list_book_source_versions", "diff_book_source_version", "restore_book_source_version",
            "read_book_source", "fetch_page_snippet", "check_book_source", "debug_book_source",
            "patch_book_source", "write_book_source_from_url",
            -> BOOK_SOURCE

            "extract_world_book", "read_world_book", "patch_world_book",
            -> WORLD

            "read_history_memory", "patch_history_memory",
            "read_outline", "patch_outline", "search_workspace", "search_context",
            "read_character_card", "patch_character_card", "list_character_cards",
            "galgame_hud",
            -> STORY

            "list_skills", "install_skill_from_url",
            -> SKILLS

            "read_tts_config", "read_cloud_tts_engine", "patch_cloud_tts_engine",
            "list_cloud_tts_voices", "read_http_tts", "patch_http_tts", "test_tts",
            "set_default_tts_engine", "export_cloud_tts_as_http_tts",
            -> TTS

            "list_html_chat_themes",
            "diff_html_chat_themes",
            "commit_html_chat_theme", "list_html_chat_theme_versions",
            "rollback_html_chat_theme", "set_html_chat_theme",
            -> HTML_THEME

            "read_file", "edit_file", "write_file", "delete_file",
            -> FILE

            "publish_html_app", "commit_html_app", "rollback_html_app",
            "list_html_app_versions", "list_html_apps",
            -> HTML_APP

            else -> CORE
        }
    }
}

/** Mutable active groups for one chat generation (thread-safe). */
class AiToolGroupState(
    initial: Set<String> = AiToolGroup.DEFAULT_ACTIVE_IDS,
) {
    private val lock = Any()
    private val active = initial.map { it.lowercase() }.toMutableSet().also {
        it.add(AiToolGroup.CORE.id)
    }

    fun snapshot(): Set<String> = synchronized(lock) { active.toSet() }

    fun isActive(groupId: String): Boolean = synchronized(lock) {
        active.contains(groupId.trim().lowercase())
    }

    /**
     * Enable/disable groups. [AiToolGroup.CORE] cannot be disabled.
     * Unknown ids are reported in the result JSON via [ApplyResult.unknown].
     * Enabling HTML_THEME or SKILLS auto-enables FILE (their file CRUD lives in the file group).
     */
    fun apply(enable: List<String>?, disable: List<String>?): ApplyResult = synchronized(lock) {
        val unknown = mutableListOf<String>()
        fun resolve(raw: String): AiToolGroup? {
            val g = AiToolGroup.fromId(raw)
            if (g == null && raw.isNotBlank()) unknown += raw.trim()
            return g
        }
        enable.orEmpty().forEach { raw ->
            resolve(raw)?.let { active.add(it.id) }
        }
        disable.orEmpty().forEach { raw ->
            val g = resolve(raw) ?: return@forEach
            if (g != AiToolGroup.CORE) active.remove(g.id)
        }
        active.add(AiToolGroup.CORE.id)
        // Enabling a file-dependent domain group brings the file group along (no extra friction).
        val fileDependent = setOf(AiToolGroup.HTML_THEME.id, AiToolGroup.SKILLS.id, AiToolGroup.HTML_APP.id)
        if (active.any { it in fileDependent }) {
            active.add(AiToolGroup.FILE.id)
        }
        ApplyResult(active = active.toSortedSet(), unknown = unknown)
    }

    data class ApplyResult(
        val active: Set<String>,
        val unknown: List<String>,
    )
}
