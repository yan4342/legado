package io.legado.app.data.repository

import io.legado.app.data.dao.AiTodoDao
import io.legado.app.data.entities.AiTodo
import io.legado.app.domain.gateway.AiTodoGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiTodoRepository(
    private val dao: AiTodoDao
) : AiTodoGateway {

    override fun observeByConversation(conversationId: String): Flow<AiTodo?> =
        dao.observeByConversation(conversationId)

    override suspend fun getByConversation(conversationId: String): AiTodo? =
        withContext(Dispatchers.IO) { dao.getByConversation(conversationId) }

    override suspend fun upsert(todo: AiTodo) = withContext(Dispatchers.IO) {
        dao.upsert(todo.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteByConversation(conversationId: String) =
        withContext(Dispatchers.IO) { dao.deleteByConversation(conversationId) }
}
