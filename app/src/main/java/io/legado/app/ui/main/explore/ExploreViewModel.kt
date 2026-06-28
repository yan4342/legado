package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class ExploreViewModel(application: Application) : BaseViewModel(application) {

    private val _sources = MutableStateFlow<List<BookSourcePart>>(emptyList())
    val sourcesFlow: StateFlow<List<BookSourcePart>> = _sources.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groupsFlow: StateFlow<List<String>> = _groups.asStateFlow()

    init {
        viewModelScope.launch {
            appDb.bookSourceDao.flowExplore()
                .catch { AppLog.put("发现界面更新数据出错", it) }
                .conflate()
                .flowOn(IO)
                .collect { _sources.value = it }
        }
        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .catch { AppLog.put("发现界面获取分组数据失败\n${it.localizedMessage}", it) }
                .conflate()
                .collect { _groups.value = it }
        }
    }

    fun topSource(bookSource: BookSourcePart) {
        execute {
            val minXh = appDb.bookSourceDao.minOrder
            bookSource.customOrder = minXh - 1
            appDb.bookSourceDao.upOrder(bookSource)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
        }
    }

}