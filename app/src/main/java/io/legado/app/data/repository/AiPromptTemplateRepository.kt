package io.legado.app.data.repository

import io.legado.app.data.dao.AiPromptTemplateDao
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiPromptTemplateRepository(
    private val dao: AiPromptTemplateDao
) : AiPromptTemplateGateway {

    override fun observeAll(): Flow<List<AiPromptTemplate>> = dao.observeAll()

    override suspend fun getByKey(key: String): AiPromptTemplate? = withContext(Dispatchers.IO) {
        dao.getByKey(key)
    }

    override suspend fun upsert(template: AiPromptTemplate) = withContext(Dispatchers.IO) {
        dao.upsert(template.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        dao.delete(key)
    }

    override suspend fun getPrompt(key: String): String = withContext(Dispatchers.IO) {
        dao.getByKey(key)?.content ?: AiPromptTemplate.DEFAULTS[key] ?: ""
    }
}
