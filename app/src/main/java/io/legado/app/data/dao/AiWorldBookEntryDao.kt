package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiWorldBookEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface AiWorldBookEntryDao {
    @Query("SELECT * FROM ai_world_book_entries WHERE worldBookId = :worldBookId ORDER BY priority, name")
    fun observeForWorldBook(worldBookId: String): Flow<List<AiWorldBookEntry>>

    @Query("SELECT * FROM ai_world_book_entries WHERE worldBookId = :worldBookId ORDER BY priority, name")
    suspend fun getForWorldBook(worldBookId: String): List<AiWorldBookEntry>

    @Query(
        """SELECT * FROM ai_world_book_entries
            WHERE enabled = 1 AND worldBookId IN (:worldBookIds)
            ORDER BY priority, name"""
    )
    suspend fun getEnabledForWorldBooks(worldBookIds: List<String>): List<AiWorldBookEntry>

    @Query("SELECT * FROM ai_world_book_entries WHERE enabled = 1 ORDER BY priority, name")
    suspend fun getAllEnabled(): List<AiWorldBookEntry>

    @Query("SELECT * FROM ai_world_book_entries ORDER BY worldBookId, priority, name")
    suspend fun getAll(): List<AiWorldBookEntry>

    @Query("SELECT * FROM ai_world_book_entries WHERE id = :id")
    suspend fun getById(id: String): AiWorldBookEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AiWorldBookEntry)

    @Query("DELETE FROM ai_world_book_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_world_book_entries WHERE worldBookId = :worldBookId")
    suspend fun deleteForWorldBook(worldBookId: String)
}
