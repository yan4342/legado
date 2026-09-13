package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiCharacterCard
import kotlinx.coroutines.flow.Flow

interface AiCharacterCardGateway {
    fun observeAll(): Flow<List<AiCharacterCard>>
    fun observeByBookUrl(bookUrl: String): Flow<List<AiCharacterCard>>
    suspend fun getAll(): List<AiCharacterCard>
    suspend fun getById(id: String): AiCharacterCard?
    suspend fun getByBookUrl(bookUrl: String): List<AiCharacterCard>
    suspend fun save(name: String, description: String, openingLine: String, worldBookIds: String = "", cardId: String? = null): AiCharacterCard
    suspend fun upsert(card: AiCharacterCard): AiCharacterCard
    suspend fun delete(id: String)
}
