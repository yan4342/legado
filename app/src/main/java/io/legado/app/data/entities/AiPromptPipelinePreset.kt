package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_prompt_pipeline_presets")
data class AiPromptPipelinePreset(
    @PrimaryKey
    val id: String,
    val name: String,
    val mode: String,
    val blocksJson: String,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
