package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.usecase.SkillImportConflictException
import io.legado.app.domain.usecase.SkillRepoUrlImporter
import io.legado.app.help.config.AppConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiSkillsViewModel(
    private val skillGateway: AiSkillGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiSkillsUiState(downloadProxy = AppConfig.aiSkillDownloadProxy),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSkillsEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            skillGateway.ensureSeeded()
            skillGateway.observeAll().collect { skills ->
                _uiState.update { it.copy(skills = skills.toImmutableList()) }
            }
        }
    }

    fun onIntent(intent: AiSkillsIntent) {
        when (intent) {
            is AiSkillsIntent.SetSkillEnabled -> viewModelScope.launch {
                val skill = skillGateway.getById(intent.skillId) ?: return@launch
                skillGateway.save(skill.copy(enabled = intent.enabled))
            }
            is AiSkillsIntent.MoveSkill -> viewModelScope.launch {
                skillGateway.moveSort(intent.skillId, intent.direction)
            }
            is AiSkillsIntent.DeleteSkill -> viewModelScope.launch {
                if (skillGateway.delete(intent.skillId)) {
                    _effects.tryEmit(AiSkillsEffect.ShowMessage(R.string.ai_skill_deleted))
                } else {
                    _effects.tryEmit(AiSkillsEffect.ShowMessage(R.string.ai_skill_edit_failed, "not found"))
                }
            }
            AiSkillsIntent.OpenSkillImport ->
                _uiState.update {
                    it.copy(
                        showSkillImport = true,
                        skillImportText = "",
                        showSkillImportOverwriteConfirm = false,
                        skillImportConflictIds = persistentListOf(),
                    )
                }
            AiSkillsIntent.CloseSkillImport ->
                _uiState.update {
                    it.copy(
                        showSkillImport = false,
                        skillImportText = "",
                        showSkillImportOverwriteConfirm = false,
                        skillImportConflictIds = persistentListOf(),
                    )
                }
            is AiSkillsIntent.UpdateSkillImportText ->
                _uiState.update { it.copy(skillImportText = intent.text) }
            AiSkillsIntent.ConfirmSkillImport -> confirmSkillImport(overwrite = false)
            AiSkillsIntent.ConfirmSkillImportOverwrite -> confirmSkillImport(overwrite = true)
            AiSkillsIntent.DismissSkillImportOverwrite ->
                _uiState.update {
                    it.copy(
                        showSkillImportOverwriteConfirm = false,
                        skillImportConflictIds = persistentListOf(),
                    )
                }
            is AiSkillsIntent.ExportSkill -> viewModelScope.launch {
                skillGateway.exportPackage(intent.skillId)
                    .onSuccess { text ->
                        _uiState.update { it.copy(showSkillExport = true, skillExportText = text) }
                    }
                    .onFailure {
                        _effects.tryEmit(
                            AiSkillsEffect.ShowMessage(R.string.ai_skill_export_failed, it.message ?: ""),
                        )
                    }
            }
            AiSkillsIntent.CloseSkillExport ->
                _uiState.update { it.copy(showSkillExport = false, skillExportText = "") }
            is AiSkillsIntent.UpdateDownloadProxy ->
                _uiState.update { it.copy(downloadProxy = intent.proxy) }
            AiSkillsIntent.SaveDownloadProxy -> saveDownloadProxy()
        }
    }

    private fun saveDownloadProxy() {
        val proxy = _uiState.value.downloadProxy.trim()
        if (!SkillRepoUrlImporter.isValidProxy(proxy)) {
            _effects.tryEmit(AiSkillsEffect.ShowMessage(R.string.ai_skills_proxy_invalid))
            return
        }
        AppConfig.aiSkillDownloadProxy = proxy
        _uiState.update { it.copy(downloadProxy = proxy) }
        _effects.tryEmit(AiSkillsEffect.ShowMessage(R.string.ai_skills_proxy_saved))
    }

    private fun confirmSkillImport(overwrite: Boolean) {
        val raw = _uiState.value.skillImportText.trim()
        if (raw.isBlank()) {
            _effects.tryEmit(AiSkillsEffect.ShowMessage(R.string.ai_skill_import_failed, "empty"))
            return
        }
        viewModelScope.launch {
            val result = if (looksLikeImportUrl(raw)) {
                skillGateway.importFromUrl(raw, overwrite = overwrite).map { 1 }
            } else {
                skillGateway.importPackage(raw, overwrite = overwrite)
            }
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showSkillImport = false,
                            skillImportText = "",
                            showSkillImportOverwriteConfirm = false,
                            skillImportConflictIds = persistentListOf(),
                        )
                    }
                    _effects.tryEmit(
                        AiSkillsEffect.ShowMessage(R.string.ai_skill_imported_count, "1"),
                    )
                }
                .onFailure { err ->
                    if (!overwrite && err is SkillImportConflictException) {
                        _uiState.update {
                            it.copy(
                                showSkillImportOverwriteConfirm = true,
                                skillImportConflictIds = err.conflictIds.toImmutableList(),
                            )
                        }
                    } else {
                        _effects.tryEmit(
                            AiSkillsEffect.ShowMessage(R.string.ai_skill_import_failed, err.message ?: ""),
                        )
                    }
                }
        }
    }

    private fun looksLikeImportUrl(text: String): Boolean {
        val line = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (line.size != 1) return false
        val u = line.first()
        return u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)
    }
}
