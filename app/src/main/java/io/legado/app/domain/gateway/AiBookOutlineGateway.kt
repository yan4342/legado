package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiBookOutline
import kotlinx.coroutines.flow.Flow

/** 书级正典大纲读写（PK=bookUrl）。 */
interface AiBookOutlineGateway {
    suspend fun getByBookUrl(bookUrl: String): AiBookOutline?
    fun observeByBookUrl(bookUrl: String): Flow<AiBookOutline?>
    suspend fun getAll(): List<AiBookOutline>
    suspend fun upsert(outline: AiBookOutline)
    suspend fun toggleEnabled(bookUrl: String)
    suspend fun delete(bookUrl: String)
}
