package io.legado.app.ui.main.rss

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class RssViewModel(application: Application) : BaseViewModel(application) {

    private val _sources = MutableStateFlow<List<RssSource>>(emptyList())
    val sourcesFlow: StateFlow<List<RssSource>> = _sources.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groupsFlow: StateFlow<List<String>> = _groups.asStateFlow()

    init {
        viewModelScope.launch {
            appDb.rssSourceDao.flowEnabled()
                .catch { AppLog.put("RSS界面更新数据出错", it) }
                .conflate()
                .collect { _sources.value = it }
        }
        viewModelScope.launch {
            appDb.rssSourceDao.flowEnabledGroups()
                .catch { AppLog.put("RSS界面获取分组数据失败\n${it.localizedMessage}", it) }
                .conflate()
                .collect { _groups.value = it }
        }
    }

    fun topSource(vararg sources: RssSource) {
        execute {
            sources.sortBy { it.customOrder }
            val minOrder = appDb.rssSourceDao.minOrder - 1
            val array = Array(sources.size) {
                sources[it].copy(customOrder = minOrder - it)
            }
            appDb.rssSourceDao.update(*array)
        }
    }

    fun bottomSource(vararg sources: RssSource) {
        execute {
            sources.sortBy { it.customOrder }
            val maxOrder = appDb.rssSourceDao.maxOrder + 1
            val array = Array(sources.size) {
                sources[it].copy(customOrder = maxOrder + it)
            }
            appDb.rssSourceDao.update(*array)
        }
    }

    fun del(vararg rssSource: RssSource) {
        execute {
            SourceHelp.deleteRssSources(rssSource.toList())
        }
    }

    fun disable(rssSource: RssSource) {
        execute {
            rssSource.enabled = false
            appDb.rssSourceDao.update(rssSource)
        }
    }

    fun getSingleUrl(rssSource: RssSource, onSuccess: (url: String) -> Unit) {
        execute {
            var sortUrl = rssSource.sortUrl
            if (!sortUrl.isNullOrBlank()) {
                if (sortUrl.startsWith("<js>", false)
                    || sortUrl.startsWith("@js:", false)
                ) {
                    val jsStr = if (sortUrl.startsWith("@")) {
                        sortUrl.substring(4)
                    } else {
                        sortUrl.substring(4, sortUrl.lastIndexOf("<"))
                    }
                    val result = runScriptWithContext {
                        rssSource.evalJS(jsStr)?.toString()
                    }
                    if (!result.isNullOrBlank()) {
                        sortUrl = result
                    }
                }
                if (sortUrl.contains("::")) {
                    return@execute sortUrl.split("::")[1]
                } else {
                    return@execute sortUrl
                }
            }
            rssSource.sourceUrl
        }.timeout(10000)
            .onSuccess {
                onSuccess.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
    }


}