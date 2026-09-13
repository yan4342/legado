package io.legado.app.ui.config.ai

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiSkillsUiState(
    val skills: ImmutableList<io.legado.app.data.entities.AiSkill> = persistentListOf(),
    val showSkillImport: Boolean = false,
    val skillImportText: String = "",
    val showSkillImportOverwriteConfirm: Boolean = false,
    val skillImportConflictIds: ImmutableList<String> = persistentListOf(),
    val showSkillExport: Boolean = false,
    val skillExportText: String = "",
    /** Draft for Skill URL-download proxy; blank = direct. */
    val downloadProxy: String = "",
)

sealed interface AiSkillsIntent {
    data class SetSkillEnabled(val skillId: String, val enabled: Boolean) : AiSkillsIntent
    data class MoveSkill(val skillId: String, val direction: Int) : AiSkillsIntent
    data class DeleteSkill(val skillId: String) : AiSkillsIntent
    data object OpenSkillImport : AiSkillsIntent
    data object CloseSkillImport : AiSkillsIntent
    data class UpdateSkillImportText(val text: String) : AiSkillsIntent
    data object ConfirmSkillImport : AiSkillsIntent
    data object ConfirmSkillImportOverwrite : AiSkillsIntent
    data object DismissSkillImportOverwrite : AiSkillsIntent
    data class ExportSkill(val skillId: String) : AiSkillsIntent
    data object CloseSkillExport : AiSkillsIntent
    data class UpdateDownloadProxy(val proxy: String) : AiSkillsIntent
    data object SaveDownloadProxy : AiSkillsIntent
}

sealed interface AiSkillsEffect {
    data class ShowMessage(@StringRes val resId: Int, val formatArg: String? = null) : AiSkillsEffect
}
