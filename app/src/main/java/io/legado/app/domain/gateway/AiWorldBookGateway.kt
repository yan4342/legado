package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiWorldBook
import kotlinx.coroutines.flow.Flow

interface AiWorldBookGateway {
    fun observeAll(): Flow<List<AiWorldBook>>
    fun observeEnabled(): Flow<List<AiWorldBook>>
    suspend fun getById(id: String): AiWorldBook?
    suspend fun getEnabled(): List<AiWorldBook>
    suspend fun updateEnabled(id: String, enabled: Boolean)
    suspend fun toggleEnabled(id: String)
    suspend fun save(
        name: String,
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
        writingStyle: String,
        grammar: String,
        plotSummary: String,
        representativeDialogues: String,
        representativeProse: String,
        sourceChapterIndices: String,
        worldBookId: String? = null,
        enabled: Boolean = true,
        canonical: Boolean = false,
    ): AiWorldBook
    suspend fun delete(id: String)
}
