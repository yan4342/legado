package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiTodo
import kotlinx.coroutines.flow.Flow

interface AiTodoGateway {
    fun observeByConversation(conversationId: String): Flow<AiTodo?>
    suspend fun getByConversation(conversationId: String): AiTodo?
    suspend fun upsert(todo: AiTodo)
    suspend fun deleteByConversation(conversationId: String)
}
