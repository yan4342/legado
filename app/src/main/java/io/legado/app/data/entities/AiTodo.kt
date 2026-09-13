package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话任务清单：AI 在多步任务中通过 update_todos 工具全量维护的进度清单。
 * 每会话一行；todosJson 存 JSON 数组 [{content, status, activeForm}]。
 * 与 ai_plans 不同：无需人工审批，模型高频自更新，UI 用 Room Flow 实时刷新。
 */
@Entity(tableName = "ai_todos")
data class AiTodo(
    @PrimaryKey val conversationId: String,
    /** JSON 数组：items 形如 {"content":"...","status":"pending|in_progress|completed","activeForm":"..."} */
    @ColumnInfo(defaultValue = "[]")
    val todosJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
