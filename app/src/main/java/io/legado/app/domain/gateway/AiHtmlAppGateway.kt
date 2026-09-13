package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiHtmlApp
import kotlinx.coroutines.flow.Flow

/** HTML App 的一个已提交版本（来自 commits.log）。 */
data class HtmlAppVersion(
    val version: Int,
    val message: String,
    val timestamp: Long,
)

/** index.html 按行切片（供 AI 迭代时局部读取）。 */
data class HtmlAppFileSlice(
    val path: String,
    val content: String,
    val totalLines: Int,
    val offset: Int,
    val limit: Int,
    val truncated: Boolean,
)

/**
 * AI 生成的 HTML 应用存取。每个 app 是一个带版本控制的目录包：
 * `{filesDir}/html_apps/{appId}/` 下为 index.html + manifest.json + .snapshots/v{n} + commits.log。
 *
 * 版本控制语义（同主题包）：工作树 = live（writeFile/editFile 立即生效，标记 dirty），
 * 只有 commit 才升版本 + 快照 + 清 dirty；rollback 从快照恢复并生成新版本。
 * 元数据走 Room，源码走文件系统。
 */
interface AiHtmlAppGateway {
    fun observeByConversation(conversationId: String): Flow<List<AiHtmlApp>>
    suspend fun getById(id: String): AiHtmlApp?
    suspend fun getByMessageId(messageId: String): AiHtmlApp?

    /**
     * 创建（无 [appId]）或整体重写 + 自动提交（有 [appId]，属于同会话时）一个 HTML App。
     * 新 app 落 v1 快照；重写既有 app 覆写工作树并直接提交一个新版本。
     */
    suspend fun publish(
        title: String,
        html: String,
        conversationId: String,
        messageId: String = "",
        appId: String? = null,
    ): AiHtmlApp

    /** 工具执行时消息未落库，assistant 消息保存后回填 messageId。 */
    suspend fun updateMessageId(appId: String, messageId: String)

    /** 覆写工作树 index.html，标记 dirty。返回 null 表示成功，否则为错误信息。 */
    suspend fun writeFile(appId: String, content: String): String?

    /** 对工作树 index.html 做 StrReplace，标记 dirty。返回替换次数，失败返回错误信息。 */
    suspend fun editFile(appId: String, oldString: String, newString: String, replaceAll: Boolean): Either<String, Int>

    /** 提交工作树：升版本 + 快照 + 日志 + 清 dirty。无改动返回 null。 */
    suspend fun commit(appId: String, message: String?): AiHtmlApp?

    /** index.html 按行切片（offset 1-based，limit 默认 400）；不存在返回 null。 */
    suspend fun readHtmlSlice(appId: String, offset: Int = 1, limit: Int = 400): HtmlAppFileSlice?

    /** index.html 的绝对路径（供 file:// 加载）；不存在返回 null。 */
    suspend fun entryPath(appId: String): String?

    /** 回滚到某已提交版本（以 max(current, target)+1 生成新版本，保留历史）。 */
    suspend fun rollback(appId: String, version: Int): AiHtmlApp?

    /** 提交历史（最新在前）。 */
    suspend fun listVersions(appId: String): List<HtmlAppVersion>

    suspend fun delete(id: String)

    /** 删除整个会话的全部 HTML App（实体 + 目录文件）。 */
    suspend fun deleteByConversation(conversationId: String)
}

/** 结果包装：Right=成功值，Left=错误信息。 */
sealed class Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>()
    data class Right<R>(val value: R) : Either<Nothing, R>()
}
