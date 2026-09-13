package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiWritingPrompt
import kotlinx.coroutines.flow.Flow

interface AiWritingPromptGateway {
    fun observeAll(): Flow<List<AiWritingPrompt>>
    fun observeByCategory(category: String): Flow<List<AiWritingPrompt>>
    suspend fun getById(id: String): AiWritingPrompt?
    suspend fun save(name: String, content: String, category: String, promptId: String? = null): AiWritingPrompt
    suspend fun delete(id: String)
    suspend fun updateEnabled(id: String, enabled: Boolean)
}
