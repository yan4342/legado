package io.legado.app.ui.config.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiPromptPipelineGateway
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptBlockPosition
import io.legado.app.domain.prompt.PromptBlockSpec
import io.legado.app.domain.prompt.PromptPipelineDefaults
import io.legado.app.domain.prompt.PromptPipelinePreset
import io.legado.app.domain.prompt.PromptPresetExporter
import io.legado.app.domain.prompt.PromptPresetImporter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun blocksMatch(
    left: List<PromptBlockSpec>,
    right: List<PromptBlockSpec>,
): Boolean {
    if (left.size != right.size) return false
    val a = left.sortedBy { it.order }
    val b = right.sortedBy { it.order }
    return a.zip(b).all { (x, y) -> x == y }
}

data class PromptPipelineUiState(
    val presets: ImmutableList<PromptPipelinePreset> = persistentListOf(),
    val selectedPresetId: String? = null,
    val blocks: ImmutableList<PromptBlockSpec> = persistentListOf(),
    val savedBlocks: ImmutableList<PromptBlockSpec> = persistentListOf(),
    val exportJson: String = "",
    val importJson: String = "",
    val showExportDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val showPrefetchEnableDialog: Boolean = false,
) {
    val hasUnsavedChanges: Boolean = !blocksMatch(blocks, savedBlocks)
}

sealed interface PromptPipelineIntent {
    data class SelectPreset(val id: String) : PromptPipelineIntent
    data class ToggleBlock(val id: PromptBlockId) : PromptPipelineIntent
    data class MoveBlock(val id: PromptBlockId, val up: Boolean) : PromptPipelineIntent
    data class UpdateBlockPosition(
        val id: PromptBlockId,
        val position: PromptBlockPosition,
    ) : PromptPipelineIntent
    data class UpdateBlockDepth(val id: PromptBlockId, val depth: Int) : PromptPipelineIntent
    data class UpdateMaxTokens(val id: PromptBlockId, val maxTokens: Int) : PromptPipelineIntent
    data object Save : PromptPipelineIntent
    data object ResetToDefault : PromptPipelineIntent
    data object ShowExport : PromptPipelineIntent
    data object DismissExport : PromptPipelineIntent
    data object ShowImport : PromptPipelineIntent
    data object DismissImport : PromptPipelineIntent
    data class UpdateImportJson(val json: String) : PromptPipelineIntent
    data class ConfirmImport(val overwriteTemplates: Boolean) : PromptPipelineIntent
    data object ConfirmDiscard : PromptPipelineIntent
    data object DismissDiscard : PromptPipelineIntent
    data object ConfirmEnablePrefetch : PromptPipelineIntent
    data object DismissPrefetchEnableDialog : PromptPipelineIntent
}

sealed interface PromptPipelineEffect {
    data class ShowMessage(val message: String) : PromptPipelineEffect
}

