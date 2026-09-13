package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableExport
import io.legado.app.data.entities.AiMemoryTableRow
import kotlinx.coroutines.flow.Flow

interface AiMemoryTableGateway {
    // Tables
    fun observeByConversation(conversationId: String): Flow<List<AiMemoryTable>>
    fun observeByBookUrl(bookUrl: String): Flow<List<AiMemoryTable>>

    /** Tables bound by bookUrl or legacy bookName+bookAuthor metadata. */
    fun observeForBook(bookUrl: String, bookName: String, bookAuthor: String): Flow<List<AiMemoryTable>>
    fun observeAll(): Flow<List<AiMemoryTable>>
    suspend fun <R> withTransaction(block: suspend () -> R): R
    suspend fun getTable(id: String): AiMemoryTable?
    suspend fun getByBookUrl(bookUrl: String): List<AiMemoryTable>
    suspend fun getEnabled(): List<AiMemoryTable>
    suspend fun getEnabledForConversation(conversationId: String): List<AiMemoryTable>
    suspend fun getTablesForConversation(conversationId: String): List<AiMemoryTable>
    suspend fun upsertTable(table: AiMemoryTable)
    suspend fun toggleEnabled(id: String)
    suspend fun setAllEnabled(enabled: Boolean)
    suspend fun updateEnabled(id: String, enabled: Boolean)
    suspend fun deleteTable(id: String)
    suspend fun deleteAllForConversation(conversationId: String)
    /** Remove global, NULL, or dangling conversation tables. Returns count removed. */
    suspend fun cleanupOrphanTables(): Int

    // Rows
    fun observeRows(tableId: String): Flow<List<AiMemoryTableRow>>
    suspend fun getRows(tableId: String): List<AiMemoryTableRow>
    suspend fun getRecentRows(tableId: String, limit: Int): List<AiMemoryTableRow>
    suspend fun getRow(id: String): AiMemoryTableRow?
    suspend fun upsertRow(row: AiMemoryTableRow)
    suspend fun deleteRow(id: String)
    suspend fun deleteAllRows(tableId: String)
    suspend fun deleteBySourceMessageId(messageId: String)
    suspend fun replaceAllRows(tableId: String, rows: List<AiMemoryTableRow>)

    // Import / Export
    suspend fun exportForConversation(conversationId: String): AiMemoryTableExport
    suspend fun importFromExport(export: AiMemoryTableExport)
    suspend fun copyTablesToConversation(sourceConversationId: String, targetConversationId: String): Int
}
