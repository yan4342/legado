package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chat_conversations")
data class AiChatConversation(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(defaultValue = "chat")
    val type: String = "chat",
    val characterCardId: String? = null,
    val promptIds: String? = null,
    val reasoningLevel: String = "auto",
    val modelProfileId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val forkedFromConversationId: String? = null,
    val forkedAtMessageId: String? = null,
    val characterCardIds: String? = null,
    val galgameHudHtml: String? = null,
    @ColumnInfo(defaultValue = "0")
    val contextPromptTokens: Int = 0,
    @ColumnInfo(defaultValue = "'estimate'")
    val contextTokensSource: String = "estimate",
    @ColumnInfo(defaultValue = "1.0")
    val contextCalibrationScale: Float = 1f,
    @ColumnInfo(defaultValue = "''")
    val userName: String = "",
    @ColumnInfo(defaultValue = "''")
    val userDescription: String = "",
    @ColumnInfo(defaultValue = "0")
    val userCardEnabled: Boolean = false,
    val workspaceId: String? = null,
    /** Writing sub-mode: "roleplay" | "author". Ignored when [type] is not writing. */
    @ColumnInfo(defaultValue = "'roleplay'")
    val writingSubMode: String = "roleplay",
    /**
     * CSV allowlist of skillIds for SkillsCatalog injection.
     * null = all globally-enabled skills matching this conversation's mode.
     */
    val skillIds: String? = null,
    /** Per-conversation composer draft (text only; attachments are not persisted). */
    @ColumnInfo(defaultValue = "''")
    val draftText: String = "",
    /**
     * Non-empty when earlier history has been folded into an AI summary
     * (`compressContext`). Re-injected at the front of model context on
     * every request and restored on re-entry so compression survives restarts.
     */
    @ColumnInfo(defaultValue = "''")
    val compressedSummary: String = "",
    /**
     * AI 输出审批模式："auto" | "ask" | "plan"。每会话记忆。
     * ask = 原 confirmToolsBeforeExecute=true（写工具逐项确认）；plan = 计划模式。
     */
    @ColumnInfo(defaultValue = "'ask'")
    val outputMode: String = "ask",
) {
    /** Gson/backup may omit new fields; Room insert requires non-null strings. */
    fun ensurePersistDefaults(): AiChatConversation = copy(
        writingSubMode = runCatching { writingSubMode }.getOrNull()
            .let { if (it == "author") "author" else "roleplay" },
        draftText = runCatching { draftText }.getOrNull().orEmpty(),
        compressedSummary = runCatching { compressedSummary }.getOrNull().orEmpty(),
        outputMode = normalizeOutputMode(runCatching { outputMode }.getOrNull()),
    )

    companion object {
        /** 非法或空值回退 "ask"。 */
        fun normalizeOutputMode(raw: String?): String =
            if (raw == "auto" || raw == "plan") raw else "ask"
    }
}