class PromptPipelineViewModel(
    application: Application,
    private val pipelineGateway: AiPromptPipelineGateway,
    private val exporter: PromptPresetExporter,
    private val importer: PromptPresetImporter,
) : AndroidViewModel(application) {

    private val app = application
    private val _uiState = MutableStateFlow(PromptPipelineUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PromptPipelineEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var pendingBack: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            pipelineGateway.ensureDefaults()
            pipelineGateway.observeAll().collect { presets ->
                _uiState.update { state ->
                    val selected = state.selectedPresetId ?: presets.firstOrNull()?.id
                    val preset = presets.find { it.id == selected }
                    val dbBlocks = preset?.blocks.orEmpty().sortedBy { it.order }.toImmutableList()
                    val presetChanged = state.selectedPresetId != null && state.selectedPresetId != selected
                    val shouldRefreshBlocks = presetChanged || !state.hasUnsavedChanges
                    state.copy(
                        presets = presets.toImmutableList(),
                        selectedPresetId = selected,
                        blocks = if (shouldRefreshBlocks) dbBlocks else state.blocks,
                        savedBlocks = if (shouldRefreshBlocks) dbBlocks else state.savedBlocks,
                    )
                }
            }
        }
    }

    fun onIntent(intent: PromptPipelineIntent) {
        when (intent) {
            is PromptPipelineIntent.SelectPreset -> selectPreset(intent.id)
            is PromptPipelineIntent.ToggleBlock -> {
                val block = _uiState.value.blocks.find { it.id == intent.id } ?: return
                if (intent.id == PromptBlockId.WorkspacePrefetch && !block.enabled) {
                    _uiState.update { it.copy(showPrefetchEnableDialog = true) }
                } else {
                    updateBlocks { blocks ->
                        blocks.map { if (it.id == intent.id) it.copy(enabled = !it.enabled) else it }
                    }
                }
            }
            is PromptPipelineIntent.MoveBlock -> moveBlock(intent.id, intent.up)
            is PromptPipelineIntent.UpdateBlockPosition -> updateBlocks { blocks ->
                blocks.map {
                    if (it.id != intent.id) it
                    else it.copy(
                        position = intent.position,
                        depth = if (intent.position == PromptBlockPosition.Prefix) 0 else it.depth,
                    )
                }
            }
            is PromptPipelineIntent.UpdateBlockDepth -> updateBlocks { blocks ->
                val depth = intent.depth.coerceIn(0, 20)
                blocks.map {
                    if (it.id != intent.id) it
                    else it.copy(position = PromptBlockPosition.InChat, depth = depth)
                }
            }
            is PromptPipelineIntent.UpdateMaxTokens -> updateBlocks { blocks ->
                blocks.map { if (it.id == intent.id) it.copy(maxTokens = intent.maxTokens) else it }
            }
            PromptPipelineIntent.Save -> save()
            PromptPipelineIntent.ResetToDefault -> resetToDefault()
            PromptPipelineIntent.ShowExport -> export()
            PromptPipelineIntent.DismissExport -> _uiState.update { it.copy(showExportDialog = false) }
            PromptPipelineIntent.ShowImport -> _uiState.update { it.copy(showImportDialog = true, importJson = "") }
            PromptPipelineIntent.DismissImport -> _uiState.update { it.copy(showImportDialog = false) }
            is PromptPipelineIntent.UpdateImportJson -> _uiState.update { it.copy(importJson = intent.json) }
            is PromptPipelineIntent.ConfirmImport -> importPreset(intent.overwriteTemplates)
            PromptPipelineIntent.ConfirmDiscard -> {
                _uiState.update { it.copy(showDiscardDialog = false) }
                pendingBack?.invoke()
                pendingBack = null
            }
            PromptPipelineIntent.DismissDiscard -> {
                _uiState.update { it.copy(showDiscardDialog = false) }
                pendingBack = null
            }
            PromptPipelineIntent.ConfirmEnablePrefetch -> {
                updateBlocks { blocks ->
                    blocks.map { if (it.id == PromptBlockId.WorkspacePrefetch) it.copy(enabled = true) else it }
                }
                _uiState.update { it.copy(showPrefetchEnableDialog = false) }
            }
            PromptPipelineIntent.DismissPrefetchEnableDialog -> {
                _uiState.update { it.copy(showPrefetchEnableDialog = false) }
            }
        }
    }

    fun requestBack(onBack: () -> Unit) {
        if (_uiState.value.hasUnsavedChanges) {
            pendingBack = onBack
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            onBack()
        }
    }

    private fun selectPreset(id: String) {
        val preset = _uiState.value.presets.find { it.id == id } ?: return
        val blocks = preset.blocks.sortedBy { it.order }.toImmutableList()
        _uiState.update {
            it.copy(
                selectedPresetId = id,
                blocks = blocks,
                savedBlocks = blocks,
            )
        }
    }

    private fun updateBlocks(transform: (List<PromptBlockSpec>) -> List<PromptBlockSpec>) {
        _uiState.update {
            it.copy(blocks = transform(it.blocks).toImmutableList())
        }
    }

    private fun moveBlock(id: PromptBlockId, up: Boolean) {
        val blocks = _uiState.value.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in blocks.indices) return
        val current = blocks[index]
        val other = blocks[swapWith]
        blocks[index] = other.copy(order = current.order)
        blocks[swapWith] = current.copy(order = other.order)
        _uiState.update { it.copy(blocks = blocks.sortedBy { b -> b.order }.toImmutableList()) }
    }

    private fun resetToDefault() {
        val presetId = _uiState.value.selectedPresetId ?: return
        val default = PromptPipelineDefaults.allPresets().find { it.id == presetId } ?: return
        val blocks = default.blocks.sortedBy { it.order }.toImmutableList()
        _uiState.update { it.copy(blocks = blocks) }
        emitMessage(app.getString(R.string.restore_default))
    }

    private fun save() {
        val presetId = _uiState.value.selectedPresetId ?: return
        val preset = _uiState.value.presets.find { it.id == presetId } ?: return
        val blocks = _uiState.value.blocks
        viewModelScope.launch {
            pipelineGateway.upsert(
                preset.copy(
                    blocks = blocks,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(savedBlocks = blocks) }
            emitMessage(app.getString(R.string.ai_ability_saved))
        }
    }

    private fun export() {
        val presetId = _uiState.value.selectedPresetId ?: return
        viewModelScope.launch {
            val json = exporter.export(presetId)
            _uiState.update { it.copy(exportJson = json, showExportDialog = true) }
        }
    }

    private fun importPreset(overwriteTemplates: Boolean) {
        viewModelScope.launch {
            runCatching {
                importer.import(_uiState.value.importJson, overwriteTemplates)
            }.onSuccess { preset ->
                val blocks = preset.blocks.sortedBy { it.order }.toImmutableList()
                _uiState.update {
                    it.copy(
                        showImportDialog = false,
                        selectedPresetId = preset.id,
                        blocks = blocks,
                        savedBlocks = blocks,
                    )
                }
                emitMessage(app.getString(R.string.ai_pipeline_import_success, preset.name))
            }.onFailure {
                emitMessage(it.message ?: app.getString(R.string.ai_pipeline_import_failed))
            }
        }
    }

    private fun emitMessage(message: String) {
        _effects.tryEmit(PromptPipelineEffect.ShowMessage(message))
    }
}
