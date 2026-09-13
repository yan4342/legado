package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.BookCharacterCast
import kotlinx.coroutines.flow.Flow

@Dao
interface BookCharacterCastDao {

    @Query(
        """
        SELECT c.* FROM ai_character_cards c
        INNER JOIN book_character_cast book_cast ON book_cast.characterCardId = c.id
        WHERE book_cast.bookUrl = :bookUrl AND c.canonical = 1
        ORDER BY book_cast.sortOrder ASC, c.name ASC
        """
    )
    fun observeCharactersForBook(bookUrl: String): Flow<List<AiCharacterCard>>

    @Query(
        """
        SELECT c.* FROM ai_character_cards c
        INNER JOIN book_character_cast book_cast ON book_cast.characterCardId = c.id
        WHERE book_cast.bookUrl = :bookUrl AND c.canonical = 1
        ORDER BY book_cast.sortOrder ASC, c.name ASC
        """
    )
    suspend fun getCharactersForBook(bookUrl: String): List<AiCharacterCard>

    @Query("SELECT * FROM book_character_cast WHERE bookUrl = :bookUrl ORDER BY sortOrder ASC")
    fun observeCast(bookUrl: String): Flow<List<BookCharacterCast>>

    @Query("SELECT * FROM book_character_cast WHERE bookUrl = :bookUrl ORDER BY sortOrder ASC")
    suspend fun getCast(bookUrl: String): List<BookCharacterCast>

    @Query("SELECT * FROM book_character_cast")
    fun getAll(): List<BookCharacterCast>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cast: BookCharacterCast)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(casts: List<BookCharacterCast>)

    @Query(
        "SELECT dramaticRole FROM book_character_cast WHERE bookUrl = :bookUrl AND characterCardId = :characterCardId LIMIT 1"
    )
    suspend fun getDramaticRole(bookUrl: String, characterCardId: String): String?

    @Query(
        "UPDATE book_character_cast SET dramaticRole = :dramaticRole WHERE bookUrl = :bookUrl AND characterCardId = :characterCardId"
    )
    suspend fun updateDramaticRole(bookUrl: String, characterCardId: String, dramaticRole: String)

    @Query("DELETE FROM book_character_cast WHERE bookUrl = :bookUrl")
    suspend fun deleteByBook(bookUrl: String)

    @Query(
        "DELETE FROM book_character_cast WHERE bookUrl = :bookUrl AND characterCardId = :characterCardId"
    )
    suspend fun delete(bookUrl: String, characterCardId: String)

    @Transaction
    suspend fun setCast(bookUrl: String, characterCardIds: List<String>) {
        val previousRoles = getCast(bookUrl).associate { it.characterCardId to it.dramaticRole }
        deleteByBook(bookUrl)
        if (characterCardIds.isEmpty()) return
        insertAll(
            characterCardIds.mapIndexed { index, cardId ->
                BookCharacterCast(
                    bookUrl = bookUrl,
                    characterCardId = cardId,
                    sortOrder = index,
                    dramaticRole = previousRoles[cardId].orEmpty(),
                )
            }
        )
    }
}
