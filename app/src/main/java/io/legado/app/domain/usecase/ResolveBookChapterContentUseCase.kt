package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.withTimeout

class ResolveBookChapterContentUseCase(
    private val bookSourceDao: BookSourceDao,
) {

    sealed class Result {
        data class Success(val content: String) : Result()
        data class Error(val message: String, val hint: String? = null) : Result()
    }

    suspend fun resolveRaw(
        book: Book,
        chapter: BookChapter,
        allowNetworkFetch: Boolean = false,
    ): Result {
        BookHelp.getContent(book, chapter)?.let { return Result.Success(it) }
        if (book.isLocal) {
            return Result.Error("Failed to read local book file")
        }
        if (!AppConfig.aiAllowBookSourceFetch || !allowNetworkFetch) {
            return Result.Error(
                message = "Chapter not cached",
                hint = "Enable book source fetch in AI ability settings and confirm the read",
            )
        }
        val bookSource = bookSourceDao.getBookSource(book.origin)
            ?: return Result.Error("Book source not found")
        return try {
            val content = withTimeout(FETCH_TIMEOUT_MS) {
                WebBook.getContentAwait(bookSource, book, chapter)
            }
            if (content.isBlank()) {
                Result.Error("Fetched chapter content is empty")
            } else {
                Result.Success(content)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to fetch chapter from book source")
        }
    }

    companion object {
        private const val FETCH_TIMEOUT_MS = 60_000L
    }
}
