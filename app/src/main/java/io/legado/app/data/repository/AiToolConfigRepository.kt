package io.legado.app.data.repository

import io.legado.app.data.dao.AiToolConfigDao
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.domain.gateway.AiToolConfigGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiToolConfigRepository(
    private val dao: AiToolConfigDao
) : AiToolConfigGateway {

    override fun observeAll(): Flow<List<AiToolConfig>> = dao.observeAll()

    override suspend fun getByToolName(toolName: String): AiToolConfig? = withContext(Dispatchers.IO) {
        AiToolConfigMigrator.migrateLegacyConfigsIfNeeded(dao)
        dao.getByToolName(toolName)
    }

    override suspend fun save(config: AiToolConfig) = withContext(Dispatchers.IO) {
        dao.insert(config.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(toolName: String) = withContext(Dispatchers.IO) {
        dao.delete(toolName)
    }

    suspend fun ensureMigrated() = withContext(Dispatchers.IO) {
        AiToolConfigMigrator.migrateLegacyConfigsIfNeeded(dao)
    }
}
