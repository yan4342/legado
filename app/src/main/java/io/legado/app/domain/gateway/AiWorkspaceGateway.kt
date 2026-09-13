package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiWorkspace
import kotlinx.coroutines.flow.Flow

interface AiWorkspaceGateway {
    fun observeByConversationId(conversationId: String): Flow<AiWorkspace?>
    fun observeAll(): Flow<List<AiWorkspace>>
    suspend fun getById(id: String): AiWorkspace?
    suspend fun getByConversationId(conversationId: String): AiWorkspace?
    suspend fun getAll(): List<AiWorkspace>
    /** Ensure a workspace exists for a writing conversation; returns it. */
    suspend fun ensureForConversation(
        conversationId: String,
        name: String = "",
        characterCardIds: String = "",
        worldBookIds: String = "",
        writingPromptIds: String = "",
        bookUrl: String = "",
    ): AiWorkspace
    suspend fun upsert(workspace: AiWorkspace)
    suspend fun updateRefs(
        workspaceId: String,
        characterCardIds: String? = null,
        worldBookIds: String? = null,
        writingPromptIds: String? = null,
        name: String? = null,
        bookUrl: String? = null,
    ): AiWorkspace?
    /**
     * Copy card/world-book/prompt refs to [targetConversationId].
     * Does not copy memory rows or outline content.
     */
    suspend fun cloneWorkspaceToConversation(
        sourceWorkspaceId: String,
        targetConversationId: String,
        name: String? = null,
    ): AiWorkspace
    suspend fun exportWorkspaceJson(workspaceId: String): String
    suspend fun importWorkspaceJson(json: String, targetConversationId: String): AiWorkspace
    suspend fun deleteByConversationId(conversationId: String)
}
