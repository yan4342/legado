package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "ai_writing_prompts")
data class AiWritingPrompt(
    @PrimaryKey
    val id: String,
    val name: String,
    val content: String = "",
    val category: String = "style",
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        data class Seed(val id: String, val name: String, val content: String, val category: String)

        const val WPROMPT_STYLE_CLASSICAL = "wprompt_style_classical"
        const val WPROMPT_STYLE_MODERN = "wprompt_style_modern"
        const val WPROMPT_STYLE_DETAILED = "wprompt_style_detailed"
        const val WPROMPT_APPROACH_FIRST = "wprompt_approach_first"
        const val WPROMPT_APPROACH_THIRD = "wprompt_approach_third"
        const val WPROMPT_APPROACH_DETAIL = "wprompt_approach_detail"
        const val WPROMPT_ACTION_CONTINUE = "wprompt_action_continue"
        const val WPROMPT_HELP_REPLY_SYSTEM = "wprompt_help_reply_system"
        const val WPROMPT_HELP_REPLY_WITH_DRAFT = "wprompt_help_reply_with_draft"
        const val WPROMPT_HELP_REPLY_EMPTY = "wprompt_help_reply_empty"
        const val CATEGORY_ACTION_HELP_REPLY = "action_help_reply"

        private val WRITING_DEFAULTS = mapOf(
            WPROMPT_STYLE_CLASSICAL to
                "使用古风文风写作，语言优美典雅，带有古典韵味，善用诗词典故和文言词汇。",
            WPROMPT_STYLE_MODERN to
                "使用现代白话文写作，语言流畅自然，贴近日常表达，避免过度修饰。",
            WPROMPT_STYLE_DETAILED to
                "文字细腻，注重情感表达和氛围营造，适合描写人物内心和情感纠葛。",
            WPROMPT_APPROACH_FIRST to
                "使用第一人称视角\"我\"进行写作，让读者代入主角视角，展示主角的内心想法和感受。",
            WPROMPT_APPROACH_THIRD to
                "使用第三人称视角进行写作，客观叙述故事，可以切换不同角色的视角。",
            WPROMPT_APPROACH_DETAIL to
                "注重环境、动作、神态和心理的细节描写，让场景和人物更加生动立体。",
            WPROMPT_ACTION_CONTINUE to
                "请根据对话历史，以角色的身份续写故事。保持角色性格、说话方式和文风的一致性，推动情节发展。",
        )

        private fun defaultContent(id: String): String = when (id) {
            WPROMPT_HELP_REPLY_SYSTEM ->
                AiPromptTemplate.DEFAULTS[AiPromptTemplate.HELP_REPLY_SYSTEM_PROMPT].orEmpty()
            WPROMPT_HELP_REPLY_WITH_DRAFT ->
                AiPromptTemplate.DEFAULTS[AiPromptTemplate.HELP_REPLY_WITH_DRAFT].orEmpty()
            WPROMPT_HELP_REPLY_EMPTY ->
                AiPromptTemplate.DEFAULTS[AiPromptTemplate.HELP_REPLY_EMPTY].orEmpty()
            else -> WRITING_DEFAULTS[id].orEmpty()
        }

        val HELP_REPLY_SEEDS = listOf(
            Seed(WPROMPT_HELP_REPLY_SYSTEM, "帮答·系统指令", defaultContent(WPROMPT_HELP_REPLY_SYSTEM), CATEGORY_ACTION_HELP_REPLY),
            Seed(WPROMPT_HELP_REPLY_WITH_DRAFT, "帮答·有草稿", defaultContent(WPROMPT_HELP_REPLY_WITH_DRAFT), CATEGORY_ACTION_HELP_REPLY),
            Seed(WPROMPT_HELP_REPLY_EMPTY, "帮答·无草稿", defaultContent(WPROMPT_HELP_REPLY_EMPTY), CATEGORY_ACTION_HELP_REPLY),
        )

        val SEED_PROMPTS = listOf(
            Seed(WPROMPT_STYLE_CLASSICAL, "古风", defaultContent(WPROMPT_STYLE_CLASSICAL), "style"),
            Seed(WPROMPT_STYLE_MODERN, "现代", defaultContent(WPROMPT_STYLE_MODERN), "style"),
            Seed(WPROMPT_STYLE_DETAILED, "细腻", defaultContent(WPROMPT_STYLE_DETAILED), "style"),
            Seed(WPROMPT_APPROACH_FIRST, "第一人称", defaultContent(WPROMPT_APPROACH_FIRST), "approach"),
            Seed(WPROMPT_APPROACH_THIRD, "第三人称", defaultContent(WPROMPT_APPROACH_THIRD), "approach"),
            Seed(WPROMPT_APPROACH_DETAIL, "细节描写", defaultContent(WPROMPT_APPROACH_DETAIL), "approach"),
            Seed(WPROMPT_ACTION_CONTINUE, "默认续写指令", defaultContent(WPROMPT_ACTION_CONTINUE), "action_continue"),
        ) + HELP_REPLY_SEEDS

        fun helpReplyTemplateKey(writingPromptId: String): String? = when (writingPromptId) {
            WPROMPT_HELP_REPLY_SYSTEM -> AiPromptTemplate.HELP_REPLY_SYSTEM_PROMPT
            WPROMPT_HELP_REPLY_WITH_DRAFT -> AiPromptTemplate.HELP_REPLY_WITH_DRAFT
            WPROMPT_HELP_REPLY_EMPTY -> AiPromptTemplate.HELP_REPLY_EMPTY
            else -> null
        }

        fun seedWritingPrompts(db: SupportSQLiteDatabase, prompts: List<Seed>, sortOrderStart: Int = 0) {
            val now = System.currentTimeMillis()
            prompts.forEachIndexed { index, (id, name, content, category) ->
                db.execSQL(
                    """INSERT OR IGNORE INTO ai_writing_prompts(id, name, content, category, sortOrder, enabled, createdAt, updatedAt)
                       VALUES ('$id', '${name.replace("'", "''")}', '${content.replace("'", "''")}', '$category', ${sortOrderStart + index}, 1, $now, $now)"""
                )
            }
        }

        fun seedDefaults(db: SupportSQLiteDatabase) {
            seedWritingPrompts(db, SEED_PROMPTS)
        }
    }
}
