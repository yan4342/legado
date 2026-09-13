package io.legado.app.ui.ai.chat

import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.utils.GSON
import io.legado.app.utils.parseJsonStringMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTML App 游戏运行时状态的持久化：映射到 [AiMemoryTable]。
 *
 * 约定：每个会话一张表，表名 `_html_app_state`，单行 rowData 为
 * `{"key":"state","value":"<游戏JSON>"}`。首次读写自动建表。
 * 复用记忆表基础设施（CRUD/Flow/导入导出），无需新表。
 */
class HtmlAppStateManager(
    private val memoryTableGateway: AiMemoryTableGateway,
    private val conversationId: String,
) {
    private val tableName = "_html_app_state"

    suspend fun loadGameState(): String? = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext null
        val table = findTable() ?: return@withContext null
        val rows = memoryTableGateway.getRows(table.id)
        rows.firstNotNullOfOrNull { row ->
            val data = runCatching { parseJsonStringMap(row.rowData) }.getOrDefault(emptyMap())
            data["value"]?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun saveGameState(json: String) {
        if (conversationId.isBlank() || json.isBlank()) return
        val table = ensureTable()
        val value = GSON.toJson(json) // 内嵌字符串：value 字段存游戏 JSON 原文
        val rows = memoryTableGateway.getRows(table.id)
        if (rows.isEmpty()) {
            memoryTableGateway.upsertRow(
                AiMemoryTableRow(
                    id = "memrow_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                    tableId = table.id,
                    rowData = """{"key":"state","value":$value}""",
                ),
            )
        } else {
            memoryTableGateway.upsertRow(
                rows.first().copy(
                    rowData = """{"key":"state","value":$value}""",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun findTable(): AiMemoryTable? =
        memoryTableGateway.getTablesForConversation(conversationId)
            .firstOrNull { it.name == tableName }

    private suspend fun ensureTable(): AiMemoryTable {
        findTable()?.let { return it }
        val id = "memtable_${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"
        memoryTableGateway.upsertTable(
            AiMemoryTable(
                id = id,
                name = tableName,
                columns = """["key","value"]""",
                conversationId = conversationId,
                enabled = true,
            ),
        )
        return findTable() ?: error("Failed to create $tableName table")
    }
}
