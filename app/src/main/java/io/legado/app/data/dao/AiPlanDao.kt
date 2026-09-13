package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface AiPlanDao {

    /** 会话的全部计划（按更新时间倒序，最新在前）。 */
    @Query("SELECT * FROM ai_plans WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    fun observeByConversation(conversationId: String): Flow<List<AiPlan>>

    /** 会话的全部计划。 */
    @Query("SELECT * FROM ai_plans WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    suspend fun getByConversation(conversationId: String): List<AiPlan>

    /** 会话中状态为 pending 的计划（预期最多一条）。 */
    @Query("SELECT * FROM ai_plans WHERE conversationId = :conversationId AND status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getPendingByConversation(conversationId: String, status: String = AiPlan.STATUS_PENDING): AiPlan?

    @Query("SELECT * FROM ai_plans WHERE id = :planId")
    suspend fun getById(planId: String): AiPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: AiPlan)

    @Query("UPDATE ai_plans SET status = :status, updatedAt = :updatedAt WHERE id = :planId")
    suspend fun updateStatus(planId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    /** 把会话内指定旧状态的计划批量改为新状态（如新计划创建时作废旧 pending）。 */
    @Query("UPDATE ai_plans SET status = :newStatus, updatedAt = :updatedAt WHERE conversationId = :conversationId AND status = :oldStatus")
    suspend fun updateStatusByConversation(
        conversationId: String,
        oldStatus: String,
        newStatus: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM ai_plans WHERE id = :planId")
    suspend fun delete(planId: String)

    @Query("DELETE FROM ai_plans WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}
