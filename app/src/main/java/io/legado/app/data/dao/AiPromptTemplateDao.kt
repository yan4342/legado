package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiPromptTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface AiPromptTemplateDao {

    @Query("SELECT * FROM ai_prompt_templates ORDER BY promptKey")
    fun observeAll(): Flow<List<AiPromptTemplate>>

    @Query("SELECT * FROM ai_prompt_templates ORDER BY promptKey")
    suspend fun getAll(): List<AiPromptTemplate>

    @Query("SELECT * FROM ai_prompt_templates WHERE promptKey = :key")
    suspend fun getByKey(key: String): AiPromptTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: AiPromptTemplate)

    @Query("DELETE FROM ai_prompt_templates WHERE promptKey = :key")
    suspend fun delete(key: String)
}
