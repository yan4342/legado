package io.legado.app.ui.ai.chat

import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.domain.model.AiCallSource
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.ui.config.ai.AiAbilityManagementViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun AiChatViewModel.observeGalgameConfig() {
    viewModelScope.launch {
        aiToolConfigGateway.observeAll().collect { configs ->
            val galgameConfig = configs.find { it.toolName == "galgame_hud" }
            _uiState.update { it.copy(galgameEnabled = galgameConfig?.enabled == true) }
        }
    }
}

internal fun AiChatViewModel.toggleGalgame() {
    viewModelScope.launch {
        val config = aiToolConfigGateway.getByToolName("galgame_hud")
            ?: AiToolConfig(
                toolName = "galgame_hud",
                enabled = true,
                useSubModel = true,
                subModelPrompt = AiAbilityManagementViewModel.defaultPrompt("galgame_hud"),
            )
        aiToolConfigGateway.save(config.copy(enabled = !config.enabled))
    }
}

internal fun AiChatViewModel.generateGalgameHud() {
    val state = _uiState.value
    if (state.conversationType != "writing") return
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        val config = aiToolConfigGateway.getByToolName("galgame_hud") ?: return@launch
        if (!config.enabled || !config.useSubModel) return@launch

        _uiState.update { it.copy(galgameHudLoading = true) }

        try {
            val recentMsgs = allLoadedMessages.takeLast(6)
            val contextText = recentMsgs.joinToString("\n\n") { "[${it.role}] ${it.content}" }
            val outline = aiOutlineGateway.getByConversation(convId)
            val outlineSummary = buildString {
                if (outline != null && outline.content.isNotBlank()) {
                    val parsed = io.legado.app.domain.usecase.structured.OutlineParser.parse(outline.content)
                    if (parsed.premise.isNotBlank()) append("Premise: ${parsed.premise}\n")
                    if (parsed.currentProgress.isNotBlank()) append("Current: ${parsed.currentProgress}\n")
                    if (parsed.nextGoal.isNotBlank()) append("Next: ${parsed.nextGoal}\n")
                    append("In progress: ${parsed.inProgress}\n")
                    val tail = io.legado.app.domain.usecase.structured.OutlineParser.bodyTail(outline.content)
                    if (tail.isNotBlank()) append("Recent: $tail")
                } else {
                    append("(No outline set)")
                }
            }

            val hasExistingHud = state.galgameHudHtml.isNotBlank()
            val promptKey = if (hasExistingHud) {
                AiPromptTemplate.GALGAME_HUD_UPDATE_PROMPT
            } else {
                AiPromptTemplate.GALGAME_HUD_GENERATE_PROMPT
            }

            val prompt = promptTemplateGateway.getPrompt(promptKey)

            val userMessage = buildString {
                if (hasExistingHud) {
                    val divCount = state.galgameHudHtml.split("<div").size - 1
                    if (divCount < 2) {
                        append(
                            "The previous HUD is missing the details block. Add a second <div> below the summary bar with character sheets, quests, inventory, relationships, and timers.\n\n",
                        )
                    }
                    append("PREVIOUS HUD:\n${state.galgameHudHtml}\n\n")
                    append("Remove character/quest/timer entries that no longer apply; keep the same HTML layout.\n\n")
                }
                append("OUTLINE:\n$outlineSummary\n\n")
                append("CONVERSATION:\n$contextText")
            }

            val request = buildSuggestionsRequest(config, prompt, userMessage, AiCallSource.GALGAME, convId) ?: run {
                _uiState.update { it.copy(galgameHudLoading = false) }
                return@launch
            }

            val result = aiTextGateway.generate(request)
            if (currentConversationId.value != convId) return@launch

            result.onSuccess { response ->
                val html = cleanGalgameHudHtml(response.text)
                _uiState.update { it.copy(galgameHudHtml = html, galgameHudLoading = false) }
                aiChatGateway.updateConversationHud(convId, html)
            }.onFailure {
                _uiState.update { it.copy(galgameHudLoading = false) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.update { it.copy(galgameHudLoading = false) }
        }
    }
}

internal fun AiChatViewModel.importHudHtml(html: String) {
    val convId = currentConversationId.value ?: return
    _uiState.update { it.copy(galgameHudHtml = html) }
    viewModelScope.launch {
        aiChatGateway.updateConversationHud(convId, html)
    }
}

internal fun AiChatViewModel.recreateGalgameHud() {
    val convId = currentConversationId.value ?: return
    _uiState.update { it.copy(galgameHudHtml = "") }
    viewModelScope.launch {
        aiChatGateway.updateConversationHud(convId, null)
    }
    generateGalgameHud()
}
