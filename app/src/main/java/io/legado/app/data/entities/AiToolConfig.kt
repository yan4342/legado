package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_tool_configs")
data class AiToolConfig(
    @PrimaryKey
    val toolName: String,
    val displayName: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val useSubModel: Boolean = false,
    val subModelProfileId: String? = null,
    val subModelPrompt: String = "",
    @ColumnInfo(defaultValue = "12000")
    val maxChars: Int = 12000,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Whether sub-model delegation should run.
     * Skipped when the configured sub-model equals the active main chat model,
     * except for intentional second-pass tools (polish / maintain / game HUD).
     */
    fun shouldDelegateToSubModel(mainModelProfileId: String?): Boolean {
        if (!useSubModel) return false
        val subId = subModelProfileId?.takeIf { it.isNotBlank() } ?: return false
        if (toolName in INTENTIONAL_SECOND_PASS_TOOLS) return true
        return mainModelProfileId == null || subId != mainModelProfileId
    }

    companion object {
        /** Second-pass tools that still run when sub-model == main model. */
        val INTENTIONAL_SECOND_PASS_TOOLS = setOf(
            "post_edit",
            "galgame_hud",
            "patch_outline",
            "patch_history_memory",
        )
    }
}
