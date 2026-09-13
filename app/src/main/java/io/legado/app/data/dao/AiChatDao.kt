package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import kotlinx.coroutines.flow.Flow

data class BranchCount(
    val parentMessageId: String,
    val cnt: Int
)

@Dao
interface AiChatDao {

    @Query("SELECT * FROM ai_chat_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AiChatConversation>>

    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<AiChatMessage>>

    /** Observe only the selected branch path for display */
    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId AND isSelected = 1 ORDER BY createdAt ASC")
    fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>>

    /** Count regenerate forks (multi-bubble siblings share one branchIndex). */
    @Query(
        "SELECT COUNT(DISTINCT branchIndex) FROM ai_chat_messages WHERE parentMessageId = :parentMessageId",
    )
    suspend fun countBranches(parentMessageId: String): Int

    /** Get all messages for a parent (all forks + multi-bubble siblings). */
    @Query("SELECT * FROM ai_chat_messages WHERE parentMessageId = :parentMessageId ORDER BY branchIndex ASC, createdAt ASC")
    suspend fun getBranches(parentMessageId: String): List<AiChatMessage>

    /** Distinct regenerate forks per parent (multi-bubble does not inflate the count). */
    @Query(
        """
        SELECT parentMessageId, COUNT(DISTINCT branchIndex) as cnt
        FROM ai_chat_messages
        WHERE conversationId = :conversationId AND parentMessageId IS NOT NULL
        GROUP BY parentMessageId
        """,
    )
    suspend fun getBranchCounts(conversationId: String): List<BranchCount>

    /** Mark all messages after a given point in the conversation as unselected */
    @Query("UPDATE ai_chat_messages SET isSelected = 0 WHERE conversationId = :conversationId AND createdAt > :afterTimestamp AND role = 'assistant'")
    suspend fun deselectAssistantAfter(conversationId: String, afterTimestamp: Long)

    /** Select a specific branch by id */
    @Query("UPDATE ai_chat_messages SET isSelected = 1 WHERE id = :messageId")
    suspend fun selectBranch(messageId: String)

    /** Deselect every message under a parent (all forks / multi-bubbles). */
    @Query("UPDATE ai_chat_messages SET isSelected = 0 WHERE parentMessageId = :parentMessageId")
    suspend fun deselectAllForParent(parentMessageId: String)

    /** Select one regenerate fork (all multi-bubble siblings with that branchIndex). */
    @Query(
        """
        UPDATE ai_chat_messages SET isSelected = 1
        WHERE parentMessageId = :parentMessageId AND branchIndex = :branchIndex
        """,
    )
    suspend fun selectBranchGroup(parentMessageId: String, branchIndex: Int)

    /** Deselect all messages for a conversation */
    @Query("UPDATE ai_chat_messages SET isSelected = 0 WHERE conversationId = :conversationId")
    suspend fun deselectAll(conversationId: String)

    @Query("SELECT * FROM ai_chat_conversations WHERE id = :id")
    suspend fun getConversation(id: String): AiChatConversation?

    @Query("SELECT * FROM ai_chat_messages WHERE id = :id")
    suspend fun getMessage(id: String): AiChatMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AiChatConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AiChatMessage>)

