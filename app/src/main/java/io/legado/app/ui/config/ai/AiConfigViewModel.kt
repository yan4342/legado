package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AiProfileGateway
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AiConfigViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var autoSelectDone = false

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observePresets()
            ) { providers, models, presets ->
                Triple(providers, models, presets)
            }.collect { (providers, models, presets) ->
                val defaultPreset = presets.firstOrNull { it.isDefault && it.taskType == "chat" }
                    ?: presets.firstOrNull { it.isDefault }
                var currentModelId = defaultPreset?.modelProfileId
                var currentModel = models.firstOrNull { it.id == currentModelId }

                // Auto-select first model on first load if no default exists
                if (!autoSelectDone && currentModel == null && models.isNotEmpty()) {
                    autoSelectDone = true
                    val firstModel = models.first()
                    runCatching { aiProfileGateway.setDefaultModel(firstModel.id) }
                    currentModelId = firstModel.id
                    currentModel = firstModel
                }

                val providerList = providers.map { provider ->
                    val providerModels = models.filter { it.providerId == provider.id }
                    AiProviderListItemUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelCount = providerModels.size,
                        enabled = provider.enabled,
                        models = providerModels.map { model ->
                            AiModelListItemUi(
                                providerId = model.providerId,
                                modelProfileId = model.id,
                                providerName = provider.name,
                                protocol = provider.protocol,
                                baseUrl = provider.baseUrl,
                                modelName = model.displayName,
                                modelId = model.modelId,
                                contextWindow = model.contextWindow,
                                maxOutputTokens = model.maxOutputTokens,
                                enabled = model.enabled,
                                isCurrent = model.id == currentModelId
                            )
                        }.toImmutableList()
                    )
                }

                _uiState.update {
                    it.copy(
                        providers = providerList.toImmutableList(),
                        models = models.map { model ->
                            val provider = providers.firstOrNull { it.id == model.providerId }
                            AiModelListItemUi(
                                providerId = model.providerId,
                                modelProfileId = model.id,
                                providerName = provider?.name.orEmpty(),
                                protocol = provider?.protocol.orEmpty(),
                                baseUrl = provider?.baseUrl.orEmpty(),
                                modelName = model.displayName,
                                modelId = model.modelId,
                                contextWindow = model.contextWindow,
                                maxOutputTokens = model.maxOutputTokens,
                                enabled = model.enabled,
                                isCurrent = model.id == currentModelId
                            )
                        }.toImmutableList(),
                        currentModelProfileId = currentModelId,
                        currentModelName = currentModel?.displayName.orEmpty(),
                        providerCount = providers.size,
                        modelCount = models.size,
                        presetCount = presets.size
                    )
                }
            }
        }
    }

    fun onIntent(intent: AiConfigIntent) {
        when (intent) {
            is AiConfigIntent.SetDefaultModel -> setDefaultModel(intent.modelProfileId)
        }
    }

    private fun setDefaultModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.setDefaultModel(modelProfileId)
            }.onSuccess {
                _effects.tryEmit(AiConfigEffect.ShowMessage("Default model set"))
            }.onFailure { error ->
                _effects.tryEmit(AiConfigEffect.ShowMessage(error.message ?: "Failed to set default model"))
            }
        }
    }
}
