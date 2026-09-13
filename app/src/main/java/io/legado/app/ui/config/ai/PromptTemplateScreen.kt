package io.legado.app.ui.config.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.domain.model.PostEditLinkageReport
import io.legado.app.domain.model.PostEditRules
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.CategorySection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- Contract ----

data class PromptTemplateUiState(
    val templates: ImmutableList<PromptTemplateItem> = persistentListOf(),
    val editingKey: String? = null,
    val editingContent: String = "",
)

data class PromptTemplateItem(
    val key: String,
    val displayName: String,
    val content: String,
    val isCustomized: Boolean,
)

sealed interface PromptTemplateIntent {
    data class Edit(val key: String) : PromptTemplateIntent
    data class UpdateContent(val content: String) : PromptTemplateIntent
    data object Save : PromptTemplateIntent
    data class ResetDefault(val key: String) : PromptTemplateIntent
    data object CancelEdit : PromptTemplateIntent
}

sealed interface PromptTemplateEffect {
    data class ShowMessage(val message: String) : PromptTemplateEffect
}

// ---- ViewModel ----

class PromptTemplateViewModel(
    private val promptTemplateGateway: AiPromptTemplateGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptTemplateUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PromptTemplateEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        observeTemplates()
    }

    fun onIntent(intent: PromptTemplateIntent) {
        when (intent) {
            is PromptTemplateIntent.Edit -> edit(intent.key)
            is PromptTemplateIntent.UpdateContent -> updateContent(intent.content)
            PromptTemplateIntent.Save -> save()
            is PromptTemplateIntent.ResetDefault -> resetDefault(intent.key)
            PromptTemplateIntent.CancelEdit -> cancelEdit()
        }
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            promptTemplateGateway.observeAll().collect { savedTemplates ->
                val savedMap = savedTemplates.associateBy { it.promptKey }
                val items = AiPromptTemplate.DEFAULTS.keys.sorted().map { key ->
                    val saved = savedMap[key]
                    PromptTemplateItem(
                        key = key,
                        displayName = displayName(key),
                        content = saved?.content ?: AiPromptTemplate.DEFAULTS[key] ?: "",
                        isCustomized = saved != null,
                    )
                }
                _uiState.update { it.copy(templates = items.toImmutableList()) }
            }
        }
    }

    private fun edit(key: String) {
        val template = _uiState.value.templates.find { it.key == key } ?: return
        _uiState.update { it.copy(editingKey = key, editingContent = template.content) }
    }

    private fun updateContent(content: String) {
        _uiState.update { it.copy(editingContent = content) }
    }

    private fun save() {
        val state = _uiState.value
        val key = state.editingKey ?: return
        viewModelScope.launch {
            promptTemplateGateway.upsert(
                AiPromptTemplate(promptKey = key, content = state.editingContent)
            )
            _uiState.update { it.copy(editingKey = null, editingContent = "") }
        }
    }

    private fun resetDefault(key: String) {
        viewModelScope.launch {
            promptTemplateGateway.delete(key)
            _effects.emit(PromptTemplateEffect.ShowMessage("Restored to default"))
        }
    }

    private fun cancelEdit() {
        _uiState.update { it.copy(editingKey = null, editingContent = "") }
    }

    companion object {
        fun displayName(key: String): String = when (key) {
            AiPromptTemplate.CHAT_SYSTEM_PROMPT -> "Chat System Prompt"
            AiPromptTemplate.CHAT_MULTI_BUBBLE_PROTOCOL -> "Chat: Multi-Bubble Protocol"
            AiPromptTemplate.ROLEPLAY_MULTI_BUBBLE_PROTOCOL -> "Writing: Roleplay Multi-Bubble Protocol"
            AiPromptTemplate.TITLE_GENERATION_PROMPT -> "Title Generation"
            AiPromptTemplate.COMPRESS_HISTORY_PROMPT -> "Compress History"
            AiPromptTemplate.WRITING_SUBMODE_ROLEPLAY -> "Writing: Roleplay Mode"
            AiPromptTemplate.WRITING_SUBMODE_AUTHOR -> "Writing: Author Mode"
            AiPromptTemplate.WRITING_USER_INPUT_FORMAT -> "Writing: User Input Format"
            AiPromptTemplate.WRITING_USER_INPUT_FORMAT_ROLEPLAY -> "Writing: Roleplay Input/Output Format"
            AiPromptTemplate.WRITING_ROLEPLAY_IDENTITY -> "Writing: Roleplay Identity Slots"
            AiPromptTemplate.WRITING_WORKSPACE_INDEX_HINT -> "Writing: Workspace Index Hint"
            AiPromptTemplate.STRUCTURED_MAINTAIN_MEMORY_PROMPT -> "Maintain: Memory (App)"
            AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_PROMPT -> "Maintain: Outline (legacy)"
            AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_AUTHOR -> "Maintain: Outline Author"
            AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_ROLEPLAY -> "Maintain: Outline Roleplay"
            AiPromptTemplate.HELP_REPLY_SYSTEM_PROMPT -> "Help Reply: System Prompt"
            AiPromptTemplate.HELP_REPLY_WITH_DRAFT -> "Help Reply: With Draft"
            AiPromptTemplate.HELP_REPLY_EMPTY -> "Help Reply: Empty Draft"
            AiPromptTemplate.STRUCTURED_DATA_CHECKLIST -> "Structured Data Checklist (deprecated)"
            AiPromptTemplate.MEMORY_TABLE_EMPTY_HINT -> "Memory Table Empty Hint (deprecated)"
            AiPromptTemplate.REGENERATE_MEMORY_TABLE_PROMPT -> "Regenerate Memory Table"
            AiPromptTemplate.SUBMODEL_DEFAULT_PROMPT -> "Sub-Model: Chapter Content"
            AiPromptTemplate.SUBMODEL_READ_WEB_PAGE_PROMPT -> "Sub-Model: Read Web Page"
            AiPromptTemplate.SUBMODEL_FETCH_PAGE_SNIPPET_PROMPT -> "Sub-Model: Fetch Page Snippet"
            AiPromptTemplate.SUBMODEL_DEBUG_BOOK_SOURCE_PROMPT -> "Sub-Model: Debug Book Source"
            AiPromptTemplate.TABLE_MUTATION_DEFAULT_PROMPT -> "Table Mutation Default Prompt"
            AiPromptTemplate.WORLD_BOOK_EXTRACTION_PROMPT -> "World Book Extraction"
            AiPromptTemplate.GENERATE_WORLD_BOOK_PROMPT -> "Generate World Book"
            AiPromptTemplate.GENERATE_CHARACTER_CARD_PROMPT -> "Generate Character Card"
            AiPromptTemplate.GENERATE_USER_CARD_PROMPT -> "Generate User Persona"
            AiPromptTemplate.GENERATE_MEMORY_TABLE_PROMPT -> "Generate Memory Tables"
            AiPromptTemplate.GENERATE_ONE_KIND_TABLE_PROMPT -> "Generate One-Kind Table"
            AiPromptTemplate.GENERATE_OUTLINE_PROMPT -> "Generate Outline (legacy)"
            AiPromptTemplate.GENERATE_OUTLINE_AUTHOR -> "Generate Outline Author"
            AiPromptTemplate.GENERATE_OUTLINE_ROLEPLAY -> "Generate Outline Roleplay"
            AiPromptTemplate.OUTLINE_PLOT_DIGEST_PROMPT -> "Outline Plot Digest"
            AiPromptTemplate.GALGAME_HUD_GENERATE_PROMPT -> "Galgame: HUD (New)"
            AiPromptTemplate.GALGAME_HUD_UPDATE_PROMPT -> "Galgame: HUD (Update)"
            AiPromptTemplate.GALGAME_SUGGESTIONS_PROMPT -> "Galgame: Suggestions"
            AiPromptTemplate.SUGGESTIONS_PROMPT -> "Suggestions"
            AiPromptTemplate.MULTI_CHARACTER_INSTRUCTION -> "Multi-Character Instruction"
            AiPromptTemplate.POST_EDIT_PROMPT -> "Post-Edit: Polish Instructions"
            AiPromptTemplate.POST_EDIT_TRIGGER_REGEX -> "Post-Edit: Trigger Regex"
            else -> key
        }
    }
}

