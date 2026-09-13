package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.help.AppWebDav

class WebDavReadingProgressRepository : ReadingProgressGateway {

    override val isConfigured: Boolean
        get() = AppWebDav.isOk

    override suspend fun getProgress(name: String, author: String): ReadingProgress? {
        val book = Book(name = name, author = author, bookUrl = "", tocUrl = "")
        return AppWebDav.getBookProgress(book)?.let {
            ReadingProgress(
                name = it.name,
                author = it.author,
                durChapterIndex = it.durChapterIndex,
                durChapterPos = it.durChapterPos,
                durChapterTime = it.durChapterTime,
                durChapterTitle = it.durChapterTitle
            )
        }
    }

    override suspend fun uploadProgress(progress: ReadingProgress): Long? {
        val uploadTime = System.currentTimeMillis()
        val bookProgress = BookProgress(
            name = progress.name,
            author = progress.author,
            durChapterIndex = progress.durChapterIndex,
            durChapterPos = progress.durChapterPos,
            durChapterTime = progress.durChapterTime,
            durChapterTitle = progress.durChapterTitle
        )
        return try {
            AppWebDav.uploadBookProgress(bookProgress)
            uploadTime
        } catch (e: Exception) {
            null
        }
    }
}
