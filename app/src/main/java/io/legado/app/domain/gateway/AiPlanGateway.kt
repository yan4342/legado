package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiPlan
import kotlinx.coroutines.flow.Flow

interface AiPlanGateway {
    fun observeByConversation(conversationId: String): Flow<List<AiPlan>>
    suspend fun getByConversation(conversationId: String): List<AiPlan>
    suspend fun getPendingByConversation(conversationId: String): AiPlan?
    suspend fun getById(planId: String): AiPlan?
    suspend fun upsert(plan: AiPlan)
    suspend fun updateStatus(planId: String, status: String)
    /** 新计划创建时，把会话内旧的 pending 计划标记为 superseded。 */
    suspend fun supersedePending(conversationId: String)
    suspend fun delete(planId: String)
    suspend fun deleteByConversation(conversationId: String)
}
