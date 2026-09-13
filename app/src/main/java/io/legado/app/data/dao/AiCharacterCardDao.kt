package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiCharacterCard
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCharacterCardDao {

    @Query("SELECT * FROM ai_character_cards ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiCharacterCard>>

    @Query(
        "SELECT * FROM ai_character_cards WHERE bookUrl = :bookUrl AND bookUrl != '' AND canonical = 1 ORDER BY updatedAt DESC"
    )
    fun observeByBookUrl(bookUrl: String): Flow<List<AiCharacterCard>>

    @Query(
        "SELECT * FROM ai_character_cards WHERE bookUrl = :bookUrl AND bookUrl != '' AND canonical = 1 ORDER BY updatedAt DESC"
    )
    suspend fun getByBookUrl(bookUrl: String): List<AiCharacterCard>

    @Query("SELECT * FROM ai_character_cards WHERE id = :id")
    suspend fun getById(id: String): AiCharacterCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: AiCharacterCard)

    @Query("DELETE FROM ai_character_cards WHERE id = :id")
    suspend fun delete(id: String)

    @get:Query("SELECT * FROM ai_character_cards")
    val all: List<AiCharacterCard>
}
