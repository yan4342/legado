package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiToolConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AiToolConfigDao {

    @Query("SELECT * FROM ai_tool_configs ORDER BY toolName ASC")
    fun observeAll(): Flow<List<AiToolConfig>>

    @Query("SELECT * FROM ai_tool_configs ORDER BY toolName ASC")
    suspend fun getAll(): List<AiToolConfig>

    @Query("SELECT * FROM ai_tool_configs WHERE toolName = :toolName")
    suspend fun getByToolName(toolName: String): AiToolConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AiToolConfig)

    @Query("DELETE FROM ai_tool_configs WHERE toolName = :toolName")
    suspend fun delete(toolName: String)
}
