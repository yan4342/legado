package io.legado.app.ui.book.bookmark

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.R
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AllBookmarkViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(AllBookmarkUiState())
    val uiState: StateFlow<AllBookmarkUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AllBookmarkEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            appDb.bookmarkDao.flowAll().collect { list ->
                _uiState.update { it.copy(bookmarks = list.toImmutableList()) }
            }
        }
    }

    fun onIntent(intent: AllBookmarkIntent) {
        when (intent) {
            is AllBookmarkIntent.BookmarkClick ->
                _uiState.update { it.copy(editingBookmark = intent.bookmark) }

            AllBookmarkIntent.DismissDialog ->
                _uiState.update { it.copy(editingBookmark = null) }

            is AllBookmarkIntent.SaveBookmark -> saveBookmark(intent)
            is AllBookmarkIntent.DeleteBookmark -> deleteBookmark(intent.time)
            AllBookmarkIntent.ExportJson -> _effects.tryEmit(AllBookmarkEffect.RequestExport(ExportType.JSON))
            AllBookmarkIntent.ExportMd -> _effects.tryEmit(AllBookmarkEffect.RequestExport(ExportType.MD))
        }
    }

    private fun saveBookmark(intent: AllBookmarkIntent.SaveBookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.value.bookmarks.firstOrNull { it.time == intent.time }?.let { bookmark ->
                    appDb.bookmarkDao.insert(bookmark.copy(bookText = intent.bookText, content = intent.content))
                }
            }
            _uiState.update { it.copy(editingBookmark = null) }
        }
    }

    private fun deleteBookmark(time: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.value.bookmarks.firstOrNull { it.time == time }?.let { bookmark ->
                    appDb.bookmarkDao.delete(bookmark)
                }
            }
            _uiState.update { it.copy(editingBookmark = null) }
        }
    }

    /**
     * 导出书签为 JSON
     */
    fun exportBookmark(treeUri: Uri) {
        execute {
            val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
            val fileName = "bookmark-${dateFormat.format(Date())}.json"
            val dirDoc = FileDoc.fromUri(treeUri, true)
            dirDoc.createFileIfNotExist(fileName).openOutputStream().getOrThrow().use {
                GSON.writeToOutputStream(it, appDb.bookmarkDao.all)
            }
        }.onError {
            AppLog.put("导出失败\n${it.localizedMessage}", it, true)
            _effects.tryEmit(AllBookmarkEffect.ShowToast("导出失败\n${it.localizedMessage}"))
        }.onSuccess {
            _effects.tryEmit(AllBookmarkEffect.ShowToast(context.getString(R.string.export_success)))
        }
    }

    /**
     * 导出书签为 Markdown
     */
    fun exportBookmarkMd(treeUri: Uri) {
        execute {
            val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
            val fileName = "bookmark-${dateFormat.format(Date())}.md"
            val dirDoc = FileDoc.fromUri(treeUri, true)
            val fileDoc = dirDoc.createFileIfNotExist(fileName).openOutputStream().getOrThrow()
            fileDoc.use { outputStream ->
                var name = ""
                var author = ""
                appDb.bookmarkDao.all.forEach {
                    if (it.bookName != name && it.bookAuthor != author) {
                        name = it.bookName
                        author = it.bookAuthor
                        outputStream.write("## ${it.bookName} ${it.bookAuthor}\n\n".toByteArray())
                    }
                    outputStream.write("#### ${it.chapterName}\n\n".toByteArray())
                    outputStream.write("###### 原文\n ${it.bookText}\n\n".toByteArray())
                    outputStream.write("###### 摘要\n ${it.content}\n\n".toByteArray())
                }
            }
        }.onError {
            AppLog.put("导出失败\n${it.localizedMessage}", it, true)
            _effects.tryEmit(AllBookmarkEffect.ShowToast("导出失败\n${it.localizedMessage}"))
        }.onSuccess {
            _effects.tryEmit(AllBookmarkEffect.ShowToast(context.getString(R.string.export_success)))
        }
    }
}
