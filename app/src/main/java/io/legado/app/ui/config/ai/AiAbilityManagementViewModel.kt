package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.data.repository.AiToolConfigRepository
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.usecase.UserMemoryTools
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
class AiAbilityManagementViewModel(
    private val toolConfigGateway: AiToolConfigGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val memoryGateway: AiMemoryGateway,
    private val aiToolGateway: AiToolGateway,
) : ViewModel() {

    companion object {
        /** Tool names and their hardcoded defaults (used to seed configs). */
        val ALL_TOOL_NAMES = listOf(
            "list_tool_groups", "set_tool_groups",
            "search_books", "search_book_sources", "add_book_to_bookshelf", "get_book_detail", "list_book_chapters",
            "get_chapter_content", "search_book_content", "extract_world_book", "read_world_book", "patch_world_book",
            "read_history_memory", "patch_history_memory",
            "read_outline", "patch_outline", "search_workspace",
            "read_character_card", "patch_character_card", "list_character_cards",
            "read_user_card", "patch_user_card",
            "read_user_memory", "patch_user_memory",
            "suggested_replies", "galgame_hud", "ai_help_reply", "list_conversations",
            "publish_html_app", "commit_html_app", "rollback_html_app",
            "list_html_app_versions", "list_html_apps",
            "read_conversation", "post_edit", "ask_user_questions", "web_search", "read_web_page",
            "read_web_search_page_config", "patch_web_search_page_config",
            "read_tts_config", "read_cloud_tts_engine", "patch_cloud_tts_engine", "list_cloud_tts_voices",
            "read_http_tts", "patch_http_tts", "test_tts", "set_default_tts_engine",
            "export_cloud_tts_as_http_tts",
            "search_rule_help", "list_skills",
            "install_skill_from_url",
            "list_html_chat_themes",
            "diff_html_chat_themes",
            "commit_html_chat_theme", "list_html_chat_theme_versions",
            "rollback_html_chat_theme", "set_html_chat_theme",
            "read_file", "edit_file", "write_file", "delete_file",
            "eval_js", "list_book_source_versions", "diff_book_source_version",
            "restore_book_source_version", "read_book_source", "fetch_page_snippet",
            "check_book_source", "debug_book_source", "patch_book_source", "write_book_source_from_url",
        )

        fun defaultDisplayName(name: String): String = when (name) {
            "list_tool_groups" -> "List tool groups"
            "set_tool_groups" -> "Set tool groups"
            "search_books" -> "Search bookshelf"
            "search_book_sources" -> "Search book sources"
            "add_book_to_bookshelf" -> "Add to bookshelf"
            "list_conversations" -> "List conversations"
            "read_conversation" -> "Read conversation"
            "get_book_detail" -> "Get book detail"
            "list_book_chapters" -> "List chapters"
            "get_chapter_content" -> "Read chapter"
            "search_book_content" -> "Search book content"
            "extract_world_book" -> "Extract world book"
            "read_world_book" -> "Read world book"
            "patch_world_book" -> "Patch world book"
            "read_history_memory" -> "Read history memory"
            "patch_history_memory" -> "Patch history memory"
            "read_outline" -> "Read outline"
            "patch_outline" -> "Patch outline"
            "search_workspace" -> "Search workspace"
            "read_character_card" -> "Read character card"
            "patch_character_card" -> "Patch character card"
            "list_character_cards" -> "List character cards"
            "read_user_card" -> "Read user persona / 用户描述 (writing roleplay; not chat context)"
            "patch_user_card" -> "Patch user persona / 用户描述 (writing roleplay; not chat context)"
            "read_user_memory" -> "Read habit memory"
            "patch_user_memory" -> "Patch habit memory"
            "suggested_replies" -> "Suggested replies"
            "galgame_hud" -> "Galgame HUD"
            "publish_html_app" -> "Publish HTML App"
            "commit_html_app" -> "Commit HTML App"
            "rollback_html_app" -> "Rollback HTML App"
            "list_html_app_versions" -> "List HTML App Versions"
            "list_html_apps" -> "List HTML Apps"
            "ai_help_reply" -> "AI help reply"
            "post_edit" -> "Post-edit polish"
            "ask_user_questions" -> "Ask user questions"
            "web_search" -> "Web search"
            "read_web_page" -> "Read web page"
            "read_web_search_page_config" -> "Read web search page config"
            "patch_web_search_page_config" -> "Patch web search page config"
            "read_tts_config" -> "Read TTS config"
            "read_cloud_tts_engine" -> "Read cloud TTS engine"
            "patch_cloud_tts_engine" -> "Patch cloud TTS engine"
            "list_cloud_tts_voices" -> "List cloud TTS voices"
            "read_http_tts" -> "Read HttpTTS"
            "patch_http_tts" -> "Patch HttpTTS"
            "test_tts" -> "Test TTS"
            "set_default_tts_engine" -> "Set default TTS engine"
            "export_cloud_tts_as_http_tts" -> "Export cloud TTS as HttpTTS"
            "search_rule_help" -> "Search rule help"
            "list_skills" -> "List skills"
            "install_skill_from_url" -> "Install skill from URL"
            "list_html_chat_themes" -> "List HTML chat themes"
            "diff_html_chat_themes" -> "Diff HTML chat themes"
            "commit_html_chat_theme" -> "Commit HTML chat theme"
            "list_html_chat_theme_versions" -> "List HTML chat theme versions"
            "rollback_html_chat_theme" -> "Rollback HTML chat theme"
            "set_html_chat_theme" -> "Set HTML chat theme"
            "read_file" -> "Read file"
            "edit_file" -> "Edit file"
            "write_file" -> "Write file"
            "delete_file" -> "Delete file"
            "eval_js" -> "Eval JS (limited)"
            "list_book_source_versions" -> "List book source versions"
            "diff_book_source_version" -> "Diff book source version"
            "restore_book_source_version" -> "Restore book source version"
            "read_book_source" -> "Read book source"
            "fetch_page_snippet" -> "Fetch page snippet"
            "check_book_source" -> "Check book source"
            "debug_book_source" -> "Debug book source"
            "patch_book_source" -> "Patch book source"
            "write_book_source_from_url" -> "Write book source from URL"
            else -> name
        }

        // All prompts are now managed in AiPromptTemplate / PromptTemplateScreen.
        // This method returns "" so that generation code falls through to the template.
        fun defaultPrompt(name: String): String = ""

        fun templateKeyForTool(toolName: String): String? = when (toolName) {
            "galgame_hud" -> AiPromptTemplate.GALGAME_HUD_GENERATE_PROMPT
            "suggested_replies" -> AiPromptTemplate.SUGGESTIONS_PROMPT
            "patch_outline" -> AiPromptTemplate.GENERATE_OUTLINE_PROMPT
            "patch_character_card" -> AiPromptTemplate.GENERATE_CHARACTER_CARD_PROMPT
            "patch_user_card" -> AiPromptTemplate.GENERATE_USER_CARD_PROMPT
            "patch_history_memory" -> AiPromptTemplate.GENERATE_MEMORY_TABLE_PROMPT
            "get_chapter_content" -> AiPromptTemplate.SUBMODEL_DEFAULT_PROMPT
            "read_web_page" -> AiPromptTemplate.SUBMODEL_READ_WEB_PAGE_PROMPT
            "fetch_page_snippet" -> AiPromptTemplate.SUBMODEL_FETCH_PAGE_SNIPPET_PROMPT
            "debug_book_source" -> AiPromptTemplate.SUBMODEL_DEBUG_BOOK_SOURCE_PROMPT
            "extract_world_book" -> AiPromptTemplate.WORLD_BOOK_EXTRACTION_PROMPT
            "patch_world_book" -> AiPromptTemplate.GENERATE_WORLD_BOOK_PROMPT
            "post_edit" -> AiPromptTemplate.POST_EDIT_PROMPT
            "ai_help_reply" -> AiPromptTemplate.HELP_REPLY_SYSTEM_PROMPT
            else -> null
        }
    }

    private suspend fun loadTemplatePreview(toolName: String): String? {
        val key = templateKeyForTool(toolName) ?: return null
        val template = promptTemplateGateway.getPrompt(key)
        return template.ifBlank { AiPromptTemplate.DEFAULTS[key] }
    }

    private val _uiState = MutableStateFlow(AiAbilityManagementUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiAbilityManagementEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            (toolConfigGateway as? AiToolConfigRepository)?.ensureMigrated()
            combine(
                toolConfigGateway.observeAll(),
                aiProfileGateway.observeModels(),
                memoryGateway.observeGlobal(),
            ) { configs, models, memories ->
                AiAbilityCombine(configs, models, memories)
            }.collect { data ->
                val configMap = data.configs.associateBy { it.toolName }
                val merged = ALL_TOOL_NAMES.map { name ->
                    configMap[name] ?: AiToolConfig(
                        toolName = name,
                        displayName = defaultDisplayName(name),
                        description = "",
                        subModelPrompt = defaultPrompt(name),
                        maxChars = if (name == "read_web_page") 6000 else 12000,
                    )
                }
                _uiState.update {
                    it.copy(
                        tools = merged.toImmutableList(),
                        availableModels = data.models.filter { m -> m.enabled }.toImmutableList(),
                        habitMemories = data.memories.toImmutableList(),
                    )
                }
            }
        }
    }

    private data class AiAbilityCombine(
        val configs: List<AiToolConfig>,
        val models: List<io.legado.app.data.entities.AiModelProfile>,
        val memories: List<AiMemory>,
    )

    fun onIntent(intent: AiAbilityManagementIntent) {
        when (intent) {
            is AiAbilityManagementIntent.Edit -> {
                _uiState.update { it.copy(editingTool = intent.tool, templatePreview = null) }
                viewModelScope.launch {
                    val preview = loadTemplatePreview(intent.tool.toolName)
                    _uiState.update { it.copy(templatePreview = preview) }
                }
            }
            is AiAbilityManagementIntent.UpdateField -> {
                _uiState.update { state ->
                    state.editingTool?.let { editing ->
                        val updated = when (intent.field) {
                            "displayName" -> editing.copy(displayName = intent.value)
                            "description" -> editing.copy(description = intent.value)
                            "subModelPrompt" -> {
                                if (editing.toolName == "get_chapter_content") {
                                    editing.copy(subModelPrompt = intent.value, description = intent.value)
                                } else {
                                    editing.copy(subModelPrompt = intent.value)
                                }
                            }
                            "maxChars" -> editing.copy(maxChars = intent.value.toIntOrNull() ?: editing.maxChars)
                            else -> editing
                        }
                        state.copy(editingTool = updated)
                    } ?: state
                }
            }
            is AiAbilityManagementIntent.UpdateBoolean -> {
                _uiState.update { state ->
                    state.editingTool?.let { editing ->
                        val updated = when (intent.field) {
                            "enabled" -> editing.copy(enabled = intent.value)
                            "useSubModel" -> {
                                val turnedOff = editing.useSubModel && !intent.value
                                if (turnedOff && editing.toolName == "get_chapter_content" && editing.description.isBlank()) {
                                    editing.copy(useSubModel = false, description = editing.subModelPrompt)
                                } else {
                                    editing.copy(useSubModel = intent.value)
                                }
                            }
                            else -> editing
                        }
                        state.copy(editingTool = updated)
                    } ?: state
                }
            }
            is AiAbilityManagementIntent.UpdateSubModelProfile -> {
                _uiState.update { state ->
                    state.editingTool?.let { editing ->
                        state.copy(editingTool = editing.copy(subModelProfileId = intent.profileId))
                    } ?: state
                }
            }
            AiAbilityManagementIntent.Save -> save()
            is AiAbilityManagementIntent.ResetDefault -> resetDefault(intent.toolName)
            AiAbilityManagementIntent.CancelEdit -> {
                _uiState.update { it.copy(editingTool = null, templatePreview = null) }
            }
            AiAbilityManagementIntent.OpenHabitMemoryList -> {
                _uiState.update { it.copy(showHabitMemoryList = true) }
            }
            AiAbilityManagementIntent.CloseHabitMemoryList -> {
                _uiState.update { it.copy(showHabitMemoryList = false) }
            }
            is AiAbilityManagementIntent.DeleteHabitMemory -> {
                viewModelScope.launch {
                    memoryGateway.delete("", intent.key)
                }
            }
            is AiAbilityManagementIntent.SaveHabitMemory -> saveHabitMemory(
                originalKey = intent.originalKey,
                key = intent.key.trim(),
                value = intent.value.trim(),
            )
        }
    }

    private fun invalidateToolCache() {
        (aiToolGateway as? AiToolRepository)?.invalidateConfigCache()
    }

    private fun saveHabitMemory(originalKey: String?, key: String, value: String) {
        if (key.isBlank()) {
            _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_habit_memory_key_required))
            return
        }
        if (!UserMemoryTools.isValidKey(key)) {
            _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_habit_memory_invalid_key))
            return
        }
        if (value.isBlank()) {
            _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_habit_memory_value_required))
            return
        }
        val trimmedValue = value.take(UserMemoryTools.MAX_VALUE_CHARS)
        viewModelScope.launch {
            runCatching {
                val existing = memoryGateway.getGlobal()
                val isNew = originalKey == null
                if (isNew && existing.none { it.key == key } && existing.size >= UserMemoryTools.MAX_GLOBAL_ENTRIES) {
                    throw IllegalStateException("limit")
                }
                if (originalKey != null && originalKey != key) {
                    memoryGateway.delete("", originalKey)
                }
                memoryGateway.upsert(
                    AiMemory(
                        conversationId = "",
                        key = key,
                        value = trimmedValue,
                    ),
                )
            }.onSuccess {
                _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_habit_memory_saved))
            }.onFailure {
                val resId = if (it.message == "limit") {
                    R.string.ai_habit_memory_limit_reached
                } else {
                    R.string.ai_ability_save_failed
                }
                _effects.tryEmit(
                    AiAbilityManagementEffect.ShowMessage(
                        resId,
                        if (it.message == "limit") UserMemoryTools.MAX_GLOBAL_ENTRIES.toString() else it.message,
                    ),
                )
            }
        }
    }

    private fun save() {
        val editing = _uiState.value.editingTool ?: return
        viewModelScope.launch {
            runCatching {
                toolConfigGateway.save(editing)
            }.onSuccess {
                _uiState.update { it.copy(editingTool = null, templatePreview = null) }
                invalidateToolCache()
                _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_ability_saved))
            }.onFailure {
                _effects.tryEmit(
                    AiAbilityManagementEffect.ShowMessage(
                        R.string.ai_ability_save_failed,
                        it.message ?: "",
                    )
                )
            }
        }
    }

    private fun resetDefault(toolName: String) {
        viewModelScope.launch {
            runCatching {
                toolConfigGateway.delete(toolName)
            }.onSuccess {
                _uiState.update { it.copy(editingTool = null, templatePreview = null) }
                invalidateToolCache()
                _effects.tryEmit(AiAbilityManagementEffect.ShowMessage(R.string.ai_ability_restored_defaults))
            }.onFailure {
                _effects.tryEmit(
                    AiAbilityManagementEffect.ShowMessage(
                        R.string.ai_ability_reset_failed,
                        it.message ?: "",
                    )
                )
            }
        }
    }
}
