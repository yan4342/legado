package io.legado.app.data.repository

import io.legado.app.data.dao.AiPlanDao
import io.legado.app.data.entities.AiPlan
import io.legado.app.domain.gateway.AiPlanGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiPlanRepository(
    private val dao: AiPlanDao
) : AiPlanGateway {

    override fun observeByConversation(conversationId: String): Flow<List<AiPlan>> =
        dao.observeByConversation(conversationId)

    override suspend fun getByConversation(conversationId: String): List<AiPlan> =
        withContext(Dispatchers.IO) { dao.getByConversation(conversationId) }

    override suspend fun getPendingByConversation(conversationId: String): AiPlan? =
        withContext(Dispatchers.IO) { dao.getPendingByConversation(conversationId) }

    override suspend fun getById(planId: String): AiPlan? =
        withContext(Dispatchers.IO) { dao.getById(planId) }

    override suspend fun upsert(plan: AiPlan) = withContext(Dispatchers.IO) {
        dao.upsert(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun updateStatus(planId: String, status: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(planId, status, System.currentTimeMillis())
    }

    override suspend fun supersedePending(conversationId: String) = withContext(Dispatchers.IO) {
        dao.updateStatusByConversation(
            conversationId,
            AiPlan.STATUS_PENDING,
            AiPlan.STATUS_SUPERSEDED,
            System.currentTimeMillis(),
        )
    }

    override suspend fun delete(planId: String) = withContext(Dispatchers.IO) {
        dao.delete(planId)
    }

    override suspend fun deleteByConversation(conversationId: String) = withContext(Dispatchers.IO) {
        dao.deleteByConversation(conversationId)
    }
}
