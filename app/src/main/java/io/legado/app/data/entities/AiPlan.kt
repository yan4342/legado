package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划工件：独立于普通回复的 Claude-Code 式计划审批对象。
 * 内容以 plan_<planId>.md 文件为唯一来源（filesDir/ai_plans/<conversationId>/），
 * 本表只存状态与元数据；AI 工具与 UI 都读文件。
 */
@Entity(
    tableName = "ai_plans",
    indices = [Index(value = ["conversationId"])]
)
data class AiPlan(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** 时间线中承载该计划的 assistant 消息 id（正文渲染为指向计划文件的链接）。 */
    val messageId: String,
    @ColumnInfo(defaultValue = "pending")
    val status: String = STATUS_PENDING,
    @ColumnInfo(defaultValue = "0")
    val revision: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_SUPERSEDED = "superseded"
    }
}
