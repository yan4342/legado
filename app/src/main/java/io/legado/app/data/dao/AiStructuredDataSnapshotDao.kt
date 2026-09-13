package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiStructuredDataSnapshot

@Dao
interface AiStructuredDataSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AiStructuredDataSnapshot)

    @Query("SELECT * FROM ai_structured_data_snapshots ORDER BY createdAt DESC")
    suspend fun getAll(): List<AiStructuredDataSnapshot>

    @Query("SELECT * FROM ai_structured_data_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AiStructuredDataSnapshot?

    @Query(
        """
        SELECT * FROM ai_structured_data_snapshots
        WHERE (:conversationId IS NULL AND conversationId IS NULL)
           OR conversationId = :conversationId
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentByConversation(conversationId: String?, limit: Int): List<AiStructuredDataSnapshot>

    @Query(
        """
        DELETE FROM ai_structured_data_snapshots
        WHERE id IN (
            SELECT id FROM ai_structured_data_snapshots
            WHERE (:conversationId IS NULL AND conversationId IS NULL)
               OR conversationId = :conversationId
            ORDER BY createdAt DESC
            LIMIT -1 OFFSET :keep
        )
        """
    )
    suspend fun deleteOlderThan(conversationId: String?, keep: Int)

    @Query(
        """
        SELECT * FROM ai_structured_data_snapshots
        WHERE resourceType = :resourceType
          AND ((:conversationId IS NULL AND conversationId IS NULL)
            OR conversationId = :conversationId)
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentByConversationAndType(
        conversationId: String?,
        resourceType: String,
        limit: Int,
    ): List<AiStructuredDataSnapshot>

    @Query(
        """
        SELECT * FROM ai_structured_data_snapshots
        WHERE resourceType = 'outline'
          AND ((:conversationId IS NULL AND conversationId IS NULL)
            OR conversationId = :conversationId)
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentOutlineByConversation(conversationId: String?, limit: Int): List<AiStructuredDataSnapshot>

    @Query(
        """
        DELETE FROM ai_structured_data_snapshots
        WHERE resourceType = 'outline'
          AND id IN (
            SELECT id FROM ai_structured_data_snapshots
            WHERE resourceType = 'outline'
              AND ((:conversationId IS NULL AND conversationId IS NULL)
                OR conversationId = :conversationId)
            ORDER BY createdAt DESC
            LIMIT -1 OFFSET :keep
        )
        """
    )
    suspend fun deleteOlderOutlineThan(conversationId: String?, keep: Int)
}
