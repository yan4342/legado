package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiPromptPipelinePreset
import kotlinx.coroutines.flow.Flow

@Dao
interface AiPromptPipelinePresetDao {
    @Query("SELECT * FROM ai_prompt_pipeline_presets ORDER BY mode, name")
    fun observeAll(): Flow<List<AiPromptPipelinePreset>>

    @Query("SELECT * FROM ai_prompt_pipeline_presets ORDER BY mode, name")
    suspend fun getAll(): List<AiPromptPipelinePreset>

    @Query("SELECT * FROM ai_prompt_pipeline_presets WHERE id = :id")
    suspend fun getById(id: String): AiPromptPipelinePreset?

    @Query("SELECT * FROM ai_prompt_pipeline_presets WHERE mode = :mode AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultForMode(mode: String): AiPromptPipelinePreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: AiPromptPipelinePreset)

    @Query("DELETE FROM ai_prompt_pipeline_presets WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustom(id: String)
}
