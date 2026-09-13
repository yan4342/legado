package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiWritingPrompt
import kotlinx.coroutines.flow.Flow

@Dao
interface AiWritingPromptDao {

    @Query("SELECT * FROM ai_writing_prompts ORDER BY category, sortOrder, updatedAt DESC")
    fun observeAll(): Flow<List<AiWritingPrompt>>

    @Query("SELECT * FROM ai_writing_prompts WHERE category = :category ORDER BY sortOrder, updatedAt DESC")
    fun observeByCategory(category: String): Flow<List<AiWritingPrompt>>

    @Query("SELECT * FROM ai_writing_prompts WHERE id = :id")
    suspend fun getById(id: String): AiWritingPrompt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: AiWritingPrompt)

    @Query("DELETE FROM ai_writing_prompts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE ai_writing_prompts SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @get:Query("SELECT * FROM ai_writing_prompts")
    val all: List<AiWritingPrompt>
}
