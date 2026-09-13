package io.legado.app.domain.gateway

import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.domain.prompt.PromptPipelinePreset
import kotlinx.coroutines.flow.Flow

interface AiPromptPipelineGateway {
    fun observeAll(): Flow<List<PromptPipelinePreset>>
    suspend fun getAll(): List<PromptPipelinePreset>
    suspend fun getById(id: String): PromptPipelinePreset?
    suspend fun getPresetForMode(mode: PromptPipelineMode): PromptPipelinePreset?
    suspend fun upsert(preset: PromptPipelinePreset)
    suspend fun deleteCustom(id: String)
    suspend fun ensureDefaults()
}
