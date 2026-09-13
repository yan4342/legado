package io.legado.app.domain.usecase

import android.util.Log
import io.legado.app.constant.BookType
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.WebBook

class AddBookToBookshelfUseCase(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookSourceDao: BookSourceDao,
    private val searchBookDao: SearchBookDao,
) {

    companion object {
        /** Same filter tag as AiToolRepository.SHELF_LOG_TAG */
        private const val TAG = "AiShelf"
    }

    data class Result(
        val book: Book,
        val chapterCount: Int,
        val alreadyOnShelf: Boolean,
    )

    suspend fun add(
        bookUrl: String?,
        bookName: String?,
        bookAuthor: String?,
    ): Result {
        require(AppConfig.aiAllowBookSourceFetch) {
            "Book-source fetch is disabled in AI ability settings"
        }
        Log.i(TAG, "add USECASE_ENTER url=${bookUrl?.take(64)} name=$bookName author=$bookAuthor")
        val existing = resolveOnShelf(bookUrl, bookName, bookAuthor)
        if (existing != null) {
            val count = bookChapterDao.getChapterList(existing.bookUrl).size
            Log.i(TAG, "add USECASE_SKIP alreadyOnShelf name=${existing.name} chapters=$count")
            return Result(existing, count, alreadyOnShelf = true)
        }
        val seed = resolveSeedBook(bookUrl, bookName, bookAuthor)
            ?: error("Book not found — run search_book_sources first and use the exact bookUrl from results")
        val book = seed.copy()
        book.removeType(BookType.notShelf)
        val source = bookSourceDao.getBookSource(book.origin)
            ?: error("Book source not found: ${book.originName}")
        Log.i(TAG, "add USECASE_NET_START info+toc origin=${source.bookSourceName} name=${book.name}")
        WebBook.getBookInfoAwait(source, book, canReName = true)
        val chapters = WebBook.getChapterListAwait(source, book, runPerJs = false).getOrThrow()
        if (book.order == 0) {
            book.order = bookDao.minOrder - 1
        }
        book.save()
        bookChapterDao.delByBook(book.bookUrl)
        bookChapterDao.insert(*chapters.toTypedArray())
        Log.i(TAG, "add USECASE_NET_DONE name=${book.name} chapters=${chapters.size}")
        return Result(book, chapters.size, alreadyOnShelf = false)
    }

    private fun resolveOnShelf(bookUrl: String?, bookName: String?, bookAuthor: String?): Book? {
        bookUrl?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.takeIf { !it.isNotShelf }?.let { return it }
        }
        val name = bookName?.trim().orEmpty()
        val author = bookAuthor?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.takeIf { !it.isNotShelf }?.let { return it }
        }
        if (name.isNotBlank()) {
            return bookDao.findByName(name).firstOrNull { !it.isNotShelf }
        }
        return null
    }

    private fun resolveSeedBook(bookUrl: String?, bookName: String?, bookAuthor: String?): Book? {
        bookUrl?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.let { return it }
            searchBookDao.getSearchBook(url)?.toBook()?.let { return it }
        }
        val name = bookName?.trim().orEmpty()
        val author = bookAuthor?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.let { return it }
            searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let { return it }
        }
        if (name.isNotBlank()) {
            return bookDao.findByName(name).firstOrNull()
                ?: searchBookDao.getFirstByNameAuthor(name, author)?.toBook()
        }
        return null
    }
}
