package io.legado.app.data.repository

import io.legado.app.data.dao.AiBookOutlineDao
import io.legado.app.data.entities.AiBookOutline
import io.legado.app.domain.gateway.AiBookOutlineGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiBookOutlineRepository(
    private val dao: AiBookOutlineDao,
) : AiBookOutlineGateway {

    override suspend fun getByBookUrl(bookUrl: String): AiBookOutline? =
        withContext(Dispatchers.IO) { dao.getByBookUrl(bookUrl) }

    override fun observeByBookUrl(bookUrl: String): Flow<AiBookOutline?> =
        dao.observeByBookUrl(bookUrl)

    override suspend fun getAll(): List<AiBookOutline> =
        withContext(Dispatchers.IO) { dao.getAll() }

    override suspend fun upsert(outline: AiBookOutline) = withContext(Dispatchers.IO) {
        dao.upsert(outline.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun toggleEnabled(bookUrl: String) = withContext(Dispatchers.IO) {
        dao.toggleEnabled(bookUrl)
    }

    override suspend fun delete(bookUrl: String) = withContext(Dispatchers.IO) {
        dao.delete(bookUrl)
    }
}
