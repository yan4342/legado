package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookSourceVersion

@Dao
interface BookSourceVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: BookSourceVersion)

    @Query("SELECT * FROM book_source_versions ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookSourceVersion>

    @Query("SELECT * FROM book_source_versions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookSourceVersion?

    @Query(
        """
        SELECT * FROM book_source_versions
        WHERE bookSourceUrl = :bookSourceUrl
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun listByUrl(bookSourceUrl: String, limit: Int): List<BookSourceVersion>

    @Query(
        """
        DELETE FROM book_source_versions
        WHERE bookSourceUrl = :bookSourceUrl
          AND id IN (
            SELECT id FROM book_source_versions
            WHERE bookSourceUrl = :bookSourceUrl
            ORDER BY createdAt DESC
            LIMIT -1 OFFSET :keep
          )
        """
    )
    suspend fun deleteOlderThan(bookSourceUrl: String, keep: Int)

    @Query("UPDATE book_source_versions SET bookSourceUrl = :newUrl WHERE bookSourceUrl = :oldUrl")
    suspend fun remapeUrl(oldUrl: String, newUrl: String)
}
