package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 生成的 HTML 应用（游戏/可视化等）的元数据。
 * HTML 源码存于 app 私有目录 `files/html_apps/{id}.html`（可能很长，不落消息表）。
 * 消息里只存轻量 [io.legado.app.domain.model.AiMessagePart.HtmlAppRef]（含 appId）。
 */
@Entity(
    tableName = "ai_html_apps",
    indices = [Index("conversationId"), Index("messageId")],
)
data class AiHtmlApp(
    @PrimaryKey
    val id: String,                    // "htmlapp_" + UUID
    val conversationId: String,
    /** 关联的 assistant 消息 id；工具执行时消息尚未落库，先置空，保存后回填。 */
    val messageId: String = "",
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
