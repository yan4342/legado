package io.legado.app.ui.ai.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
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
import io.legado.app.R
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.data.entities.Book
import io.legado.app.ui.common.compose.legadoSheetInsets
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.sendToClip

// ---- Prompt select sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSelectSheet(
    state: AiChatUiState,
    onDismiss: () -> Unit,
    onToggleEnabled: (String) -> Unit,
    onEdit: (AiWritingPromptUi) -> Unit,
    onDelete: (AiWritingPromptUi) -> Unit,
    onNew: () -> Unit,
) {
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).legadoSheetInsets()) {
            Text(
                stringResource(R.string.ai_select_prompts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                val categories = state.writingPrompts.map { it.category }.distinct()
                for (category in categories) {
                    item(key = "header_$category") {
                        Text(
                            categoryDisplayName(category),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(
                        state.writingPrompts.filter { it.category == category },
                        key = { it.id }
                    ) { prompt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (prompt.enabled) colorScheme.surface
                            else colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        prompt.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (prompt.enabled) colorScheme.onSurface
                                        else colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                    if (prompt.content.isNotBlank()) {
                                        Text(
                                            prompt.content.take(100),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (prompt.enabled) colorScheme.onSurfaceVariant
                                            else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                // Enable/disable toggle
                                Switch(
                                    checked = prompt.enabled,
                                    onCheckedChange = { onToggleEnabled(prompt.id) },
                                    modifier = Modifier.height(24.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { onEdit(prompt) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(prompt) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_new_prompt))
            }
        }
    }
}

// ---- Conversation skills select sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillSelectSheet(
    state: AiChatUiState,
    onDismiss: () -> Unit,
    onToggleSkill: (String) -> Unit,
    onResetToAll: () -> Unit,
) {
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme
    val type = state.conversationType
    val modeSkills = remember(state.availableSkills, type) {
        state.availableSkills.filter { skill ->
            when (skill.mode) {
                "both" -> true
                "chat" -> type == "chat"
                "writing" -> type == "writing"
                else -> type == skill.mode
            }
        }
    }
    val allowlist = remember(state.conversationSkillIds) {
        state.conversationSkillIds?.let { AiIdListCodec.parse(it) }
    }
    val eligible = modeSkills.filter { it.globallyEnabled }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).legadoSheetInsets()) {
            Text(
                stringResource(R.string.ai_conversation_skills),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                stringResource(R.string.ai_conversation_skills_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (eligible.isEmpty()) {
                Text(
                    stringResource(R.string.ai_conversation_skills_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(eligible, key = { it.skillId }) { skill ->
                        val selected = allowlist == null || skill.skillId in allowlist
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) colorScheme.surface
                            else colorScheme.surface.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        skill.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) colorScheme.onSurface
                                        else colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                    if (skill.description.isNotBlank()) {
                                        Text(
                                            skill.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) colorScheme.onSurfaceVariant
                                            else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Switch(
                                    checked = selected,
                                    onCheckedChange = { onToggleSkill(skill.skillId) },
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (state.conversationSkillIds != null) {
                TextButton(
                    onClick = onResetToAll,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(stringResource(R.string.ai_conversation_skills_reset))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---- Character edit sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCardEditDialog(
    userName: String,
    userDescription: String,
    userCardEnabled: Boolean,
    readOnly: Boolean = false,
    readOnlyMessage: String? = null,
    previewChanges: List<FieldChangeUi> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, enabled: Boolean) -> Unit,
    onToggleEnabled: () -> Unit,
) {
    var name by remember { mutableStateOf(userName) }
    var description by remember { mutableStateOf(userDescription) }
    var enabled by remember { mutableStateOf(userCardEnabled) }
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme
    val fieldMap = fieldChangesByLeaf(previewChanges)
    val useInlineGit = canInlineGitHighlightUserFields(previewChanges)
    val showBanner = shouldShowSheetPreviewBanner(previewChanges, useInlineGit)
    val nameChange = fieldMap["userName"] ?: fieldMap["name"]
    val descChange = fieldMap["userDescription"] ?: fieldMap["description"]

    LaunchedEffect(userName, userDescription, userCardEnabled) {
        name = userName
        description = userDescription
        enabled = userCardEnabled
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!readOnly) {
                onSave(name, description, enabled)
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .legadoSheetInsets()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ai_edit_user_description),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        if (!readOnly) {
                            enabled = it
                            onToggleEnabled()
                        }
                    },
                    enabled = !readOnly,
                )
            }
            if (readOnly) {
                Text(
                    readOnlyMessage ?: stringResource(R.string.ai_sheet_viewing_other_conversation),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.tertiary,
                )
            }
            if (showBanner) {
                SheetPreviewBanner(changes = previewChanges, maxHeightDp = 120)
            }
            if (useInlineGit && nameChange != null) {
                GitStyleFieldPreview(
                    label = stringResource(R.string.ai_user_name),
                    change = nameChange,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { if (!readOnly) name = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_user_name)) },
                placeholder = { Text(stringResource(R.string.ai_user_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (useInlineGit && descChange != null) {
                GitStyleFieldPreview(
                    label = stringResource(R.string.ai_user_description),
                    change = descChange,
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { if (!readOnly) description = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_user_description)) },
                placeholder = { Text(stringResource(R.string.ai_user_description_hint)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(if (readOnly) stringResource(R.string.ok) else stringResource(R.string.cancel))
                }
                if (!readOnly) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onSave(name, description, enabled) },
                    ) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
fun SourceBookBindingRow(
    bookName: String,
    bookAuthor: String,
    readOnly: Boolean,
    onSelectBook: () -> Unit,
    onClearBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ai_source_book),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.primary,
        )
        if (bookName.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bookName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (bookAuthor.isNotBlank()) {
                            Text(
                                bookAuthor,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!readOnly) {
                        IconButton(onClick = onClearBook) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.ai_source_book_clear),
                            )
                        }
                    }
                }
            }
        } else if (!readOnly) {
            OutlinedButton(onClick = onSelectBook, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_world_book_select_book))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBookPickerSheet(
    books: List<Book>,
    onSelect: (Book) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).legadoSheetInsets()) {
            Text(
                stringResource(R.string.ai_world_book_select_book),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(books, key = { it.bookUrl }) { book ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surface,
                        onClick = { onSelect(book) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(book.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${book.author} · ${book.durChapterIndex + 1}/${book.totalChapterNum} chapters",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Prompt edit sheet ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptEditDialog(
    existing: AiWritingPromptUi?,
    forceCategory: String? = null,
    existingCategories: List<String> = emptyList(),
    onRestoreDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, content: String, category: String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: forceCategory ?: "style") }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    val isNew = existing == null
    val fixedCategory = forceCategory != null
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    val predefinedCategories = listOf(
        "style" to stringResource(R.string.ai_prompt_style),
        "approach" to stringResource(R.string.ai_prompt_approach),
        "action_continue" to stringResource(R.string.ai_action_continue_prompt),
        AiWritingPrompt.CATEGORY_ACTION_HELP_REPLY to stringResource(R.string.ai_help_reply),
    )
    val displayCategories = (predefinedCategories + existingCategories
        .filter { it !in predefinedCategories.map { p -> p.first } && it != category }
        .map { it to it })
        .distinctBy { it.first }

    ModalBottomSheet(
        onDismissRequest = { onSave(name, content, category) },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .legadoSheetInsets()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (isNew) stringResource(R.string.ai_new_prompt) else stringResource(R.string.ai_edit_prompt),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!fixedCategory) {
                var catExpanded by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.ai_prompt_category)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("输入分类名，如 style、文风") },
                    trailingIcon = {
                        IconButton(onClick = { catExpanded = !catExpanded }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    },
                )
                if (catExpanded) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        displayCategories.filter { (value, _) -> value != category }.forEach { (value, label) ->
                            AssistChip(
                                onClick = { category = value },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.ai_prompt_content)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isNew && onRestoreDefault != null) {
                    TextButton(
                        onClick = onRestoreDefault,
                    ) {
                        Text("Restore Default", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    onClick = { onSave(name, content, category) },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

@Composable
private fun categoryDisplayName(category: String): String {
    return when (category) {
        "style" -> stringResource(R.string.ai_prompt_style)
        "approach" -> stringResource(R.string.ai_prompt_approach)
        "action_continue" -> stringResource(R.string.ai_action_continue_prompt)
        AiWritingPrompt.CATEGORY_ACTION_HELP_REPLY -> stringResource(R.string.ai_help_reply)
        else -> category
    }
}
//outline sheet was removed to OutlineSheet.kt

/** Compact side panel for tablet/wide writing mode — quick access to structured data. */
@Composable
fun WritingDataSidePanel(
    state: AiChatUiState,
    onOpenMemoryTable: () -> Unit,
    onOpenOutline: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenExecutionHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val cards = state.selectedCharacterCards.ifEmpty {
        listOfNotNull(state.selectedCharacterCard)
    }
    val wbNames = remember(state.workspaceWorldBookIds, state.enabledWorldBooks) {
        val ids = AiIdListCodec.parse(state.workspaceWorldBookIds).toSet()
        state.enabledWorldBooks.filter { it.id in ids }.map { it.name }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.ai_workspace_tools), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_workspace_title), maxLines = 1)
            }
            if (state.isMaintainingStructuredData) {
                Text(
                    stringResource(R.string.ai_workspace_maintaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                )
            } else {
                Text(
                    buildString {
                        append(stringResource(R.string.ai_workspace_characters))
                        append("：")
                        append(
                            cards.joinToString { it.name }
                                .ifBlank { stringResource(R.string.ai_workspace_none) },
                        )
                        if (wbNames.isNotEmpty()) {
                            append('\n')
                            append(stringResource(R.string.ai_workspace_world_books))
                            append("：")
                            append(wbNames.joinToString())
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onOpenMemoryTable, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_workspace_memory_tables), maxLines = 1)
            }
            OutlinedButton(onClick = onOpenOutline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_workspace_outline), maxLines = 1)
            }
            OutlinedButton(onClick = onOpenCharacter, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_workspace_character), maxLines = 1)
            }
            OutlinedButton(onClick = onOpenExecutionHistory, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_execution_history_title), maxLines = 1)
            }
            if (state.outlineContent.isNotBlank()) {
                Text(
                    stringResource(R.string.ai_workspace_outline),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    state.outlineContent.take(400),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.memoryTableContent.isNotBlank()) {
                Text(
                    stringResource(R.string.ai_workspace_memory_tables),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    state.memoryTableContent.take(400),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceSheet(
    state: AiChatUiState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onShowClonePicker: () -> Unit,
    onImportJsonChange: (String) -> Unit,
    onImport: () -> Unit,
    onWorldBookIdsChange: (String) -> Unit,
    onStructuredAutoMaintainChange: (Boolean) -> Unit,
    onShowAdoptPreview: () -> Unit,
    onDismissAdoptPreview: () -> Unit,
    onConfirmAdopt: () -> Unit,
    onToggleAdoptType: (String) -> Unit,
    onShowBookPicker: () -> Unit,
    onSelectBook: (io.legado.app.data.entities.Book) -> Unit,
    onDismissBookPicker: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = true)
    val cards = state.selectedCharacterCards.ifEmpty {
        listOfNotNull(state.selectedCharacterCard)
    }
    val enabledPrompts = state.writingPrompts.filter {
        it.enabled && it.category != AiWritingPrompt.CATEGORY_ACTION_HELP_REPLY
    }
    val selectedWbIds = remember(state.workspaceWorldBookIds) {
        AiIdListCodec.parse(state.workspaceWorldBookIds).toSet()
    }
    val noneLabel = stringResource(R.string.ai_workspace_none)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .legadoSheetInsets(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ai_workspace_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ai_workspace_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ai_workspace_auto_maintain_switch),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (state.structuredAutoMaintainEnabled) {
                            stringResource(R.string.ai_workspace_auto_maintain)
                        } else {
                            stringResource(R.string.ai_workspace_auto_maintain_off)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.structuredAutoMaintainEnabled,
                    onCheckedChange = onStructuredAutoMaintainChange,
                )
            }
            if (state.isMaintainingStructuredData) {
                Text(
                    stringResource(R.string.ai_workspace_maintaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.ai_workspace_section_bound),
                style = MaterialTheme.typography.titleSmall,
            )

            WorkspaceBoundBlock(
                title = stringResource(R.string.ai_workspace_characters),
                body = cards.joinToString { it.name }.ifBlank { noneLabel },
            )
            WorkspaceBoundBlock(
                title = stringResource(R.string.ai_workspace_prompts),
                body = enabledPrompts.joinToString { it.name }.ifBlank { noneLabel },
            )

            Text(
                stringResource(R.string.ai_workspace_world_books),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(R.string.ai_workspace_world_books_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            if (state.enabledWorldBooks.isEmpty()) {
                Text(
                    stringResource(R.string.ai_workspace_world_books_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.enabledWorldBooks.forEach { book ->
                        val selected = book.id in selectedWbIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) {
                                    selectedWbIds - book.id
                                } else {
                                    selectedWbIds + book.id
                                }
                                onWorldBookIdsChange(AiIdListCodec.toCsv(next))
                            },
                            label = { Text(book.name) },
                        )
                    }
                }
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.ai_workspace_section_reuse),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.ai_workspace_section_reuse_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onExport) {
                    Text(stringResource(R.string.ai_workspace_export))
                }
                OutlinedButton(onClick = onShowClonePicker) {
                    Text(stringResource(R.string.ai_workspace_clone))
                }
            }
            // 采纳桥:工作区绑定本书(写入正典的目标书)
            HorizontalDivider()
            Text(
                stringResource(R.string.ai_workspace_section_book_binding),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.workspaceBookUrl.isNotBlank()) {
                            state.workspaceBookUrl
                        } else {
                            stringResource(R.string.ai_workspace_no_book_bound)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.ai_workspace_book_binding_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onShowBookPicker) {
                    Text(stringResource(R.string.ai_workspace_bind_book))
                }
            }
            // 采纳桥:衍生会话 → 写入本书(正典)
            OutlinedButton(
                onClick = onShowAdoptPreview,
                enabled = !state.adoptPreviewLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_workspace_adopt_to_book))
            }
            if (state.adoptPreviewLoading) {
                Text(
                    stringResource(R.string.ai_workspace_adopt_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
            }
            state.adoptPreview?.let { preview ->
                if (preview.sections.isEmpty()) {
                    Text(
                        stringResource(R.string.ai_workspace_adopt_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            preview.sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = section.type in state.adoptSelectedTypes,
                                            onCheckedChange = { onToggleAdoptType(section.type) },
                                        )
                                        Text(
                                            "${section.label}：${section.summary}",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    section.changes.take(6).forEach { change ->
                                        Text(
                                            change.newValue.ifBlank { change.path }.take(120),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (section.changes.size > 6) {
                                        Text(
                                            "… 共 ${section.changes.size} 项",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onConfirmAdopt,
                        enabled = state.adoptSelectedTypes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ai_workspace_adopt_confirm))
                    }
                }
            }
            if (state.workspaceExportJson.isNotBlank()) {
                Text(
                    stringResource(R.string.ai_workspace_export_done),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            state.workspaceExportJson.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { context.sendToClip(state.workspaceExportJson) },
                        ) {
                            Text(stringResource(R.string.ai_workspace_export_copy))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.workspaceImportJson,
                onValueChange = onImportJsonChange,
                label = { Text(stringResource(R.string.ai_workspace_import_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
            )
            Button(
                onClick = onImport,
                enabled = state.workspaceImportJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.ai_workspace_import))
            }
            // bottom inset handled by legadoSheetInsets
        }
    }

    if (state.showWorkspaceBookPicker) {
        AlertDialog(
            onDismissRequest = onDismissBookPicker,
            title = { Text(stringResource(R.string.ai_workspace_bind_book)) },
            text = {
                if (state.workspaceBookshelfBooks.isEmpty()) {
                    Text(stringResource(R.string.ai_workspace_no_book_to_pick))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.workspaceBookshelfBooks.take(30).forEach { book ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBook(book) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = book.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissBookPicker) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WorkspaceBoundBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
