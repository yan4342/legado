package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import io.legado.app.domain.model.textContent
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class AiChatRepository(
    private val aiChatDao: AiChatDao,
    private val memoryTableGateway: AiMemoryTableGateway? = null,
    private val outlineGateway: AiOutlineGateway? = null,
    private val memoryGateway: AiMemoryGateway? = null,
    private val workspaceGateway: AiWorkspaceGateway? = null,
) : AiChatGateway {

    override fun observeConversations(): Flow<List<AiChatConversation>> = aiChatDao.observeConversations()

    override fun observeMessages(conversationId: String): Flow<List<AiChatMessage>> =
        // Always expose the on-page (selected) branch only — never sibling forks.
        aiChatDao.observeSelectedMessages(conversationId)

    override fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>> =
        aiChatDao.observeSelectedMessages(conversationId)

    override fun observeRecentSelectedMessages(conversationId: String, limit: Int): Flow<List<AiChatMessage>> =
        aiChatDao.observeRecentSelectedMessages(conversationId, limit)
            .map { it.reversed() }

    override suspend fun getContextMessages(conversationId: String, maxCount: Int): List<AiChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.getRecentSelectedMessages(conversationId, maxCount).reversed()
        }

    override suspend fun getSelectedMessagesBefore(
        conversationId: String,
        beforeCreatedAt: Long,
        beforeId: String,
        limit: Int,
    ): List<AiChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.getSelectedMessagesBefore(conversationId, beforeCreatedAt, beforeId, limit).reversed()
        }

    override suspend fun getConversation(id: String): AiChatConversation? = withContext(Dispatchers.IO) {
        aiChatDao.getConversation(id)
    }

    override suspend fun getMessage(id: String): AiChatMessage? = withContext(Dispatchers.IO) {
        aiChatDao.getMessage(id)
    }

    override suspend fun createConversation(title: String): AiChatConversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatConversation(
            id = newId("chat"),
            title = title,
            createdAt = now,
            updatedAt = now
        ).also { aiChatDao.insertConversation(it) }
    }

    override suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String?,
        thinkingDuration: Int,
        speakerCardId: String?,
        branchIndex: Int,
        excludeFromContext: Boolean,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatMessage(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = branchIndex,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration,
            speakerCardId = speakerCardId,
            excludeFromContext = excludeFromContext,
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }
    }

    override suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int,
        speakerCardId: String?,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val branchCount = aiChatDao.countBranches(parentMessageId)
        val now = System.currentTimeMillis()
        aiChatDao.deselectAllForParent(parentMessageId)
        AiChatMessage(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = branchCount,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration,
            speakerCardId = speakerCardId,
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }
    }

    override suspend fun selectBranch(messageId: String) = withContext(Dispatchers.IO) {
        val message = aiChatDao.getMessage(messageId) ?: return@withContext
        val parentId = message.parentMessageId ?: return@withContext
        // Select whole fork group (multi-bubble siblings share branchIndex).
        appDb.withTransaction {
            aiChatDao.deselectAllForParent(parentId)
            aiChatDao.selectBranchGroup(parentId, message.branchIndex)
        }
    }

    override suspend fun getBranches(parentMessageId: String): List<AiChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.getBranches(parentMessageId)
        }

    override suspend fun getBranchCounts(conversationId: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            aiChatDao.getBranchCounts(conversationId).associate { it.parentMessageId to it.cnt }
        }

    override suspend fun updateConversationTitle(conversationId: String, title: String) = withContext(Dispatchers.IO) {
        aiChatDao.updateConversationTitle(conversationId, title, System.currentTimeMillis())
    }

    override suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationReasoningLevel(
                conversationId = conversationId,
                reasoningLevel = reasoningLevel,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        aiChatDao.deleteMessagesByConversation(conversationId)
        aiChatDao.deleteConversation(conversationId)
        outlineGateway?.delete(conversationId)
        memoryTableGateway?.deleteAllForConversation(conversationId)
        workspaceGateway?.deleteByConversationId(conversationId)
        Unit
    }

    override suspend fun updateConversationType(conversationId: String, type: String) = withContext(Dispatchers.IO) {
        aiChatDao.updateConversationType(conversationId, type, System.currentTimeMillis())
    }

    override suspend fun updateConversationWritingSubMode(conversationId: String, writingSubMode: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationWritingSubMode(
                conversationId,
                writingSubMode,
                System.currentTimeMillis(),
            )
        }

    override suspend fun updateConversationOutputMode(conversationId: String, outputMode: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationOutputMode(
                conversationId,
                outputMode,
                System.currentTimeMillis(),
            )
        }

    override suspend fun updateConversationCharacter(conversationId: String, characterCardId: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationCharacter(conversationId, characterCardId, System.currentTimeMillis())
        }

    override suspend fun updateConversationCharacters(conversationId: String, cardIds: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationCharacters(conversationId, cardIds, System.currentTimeMillis())
        }

    override suspend fun updateConversationPrompts(conversationId: String, promptIds: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationPrompts(conversationId, promptIds, System.currentTimeMillis())
        }

    override suspend fun updateConversationSkills(conversationId: String, skillIds: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationSkills(conversationId, skillIds, System.currentTimeMillis())
        }

    override suspend fun updateConversationDraft(conversationId: String, draftText: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationDraft(conversationId, draftText)
        }

    override suspend fun updateConversationCompressedSummary(
        conversationId: String,
        compressedSummary: String,
    ) = withContext(Dispatchers.IO) {
        aiChatDao.updateConversationCompressedSummary(conversationId, compressedSummary)
    }

    override suspend fun updateConversationWorkspaceId(conversationId: String, workspaceId: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationWorkspaceId(conversationId, workspaceId, System.currentTimeMillis())
        }

    override suspend fun updateConversationHud(conversationId: String, hudHtml: String?) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationHud(conversationId, hudHtml, System.currentTimeMillis())
        }

    override suspend fun updateConversationUserCard(
        conversationId: String,
        userName: String,
        userDescription: String,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        aiChatDao.updateConversationUserCard(
            conversationId,
            userName,
            userDescription,
            enabled,
            System.currentTimeMillis(),
        )
    }

    override suspend fun updateConversationUserCardEnabled(conversationId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationUserCardEnabled(conversationId, enabled, System.currentTimeMillis())
        }

    override suspend fun countConversationsByCharacter(characterCardId: String): Int =
        withContext(Dispatchers.IO) {
            aiChatDao.countConversationsByCharacter(characterCardId)
        }

    override suspend fun getMessagesForRegeneration(conversationId: String, limit: Int, offset: Int): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            aiChatDao.getRecentMessages(conversationId, limit, offset)
                .reversed()
                .map { msg ->
                    val textContent = AiMessagePartJson.decode(msg.partsJson).textContent()
                    (msg.role to textContent)
                }
        }

    override suspend fun listConversations(limit: Int): List<AiChatConversation> = withContext(Dispatchers.IO) {
        aiChatDao.getAllConversations().sortedByDescending { it.updatedAt }.take(limit)
    }

    override suspend fun forkConversation(sourceConversationId: String, forkAtMessageId: String): AiChatConversation =
        withContext(Dispatchers.IO) {
            val sourceConv = aiChatDao.getConversation(sourceConversationId)
                ?: throw IllegalArgumentException("Source conversation not found")
            val forkMsg = aiChatDao.getMessage(forkAtMessageId)
                ?: throw IllegalArgumentException("Fork point message not found")

            // 1. Create new conversation
            val now = System.currentTimeMillis()
            val newConv = AiChatConversation(
                id = newId("chat"),
                title = "Fork of ${sourceConv.title}",
                type = sourceConv.type,
                characterCardId = sourceConv.characterCardId,
                promptIds = sourceConv.promptIds,
                reasoningLevel = sourceConv.reasoningLevel,
                modelProfileId = sourceConv.modelProfileId,
                createdAt = now,
                updatedAt = now,
                forkedFromConversationId = sourceConversationId,
                forkedAtMessageId = forkAtMessageId,
                galgameHudHtml = sourceConv.galgameHudHtml,
                characterCardIds = sourceConv.characterCardIds,
                userName = sourceConv.userName,
                userDescription = sourceConv.userDescription,
                userCardEnabled = sourceConv.userCardEnabled,
                writingSubMode = sourceConv.writingSubMode,
                skillIds = sourceConv.skillIds,
            )
            aiChatDao.insertConversation(newConv)

            // 2. Copy messages up to fork point (inclusive)
            val messagesToCopy = aiChatDao.getSelectedMessagesUpTo(sourceConversationId, forkMsg.createdAt)
            val idMap = mutableMapOf<String, String>() // oldId -> newId
            val newMessages = messagesToCopy.map { oldMsg ->
                val newId = newId("message")
                idMap[oldMsg.id] = newId
                oldMsg.copy(
                    id = newId,
                    conversationId = newConv.id,
                    parentMessageId = oldMsg.parentMessageId?.let { parentId ->
                        idMap[parentId] ?: parentId // remap if already mapped, else keep (root messages)
                    },
                    isSelected = true,
                    branchIndex = 0,
                )
            }
            // Second pass: remap parentMessageIds for messages whose parent was also copied
            val finalMessages = newMessages.map { msg ->
                msg.parentMessageId?.let { parentId ->
                    val remapped = idMap[parentId]
                    if (remapped != null) msg.copy(parentMessageId = remapped)
                    else msg
                } ?: msg
            }
            if (finalMessages.isNotEmpty()) {
                aiChatDao.insertMessages(finalMessages)
            }

            // 3. Copy associated data
            outlineGateway?.getByConversation(sourceConversationId)?.let { outline ->
                outlineGateway.upsert(outline.copy(conversationId = newConv.id))
            }
            memoryTableGateway?.copyTablesToConversation(sourceConversationId, newConv.id)
            memoryGateway?.copyToConversation(sourceConversationId, newConv.id)

            // Clone workspace refs (cards / world books / prompts) for the forked conversation
            val sourceWsId = sourceConv.workspaceId
                ?: workspaceGateway?.getByConversationId(sourceConversationId)?.id
            if (sourceWsId != null) {
                runCatching {
                    workspaceGateway?.cloneWorkspaceToConversation(
                        sourceWorkspaceId = sourceWsId,
                        targetConversationId = newConv.id,
                        name = "Fork of ${sourceConv.title}",
                    )
                }
            } else if (sourceConv.type == "writing") {
                runCatching {
                    workspaceGateway?.ensureForConversation(
                        conversationId = newConv.id,
                        name = "Fork of ${sourceConv.title}",
                        characterCardIds = sourceConv.characterCardIds
                            ?: sourceConv.characterCardId.orEmpty(),
                        writingPromptIds = sourceConv.promptIds.orEmpty(),
                    )
                }
            }

            // Refresh workspaceId on returned conversation
            aiChatDao.getConversation(newConv.id) ?: newConv
        }

    override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        aiChatDao.deleteMessagesByParent(messageId)
        aiChatDao.deleteMessage(messageId)
        memoryTableGateway?.deleteBySourceMessageId(messageId)
        Unit
    }

    override suspend fun deleteMessages(messageIds: List<String>) = withContext(Dispatchers.IO) {
        aiChatDao.deleteMessages(messageIds)
    }

    override suspend fun getAllContextMessages(conversationId: String): List<AiChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.getAllSelectedContextMessages(conversationId)
        }

    override suspend fun deleteContextMessagesExceptRecent(conversationId: String, keepCount: Int) =
        withContext(Dispatchers.IO) {
            aiChatDao.deleteContextMessagesExceptRecent(conversationId, keepCount)
        }

    override suspend fun updateMessageParts(messageId: String, parts: List<AiMessagePart>) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateMessageParts(messageId, AiMessagePartJson.encode(parts))
        }

    override suspend fun updateContextUsage(
        conversationId: String,
        promptTokens: Int,
        source: String,
        calibrationScale: Float,
    ) = withContext(Dispatchers.IO) {
        aiChatDao.updateContextUsage(
            conversationId = conversationId,
            promptTokens = promptTokens,
            source = source,
            calibrationScale = calibrationScale,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
}
