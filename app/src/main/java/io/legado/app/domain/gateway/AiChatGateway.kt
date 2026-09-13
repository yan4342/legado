package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.domain.model.AiMessagePart
import kotlinx.coroutines.flow.Flow

interface AiChatGateway {
    fun observeConversations(): Flow<List<AiChatConversation>>
    fun observeMessages(conversationId: String): Flow<List<AiChatMessage>>
    fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>>
    suspend fun getConversation(id: String): AiChatConversation?
    suspend fun getMessage(id: String): AiChatMessage?
    suspend fun createConversation(title: String = "New Chat"): AiChatConversation
    suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String? = null,
        thinkingDuration: Int = 0,
        speakerCardId: String? = null,
        branchIndex: Int = 0,
        excludeFromContext: Boolean = false,
    ): AiChatMessage
    suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int = 0,
        speakerCardId: String? = null,
    ): AiChatMessage
    suspend fun selectBranch(messageId: String)
    /** All sibling forks for UI branch switching only — not for chat context / display. */
    suspend fun getBranches(parentMessageId: String): List<AiChatMessage>
    /** Branch counts for UI badges only — does not return message content. */
    suspend fun getBranchCounts(conversationId: String): Map<String, Int>
    suspend fun updateConversationTitle(conversationId: String, title: String)
    suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String)
    suspend fun deleteConversation(conversationId: String)
    suspend fun updateConversationType(conversationId: String, type: String)
    suspend fun updateConversationWritingSubMode(conversationId: String, writingSubMode: String)
    suspend fun updateConversationOutputMode(conversationId: String, outputMode: String)
    suspend fun updateConversationCharacter(conversationId: String, characterCardId: String?)
    suspend fun updateConversationCharacters(conversationId: String, cardIds: String?)
    suspend fun updateConversationPrompts(conversationId: String, promptIds: String?)
    suspend fun updateConversationSkills(conversationId: String, skillIds: String?)
    suspend fun updateConversationDraft(conversationId: String, draftText: String)
    suspend fun updateConversationCompressedSummary(conversationId: String, compressedSummary: String)
    suspend fun updateConversationWorkspaceId(conversationId: String, workspaceId: String?)
    suspend fun updateConversationHud(conversationId: String, hudHtml: String?)
    suspend fun updateConversationUserCard(conversationId: String, userName: String, userDescription: String, enabled: Boolean)
    suspend fun updateConversationUserCardEnabled(conversationId: String, enabled: Boolean)
    suspend fun countConversationsByCharacter(characterCardId: String): Int
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteMessages(messageIds: List<String>)
    /** All selected context messages for a conversation (compression source). */
    suspend fun getAllContextMessages(conversationId: String): List<AiChatMessage>
    /** Delete every selected context message except the most recent [keepCount]. */
    suspend fun deleteContextMessagesExceptRecent(conversationId: String, keepCount: Int)
    suspend fun updateMessageParts(messageId: String, parts: List<AiMessagePart>)
    suspend fun getMessagesForRegeneration(conversationId: String, limit: Int = 30, offset: Int = 0): List<Pair<String, String>>
    suspend fun listConversations(limit: Int = 20): List<AiChatConversation>
    suspend fun forkConversation(sourceConversationId: String, forkAtMessageId: String): AiChatConversation
    fun observeRecentSelectedMessages(conversationId: String, limit: Int = 30): Flow<List<AiChatMessage>>
    suspend fun getContextMessages(conversationId: String, maxCount: Int = 80): List<AiChatMessage>
    suspend fun getSelectedMessagesBefore(
        conversationId: String,
        beforeCreatedAt: Long,
        beforeId: String,
        limit: Int = 20,
    ): List<AiChatMessage>
    suspend fun updateContextUsage(
        conversationId: String,
        promptTokens: Int,
        source: String,
        calibrationScale: Float,
    )
}
