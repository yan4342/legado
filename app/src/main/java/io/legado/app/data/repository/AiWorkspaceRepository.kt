package io.legado.app.data.repository

import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.dao.AiWorkspaceDao
import io.legado.app.data.entities.AiWorkspace
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AiWorkspaceRepository(
    private val dao: AiWorkspaceDao,
    private val chatDao: AiChatDao,
) : AiWorkspaceGateway {

    companion object {
        private const val EXPORT_VERSION = 1
        private const val MAX_IMPORT_JSON_CHARS = 100_000
        private const val MAX_NAME_CHARS = 200
        private const val MAX_IDS_FIELD_CHARS = 8_000
    }

    override fun observeByConversationId(conversationId: String): Flow<AiWorkspace?> =
        dao.observeByConversationId(conversationId)

    override fun observeAll(): Flow<List<AiWorkspace>> = dao.observeAll()

    override suspend fun getById(id: String): AiWorkspace? = withContext(Dispatchers.IO) {
        dao.getById(id)?.normalized()
    }

    override suspend fun getByConversationId(conversationId: String): AiWorkspace? =
        withContext(Dispatchers.IO) {
            dao.getByConversationId(conversationId)?.normalized()
        }

    override suspend fun getAll(): List<AiWorkspace> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.normalized() }
    }

    override suspend fun ensureForConversation(
        conversationId: String,
        name: String,
        characterCardIds: String,
        worldBookIds: String,
        writingPromptIds: String,
        bookUrl: String,
    ): AiWorkspace = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.getByConversationId(conversationId)?.let { existing ->
            val normalized = existing.normalized()
            if (normalized != existing) {
                dao.upsert(normalized.copy(updatedAt = now))
            }
            chatDao.updateConversationWorkspaceId(conversationId, normalized.id, now)
            return@withContext if (normalized != existing) normalized.copy(updatedAt = now) else normalized
        }
        val ws = AiWorkspace(
            id = newWorkspaceId(),
            name = name.ifBlank { "Workspace" }.take(MAX_NAME_CHARS),
            conversationId = conversationId,
            characterCardIds = AiIdListCodec.toCsv(characterCardIds),
            worldBookIds = AiIdListCodec.toCsv(worldBookIds),
            writingPromptIds = AiIdListCodec.toCsv(writingPromptIds),
            bookUrl = bookUrl,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(ws)
        chatDao.updateConversationWorkspaceId(conversationId, ws.id, now)
        ws
    }

    override suspend fun upsert(workspace: AiWorkspace) = withContext(Dispatchers.IO) {
        val normalized = workspace.normalized().copy(updatedAt = System.currentTimeMillis())
        dao.upsert(normalized)
        chatDao.updateConversationWorkspaceId(
            normalized.conversationId,
            normalized.id,
            System.currentTimeMillis(),
        )
    }

    override suspend fun updateRefs(
        workspaceId: String,
        characterCardIds: String?,
        worldBookIds: String?,
        writingPromptIds: String?,
        name: String?,
        bookUrl: String?,
    ): AiWorkspace? = withContext(Dispatchers.IO) {
        val existing = dao.getById(workspaceId) ?: return@withContext null
        val updated = existing.copy(
            characterCardIds = characterCardIds?.let { AiIdListCodec.toCsv(it) } ?: existing.characterCardIds,
            worldBookIds = worldBookIds?.let { AiIdListCodec.toCsv(it) } ?: existing.worldBookIds,
            writingPromptIds = writingPromptIds?.let { AiIdListCodec.toCsv(it) } ?: existing.writingPromptIds,
            name = name?.take(MAX_NAME_CHARS) ?: existing.name,
            bookUrl = bookUrl ?: existing.bookUrl,
            updatedAt = System.currentTimeMillis(),
        ).normalized()
        dao.upsert(updated)
        updated
    }

    override suspend fun cloneWorkspaceToConversation(
        sourceWorkspaceId: String,
        targetConversationId: String,
        name: String?,
    ): AiWorkspace = withContext(Dispatchers.IO) {
        val source = dao.getById(sourceWorkspaceId)?.normalized()
            ?: throw IllegalArgumentException("Workspace not found: $sourceWorkspaceId")
        val now = System.currentTimeMillis()
        dao.deleteByConversationId(targetConversationId)
        val cloned = AiWorkspace(
            id = newWorkspaceId(),
            name = (name ?: source.name.ifBlank { "Workspace" }).take(MAX_NAME_CHARS),
            conversationId = targetConversationId,
            characterCardIds = source.characterCardIds,
            worldBookIds = source.worldBookIds,
            writingPromptIds = source.writingPromptIds,
            bookUrl = source.bookUrl,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(cloned)
        dualWriteConversationRefs(targetConversationId, cloned, now)
        cloned
    }

    override suspend fun exportWorkspaceJson(workspaceId: String): String = withContext(Dispatchers.IO) {
        val ws = dao.getById(workspaceId)?.normalized()
            ?: throw IllegalArgumentException("Workspace not found: $workspaceId")
        GSON.toJson(
            mapOf(
                "version" to EXPORT_VERSION,
                "name" to ws.name,
                "characterCardIds" to ws.characterCardIds,
                "worldBookIds" to ws.worldBookIds,
                "writingPromptIds" to ws.writingPromptIds,
            )
        )
    }

    override suspend fun importWorkspaceJson(
        json: String,
        targetConversationId: String,
    ): AiWorkspace = withContext(Dispatchers.IO) {
        if (json.length > MAX_IMPORT_JSON_CHARS) {
            throw IllegalArgumentException("Workspace JSON too large")
        }
        @Suppress("UNCHECKED_CAST")
        val map = GSON.fromJson(json, Map::class.java) as? Map<String, Any?>
            ?: throw IllegalArgumentException("Invalid workspace JSON")
        val version = (map["version"] as? Number)?.toInt() ?: 1
        if (version != EXPORT_VERSION) {
            throw IllegalArgumentException("Unsupported workspace JSON version: $version")
        }
        val name = map["name"]?.toString().orEmpty().take(MAX_NAME_CHARS)
        val characterCardIds = AiIdListCodec.toCsv(map["characterCardIds"]?.toString())
        val worldBookIds = AiIdListCodec.toCsv(map["worldBookIds"]?.toString())
        val writingPromptIds = AiIdListCodec.toCsv(map["writingPromptIds"]?.toString())
        if (characterCardIds.length > MAX_IDS_FIELD_CHARS ||
            worldBookIds.length > MAX_IDS_FIELD_CHARS ||
            writingPromptIds.length > MAX_IDS_FIELD_CHARS
        ) {
            throw IllegalArgumentException("Workspace ID fields too large")
        }
        val now = System.currentTimeMillis()
        dao.deleteByConversationId(targetConversationId)
        val ws = AiWorkspace(
            id = newWorkspaceId(),
            name = name.ifBlank { "Imported Workspace" },
            conversationId = targetConversationId,
            characterCardIds = characterCardIds,
            worldBookIds = worldBookIds,
            writingPromptIds = writingPromptIds,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(ws)
        dualWriteConversationRefs(targetConversationId, ws, now)
        ws
    }

    override suspend fun deleteByConversationId(conversationId: String) = withContext(Dispatchers.IO) {
        dao.deleteByConversationId(conversationId)
    }

    private suspend fun dualWriteConversationRefs(conversationId: String, ws: AiWorkspace, now: Long) {
        chatDao.updateConversationWorkspaceId(conversationId, ws.id, now)
        chatDao.updateConversationCharacters(
            conversationId,
            AiIdListCodec.toJsonArray(ws.characterCardIds),
            now,
        )
        chatDao.updateConversationPrompts(
            conversationId,
            ws.writingPromptIds.ifBlank { null },
            now,
        )
        chatDao.updateConversationCharacter(
            conversationId,
            AiIdListCodec.primaryId(ws.characterCardIds),
            now,
        )
    }

    private fun AiWorkspace.normalized(): AiWorkspace {
        val cards = AiIdListCodec.toCsv(characterCardIds)
        val books = AiIdListCodec.toCsv(worldBookIds)
        val prompts = AiIdListCodec.toCsv(writingPromptIds)
        return if (cards == characterCardIds && books == worldBookIds && prompts == writingPromptIds) {
            this
        } else {
            copy(
                characterCardIds = cards,
                worldBookIds = books,
                writingPromptIds = prompts,
            )
        }
    }

    private fun newWorkspaceId(): String =
        "ws_${UUID.randomUUID().toString().replace("-", "").take(16)}"
}
