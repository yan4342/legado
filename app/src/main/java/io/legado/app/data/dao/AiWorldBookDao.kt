package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.AiWorldBook
import kotlinx.coroutines.flow.Flow

@Dao
interface AiWorldBookDao {

    @Query("SELECT * FROM ai_world_books ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiWorldBook>>

    @Query("SELECT * FROM ai_world_books WHERE id = :id")
    suspend fun getById(id: String): AiWorldBook?

    @Query("SELECT * FROM ai_world_books WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun observeEnabled(): Flow<List<AiWorldBook>>

    /**
     * Insert only. Do **not** use [OnConflictStrategy.REPLACE]: SQLite REPLACE deletes the
     * conflicting row first, which CASCADE-deletes lore entries under this world book.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(worldBook: AiWorldBook)

    @Update
    suspend fun update(worldBook: AiWorldBook)

    @Query("UPDATE ai_world_books SET enabled = NOT enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ai_world_books SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_world_books WHERE id = :id")
    suspend fun delete(id: String)

    @get:Query("SELECT * FROM ai_world_books")
    val all: List<AiWorldBook>
}
