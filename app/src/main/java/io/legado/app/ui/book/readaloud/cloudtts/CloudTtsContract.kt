package io.legado.app.ui.book.readaloud.cloudtts

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CloudTtsUiState(
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val exporting: Boolean = false,
    val engines: ImmutableList<CloudTtsEngineItemUi> = persistentListOf(),
    val editor: CloudTtsEngineEditorUi? = null,
    val exportPicker: CloudTtsExportPickerUi? = null,
)

@Stable
data class CloudTtsEngineItemUi(
    val id: String,
    val title: String,
    val summary: String,
    val provider: String = "",
    val exportable: Boolean = false,
)

@Stable
data class CloudTtsEngineEditorUi(
    val editingEngineId: String? = null,
    val name: String = "",
    val provider: String = "mimo",
    val baseUrl: String = "",
    val apiKey: String = "",
    val secretKey: String = "",
    val model: String = "",
    val region: String = "",
    val appId: String = "",
    val optionsJson: String = "{}",
    val enabled: Boolean = true,
    /** When true, the dialog edits [rawJson] instead of individual fields. */
    val jsonMode: Boolean = false,
    val rawJson: String = "",
)

@Stable
data class CloudTtsExportPickerUi(
    val engineId: String,
    val engineName: String,
    val voices: ImmutableList<CloudTtsVoiceOptionUi> = persistentListOf(),
    val selectedVoiceIds: ImmutableList<String> = persistentListOf(),
    val loadingVoices: Boolean = false,
)

@Stable
data class CloudTtsVoiceOptionUi(
    val id: String,
    val displayName: String,
)

sealed interface CloudTtsIntent {
    data object Refresh : CloudTtsIntent
    data object SyncVoices : CloudTtsIntent
    data object AddEngine : CloudTtsIntent
    data class EditEngine(val id: String) : CloudTtsIntent
    data class DeleteEngine(val id: String) : CloudTtsIntent
    data class UpdateEditor(val editor: CloudTtsEngineEditorUi) : CloudTtsIntent
    data object ToggleEditorJsonMode : CloudTtsIntent
    data object FormatEditorJson : CloudTtsIntent
    data object DismissEditor : CloudTtsIntent
    data object Save : CloudTtsIntent
    data class OpenExportPicker(val id: String) : CloudTtsIntent
    data object DismissExportPicker : CloudTtsIntent
    data class ToggleExportVoice(val voiceId: String) : CloudTtsIntent
    data object ConfirmExport : CloudTtsIntent
}

sealed interface CloudTtsEffect {
    data class ShowToast(val message: String) : CloudTtsEffect
}
