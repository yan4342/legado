package io.legado.app.data.repository

import io.legado.app.data.dao.AiOutlineDao
import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiOutlineExport
import io.legado.app.domain.gateway.AiOutlineGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiOutlineRepository(
    private val dao: AiOutlineDao
) : AiOutlineGateway {

    override fun observeByConversation(conversationId: String): Flow<AiOutline?> =
        dao.observeByConversation(conversationId)

    override fun observeByBookUrl(bookUrl: String): Flow<List<AiOutline>> =
        dao.observeByBookUrl(bookUrl)

    override suspend fun getByConversation(conversationId: String): AiOutline? =
        withContext(Dispatchers.IO) { dao.getByConversation(conversationId) }

    override suspend fun getByBookUrl(bookUrl: String): List<AiOutline> =
        withContext(Dispatchers.IO) { dao.getByBookUrl(bookUrl) }

    override suspend fun upsert(outline: AiOutline) = withContext(Dispatchers.IO) {
        dao.upsert(outline.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(conversationId: String) = withContext(Dispatchers.IO) {
        dao.delete(conversationId)
    }

    override suspend fun toggleEnabled(conversationId: String) = withContext(Dispatchers.IO) {
        dao.toggleEnabled(conversationId)
    }

    override suspend fun exportForConversation(conversationId: String): AiOutlineExport =
        withContext(Dispatchers.IO) {
            val outline = dao.getByConversation(conversationId)
            AiOutlineExport(
                content = outline?.content.orEmpty(),
                enabled = outline?.enabled ?: true,
                sourceConversationId = conversationId,
            )
        }

    override suspend fun importToConversation(
        targetConversationId: String,
        export: AiOutlineExport,
    ) = withContext(Dispatchers.IO) {
        dao.upsert(
            AiOutline(
                conversationId = targetConversationId,
                content = export.content,
                enabled = export.enabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun copyOutlineToConversation(
        fromConversationId: String,
        toConversationId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val source = dao.getByConversation(fromConversationId) ?: return@withContext false
        if (source.content.isBlank()) return@withContext false
        dao.upsert(
            source.copy(
                conversationId = toConversationId,
                updatedAt = System.currentTimeMillis(),
            )
        )
        true
    }

    override suspend fun cleanupOrphanOutlines(): Int = withContext(Dispatchers.IO) {
        dao.deleteOrphanOutlines()
    }
}
