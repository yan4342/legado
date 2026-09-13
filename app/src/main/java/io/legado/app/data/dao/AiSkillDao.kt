package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiSkill
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSkillDao {

    @Query("SELECT * FROM ai_skills ORDER BY sortOrder ASC, skillId ASC")
    fun observeAll(): Flow<List<AiSkill>>

    @Query("SELECT * FROM ai_skills ORDER BY sortOrder ASC, skillId ASC")
    suspend fun getAll(): List<AiSkill>

    @Query("SELECT * FROM ai_skills WHERE skillId = :skillId")
    suspend fun getById(skillId: String): AiSkill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: AiSkill)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<AiSkill>)

    @Query("DELETE FROM ai_skills WHERE skillId = :skillId")
    suspend fun delete(skillId: String)

    @Query("DELETE FROM ai_skills WHERE builtin = 1")
    suspend fun deleteBuiltins()

    @Query("SELECT COUNT(*) FROM ai_skills")
    suspend fun count(): Int
}
