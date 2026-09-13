package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiTodo
import kotlinx.coroutines.flow.Flow

@Dao
interface AiTodoDao {

    /** 观察会话的任务清单（每会话至多一行；无行为 null）。 */
    @Query("SELECT * FROM ai_todos WHERE conversationId = :conversationId")
    fun observeByConversation(conversationId: String): Flow<AiTodo?>

    @Query("SELECT * FROM ai_todos WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): AiTodo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: AiTodo)

    @Query("DELETE FROM ai_todos WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
