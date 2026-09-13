package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiHtmlApp
import kotlinx.coroutines.flow.Flow

@Dao
interface AiHtmlAppDao {
    @Query("SELECT * FROM ai_html_apps WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun observeByConversation(conversationId: String): Flow<List<AiHtmlApp>>

    @Query("SELECT * FROM ai_html_apps WHERE id = :id")
    suspend fun getById(id: String): AiHtmlApp?

    @Query("SELECT * FROM ai_html_apps WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): AiHtmlApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AiHtmlApp)

    @Query("DELETE FROM ai_html_apps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_html_apps WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
