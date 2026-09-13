package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiOutlineExport
import kotlinx.coroutines.flow.Flow

interface AiOutlineGateway {
    fun observeByConversation(conversationId: String): Flow<AiOutline?>
    fun observeByBookUrl(bookUrl: String): Flow<List<AiOutline>>
    suspend fun getByConversation(conversationId: String): AiOutline?
    suspend fun getByBookUrl(bookUrl: String): List<AiOutline>
    suspend fun upsert(outline: AiOutline)
    suspend fun delete(conversationId: String)
    suspend fun toggleEnabled(conversationId: String)
    suspend fun exportForConversation(conversationId: String): AiOutlineExport
    suspend fun importToConversation(targetConversationId: String, export: AiOutlineExport)
    suspend fun copyOutlineToConversation(fromConversationId: String, toConversationId: String): Boolean
    /** Delete outlines whose conversation no longer exists. Returns rows removed. */
    suspend fun cleanupOrphanOutlines(): Int
}
