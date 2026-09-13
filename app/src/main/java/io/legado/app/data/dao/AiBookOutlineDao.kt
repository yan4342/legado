package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiBookOutline
import kotlinx.coroutines.flow.Flow

@Dao
interface AiBookOutlineDao {

    @Query("SELECT * FROM ai_book_outlines WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): AiBookOutline?

    @Query("SELECT * FROM ai_book_outlines WHERE bookUrl = :bookUrl")
    fun observeByBookUrl(bookUrl: String): Flow<AiBookOutline?>

    @Query("SELECT * FROM ai_book_outlines")
    suspend fun getAll(): List<AiBookOutline>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outline: AiBookOutline)

    @Query("UPDATE ai_book_outlines SET enabled = NOT enabled, updatedAt = :updatedAt WHERE bookUrl = :bookUrl")
    suspend fun toggleEnabled(bookUrl: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_book_outlines WHERE bookUrl = :bookUrl")
    suspend fun delete(bookUrl: String)
}
