package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.usecase.SkillPackageCodec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSkillEditUiState(
    val skillId: String = "",
    val isNew: Boolean = true,
    val markdown: String = "",
    val loading: Boolean = true,
    val loadError: String? = null,
)

sealed interface AiSkillEditIntent {
    data class UpdateMarkdown(val markdown: String) : AiSkillEditIntent
    data object Save : AiSkillEditIntent
}

sealed interface AiSkillEditEffect {
    data class ShowMessage(val resId: Int, val formatArg: String? = null) : AiSkillEditEffect
    data object Saved : AiSkillEditEffect
}

class AiSkillEditViewModel(
    skillId: String,
    private val skillGateway: AiSkillGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiSkillEditUiState(
            skillId = skillId,
            isNew = skillId.isBlank(),
            markdown = if (skillId.isBlank()) SkillPackageCodec.TEMPLATE else "",
            loading = skillId.isNotBlank(),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSkillEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        if (skillId.isNotBlank()) {
            viewModelScope.launch {
                skillGateway.loadMarkdownForEdit(skillId)
                    .onSuccess { markdown ->
                        _uiState.update {
                            it.copy(markdown = markdown, loading = false, loadError = null)
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(loading = false, loadError = err.message ?: "load failed")
                        }
                    }
            }
        } else {
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun onIntent(intent: AiSkillEditIntent) {
        when (intent) {
            is AiSkillEditIntent.UpdateMarkdown ->
                _uiState.update { it.copy(markdown = intent.markdown) }
            AiSkillEditIntent.Save -> save()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.markdown.isBlank()) {
            _effects.tryEmit(AiSkillEditEffect.ShowMessage(R.string.ai_skill_edit_failed, "SKILL.md is empty"))
            return
        }
        viewModelScope.launch {
            skillGateway.saveUserSkillMarkdown(state.markdown)
                .onSuccess {
                    _effects.tryEmit(AiSkillEditEffect.ShowMessage(R.string.ai_skill_saved))
                    _effects.tryEmit(AiSkillEditEffect.Saved)
                }
                .onFailure {
                    _effects.tryEmit(
                        AiSkillEditEffect.ShowMessage(R.string.ai_skill_edit_failed, it.message ?: ""),
                    )
                }
        }
    }
}
