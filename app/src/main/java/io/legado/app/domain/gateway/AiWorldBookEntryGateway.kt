package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiWorldBookEntry
import kotlinx.coroutines.flow.Flow

interface AiWorldBookEntryGateway {
    fun observeForWorldBook(worldBookId: String): Flow<List<AiWorldBookEntry>>
    suspend fun getForWorldBook(worldBookId: String): List<AiWorldBookEntry>
    suspend fun getEnabledForWorldBook(worldBookId: String): List<AiWorldBookEntry>
    suspend fun getAllEnabled(): List<AiWorldBookEntry>
    suspend fun getById(id: String): AiWorldBookEntry?
    suspend fun upsert(entry: AiWorldBookEntry)
    suspend fun delete(id: String)
}
