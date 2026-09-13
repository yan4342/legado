package io.legado.app.ui.config.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.ui.common.compose.NumberPickerDialog
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAbilityManagementScreen(
    viewModel: AiAbilityManagementViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var numPicker by remember { mutableStateOf<NumPickerInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiAbilityManagementEffect.ShowMessage -> {
                    val message = effect.formatArg?.let { arg ->
                        context.getString(effect.resId, arg)
                    } ?: context.getString(effect.resId)
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_ability_management_title), color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                ),
            )
        },
    ) { padding ->
        val groupedTools = remember(state.tools) {
            state.tools.groupBy { toolCategoryRes(it.toolName) }
        }
        var maxToolRounds by remember { mutableStateOf(AppConfig.aiMaxToolRounds) }
        var maxToolOutputChars by remember { mutableStateOf(AppConfig.aiMaxToolOutputChars) }
        var allowBookSourceFetch by remember { mutableStateOf(AppConfig.aiAllowBookSourceFetch) }
        val maxToolRoundsLabel = stringResource(R.string.ai_ability_max_tool_rounds)
        val maxToolOutputCharsPickerLabel = stringResource(R.string.ai_ability_max_tool_output_chars_picker)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategorySection(stringResource(R.string.ai_ability_agent_limits)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_ability_max_tool_rounds),
                        description = maxToolRounds.toString(),
                        onClick = {
                            numPicker = NumPickerInfo(
                                title = maxToolRoundsLabel,
                                value = maxToolRounds,
                                min = 6,
                                max = 24,
                            ) {
                                maxToolRounds = it
                                AppConfig.aiMaxToolRounds = it
                            }
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_ability_max_tool_output_chars),
                        description = maxToolOutputChars.toString(),
                        onClick = {
                            numPicker = NumPickerInfo(
                                title = maxToolOutputCharsPickerLabel,
                                value = maxToolOutputChars / 1_000,
                                min = 4,
                                max = 32,
                            ) {
                                val chars = it * 1_000
                                maxToolOutputChars = chars
                                AppConfig.aiMaxToolOutputChars = chars
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                stringResource(R.string.ai_ability_allow_book_source_fetch),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.ai_ability_allow_book_source_fetch_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = allowBookSourceFetch,
                            onCheckedChange = {
                                allowBookSourceFetch = it
                                AppConfig.aiAllowBookSourceFetch = it
                            },
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_habit_memory_title),
                        description = stringResource(
                            R.string.ai_habit_memory_summary,
                        ) + if (state.habitMemories.isNotEmpty()) {
                            " (${state.habitMemories.size})"
                        } else {
                            ""
                        },
                        onClick = { viewModel.onIntent(AiAbilityManagementIntent.OpenHabitMemoryList) },
                    )
                }
            }
            for ((categoryRes, tools) in groupedTools) {
                item {
                    CategorySection(stringResource(categoryRes)) {
                        for (tool in tools) {
                            ToolConfigCard(
                                tool = tool,
                                onClick = { viewModel.onIntent(AiAbilityManagementIntent.Edit(tool)) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    numPicker?.let { info ->
        NumberPickerDialog(
            title = info.title,
            value = info.value,
            minValue = info.min,
            maxValue = info.max,
            onDismiss = { numPicker = null },
            onConfirm = { v -> info.onConfirm(v); numPicker = null },
            defaultButton = info.defaultButton,
        )
    }

    // Edit dialog
    state.editingTool?.let { tool ->
        ToolEditDialog(
            tool = tool,
            availableModels = state.availableModels,
            templatePreview = state.templatePreview,
            onUpdateField = { field, value -> viewModel.onIntent(AiAbilityManagementIntent.UpdateField(tool.toolName, field, value)) },
            onUpdateBoolean = { field, value -> viewModel.onIntent(AiAbilityManagementIntent.UpdateBoolean(tool.toolName, field, value)) },
            onUpdateSubModel = { id -> viewModel.onIntent(AiAbilityManagementIntent.UpdateSubModelProfile(tool.toolName, id)) },
            onSave = { viewModel.onIntent(AiAbilityManagementIntent.Save) },
            onResetDefault = { viewModel.onIntent(AiAbilityManagementIntent.ResetDefault(tool.toolName)) },
            onDismiss = { viewModel.onIntent(AiAbilityManagementIntent.CancelEdit) },
        )
    }

    if (state.showHabitMemoryList) {
        HabitMemoryListDialog(
            memories = state.habitMemories,
            onSave = { originalKey, key, value ->
                viewModel.onIntent(AiAbilityManagementIntent.SaveHabitMemory(originalKey, key, value))
            },
            onDelete = { key -> viewModel.onIntent(AiAbilityManagementIntent.DeleteHabitMemory(key)) },
            onDismiss = { viewModel.onIntent(AiAbilityManagementIntent.CloseHabitMemoryList) },
        )
    }
}

@Composable
private fun HabitMemoryListDialog(
    memories: List<AiMemory>,
    onSave: (originalKey: String?, key: String, value: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<HabitMemoryEditState?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_habit_memory_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (memories.isEmpty()) {
                    Text(stringResource(R.string.ai_habit_memory_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(memories.size) { index ->
                            val memory = memories[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editing = HabitMemoryEditState(
                                            originalKey = memory.key,
                                            key = memory.key,
                                            value = memory.value,
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        memory.key,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        memory.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = {
                                    editing = HabitMemoryEditState(
                                        originalKey = memory.key,
                                        key = memory.key,
                                        value = memory.value,
                                    )
                                }) {
                                    Text(stringResource(R.string.edit))
                                }
                                TextButton(onClick = { onDelete(memory.key) }) {
                                    Text(stringResource(R.string.ai_habit_memory_delete))
                                }
                            }
                            if (index < memories.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        editing = HabitMemoryEditState(originalKey = null, key = "", value = "")
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ai_habit_memory_add))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        },
    )

    editing?.let { draft ->
        HabitMemoryEditDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = { originalKey, key, value ->
                onSave(originalKey, key, value)
                editing = null
            },
        )
    }
}

private data class HabitMemoryEditState(
    val originalKey: String?,
    val key: String,
    val value: String,
)

@Composable
private fun HabitMemoryEditDialog(
    draft: HabitMemoryEditState,
    onDismiss: () -> Unit,
    onSave: (originalKey: String?, key: String, value: String) -> Unit,
) {
    var key by remember(draft) { mutableStateOf(draft.key) }
    var value by remember(draft) { mutableStateOf(draft.value) }
    val isNew = draft.originalKey == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.ai_habit_memory_add else R.string.ai_habit_memory_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { if (isNew) key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_habit_memory_key)) },
                    placeholder = { Text(stringResource(R.string.ai_habit_memory_key_hint)) },
                    singleLine = true,
                    readOnly = !isNew,
                    enabled = isNew,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_habit_memory_value)) },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft.originalKey, key.trim(), value.trim()) },
                enabled = key.isNotBlank() && value.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ToolConfigCard(
    tool: AiToolConfig,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayName = tool.displayName.ifBlank {
        val res = toolDisplayNameRes(tool.toolName)
        if (res != 0) stringResource(res) else AiAbilityManagementViewModel.defaultDisplayName(tool.toolName)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (tool.enabled) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tool.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                val subtitle = tool.description.ifBlank {
                    val res = toolDescriptionRes(tool.toolName)
                    if (res != 0) stringResource(res) else ""
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!tool.enabled) {
                    Text(
                        stringResource(R.string.ai_ability_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.error,
                    )
                }
                if (tool.useSubModel) {
                    Text(
                        stringResource(R.string.ai_ability_sub_model_enabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolEditDialog(
    tool: AiToolConfig,
    availableModels: List<io.legado.app.data.entities.AiModelProfile>,
    templatePreview: String?,
    onUpdateField: (String, String) -> Unit,
    onUpdateBoolean: (String, Boolean) -> Unit,
    onUpdateSubModel: (String?) -> Unit,
    onSave: () -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isChapterTool = tool.toolName == "get_chapter_content"
    val isSubModelTextTool = tool.toolName in setOf(
        "get_chapter_content", "read_web_page", "fetch_page_snippet", "debug_book_source",
    )
    val isRegenerateTool = tool.toolName in setOf(
        "patch_history_memory", "patch_outline", "patch_character_card", "patch_user_card",
    )
    val isSuggestionsTool = tool.toolName == "suggested_replies"
    val isGalgameHudTool = tool.toolName == "galgame_hud"
    val supportsSubModel = tool.toolName in setOf(
        "get_chapter_content", "read_web_page", "fetch_page_snippet", "debug_book_source",
        "extract_world_book", "patch_world_book",
        "patch_history_memory", "patch_outline", "patch_character_card", "patch_user_card",
        "suggested_replies", "galgame_hud", "ai_help_reply", "post_edit",
    )
    val colorScheme = MaterialTheme.colorScheme
    val dialogTitle = tool.displayName.ifBlank {
        val res = toolDisplayNameRes(tool.toolName)
        if (res != 0) stringResource(res) else AiAbilityManagementViewModel.defaultDisplayName(tool.toolName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                dialogTitle,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Enabled switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.ai_ability_enabled), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = tool.enabled,
                        onCheckedChange = { onUpdateBoolean("enabled", it) },
                    )
                }

                // Display name
                OutlinedTextField(
                    value = tool.displayName,
                    onValueChange = { onUpdateField("displayName", it) },
                    label = { Text(stringResource(R.string.ai_ability_display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Description — hidden for get_chapter_content when sub-model is on (merged into prompt)
                if (!isChapterTool || !tool.useSubModel) {
                    OutlinedTextField(
                        value = tool.description,
                        onValueChange = { onUpdateField("description", it) },
                        label = { Text(stringResource(R.string.ai_ability_description)) },
                        supportingText = { Text(stringResource(R.string.ai_ability_description_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                }

                // maxChars
                OutlinedTextField(
                    value = tool.maxChars.toString(),
                    onValueChange = { onUpdateField("maxChars", it) },
                    label = { Text(stringResource(R.string.ai_ability_max_chars)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (supportsSubModel) {
                    // Sub-model toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.ai_ability_sub_model_delegation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = tool.useSubModel,
                            onCheckedChange = { onUpdateBoolean("useSubModel", it) },
                        )
                    }

                    if (isSuggestionsTool || isGalgameHudTool) {
                        if (templatePreview != null) {
                            // Read-only preview — edit in PromptTemplateScreen
                            OutlinedTextField(
                                value = templatePreview,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ai_ability_prompt_readonly)) },
                                supportingText = {
                                    Text(stringResource(R.string.ai_ability_prompt_edit_hint))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 8,
                            )
                        }
                    }

                    if (tool.useSubModel && (isSuggestionsTool || isGalgameHudTool)) {
                        // Model picker
                        var expanded by remember { mutableStateOf(false) }
                        val selectedModel = availableModels.find { it.id == tool.subModelProfileId }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedModel?.displayName ?: stringResource(R.string.ai_select_model),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ai_ability_sub_model)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                                Text(model.modelId, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            onUpdateSubModel(model.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (tool.useSubModel) {
                        // Prompt — text-preprocess tools + regenerate_* (repair rules)
                        if (isSubModelTextTool || isRegenerateTool) {
                            if (templatePreview != null) {
                                OutlinedTextField(
                                    value = templatePreview,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.ai_ability_prompt_readonly)) },
                                    supportingText = { Text(stringResource(R.string.ai_ability_prompt_edit_hint)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 8,
                                )
                            }
                        }

                        // Model picker
                        var expanded by remember { mutableStateOf(false) }
                        val selectedModel = availableModels.find { it.id == tool.subModelProfileId }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedModel?.displayName ?: stringResource(R.string.ai_select_model),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ai_ability_sub_model)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                                Text(model.modelId, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            onUpdateSubModel(model.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onResetDefault) {
                    Text(stringResource(R.string.ai_ability_restore_default), color = colorScheme.error)
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.action_save), color = colorScheme.primary)
                    }
                }
            }
        },
    )
}

data class NumPickerInfo(
    val title: String,
    val value: Int,
    val min: Int,
    val max: Int,
    val defaultButton: @Composable (() -> Unit)? = null,
    val onConfirm: (Int) -> Unit,
)
