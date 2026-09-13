package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.AiMemoryTableDao
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableExport
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.domain.gateway.AiMemoryTableGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiMemoryTableRepository(
    private val dao: AiMemoryTableDao,
    private val db: AppDatabase,
) : AiMemoryTableGateway {

    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        withContext(Dispatchers.IO) {
            db.withTransaction { block() }
        }

    // ---- Tables ----

    override fun observeByConversation(conversationId: String): Flow<List<AiMemoryTable>> =
        dao.observeByConversation(conversationId)

    override fun observeByBookUrl(bookUrl: String): Flow<List<AiMemoryTable>> =
        dao.observeByBookUrl(bookUrl)

    override fun observeForBook(
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
    ): Flow<List<AiMemoryTable>> = dao.observeForBook(bookUrl, bookName, bookAuthor)

    override fun observeAll(): Flow<List<AiMemoryTable>> = dao.observeAll()

    override suspend fun getTable(id: String): AiMemoryTable? = withContext(Dispatchers.IO) {
        dao.getTable(id)
    }

    override suspend fun getByBookUrl(bookUrl: String): List<AiMemoryTable> =
        withContext(Dispatchers.IO) {
            dao.getByBookUrl(bookUrl)
        }

    override suspend fun getEnabled(): List<AiMemoryTable> = withContext(Dispatchers.IO) {
        dao.getAllTables().filter { it.enabled && it.conversationId.isNotBlank() }
    }

    override suspend fun getEnabledForConversation(conversationId: String): List<AiMemoryTable> =
        withContext(Dispatchers.IO) {
            AiMemoryTableRules.requireConversationId(conversationId)
            dao.getEnabledForConversation(conversationId)
        }

    override suspend fun getTablesForConversation(conversationId: String): List<AiMemoryTable> =
        withContext(Dispatchers.IO) {
            AiMemoryTableRules.requireConversationId(conversationId)
            dao.getAllTablesForConversation(conversationId)
        }

    override suspend fun upsertTable(table: AiMemoryTable) = withContext(Dispatchers.IO) {
        AiMemoryTableRules.requireTableBound(table)
        dao.upsertTable(table.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun toggleEnabled(id: String) = withContext(Dispatchers.IO) {
        dao.toggleEnabled(id)
    }

    override suspend fun setAllEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.updateAllEnabled(enabled)
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.updateEnabled(id, enabled)
    }

    override suspend fun deleteTable(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTable(id)
    }

    override suspend fun deleteAllForConversation(conversationId: String) = withContext(Dispatchers.IO) {
        dao.deleteAllForConversation(conversationId)
    }

    override suspend fun cleanupOrphanTables(): Int = withContext(Dispatchers.IO) {
        val count = dao.countGlobalTables() + dao.countOrphanTables() + dao.countDanglingConversationTables()
        dao.deleteGlobalTableRows()
        dao.deleteGlobalTables()
        dao.deleteOrphanTableRows()
        dao.deleteOrphanTables()
        dao.deleteDanglingConversationRows()
        dao.deleteDanglingConversationTables()
        count
    }

    // ---- Rows ----

    override fun observeRows(tableId: String): Flow<List<AiMemoryTableRow>> = dao.observeRows(tableId)

    override suspend fun getRows(tableId: String): List<AiMemoryTableRow> = withContext(Dispatchers.IO) {
        dao.getRows(tableId)
    }

    override suspend fun getRecentRows(tableId: String, limit: Int): List<AiMemoryTableRow> =
        withContext(Dispatchers.IO) {
            dao.getRecentRows(tableId, limit.coerceAtLeast(1))
        }

    override suspend fun getRow(id: String): AiMemoryTableRow? = withContext(Dispatchers.IO) {
        dao.getRow(id)
    }

    override suspend fun upsertRow(row: AiMemoryTableRow) = withContext(Dispatchers.IO) {
        dao.upsertRow(row.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteRow(id: String) = withContext(Dispatchers.IO) {
        dao.deleteRow(id)
    }

    override suspend fun deleteAllRows(tableId: String) = withContext(Dispatchers.IO) {
        dao.deleteAllRows(tableId)
    }

    override suspend fun deleteBySourceMessageId(messageId: String) = withContext(Dispatchers.IO) {
        dao.deleteBySourceMessageId(messageId)
    }

    override suspend fun replaceAllRows(tableId: String, rows: List<AiMemoryTableRow>) =
        withContext(Dispatchers.IO) {
            dao.deleteAllRows(tableId)
            if (rows.isNotEmpty()) {
                dao.upsertRows(rows)
            }
        }

    // ---- Import / Export ----

    override suspend fun exportForConversation(conversationId: String): AiMemoryTableExport =
        withContext(Dispatchers.IO) {
            AiMemoryTableRules.requireConversationId(conversationId)
            val tables = dao.getAllTablesForConversation(conversationId)
            val tableIds = tables.map { it.id }
            val allRows = if (tableIds.isNotEmpty()) dao.getRowsForTables(tableIds) else emptyList()
            val rowsByTable = allRows.groupBy { it.tableId }
            AiMemoryTableExport(
                conversationId = conversationId,
                tables = tables.map { table ->
                    AiMemoryTableExport.TableWithRows(
                        table = table,
                        rows = rowsByTable[table.id] ?: emptyList()
                    )
                }
            )
        }

    override suspend fun importFromExport(export: AiMemoryTableExport) = withContext(Dispatchers.IO) {
        AiMemoryTableRules.requireConversationId(export.conversationId)
        if (export.tables.isNotEmpty()) {
            export.tables.forEach { AiMemoryTableRules.requireTableBound(it.table) }
            dao.upsertTables(export.tables.map { it.table })
            val allRows = export.tables.flatMap { twr -> twr.rows.map { it.copy(tableId = twr.table.id) } }
            if (allRows.isNotEmpty()) {
                dao.upsertRows(allRows)
            }
        }
    }

    override suspend fun copyTablesToConversation(
        sourceConversationId: String,
        targetConversationId: String
    ): Int = withContext(Dispatchers.IO) {
        AiMemoryTableRules.requireConversationId(sourceConversationId)
        AiMemoryTableRules.requireConversationId(targetConversationId)
        val sourceTables = dao.getAllTablesForConversation(sourceConversationId)
        if (sourceTables.isEmpty()) return@withContext 0
        val tableIds = sourceTables.map { it.id }
        val allRows = if (tableIds.isNotEmpty()) dao.getRowsForTables(tableIds) else emptyList()
        val rowsByTable = allRows.groupBy { it.tableId }

        // Generate new IDs to avoid conflicts
        val idMap = mutableMapOf<String, String>() // oldTableId -> newTableId
        val newTables = sourceTables.map { oldTable ->
            val newId = "memtable_${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"
            idMap[oldTable.id] = newId
            oldTable.copy(
                id = newId,
                conversationId = targetConversationId,
                // 复制/派生到会话的目标一律落衍生层(正典行不随复制迁移)。
                canonical = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        val newRows = sourceTables.flatMap { oldTable ->
            val newTableId = idMap[oldTable.id]!!
            (rowsByTable[oldTable.id] ?: emptyList()).map { oldRow ->
                oldRow.copy(
                    id = "memrow_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                    tableId = newTableId,
                    sourceMessageId = null, // reset source reference in new conversation
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        dao.upsertTables(newTables)
        if (newRows.isNotEmpty()) {
            dao.upsertRows(newRows)
        }
        newTables.size
    }
}
