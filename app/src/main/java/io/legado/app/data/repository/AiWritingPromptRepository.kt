package io.legado.app.data.repository

import io.legado.app.data.dao.AiWritingPromptDao
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.domain.gateway.AiWritingPromptGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AiWritingPromptRepository(
    private val dao: AiWritingPromptDao
) : AiWritingPromptGateway {

    override fun observeAll(): Flow<List<AiWritingPrompt>> = dao.observeAll()

    override fun observeByCategory(category: String): Flow<List<AiWritingPrompt>> = dao.observeByCategory(category)

    override suspend fun getById(id: String): AiWritingPrompt? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override suspend fun save(
        name: String,
        content: String,
        category: String,
        promptId: String?
    ): AiWritingPrompt = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = promptId ?: "wprompt_${UUID.randomUUID().toString().replace("-", "")}"
        val existing = if (promptId != null) dao.getById(promptId) else null
        AiWritingPrompt(
            id = id,
            name = name,
            content = content,
            category = category,
            enabled = existing?.enabled ?: true,
            sortOrder = existing?.sortOrder ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        ).also { dao.insert(it) }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.updateEnabled(id, enabled)
    }
}
