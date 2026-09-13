package io.legado.app.ui.rss.source.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssSourceRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCacheManager
import io.legado.app.help.RuleComplete
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.removeSortCache
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.book.source.edit.BookSourceEditFieldUi
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssSourceEditViewModel(
    application: Application,
    private val repository: RssSourceRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RssSourceEditUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RssSourceEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var originalSource: RssSource? = null
    private var draftJson = JsonObject()
    private var baselineJson = ""

    fun onIntent(intent: RssSourceEditIntent) {
        when (intent) {
            is RssSourceEditIntent.Load -> load(intent.sourceUrl)
            is RssSourceEditIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            is RssSourceEditIntent.UpdateField -> updateField(intent.path, intent.value)
            is RssSourceEditIntent.SetEnabled -> updateFlags { copy(enabled = intent.value) }
            is RssSourceEditIntent.SetSingleUrl -> updateFlags { copy(singleUrl = intent.value) }
            is RssSourceEditIntent.SetCookieJarEnabled -> updateFlags { copy(enabledCookieJar = intent.value) }
            is RssSourceEditIntent.SetArticleStyle -> updateFlags { copy(articleStyle = intent.value) }
            is RssSourceEditIntent.SetEnableJs -> updateFlags { copy(enableJs = intent.value) }
            is RssSourceEditIntent.SetLoadWithBaseUrl -> updateFlags { copy(loadWithBaseUrl = intent.value) }
            is RssSourceEditIntent.ImportText -> importText(intent.text)
            RssSourceEditIntent.ToggleAutoComplete -> _uiState.update { it.copy(autoComplete = !it.autoComplete) }
            RssSourceEditIntent.Save -> save(RssSourceEditEffect::Finish)
            RssSourceEditIntent.SaveAndDebug -> save { RssSourceEditEffect.OpenDebug(it) }
            RssSourceEditIntent.SaveAndLogin -> save { RssSourceEditEffect.OpenLogin(it) }
            RssSourceEditIntent.Copy -> _effects.tryEmit(
                RssSourceEditEffect.CopyText(GSON.toJson(currentSource()))
            )
            RssSourceEditIntent.Share -> _effects.tryEmit(
                RssSourceEditEffect.ShareText(GSON.toJson(currentSource()))
            )
            RssSourceEditIntent.Paste -> _effects.tryEmit(RssSourceEditEffect.ReadClipboard)
            RssSourceEditIntent.ClearCookie -> clearCookie()
            RssSourceEditIntent.ShowLog -> _effects.tryEmit(
                RssSourceEditEffect.ShowLog(getApplication<Application>().getString(R.string.log))
            )
            RssSourceEditIntent.ShowHelp -> showHelp()
            RssSourceEditIntent.SaveAndSetVariable -> save { RssSourceEditEffect.OpenVariable(it) }
            RssSourceEditIntent.RequestBack -> if (_uiState.value.dirty) {
                _effects.tryEmit(RssSourceEditEffect.ConfirmDiscard)
            } else _effects.tryEmit(RssSourceEditEffect.Finish(""))

            RssSourceEditIntent.DiscardChanges -> _effects.tryEmit(RssSourceEditEffect.Finish(""))
        }
    }

    private fun load(sourceUrl: String?) = viewModelScope.launch(Dispatchers.IO) {
        val source = sourceUrl?.let { repository.getByKey(it) } ?: RssSource()
        withContext(Dispatchers.Main) {
            applySource(source, asOriginal = true)
            if (sourceUrl == null) originalSource = null
        }
    }

    private fun showHelp() = viewModelScope.launch(Dispatchers.IO) {
        val content = getApplication<Application>().assets
            .open("web/help/md/ruleHelp.md")
            .bufferedReader()
            .use { it.readText() }
        _effects.emit(
            RssSourceEditEffect.ShowHelp(
                getApplication<Application>().getString(R.string.help),
                content
            )
        )
    }

    private fun applySource(source: RssSource, asOriginal: Boolean = false) {
        if (asOriginal) originalSource = source
        draftJson = JsonParser.parseString(GSON.toJson(source)).asJsonObject
        if (asOriginal) baselineJson = GSON.toJson(source)
        val tab = _uiState.value.selectedTab
        _uiState.value = RssSourceEditUiState(
            loading = false,
            selectedTab = tab,
            fieldGroups = fieldGroups(),
            enabled = source.enabled,
            singleUrl = source.singleUrl,
            enabledCookieJar = source.enabledCookieJar == true,
            articleStyle = source.articleStyle,
            enableJs = source.enableJs,
            loadWithBaseUrl = source.loadWithBaseUrl,
            autoComplete = _uiState.value.autoComplete,
        )
    }

    private fun updateField(path: String, value: String) {
        draftJson.setPath(path, value)
        _uiState.update { state ->
            state.copy(
                fieldGroups = state.fieldGroups.mapValues { (_, fields) ->
                    fields.map { if (it.path == path) it.copy(value = value) else it }
                        .toImmutableList()
                }.toImmutableMap(),
                dirty = isDirty(state),
            )
        }
    }

    private fun updateFlags(transform: RssSourceEditUiState.() -> RssSourceEditUiState) {
        _uiState.update { state -> transform(state).let { it.copy(dirty = isDirty(it)) } }
    }

    private fun isDirty(state: RssSourceEditUiState): Boolean =
        GSON.toJson(currentSource(state)) != baselineJson

    private fun currentSource(state: RssSourceEditUiState = _uiState.value): RssSource {
        return GSON.fromJson(draftJson, RssSource::class.java).apply {
            enabled = state.enabled
            singleUrl = state.singleUrl
            enabledCookieJar = state.enabledCookieJar
            articleStyle = state.articleStyle
            enableJs = state.enableJs
            loadWithBaseUrl = state.loadWithBaseUrl
            if (state.autoComplete) completeRules(this)
        }
    }

    private fun completeRules(source: RssSource) {
        source.ruleNextPage = RuleComplete.autoComplete(source.ruleNextPage, source.ruleArticles, 2)
        source.ruleTitle = RuleComplete.autoComplete(source.ruleTitle, source.ruleArticles)
        source.rulePubDate = RuleComplete.autoComplete(source.rulePubDate, source.ruleArticles)
        source.ruleDescription = RuleComplete.autoComplete(source.ruleDescription, source.ruleArticles)
        source.ruleImage = RuleComplete.autoComplete(source.ruleImage, source.ruleArticles, 3)
        source.ruleLink = RuleComplete.autoComplete(source.ruleLink, source.ruleArticles)
        source.ruleContent = RuleComplete.autoComplete(source.ruleContent, source.ruleArticles)
    }

    private fun save(effect: (String) -> RssSourceEditEffect) =
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saving = true) }
            runCatching {
                val source = currentSource()
                if (source.sourceUrl.isBlank() || source.sourceName.isBlank()) {
                    throw NoStackTraceException(getApplication<Application>().getString(R.string.non_null_name_url))
                }
                val old = originalSource ?: RssSource()
                if (!source.equal(old)) {
                    source.lastUpdateTime = System.currentTimeMillis()
                    if (old.sortUrl != source.sortUrl) old.removeSortCache()
                    if (old.jsLib != source.jsLib) SharedJsScope.remove(old.jsLib)
                }
                originalSource?.let {
                    appDb.rssSourceDao.delete(it)
                    if (it.sourceUrl != source.sourceUrl) {
                        appDb.rssStarDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                        appDb.rssArticleDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                        appDb.cacheDao.deleteSourceVariables(it.sourceUrl)
                        AppCacheManager.clearSourceVariables()
                    }
                }
                repository.insertSources(source)
                originalSource = source
                baselineJson = GSON.toJson(source)
                source.sourceUrl
            }.onSuccess { url ->
                _uiState.update { it.copy(saving = false, dirty = false) }
                _effects.emit(effect(url))
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false) }
                _effects.emit(RssSourceEditEffect.ShowMessage(error.localizedMessage ?: "Error"))
            }
        }

    private fun importText(text: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            GSON.fromJson(text, RssSource::class.java)
        }.onSuccess { source ->
            withContext(Dispatchers.Main) { applySource(source) }
            _uiState.update { it.copy(dirty = true) }
        }.onFailure {
            _effects.emit(RssSourceEditEffect.ShowMessage(it.localizedMessage ?: "格式不对"))
        }
    }

    private fun clearCookie() = viewModelScope.launch(Dispatchers.IO) {
        CookieStore.removeCookie(currentSource().sourceUrl)
        _effects.emit(
            RssSourceEditEffect.ShowMessage(getApplication<Application>().getString(R.string.success))
        )
    }

    private fun fieldsFor(tab: RssSourceEditTab) = FIELD_SPECS.getValue(tab).map { spec ->
        BookSourceEditFieldUi(spec.path, spec.labelRes, spec.label, draftJson.stringAt(spec.path))
    }.toImmutableList()

    private fun fieldGroups() =
        RssSourceEditTab.entries.associateWith(::fieldsFor).toImmutableMap()

    private data class FieldSpec(
        val path: String,
        val labelRes: Int? = null,
        val label: String? = null
    )

    companion object {
        private fun f(path: String, label: Int) = FieldSpec(path, labelRes = label)
        private val FIELD_SPECS = mapOf(
            RssSourceEditTab.Base to listOf(
                f("sourceName", R.string.source_name),
                f("sourceUrl", R.string.source_url),
                f("sourceIcon", R.string.source_icon),
                f("sourceGroup", R.string.source_group),
                f("sourceComment", R.string.comment),
                f("sortUrl", R.string.sort_url),
                f("loginUrl", R.string.login_url),
                f("loginUi", R.string.login_ui),
                f("loginCheckJs", R.string.login_check_js),
                f("coverDecodeJs", R.string.cover_decode_js),
                f("header", R.string.source_http_header),
                f("variableComment", R.string.variable_comment),
                f("concurrentRate", R.string.concurrent_rate),
                FieldSpec("jsLib", label = "jsLib"),
            ),
            RssSourceEditTab.List to listOf(
                f("ruleArticles", R.string.r_articles),
                f("ruleNextPage", R.string.r_next),
                f("ruleTitle", R.string.r_title),
                f("rulePubDate", R.string.r_date),
                f("ruleDescription", R.string.r_description),
                f("ruleImage", R.string.r_image),
                f("ruleLink", R.string.r_link),
            ),
            RssSourceEditTab.WebView to listOf(
                f("ruleContent", R.string.r_content),
                f("style", R.string.r_style),
                f("injectJs", R.string.r_inject_js),
                f("contentWhitelist", R.string.c_whitelist),
                f("contentBlacklist", R.string.c_blacklist),
                FieldSpec(
                    "shouldOverrideUrlLoading",
                    label = "url跳转拦截(js, 返回true拦截,js变量url,可以通过js打开url,比如调用阅读搜索,添加书架等,简化规则写法,不用webView js注入)"
                ),
            ),
        )
    }
}

private fun JsonObject.stringAt(path: String): String {
    val parts = path.split('.')
    var current: JsonObject = this
    parts.dropLast(1).forEach {
        val child = current.get(it)
        if (child == null || !child.isJsonObject) return ""
        current = child.asJsonObject
    }
    return current.get(parts.last())?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}

private fun JsonObject.setPath(path: String, value: String) {
    val parts = path.split('.')
    var current = this
    parts.dropLast(1).forEach { part ->
        val child = current.get(part)
        current = if (child != null && child.isJsonObject) child.asJsonObject
        else JsonObject().also { current.add(part, it) }
    }
    if (value.isBlank()) current.remove(parts.last()) else current.addProperty(parts.last(), value)
}
