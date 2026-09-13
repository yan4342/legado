package io.legado.app.ui.config.ai

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiToolConfig
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiAbilityManagementUiState(
    val tools: ImmutableList<AiToolConfig> = persistentListOf(),
    val availableModels: ImmutableList<AiModelProfile> = persistentListOf(),
    val editingTool: AiToolConfig? = null,
    val templatePreview: String? = null,
    val habitMemories: ImmutableList<AiMemory> = persistentListOf(),
    val showHabitMemoryList: Boolean = false,
)

sealed interface AiAbilityManagementIntent {
    data class Edit(val tool: AiToolConfig) : AiAbilityManagementIntent
    data class UpdateField(val toolName: String, val field: String, val value: String) : AiAbilityManagementIntent
    data class UpdateBoolean(val toolName: String, val field: String, val value: Boolean) : AiAbilityManagementIntent
    data class UpdateSubModelProfile(val toolName: String, val profileId: String?) : AiAbilityManagementIntent
    data class ResetDefault(val toolName: String) : AiAbilityManagementIntent
    data object Save : AiAbilityManagementIntent
    data object CancelEdit : AiAbilityManagementIntent
    data object OpenHabitMemoryList : AiAbilityManagementIntent
    data object CloseHabitMemoryList : AiAbilityManagementIntent
    data class DeleteHabitMemory(val key: String) : AiAbilityManagementIntent
    data class SaveHabitMemory(
        val originalKey: String?,
        val key: String,
        val value: String,
    ) : AiAbilityManagementIntent
}

sealed interface AiAbilityManagementEffect {
    data class ShowMessage(@StringRes val resId: Int, val formatArg: String? = null) : AiAbilityManagementEffect
}
