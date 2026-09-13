package io.legado.app.ui.book.readaloud.cloudtts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsEngineJsonCodec
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.domain.usecase.CloudTtsHttpRuleExporter
import io.legado.app.domain.usecase.ExportCloudTtsAsHttpTtsUseCase
import io.legado.app.domain.usecase.SyncVoicesFromAllEnginesUseCase
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.util.UUID

class CloudTtsViewModel(
    private val engineGateway: CloudTtsEngineGateway,
    private val exportHttpTts: ExportCloudTtsAsHttpTtsUseCase,
    private val syncVoices: SyncVoicesFromAllEnginesUseCase,
    private val cloudSynthesizer: CloudTtsAudioSynthesizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudTtsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CloudTtsEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var engines = emptyList<CloudTtsEngine>()

    init {
        viewModelScope.launch {
            engineGateway.observeAll().collect { allEngines ->
                engines = allEngines
                publishEngines()
            }
        }
    }

    fun onIntent(intent: CloudTtsIntent) {
        when (intent) {
            CloudTtsIntent.Refresh -> publishEngines()
            CloudTtsIntent.SyncVoices -> syncAllVoices()
            CloudTtsIntent.AddEngine -> {
                val provider = CloudTtsProviderType.Mimo
                val editor = CloudTtsEngineEditorUi(
                    name = provider.profile.displayName,
                    provider = provider.storageValue,
                    model = provider.profile.defaultModel,
                    optionsJson = "{}",
                    enabled = true,
                )
                _uiState.update {
                    it.copy(editor = editor.copy(rawJson = editor.toPrettyJson()))
                }
            }
            is CloudTtsIntent.EditEngine -> editEngine(intent.id)
            is CloudTtsIntent.DeleteEngine -> deleteEngine(intent.id)
            is CloudTtsIntent.UpdateEditor -> _uiState.update { it.copy(editor = intent.editor) }
            CloudTtsIntent.ToggleEditorJsonMode -> toggleJsonMode()
            CloudTtsIntent.FormatEditorJson -> formatEditorJson()
            CloudTtsIntent.DismissEditor -> _uiState.update { it.copy(editor = null) }
            CloudTtsIntent.Save -> saveEngine()
            is CloudTtsIntent.OpenExportPicker -> openExportPicker(intent.id)
            CloudTtsIntent.DismissExportPicker -> _uiState.update { it.copy(exportPicker = null) }
            is CloudTtsIntent.ToggleExportVoice -> toggleExportVoice(intent.voiceId)
            CloudTtsIntent.ConfirmExport -> confirmExport()
        }
    }

    private fun publishEngines() {
        _uiState.update { state ->
            state.copy(
                loading = false,
                engines = engines.map { engine ->
                    CloudTtsEngineItemUi(
                        id = engine.id,
                        title = engine.name,
                        summary = buildString {
                            append(engine.provider.profile.displayName)
                            if (engine.model.isNotBlank()) append(" · ").append(engine.model)
                            if (!engine.enabled) append(" · off")
                        },
                        provider = engine.provider.storageValue,
                        exportable = CloudTtsHttpRuleExporter.isExportable(engine.provider),
                    )
                }.toImmutableList(),
            )
        }
    }

    private fun editEngine(id: String) {
        val engine = engines.firstOrNull { it.id == id } ?: return
        val editor = CloudTtsEngineEditorUi(
            editingEngineId = engine.id,
            name = engine.name,
            provider = engine.provider.storageValue,
            baseUrl = engine.baseUrl,
            apiKey = engine.apiKey,
            secretKey = engine.secretKey,
            model = engine.model.ifBlank { engine.provider.profile.defaultModel },
            region = engine.region,
            appId = engine.appId,
            optionsJson = engine.optionsJson.ifBlank { "{}" },
            enabled = engine.enabled,
            rawJson = CloudTtsEngineJsonCodec.toPrettyJson(engine),
        )
        _uiState.update { it.copy(editor = editor) }
    }

    private fun toggleJsonMode() {
        val editor = _uiState.value.editor ?: return
        if (editor.jsonMode) {
            // JSON → form
            CloudTtsEngineJsonCodec.parse(editor.rawJson)
                .onSuccess { parsed ->
                    _uiState.update {
                        it.copy(
                            editor = editor.copy(
                                jsonMode = false,
                                editingEngineId = parsed.id ?: editor.editingEngineId,
                                name = parsed.name,
                                provider = parsed.provider,
                                baseUrl = parsed.baseUrl,
                                apiKey = parsed.apiKey,
                                secretKey = parsed.secretKey,
                                region = parsed.region,
                                appId = parsed.appId,
                                model = parsed.model,
                                optionsJson = parsed.optionsJson,
                                enabled = parsed.enabled,
                                rawJson = CloudTtsEngineJsonCodec.toPrettyJson(
                                    id = parsed.id ?: editor.editingEngineId,
                                    name = parsed.name,
                                    provider = parsed.provider,
                                    baseUrl = parsed.baseUrl,
                                    apiKey = parsed.apiKey,
                                    secretKey = parsed.secretKey,
                                    region = parsed.region,
                                    appId = parsed.appId,
                                    model = parsed.model,
                                    optionsJson = parsed.optionsJson,
                                    enabled = parsed.enabled,
                                ),
                            )
                        )
                    }
                }
                .onFailure {
                    toast(it.localizedMessage ?: appCtx.getString(R.string.cloud_tts_json_invalid))
                }
        } else {
            // Form → JSON
            _uiState.update {
                it.copy(
                    editor = editor.copy(
                        jsonMode = true,
                        rawJson = editor.toPrettyJson(),
                    )
                )
            }
        }
    }

    private fun formatEditorJson() {
        val editor = _uiState.value.editor ?: return
        CloudTtsEngineJsonCodec.parse(editor.rawJson)
            .onSuccess { parsed ->
                _uiState.update {
                    it.copy(
                        editor = editor.copy(
                            rawJson = CloudTtsEngineJsonCodec.toPrettyJson(
                                id = parsed.id ?: editor.editingEngineId,
                                name = parsed.name,
                                provider = parsed.provider,
                                baseUrl = parsed.baseUrl,
                                apiKey = parsed.apiKey,
                                secretKey = parsed.secretKey,
                                region = parsed.region,
                                appId = parsed.appId,
                                model = parsed.model,
                                optionsJson = parsed.optionsJson,
                                enabled = parsed.enabled,
                            )
                        )
                    )
                }
                toast(appCtx.getString(R.string.cloud_tts_json_formatted))
            }
            .onFailure {
                toast(it.localizedMessage ?: appCtx.getString(R.string.cloud_tts_json_invalid))
            }
    }

    private fun deleteEngine(id: String) = viewModelScope.launch {
        engines.firstOrNull { it.id == id }?.let { engineGateway.delete(it) }
        toast(appCtx.getString(R.string.success))
    }

    private fun saveEngine() = viewModelScope.launch {
        val engine = buildEngine() ?: return@launch
        engineGateway.upsert(engine)
        _uiState.update { it.copy(editor = null) }
        toast(appCtx.getString(R.string.cloud_tts_engine_saved))
        runCatching { syncVoices(cloudEngineId = engine.id) }
    }

    private fun syncAllVoices() = viewModelScope.launch {
        _uiState.update { it.copy(syncing = true) }
        runCatching { syncVoices() }
            .onSuccess { result ->
                toast(
                    appCtx.getString(
                        R.string.cloud_tts_voices_synced,
                        result.inserted + result.updated,
                    )
                )
            }
            .onFailure {
                toast(it.localizedMessage ?: appCtx.getString(R.string.error))
            }
        _uiState.update { it.copy(syncing = false) }
    }

    private fun openExportPicker(id: String) = viewModelScope.launch {
        val engine = engines.firstOrNull { it.id == id } ?: return@launch
        if (!CloudTtsHttpRuleExporter.isExportable(engine.provider)) {
            toast(appCtx.getString(R.string.cloud_tts_export_not_supported))
            return@launch
        }
        _uiState.update {
            it.copy(
                exportPicker = CloudTtsExportPickerUi(
                    engineId = engine.id,
                    engineName = engine.name,
                    loadingVoices = true,
                )
            )
        }
        val voices = runCatching { cloudSynthesizer.fetchVoices(engine) }
            .onFailure { toast(it.localizedMessage ?: appCtx.getString(R.string.error)) }
            .getOrDefault(emptyList())
        _uiState.update { state ->
            val picker = state.exportPicker ?: return@update state
            state.copy(
                exportPicker = picker.copy(
                    loadingVoices = false,
                    voices = voices.map {
                        CloudTtsVoiceOptionUi(it.id, it.displayName)
                    }.toImmutableList(),
                    selectedVoiceIds = voices.firstOrNull()?.id?.let {
                        persistentListOf(it)
                    } ?: persistentListOf(""),
                )
            )
        }
    }

    private fun toggleExportVoice(voiceId: String) {
        _uiState.update { state ->
            val picker = state.exportPicker ?: return@update state
            val selected = picker.selectedVoiceIds.toMutableList()
            if (voiceId in selected) selected.remove(voiceId) else selected.add(voiceId)
            if (selected.isEmpty()) selected.add(voiceId)
            state.copy(exportPicker = picker.copy(selectedVoiceIds = selected.toImmutableList()))
        }
    }

    private fun confirmExport() = viewModelScope.launch {
        val picker = _uiState.value.exportPicker ?: return@launch
        _uiState.update { it.copy(exporting = true) }
        runCatching {
            val voiceNames = picker.voices.associate { it.id to it.displayName }
            exportHttpTts(
                engineId = picker.engineId,
                speakerIds = picker.selectedVoiceIds.filter { it.isNotBlank() }.ifEmpty { listOf("") },
                voiceNames = voiceNames,
            )
        }.onSuccess { result ->
            toast(
                appCtx.getString(R.string.cloud_tts_exported_http, result.exportedCount)
            )
            _uiState.update { it.copy(exportPicker = null) }
        }.onFailure {
            toast(it.localizedMessage ?: appCtx.getString(R.string.error))
        }
        _uiState.update { it.copy(exporting = false) }
    }

    private fun buildEngine(): CloudTtsEngine? {
        val editor = _uiState.value.editor ?: return null
        val resolved = if (editor.jsonMode) {
            CloudTtsEngineJsonCodec.parse(editor.rawJson).getOrElse {
                toast(it.localizedMessage ?: appCtx.getString(R.string.cloud_tts_json_invalid))
                return null
            }.let { parsed ->
                editor.copy(
                    editingEngineId = parsed.id ?: editor.editingEngineId,
                    name = parsed.name,
                    provider = parsed.provider,
                    baseUrl = parsed.baseUrl,
                    apiKey = parsed.apiKey,
                    secretKey = parsed.secretKey,
                    region = parsed.region,
                    appId = parsed.appId,
                    model = parsed.model,
                    optionsJson = parsed.optionsJson,
                    enabled = parsed.enabled,
                )
            }
        } else {
            editor
        }
        val provider = CloudTtsProviderType.entries.firstOrNull {
            it.storageValue == resolved.provider
        } ?: run {
            toast(appCtx.getString(R.string.cloud_tts_select_provider))
            return null
        }
        if (resolved.name.isBlank()) {
            toast(appCtx.getString(R.string.cloud_tts_engine_name_required))
            return null
        }
        if (resolved.apiKey.isBlank()) {
            toast(appCtx.getString(R.string.cloud_tts_api_key_required))
            return null
        }
        val optionsJson = resolved.optionsJson.trim().ifBlank { "{}" }
        val optionsOk = runCatching {
            val el = com.google.gson.JsonParser.parseString(optionsJson)
            el.isJsonObject
        }.getOrDefault(false)
        if (!optionsOk) {
            toast(appCtx.getString(R.string.cloud_tts_options_json_invalid))
            return null
        }
        val old = resolved.editingEngineId?.let { id -> engines.firstOrNull { it.id == id } }
        val now = System.currentTimeMillis()
        return CloudTtsEngine(
            id = old?.id ?: resolved.editingEngineId ?: UUID.randomUUID().toString(),
            name = resolved.name.trim(),
            provider = provider,
            baseUrl = resolved.baseUrl.trim(),
            apiKey = resolved.apiKey.trim(),
            secretKey = resolved.secretKey.trim(),
            region = resolved.region.trim(),
            appId = resolved.appId.trim(),
            model = resolved.model.trim().ifBlank { provider.profile.defaultModel },
            optionsJson = optionsJson,
            enabled = resolved.enabled,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun CloudTtsEngineEditorUi.toPrettyJson(): String =
        CloudTtsEngineJsonCodec.toPrettyJson(
            id = editingEngineId,
            name = name,
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            secretKey = secretKey,
            region = region,
            appId = appId,
            model = model,
            optionsJson = optionsJson,
            enabled = enabled,
        )

    private fun toast(message: String) {
        _effects.tryEmit(CloudTtsEffect.ShowToast(message))
    }
}
