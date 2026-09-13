package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.utils.GSON
import com.google.gson.JsonParser

@Entity(tableName = "ai_skills")
data class AiSkill(
    @PrimaryKey
    val skillId: String,
    val name: String,
    val description: String = "",
    /** chat | writing | both */
    val mode: String = MODE_CHAT,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    /** JSON string array of tool names. */
    val toolNamesJson: String = "[]",
    /** JSON string array of doc ids (asset basename or imported file id). */
    val docIdsJson: String = "[]",
    /** One-line activation hint for the skills catalog. */
    val hint: String = "",
    /**
     * Core SKILL.md body — loaded on demand via search_rule_help (doc=SKILL), not injected into catalog.
     * Supporting resources (other doc ids) are very on-demand.
     */
    @ColumnInfo(defaultValue = "")
    val instruction: String = "",
    @ColumnInfo(defaultValue = "0")
    val builtin: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toolNames(): List<String> = parseStringList(toolNamesJson)

    fun docIds(): List<String> = parseStringList(docIdsJson)

    fun matchesMode(conversationType: String): Boolean = when (mode) {
        MODE_BOTH -> true
        MODE_CHAT -> conversationType == "chat"
        MODE_WRITING -> conversationType == "writing"
        else -> conversationType == mode
    }

    companion object {
        const val MODE_CHAT = "chat"
        const val MODE_WRITING = "writing"
        const val MODE_BOTH = "both"

        fun encodeList(items: List<String>): String = GSON.toJson(items)

        private fun parseStringList(raw: String): List<String> {
            val el = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return emptyList()
            if (!el.isJsonArray) return emptyList()
            return el.asJsonArray.mapNotNull { item ->
                item.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }
}
