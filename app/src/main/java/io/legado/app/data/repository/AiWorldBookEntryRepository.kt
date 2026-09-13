package io.legado.app.data.repository

import io.legado.app.data.dao.AiWorldBookEntryDao
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiWorldBookEntryRepository(
    private val dao: AiWorldBookEntryDao,
) : AiWorldBookEntryGateway {

    override fun observeForWorldBook(worldBookId: String): Flow<List<AiWorldBookEntry>> =
        dao.observeForWorldBook(worldBookId)

    override suspend fun getForWorldBook(worldBookId: String): List<AiWorldBookEntry> =
        withContext(Dispatchers.IO) { dao.getForWorldBook(worldBookId) }

    override suspend fun getEnabledForWorldBook(worldBookId: String): List<AiWorldBookEntry> =
        withContext(Dispatchers.IO) {
            if (worldBookId.isBlank()) emptyList()
            else dao.getEnabledForWorldBooks(listOf(worldBookId))
        }

    override suspend fun getAllEnabled(): List<AiWorldBookEntry> =
        withContext(Dispatchers.IO) { dao.getAllEnabled() }

    override suspend fun getById(id: String): AiWorldBookEntry? =
        withContext(Dispatchers.IO) { dao.getById(id) }

    override suspend fun upsert(entry: AiWorldBookEntry) = withContext(Dispatchers.IO) {
        dao.upsert(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
