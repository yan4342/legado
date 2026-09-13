package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMemoryTableDao {

    // ---- Table CRUD ----

    @Query("SELECT * FROM ai_memory_tables WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    fun observeByConversation(conversationId: String): Flow<List<AiMemoryTable>>

    @Query("SELECT * FROM ai_memory_tables ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiMemoryTable>>

    @Query(
        "SELECT * FROM ai_memory_tables WHERE bookUrl = :bookUrl AND bookUrl != '' AND canonical = 1 ORDER BY updatedAt DESC"
    )
    fun observeByBookUrl(bookUrl: String): Flow<List<AiMemoryTable>>

    @Query(
        "SELECT * FROM ai_memory_tables WHERE bookUrl = :bookUrl AND bookUrl != '' AND canonical = 1 ORDER BY updatedAt DESC"
    )
    suspend fun getByBookUrl(bookUrl: String): List<AiMemoryTable>

    @Query(
        """
        SELECT * FROM ai_memory_tables
        WHERE (bookUrl = :bookUrl AND bookUrl != '' AND canonical = 1)
           OR (
                bookName = :bookName AND bookAuthor = :bookAuthor
                AND bookName != '' AND bookAuthor != ''
                AND canonical = 1
           )
        ORDER BY updatedAt DESC
        """
    )
    fun observeForBook(bookUrl: String, bookName: String, bookAuthor: String): Flow<List<AiMemoryTable>>

    @Query("SELECT * FROM ai_memory_tables")
    suspend fun getAllTables(): List<AiMemoryTable>

    @Query("SELECT * FROM ai_memory_table_rows")
    suspend fun getAllRows(): List<AiMemoryTableRow>

    @Query("SELECT * FROM ai_memory_tables WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    suspend fun getAllTablesForConversation(conversationId: String): List<AiMemoryTable>

    @Query(
        "SELECT * FROM ai_memory_tables WHERE conversationId = :conversationId AND enabled = 1 ORDER BY updatedAt DESC"
    )
    suspend fun getEnabledForConversation(conversationId: String): List<AiMemoryTable>

    @Query("SELECT * FROM ai_memory_tables WHERE id = :id")
    suspend fun getTable(id: String): AiMemoryTable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTable(table: AiMemoryTable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTables(tables: List<AiMemoryTable>)

    @Query("UPDATE ai_memory_tables SET enabled = NOT enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ai_memory_tables SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_memory_tables WHERE id = :id")
    suspend fun deleteTable(id: String)

    @Query("DELETE FROM ai_memory_tables WHERE conversationId = :conversationId AND canonical = 0")
    suspend fun deleteAllForConversation(conversationId: String)

    /** Tables with NULL or empty conversationId (legacy global scope). */
    @Query("SELECT COUNT(*) FROM ai_memory_tables WHERE conversationId IS NULL OR conversationId = ''")
    suspend fun countGlobalTables(): Int

    @Query(
        "DELETE FROM ai_memory_table_rows WHERE tableId IN " +
            "(SELECT id FROM ai_memory_tables WHERE conversationId IS NULL OR conversationId = '')"
    )
    suspend fun deleteGlobalTableRows()

    @Query("DELETE FROM ai_memory_tables WHERE conversationId IS NULL OR conversationId = ''")
    suspend fun deleteGlobalTables()

    /** Delete tables whose conversationId is NULL (schema violation — invisible to normal queries). */
    @Query("SELECT COUNT(*) FROM ai_memory_tables WHERE conversationId IS NULL")
    suspend fun countOrphanTables(): Int

    @Query("DELETE FROM ai_memory_table_rows WHERE tableId IN (SELECT id FROM ai_memory_tables WHERE conversationId IS NULL)")
    suspend fun deleteOrphanTableRows()

    @Query("DELETE FROM ai_memory_tables WHERE conversationId IS NULL")
    suspend fun deleteOrphanTables()

    /** Delete tables whose conversationId points to a deleted conversation. */
    @Query("SELECT COUNT(*) FROM ai_memory_tables WHERE conversationId != '' AND conversationId NOT IN (SELECT id FROM ai_chat_conversations)")
    suspend fun countDanglingConversationTables(): Int

    @Query("DELETE FROM ai_memory_table_rows WHERE tableId IN (SELECT id FROM ai_memory_tables WHERE conversationId != '' AND conversationId NOT IN (SELECT id FROM ai_chat_conversations))")
    suspend fun deleteDanglingConversationRows()

    @Query("DELETE FROM ai_memory_tables WHERE conversationId != '' AND conversationId NOT IN (SELECT id FROM ai_chat_conversations)")
    suspend fun deleteDanglingConversationTables()

    @Query("UPDATE ai_memory_tables SET enabled = :enabled, updatedAt = :updatedAt")
    suspend fun updateAllEnabled(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    // ---- Row CRUD ----

    @Query("SELECT * FROM ai_memory_table_rows WHERE tableId = :tableId ORDER BY sortOrder, createdAt ASC")
    fun observeRows(tableId: String): Flow<List<AiMemoryTableRow>>

    @Query("SELECT * FROM ai_memory_table_rows WHERE tableId = :tableId ORDER BY sortOrder, createdAt ASC")
    suspend fun getRows(tableId: String): List<AiMemoryTableRow>

    @Query(
        "SELECT * FROM ai_memory_table_rows WHERE tableId = :tableId " +
            "ORDER BY updatedAt DESC, sortOrder ASC, createdAt ASC LIMIT :limit",
    )
    suspend fun getRecentRows(tableId: String, limit: Int): List<AiMemoryTableRow>

    @Query("SELECT * FROM ai_memory_table_rows WHERE tableId IN (:tableIds) ORDER BY sortOrder, createdAt ASC")
    suspend fun getRowsForTables(tableIds: List<String>): List<AiMemoryTableRow>

    @Query("SELECT * FROM ai_memory_table_rows WHERE id = :id")
    suspend fun getRow(id: String): AiMemoryTableRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRow(row: AiMemoryTableRow)

    @Query("DELETE FROM ai_memory_table_rows WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Query("DELETE FROM ai_memory_table_rows WHERE tableId = :tableId")
    suspend fun deleteAllRows(tableId: String)

    @Query("DELETE FROM ai_memory_table_rows WHERE sourceMessageId = :messageId")
    suspend fun deleteBySourceMessageId(messageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRows(rows: List<AiMemoryTableRow>)
}