    @Query("UPDATE ai_chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationTitle(conversationId: String, title: String, updatedAt: Long)

    @Query(
        """
        UPDATE ai_chat_conversations
        SET reasoningLevel = :reasoningLevel, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationReasoningLevel(conversationId: String, reasoningLevel: String, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: String, updatedAt: Long)

    @Query("DELETE FROM ai_chat_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM ai_chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("DELETE FROM ai_chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM ai_chat_messages WHERE id IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)

    @Query("DELETE FROM ai_chat_messages WHERE parentMessageId = :parentMessageId")
    suspend fun deleteMessagesByParent(parentMessageId: String)

    @Query("UPDATE ai_chat_messages SET partsJson = :partsJson WHERE id = :messageId")
    suspend fun updateMessageParts(messageId: String, partsJson: String)

    @Query("UPDATE ai_chat_conversations SET type = :type, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationType(conversationId: String, type: String, updatedAt: Long)

    @Query(
        """
        UPDATE ai_chat_conversations
        SET writingSubMode = :writingSubMode, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationWritingSubMode(
        conversationId: String,
        writingSubMode: String,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE ai_chat_conversations
        SET outputMode = :outputMode, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationOutputMode(
        conversationId: String,
        outputMode: String,
        updatedAt: Long,
    )

    @Query("UPDATE ai_chat_conversations SET characterCardId = :characterCardId, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationCharacter(conversationId: String, characterCardId: String?, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET promptIds = :promptIds, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationPrompts(conversationId: String, promptIds: String?, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET skillIds = :skillIds, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationSkills(conversationId: String, skillIds: String?, updatedAt: Long)

    /** Composer draft only — does not bump updatedAt (avoids reshuffling the conversation list). */
    @Query("UPDATE ai_chat_conversations SET draftText = :draftText WHERE id = :conversationId")
    suspend fun updateConversationDraft(conversationId: String, draftText: String)

    /** Summary of folded-away history; non-empty means this conversation has been compressed. */
    @Query("UPDATE ai_chat_conversations SET compressedSummary = :compressedSummary WHERE id = :conversationId")
    suspend fun updateConversationCompressedSummary(conversationId: String, compressedSummary: String)

    @Query("UPDATE ai_chat_conversations SET characterCardIds = :cardIds, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationCharacters(conversationId: String, cardIds: String?, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET workspaceId = :workspaceId, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationWorkspaceId(conversationId: String, workspaceId: String?, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET galgameHudHtml = :hudHtml, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationHud(conversationId: String, hudHtml: String?, updatedAt: Long)

    @Query(
        """
        UPDATE ai_chat_conversations
        SET userName = :userName, userDescription = :userDescription, userCardEnabled = :enabled, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationUserCard(
        conversationId: String,
        userName: String,
        userDescription: String,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE ai_chat_conversations
        SET userCardEnabled = :enabled, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationUserCardEnabled(conversationId: String, enabled: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE ai_chat_conversations
        SET contextPromptTokens = :promptTokens,
            contextTokensSource = :source,
            contextCalibrationScale = :calibrationScale,
            updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateContextUsage(
        conversationId: String,
        promptTokens: Int,
        source: String,
        calibrationScale: Float,
        updatedAt: Long,
    )

    @Query("SELECT * FROM ai_chat_conversations WHERE characterCardId = :characterCardId ORDER BY updatedAt DESC")
    suspend fun getConversationsByCharacter(characterCardId: String): List<AiChatConversation>

    @Query("SELECT COUNT(*) FROM ai_chat_conversations WHERE characterCardId = :characterCardId")
    suspend fun countConversationsByCharacter(characterCardId: String): Int

    @Query("SELECT * FROM ai_chat_conversations")
    fun getAllConversations(): List<AiChatConversation>

    @Query("SELECT * FROM ai_chat_messages")
    fun getAllMessages(): List<AiChatMessage>

    /** Recent selected messages for model-facing text (excludes `/temp` director notes). */
    @Query(
        """
        SELECT * FROM ai_chat_messages
        WHERE conversationId = :conversationId AND isSelected = 1 AND excludeFromContext = 0
        ORDER BY createdAt DESC LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getRecentMessages(conversationId: String, limit: Int, offset: Int = 0): List<AiChatMessage>

    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId AND isSelected = 1 AND createdAt <= :maxCreatedAt ORDER BY createdAt ASC")
    suspend fun getSelectedMessagesUpTo(conversationId: String, maxCreatedAt: Long): List<AiChatMessage>

    /** Reactive Flow of the latest N selected messages (UI display, includes temp notes). */
    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId AND isSelected = 1 ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentSelectedMessages(conversationId: String, limit: Int): Flow<List<AiChatMessage>>

    /** On-demand latest N selected messages for AI context (excludes `/temp` director notes). */
    @Query(
        """
        SELECT * FROM ai_chat_messages
        WHERE conversationId = :conversationId AND isSelected = 1 AND excludeFromContext = 0
        ORDER BY createdAt DESC LIMIT :limit
        """,
    )
    suspend fun getRecentSelectedMessages(conversationId: String, limit: Int): List<AiChatMessage>

    /** All selected context messages (no limit) — compression source, ascending chronological. */
    @Query(
        """
        SELECT * FROM ai_chat_messages
        WHERE conversationId = :conversationId AND isSelected = 1 AND excludeFromContext = 0
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getAllSelectedContextMessages(conversationId: String): List<AiChatMessage>

    /** Delete every selected context message except the most recent [keepCount] (post-compression cleanup). */
    @Query(
        """
        DELETE FROM ai_chat_messages
        WHERE conversationId = :conversationId AND isSelected = 1 AND excludeFromContext = 0
          AND id NOT IN (
            SELECT id FROM ai_chat_messages
            WHERE conversationId = :conversationId AND isSelected = 1 AND excludeFromContext = 0
            ORDER BY createdAt DESC LIMIT :keepCount
          )
        """,
    )
    suspend fun deleteContextMessagesExceptRecent(conversationId: String, keepCount: Int)

    /** Pagination: selected messages before a (createdAt, id) cursor. */
    @Query(
        """
        SELECT * FROM ai_chat_messages
        WHERE conversationId = :conversationId AND isSelected = 1
          AND (createdAt < :beforeCreatedAt
               OR (createdAt = :beforeCreatedAt AND id < :beforeId))
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getSelectedMessagesBefore(
        conversationId: String,
        beforeCreatedAt: Long,
        beforeId: String,
        limit: Int,
    ): List<AiChatMessage>
}
