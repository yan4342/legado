package io.legado.app.domain.usecase

import android.util.Log
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SearchBookSourcesUseCase {

    companion object {
        /** Same filter tag as AiToolRepository.SHELF_LOG_TAG */
        private const val TAG = "AiShelf"
    }

    data class Result(
        val books: List<SearchBook>,
        val timedOut: Boolean = false,
    )

    /**
     * Search enabled book sources. On overall timeout, returns whatever results
     * were already collected instead of failing hard (sources can be very slow).
     */
    suspend fun search(query: String, limit: Int = 20, timeoutMs: Long = 60_000): Result {
        val key = query.trim()
        require(key.isNotBlank()) { "Query is required" }
        require(AppConfig.aiAllowBookSourceFetch) { "Book-source search is disabled" }

        Log.i(TAG, "search USECASE_NET_START query=\"$key\" limit=$limit timeoutMs=$timeoutMs")
        var latest = emptyList<SearchBook>()
        return try {
            val books = withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val job = SupervisorJob()
                    val scope = CoroutineScope(cont.context + job)
                    lateinit var searchModel: SearchModel
                    searchModel = SearchModel(
                        scope,
                        object : SearchModel.CallBack {
                            override fun getSearchScope(): SearchScope = SearchScope(AppConfig.searchScope)

                            override fun onSearchStart() {
                                Log.i(TAG, "search MODEL_START query=\"$key\"")
                            }

                            override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                                val prev = latest.size
                                latest = searchBooks
                                if (searchBooks.size > prev) {
                                    Log.d(TAG, "search MODEL_PROGRESS count=${searchBooks.size}")
                                }
                                if (searchBooks.size >= limit) {
                                    searchModel.close()
                                    if (cont.isActive) {
                                        Log.i(
                                            TAG,
                                            "search MODEL_EARLY_DONE count=${searchBooks.size} (hit limit)",
                                        )
                                        cont.resume(searchBooks.take(limit))
                                    }
                                }
                            }

                            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                                if (cont.isActive) {
                                    Log.i(
                                        TAG,
                                        "search MODEL_FINISH count=${latest.size} empty=$isEmpty hasMore=$hasMore",
                                    )
                                    cont.resume(latest.take(limit))
                                }
                            }

                            override fun onSearchCancel(exception: Throwable?) {
                                when {
                                    exception != null && cont.isActive -> {
                                        Log.e(TAG, "search MODEL_CANCEL: ${exception.message}")
                                        cont.resumeWithException(exception)
                                    }
                                    cont.isActive -> {
                                        Log.w(TAG, "search MODEL_CANCEL count=${latest.size}")
                                        cont.resume(latest.take(limit))
                                    }
                                }
                            }
                        },
                    )
                    cont.invokeOnCancellation {
                        Log.w(TAG, "search USECASE_CANCELLED query=\"$key\" partial=${latest.size}")
                        searchModel.close()
                        job.cancel()
                    }
                    searchModel.search(System.currentTimeMillis(), key)
                }
            }
            Result(books = books)
        } catch (e: TimeoutCancellationException) {
            Log.w(
                TAG,
                "search TIMEOUT query=\"$key\" returning partial=${latest.size} (limit=$limit)",
            )
            Result(books = latest.take(limit), timedOut = true)
        }
    }
}
