package io.legado.app.ui.rss.source.edit

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.ui.book.source.edit.BookSourceEditFieldUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

enum class RssSourceEditTab(@StringRes val titleRes: Int) {
    Base(io.legado.app.R.string.source_tab_base),
    List(io.legado.app.R.string.source_tab_list),
    WebView(io.legado.app.R.string.source_tab_content),
}

@Stable
data class RssSourceEditUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val selectedTab: RssSourceEditTab = RssSourceEditTab.Base,
    val fieldGroups: ImmutableMap<RssSourceEditTab, ImmutableList<BookSourceEditFieldUi>> = persistentMapOf(),
    val enabled: Boolean = true,
    val singleUrl: Boolean = false,
    val enabledCookieJar: Boolean = true,
    val articleStyle: Int = 0,
    val enableJs: Boolean = true,
    val loadWithBaseUrl: Boolean = true,
    val autoComplete: Boolean = false,
    val dirty: Boolean = false,
)

sealed interface RssSourceEditIntent {
    data class Load(val sourceUrl: String?) : RssSourceEditIntent
    data class SelectTab(val tab: RssSourceEditTab) : RssSourceEditIntent
    data class UpdateField(val path: String, val value: String) : RssSourceEditIntent
    data class SetEnabled(val value: Boolean) : RssSourceEditIntent
    data class SetSingleUrl(val value: Boolean) : RssSourceEditIntent
    data class SetCookieJarEnabled(val value: Boolean) : RssSourceEditIntent
    data class SetArticleStyle(val value: Int) : RssSourceEditIntent
    data class SetEnableJs(val value: Boolean) : RssSourceEditIntent
    data class SetLoadWithBaseUrl(val value: Boolean) : RssSourceEditIntent
    data class ImportText(val text: String) : RssSourceEditIntent
    data object ToggleAutoComplete : RssSourceEditIntent
    data object Save : RssSourceEditIntent
    data object SaveAndDebug : RssSourceEditIntent
    data object SaveAndLogin : RssSourceEditIntent
    data object Copy : RssSourceEditIntent
    data object Share : RssSourceEditIntent
    data object Paste : RssSourceEditIntent
    data object ClearCookie : RssSourceEditIntent
    data object ShowLog : RssSourceEditIntent
    data object ShowHelp : RssSourceEditIntent
    data object SaveAndSetVariable : RssSourceEditIntent
    data object RequestBack : RssSourceEditIntent
    data object DiscardChanges : RssSourceEditIntent
}

sealed interface RssSourceEditEffect {
    data class Finish(val sourceUrl: String) : RssSourceEditEffect
    data class OpenDebug(val sourceUrl: String) : RssSourceEditEffect
    data class OpenLogin(val sourceUrl: String) : RssSourceEditEffect
    data class CopyText(val text: String) : RssSourceEditEffect
    data class ShareText(val text: String) : RssSourceEditEffect
    data object ReadClipboard : RssSourceEditEffect
    data object ConfirmDiscard : RssSourceEditEffect
    data class OpenVariable(val sourceUrl: String) : RssSourceEditEffect
    data class ShowLog(val title: String) : RssSourceEditEffect
    data class ShowHelp(val title: String, val content: String) : RssSourceEditEffect
    data class ShowMessage(val message: String) : RssSourceEditEffect
}
