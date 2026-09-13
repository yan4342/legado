package io.legado.app.data.repository

import io.legado.app.data.dao.AiWorldBookDao
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.domain.gateway.AiWorldBookGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AiWorldBookRepository(
    private val dao: AiWorldBookDao
) : AiWorldBookGateway {

    override fun observeAll(): Flow<List<AiWorldBook>> = dao.observeAll()

    override fun observeEnabled(): Flow<List<AiWorldBook>> = dao.observeEnabled()

    override suspend fun getById(id: String): AiWorldBook? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override suspend fun getEnabled(): List<AiWorldBook> = withContext(Dispatchers.IO) {
        dao.all.filter { it.enabled }
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.updateEnabled(id, enabled)
    }

    override suspend fun toggleEnabled(id: String) = withContext(Dispatchers.IO) {
        dao.toggleEnabled(id)
    }

    override suspend fun save(
        name: String,
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
        writingStyle: String,
        grammar: String,
        plotSummary: String,
        representativeDialogues: String,
        representativeProse: String,
        sourceChapterIndices: String,
        worldBookId: String?,
        enabled: Boolean,
        canonical: Boolean,
    ): AiWorldBook = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = worldBookId?.takeIf { it.isNotBlank() }?.let { dao.getById(it) }
        val entity = AiWorldBook(
            id = existing?.id ?: "wbook_${UUID.randomUUID().toString().replace("-", "")}",
            name = name,
            bookUrl = bookUrl,
            bookName = bookName,
            bookAuthor = bookAuthor,
            canonical = existing?.canonical ?: canonical,
            writingStyle = writingStyle,
            grammar = grammar,
            plotSummary = plotSummary,
            representativeDialogues = representativeDialogues,
            representativeProse = representativeProse,
            sourceChapterIndices = sourceChapterIndices,
            enabled = existing?.enabled ?: enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        // Prefer UPDATE over INSERT OR REPLACE — REPLACE CASCADE-wipes lore entries.
        if (existing != null) {
            dao.update(entity)
        } else {
            dao.insert(entity)
        }
        entity
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
