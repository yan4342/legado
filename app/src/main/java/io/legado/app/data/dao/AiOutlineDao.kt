package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiOutline
import kotlinx.coroutines.flow.Flow

@Dao
interface AiOutlineDao {

    @Query("SELECT * FROM ai_outlines")
    suspend fun getAll(): List<AiOutline>

    @Query("SELECT * FROM ai_outlines WHERE conversationId = :conversationId")
    fun observeByConversation(conversationId: String): Flow<AiOutline?>

    @Query("SELECT * FROM ai_outlines WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): AiOutline?

    @Query(
        "SELECT * FROM ai_outlines WHERE bookUrl = :bookUrl AND bookUrl != '' ORDER BY updatedAt DESC"
    )
    fun observeByBookUrl(bookUrl: String): Flow<List<AiOutline>>

    @Query(
        "SELECT * FROM ai_outlines WHERE bookUrl = :bookUrl AND bookUrl != '' ORDER BY updatedAt DESC"
    )
    suspend fun getByBookUrl(bookUrl: String): List<AiOutline>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outline: AiOutline)

    @Query("DELETE FROM ai_outlines WHERE conversationId = :conversationId")
    suspend fun delete(conversationId: String)

    @Query("UPDATE ai_outlines SET enabled = NOT enabled WHERE conversationId = :conversationId")
    suspend fun toggleEnabled(conversationId: String)

    @Query(
        "DELETE FROM ai_outlines WHERE conversationId != '' " +
            "AND conversationId NOT IN (SELECT id FROM ai_chat_conversations)",
    )
    suspend fun deleteOrphanOutlines(): Int
}
