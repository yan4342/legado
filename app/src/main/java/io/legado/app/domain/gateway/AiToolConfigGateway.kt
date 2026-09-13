package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiToolConfig
import kotlinx.coroutines.flow.Flow

interface AiToolConfigGateway {
    fun observeAll(): Flow<List<AiToolConfig>>
    suspend fun getByToolName(toolName: String): AiToolConfig?
    suspend fun save(config: AiToolConfig)
    suspend fun delete(toolName: String)
}
