package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiPromptTemplate
import kotlinx.coroutines.flow.Flow

interface AiPromptTemplateGateway {
    fun observeAll(): Flow<List<AiPromptTemplate>>
    suspend fun getByKey(key: String): AiPromptTemplate?
    suspend fun upsert(template: AiPromptTemplate)
    suspend fun delete(key: String)
    suspend fun getPrompt(key: String): String
}
