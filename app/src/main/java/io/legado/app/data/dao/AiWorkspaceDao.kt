package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiWorkspace
import kotlinx.coroutines.flow.Flow

@Dao
interface AiWorkspaceDao {

    @Query("SELECT * FROM ai_workspaces WHERE id = :id")
    suspend fun getById(id: String): AiWorkspace?

    @Query("SELECT * FROM ai_workspaces WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getByConversationId(conversationId: String): AiWorkspace?

    @Query("SELECT * FROM ai_workspaces WHERE conversationId = :conversationId LIMIT 1")
    fun observeByConversationId(conversationId: String): Flow<AiWorkspace?>

    @Query("SELECT * FROM ai_workspaces ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiWorkspace>>

    @Query("SELECT * FROM ai_workspaces ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AiWorkspace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: AiWorkspace)

    @Query("DELETE FROM ai_workspaces WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_workspaces WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)
}