// ---- Screen ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateScreen(
    viewModel: PromptTemplateViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToPipeline: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt Templates", color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = onSurfaceColor,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToPipeline) {
                        Text(stringResource(R.string.ai_prompt_pipeline), color = onSurfaceColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                ),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val grouped = remember(state.templates) {
                state.templates.groupBy { templateCategory(it.key) }
            }
            LazyColumn(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((category, items) in grouped) {
                    item {
                        if (category == "Post-Edit") {
                            PostEditTemplateSection(
                                items = items,
                                onEdit = { viewModel.onIntent(PromptTemplateIntent.Edit(it)) },
                            )
                        } else {
                            CategorySection(category) {
                                var index = 0
                                for (template in items) {
                                    if (index > 0) HorizontalDivider()
                                    PromptTemplateRow(
                                        item = template,
                                        onClick = { viewModel.onIntent(PromptTemplateIntent.Edit(template.key)) },
                                    )
                                    index++
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        if (state.editingKey != null) {
            val editingKey = state.editingKey!!
            val defaultContent = AiPromptTemplate.DEFAULTS[editingKey] ?: ""
            val isEdited = state.editingContent != defaultContent
            val isPostEditTemplate = editingKey == AiPromptTemplate.POST_EDIT_PROMPT ||
                editingKey == AiPromptTemplate.POST_EDIT_TRIGGER_REGEX
            val postEditLinkage = if (isPostEditTemplate) {
                val promptContent = if (editingKey == AiPromptTemplate.POST_EDIT_PROMPT) {
                    state.editingContent
                } else {
                    state.templates.find { it.key == AiPromptTemplate.POST_EDIT_PROMPT }?.content
                        ?: PostEditRules.defaultPrompt()
                }
                val triggerContent = if (editingKey == AiPromptTemplate.POST_EDIT_TRIGGER_REGEX) {
                    state.editingContent
                } else {
                    state.templates.find { it.key == AiPromptTemplate.POST_EDIT_TRIGGER_REGEX }?.content
                        ?: PostEditRules.defaultTriggerRegex()
                }
                remember(editingKey, state.editingContent, state.templates) {
                    PostEditRules.linkageReport(promptContent, triggerContent)
                }
            } else {
                null
            }
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(PromptTemplateIntent.CancelEdit) },
                title = {
                    Text(
                        PromptTemplateViewModel.displayName(editingKey),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column {
                        Text(
                            "Key: $editingKey",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isPostEditTemplate) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Linked: ${
                                    if (editingKey == AiPromptTemplate.POST_EDIT_PROMPT) {
                                        PromptTemplateViewModel.displayName(AiPromptTemplate.POST_EDIT_TRIGGER_REGEX)
                                    } else {
                                        PromptTemplateViewModel.displayName(AiPromptTemplate.POST_EDIT_PROMPT)
                                    }
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (editingKey == AiPromptTemplate.POST_EDIT_PROMPT) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    PostEditRules.PROMPT_RULE_ID_HINT,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            postEditLinkage?.let { linkage ->
                                Spacer(Modifier.height(8.dp))
                                PostEditLinkageTable(linkage, compact = true)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.editingContent,
                            onValueChange = { viewModel.onIntent(PromptTemplateIntent.UpdateContent(it)) },
                            modifier = Modifier.fillMaxWidth().height(if (isPostEditTemplate) 240.dp else 300.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(PromptTemplateIntent.Save) },
                        enabled = state.editingContent.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = { viewModel.onIntent(PromptTemplateIntent.ResetDefault(editingKey)) },
                            enabled = isEdited,
                        ) {
                            Text(
                                "Restore Default",
                                color = if (isEdited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.onIntent(PromptTemplateIntent.CancelEdit) }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PromptTemplateRow(
    item: PromptTemplateItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                item.content.take(100).replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(8.dp))
        if (item.isCustomized) {
            Text(
                "Edited",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "Default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun templateCategory(key: String): String = when {
    key == AiPromptTemplate.POST_EDIT_PROMPT || key == AiPromptTemplate.POST_EDIT_TRIGGER_REGEX -> "Post-Edit"
    key.startsWith("chat_") || key.startsWith("title_") || key.startsWith("compress_") -> "Chat"
    key.startsWith("writing_") || key.startsWith("help_reply_") -> "Writing"
    key in setOf(
        AiPromptTemplate.STRUCTURED_DATA_CHECKLIST,
        AiPromptTemplate.MEMORY_TABLE_EMPTY_HINT,
        AiPromptTemplate.REGENERATE_MEMORY_TABLE_PROMPT,
        AiPromptTemplate.GENERATE_MEMORY_TABLE_PROMPT,
        AiPromptTemplate.GENERATE_ONE_KIND_TABLE_PROMPT,
        AiPromptTemplate.TABLE_MUTATION_DEFAULT_PROMPT,
        AiPromptTemplate.STRUCTURED_MAINTAIN_MEMORY_PROMPT,
        AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_PROMPT,
        AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_AUTHOR,
        AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_ROLEPLAY,
    ) -> "Memory"
    key in setOf(
        AiPromptTemplate.GENERATE_CHARACTER_CARD_PROMPT,
        AiPromptTemplate.GENERATE_USER_CARD_PROMPT,
        AiPromptTemplate.GENERATE_OUTLINE_PROMPT,
        AiPromptTemplate.GENERATE_OUTLINE_AUTHOR,
        AiPromptTemplate.GENERATE_OUTLINE_ROLEPLAY,
        AiPromptTemplate.OUTLINE_PLOT_DIGEST_PROMPT,
        AiPromptTemplate.GENERATE_WORLD_BOOK_PROMPT,
        AiPromptTemplate.WORLD_BOOK_EXTRACTION_PROMPT,
    ) -> "Story"
    key.startsWith("galgame_") || key == AiPromptTemplate.SUGGESTIONS_PROMPT -> "Galgame & Suggestions"
    key.startsWith("plan_") -> "Plan"
    else -> "Sub-Model"
}

@Composable
private fun PostEditTemplateSection(
    items: List<PromptTemplateItem>,
    onEdit: (String) -> Unit,
) {
    val promptItem = items.find { it.key == AiPromptTemplate.POST_EDIT_PROMPT }
    val triggerItem = items.find { it.key == AiPromptTemplate.POST_EDIT_TRIGGER_REGEX }
    val linkage = remember(promptItem?.content, triggerItem?.content) {
        PostEditRules.linkageReport(
            promptItem?.content ?: PostEditRules.defaultPrompt(),
            triggerItem?.content ?: PostEditRules.defaultTriggerRegex(),
        )
    }
    CategorySection("Post-Edit") {
        Text(
            "Trigger regex and polish instructions are linked by @id. " +
                "Only matched sentences are sent to the sub-model.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            PostEditRules.PROMPT_RULE_ID_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PostEditLinkageTable(linkage)
        Spacer(Modifier.height(8.dp))
        promptItem?.let { item ->
            PromptTemplateRow(item = item, onClick = { onEdit(item.key) })
        }
        HorizontalDivider()
        triggerItem?.let { item ->
            PromptTemplateRow(item = item, onClick = { onEdit(item.key) })
        }
    }
}

@Composable
private fun PostEditLinkageTable(
    report: PostEditLinkageReport,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("@id", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall)
            Text("Regex", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            if (!compact) {
                Text("Label", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }
            Text("Link", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        for (row in report.rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@${row.id}",
                    modifier = Modifier.width(88.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    row.regex?.take(if (compact) 24 else 40)?.replace("\n", " ").orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        row.label.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (row.isLinked) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (row.isLinked) "Linked" else "Not linked",
                    modifier = Modifier.width(36.dp),
                    tint = if (row.isLinked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        if (report.warnings.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            report.warnings.forEach { warning ->
                Text(
                    warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
