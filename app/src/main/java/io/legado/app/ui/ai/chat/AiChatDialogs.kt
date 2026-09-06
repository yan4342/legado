package io.legado.app.ui.ai.chat

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.legado.app.R
import io.legado.app.ui.ai.outline.OutlineSheet
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holder for all dialog/sheet show/hide state. */
@Stable
class AiChatDialogState {
    var renameDialogConv by mutableStateOf<AiChatConversationUi?>(null)
    var renameText by mutableStateOf("")
    var showCharacterSheet by mutableStateOf(false)
    var showCharacterMultiSelect by mutableStateOf(false)
    var selectedCardIdsForSheet by mutableStateOf<Set<String>>(emptySet())
    var showPromptSheet by mutableStateOf(false)
    var showSkillSheet by mutableStateOf(false)
    var editingCharacter by mutableStateOf<AiCharacterCardUi?>(null)
    var showCharacterEditDialog by mutableStateOf(false)
    var editingPrompt by mutableStateOf<AiWritingPromptUi?>(null)
    var showPromptEditDialog by mutableStateOf(false)
    var showActionPromptEditDialog by mutableStateOf(false)
    var editingActionCategory by mutableStateOf("")
    var showDeleteCharacterConfirm by mutableStateOf<AiCharacterCardUi?>(null)
    var showDeletePromptConfirm by mutableStateOf<AiWritingPromptUi?>(null)
    var showDeleteConversationConfirm by mutableStateOf<AiChatConversationUi?>(null)
    var showWorldBookSheet by mutableStateOf(false)
    var showMemoryTableSheet by mutableStateOf(false)
    var showOutlineSheet by mutableStateOf(false)
    var showExecutionHistorySheet by mutableStateOf(false)
    var showCompressConfirm by mutableStateOf(false)
    var showContextUsageDialog by mutableStateOf(false)
    var showUserCardEditDialog by mutableStateOf(false)
    /** Conversation ID that a tool-operated sheet should target. Falls back to current conversation if null. */
    var sheetConversationId by mutableStateOf<String?>(null)
    var memoryTableHighlightRowId by mutableStateOf<String?>(null)
    var memoryTableHighlightTableId by mutableStateOf<String?>(null)
    var memoryTableHighlightMatch by mutableStateOf<String?>(null)
    var outlineHighlightSection by mutableStateOf<String?>(null)
    var highlightWorldBookId by mutableStateOf<String?>(null)
    /** Loaded when [sheetConversationId] differs from the active chat conversation. */
    var sheetOutlineContent by mutableStateOf<String?>(null)
    var sheetOutlineEnabled by mutableStateOf<Boolean?>(null)
    var sheetOutlineBookName by mutableStateOf<String?>(null)
    var sheetOutlineBookAuthor by mutableStateOf<String?>(null)
    var sheetUserName by mutableStateOf<String?>(null)
    var sheetUserDescription by mutableStateOf<String?>(null)
    var sheetUserCardEnabled by mutableStateOf<Boolean?>(null)
    /** Pending tool field changes for sheet preview (git inline or top banner). */
    var sheetPreviewChanges by mutableStateOf<List<FieldChangeUi>>(emptyList())

    /** True while inspecting a pending tool mutation — sheets must not persist edits. */
    val isSheetPreviewMode: Boolean
        get() = sheetPreviewChanges.isNotEmpty()

    fun clearSheetTarget() {
        sheetConversationId = null
        sheetOutlineContent = null
        sheetOutlineEnabled = null
        sheetOutlineBookName = null
        sheetOutlineBookAuthor = null
        sheetUserName = null
        sheetUserDescription = null
        sheetUserCardEnabled = null
        sheetPreviewChanges = emptyList()
    }
}

private fun resolveToolDeepLink(
    toolName: String,
    inputJson: String,
    conversationId: String?,
): io.legado.app.domain.model.StructuredDataDeepLink? {
    val normalizedInput = inputJson.trim().ifBlank { "{}" }
    val args = runCatching {
        com.google.gson.JsonParser.parseString(normalizedInput).asJsonObject
    }.getOrNull()
    val convId = args?.get("conversationId")
        ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.asString
        ?.takeIf { it.isNotBlank() }
        ?: conversationId
    return if (args != null) {
        io.legado.app.domain.usecase.structured.StructuredDataDeepLinkResolver.resolve(toolName, args, convId)
    } else {
        null
    } ?: io.legado.app.domain.usecase.structured.StructuredDataDeepLinkResolver.fallback(toolName, convId)
}

/** Whether [openToolInEditor] can open a structured-data editor for this tool call. */
fun canOpenToolInEditor(
    toolName: String,
    inputJson: String,
    conversationId: String?,
): Boolean = resolveToolDeepLink(toolName, inputJson, conversationId) != null

fun openToolInEditor(
    ds: AiChatDialogState,
    toolName: String,
    inputJson: String,
    conversationId: String?,
    characterCards: List<AiCharacterCardUi>,
    selectedCharacterCards: List<AiCharacterCardUi> = emptyList(),
): Boolean {
    val normalizedInput = inputJson.trim().ifBlank { "{}" }
    val args = runCatching {
        com.google.gson.JsonParser.parseString(normalizedInput).asJsonObject
    }.getOrNull()
    val link = resolveToolDeepLink(toolName, inputJson, conversationId) ?: return false
    if (link.resourceType == "character_card") {
        val cardId = link.cardId
        if (!cardId.isNullOrBlank()) {
            val card = (characterCards + selectedCharacterCards).distinctBy { it.id }.find { it.id == cardId }
            if (card != null) {
                ds.editingCharacter = card
                ds.showCharacterEditDialog = true
                return true
            }
            ds.editingCharacter = AiCharacterCardUi(
                id = cardId,
                name = args?.stringField("name").orEmpty(),
                description = args?.stringField("description").orEmpty(),
                openingLine = args?.stringField("openingLine").orEmpty(),
            )
            ds.showCharacterEditDialog = true
            return true
        }
    }
    openStructuredDeepLink(ds, link, characterCards, selectedCharacterCards)
    return true
}

private fun com.google.gson.JsonObject.stringField(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

fun openStructuredDeepLink(
    ds: AiChatDialogState,
    link: io.legado.app.domain.model.StructuredDataDeepLink,
    characterCards: List<AiCharacterCardUi> = emptyList(),
    selectedCharacterCards: List<AiCharacterCardUi> = emptyList(),
    previewChanges: List<FieldChangeUi> = emptyList(),
) {
    if (previewChanges.isNotEmpty()) {
        ds.sheetPreviewChanges = previewChanges
    }
    ds.sheetConversationId = link.conversationId?.takeIf { it.isNotBlank() }
    when (link.resourceType) {
        "memory_table" -> {
            ds.memoryTableHighlightTableId = link.tableId
            ds.memoryTableHighlightRowId = link.rowId
            ds.memoryTableHighlightMatch = link.matchHint
            ds.showMemoryTableSheet = true
        }
        "outline" -> {
            ds.outlineHighlightSection = link.outlineSection
            ds.showOutlineSheet = true
        }
        "character_card" -> {
            val cardId = link.cardId
            if (!cardId.isNullOrBlank()) {
                val card = (characterCards + selectedCharacterCards)
                    .distinctBy { it.id }
                    .find { it.id == cardId }
                ds.editingCharacter = card ?: AiCharacterCardUi(
                    id = cardId,
                    name = "",
                    description = "",
                    openingLine = "",
                )
                ds.showCharacterEditDialog = true
            } else {
                ds.showCharacterSheet = true
            }
        }
        "world_book" -> {
            ds.highlightWorldBookId = link.worldBookId
            ds.showWorldBookSheet = true
        }
        "user_card" -> {
            ds.showUserCardEditDialog = true
        }
    }
}

// ---- Dialog / Sheet rendering ----

@androidx.compose.runtime.Composable
fun AiChatDialogs(
    ds: AiChatDialogState,
    viewModel: AiChatViewModel,
    state: AiChatUiState,
    onConversationDeleted: (() -> Unit)? = null,
    onSaveOutlineExportFile: () -> Unit = {},
    onOpenOutlineImportFile: () -> Unit = {},
) {
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val context = LocalContext.current
    val characterImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onSuccess { bytes ->
            if (bytes != null && bytes.isNotEmpty()) {
                android.util.Log.i(
                    "StCardImport",
                    "picker uri=$uri mime=${context.contentResolver.getType(uri)} bytes=${bytes.size}",
                )
                viewModel.onIntent(AiChatIntent.ImportCharacterCardBytes(bytes))
            } else {
                android.util.Log.w("StCardImport", "picker empty bytes uri=$uri")
            }
        }.onFailure {
            android.util.Log.e("StCardImport", "picker read failed uri=$uri: ${it.message}", it)
        }
    }

    // ---- Rename dialog ----
    ds.renameDialogConv?.let { conv ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ds.renameDialogConv = null },
            title = { androidx.compose.material3.Text("Rename") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = ds.renameText,
                    onValueChange = { ds.renameText = it },
                    singleLine = true,
                    placeholder = { androidx.compose.material3.Text("Chat name") },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (ds.renameText.isNotBlank()) {
                        viewModel.onIntent(AiChatIntent.RenameConversation(conv.id, ds.renameText))
                    }
                    ds.renameDialogConv = null
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.legado.app.R.string.ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ds.renameDialogConv = null }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.legado.app.R.string.cancel))
                }
            },
        )
    }

    // ---- Character select bottom sheet (single) ----
    if (ds.showCharacterSheet) {
        CharacterSelectSheet(
            state = state,
            onDismiss = { ds.showCharacterSheet = false },
            onSelect = { card ->
                viewModel.onIntent(AiChatIntent.CreateConversationWithCharacter(card.id))
                ds.showCharacterSheet = false
            },
            onUpdateCurrent = { cardId ->
                viewModel.onIntent(AiChatIntent.UpdateConversationCharacter(cardId))
                ds.showCharacterSheet = false
            },
            onEdit = { card ->
                ds.editingCharacter = card
                ds.showCharacterEditDialog = true
                ds.showCharacterSheet = false
            },
            onDelete = { card ->
                ds.showDeleteCharacterConfirm = card
            },
            onNew = {
                ds.editingCharacter = null
                ds.showCharacterEditDialog = true
                ds.showCharacterSheet = false
            },
            onImport = { characterImportLauncher.launch(arrayOf("application/json", "image/png", "text/*", "*/*")) },
        )
    }

    // ---- Character select bottom sheet (multi-select) ----
    if (ds.showCharacterMultiSelect) {
        CharacterSelectSheet(
            state = state,
            onDismiss = { ds.showCharacterMultiSelect = false },
            onSelect = { card ->
                viewModel.onIntent(AiChatIntent.CreateConversationWithCharacter(card.id))
                ds.showCharacterMultiSelect = false
            },
            onUpdateCurrent = {},
            onEdit = { card ->
                ds.editingCharacter = card
                ds.showCharacterEditDialog = true
                ds.showCharacterMultiSelect = false
            },
            onDelete = { card ->
                ds.showDeleteCharacterConfirm = card
            },
            onNew = {
                ds.editingCharacter = null
                ds.showCharacterEditDialog = true
                ds.showCharacterMultiSelect = false
            },
            selectedIds = ds.selectedCardIdsForSheet,
            onConfirm = { cardIds ->
                viewModel.onIntent(AiChatIntent.UpdateConversationCharacters(cardIds))
                ds.showCharacterMultiSelect = false
            },
            onImport = { characterImportLauncher.launch(arrayOf("application/json", "image/png", "text/*", "*/*")) },
        )
    }

    // ---- Prompt select bottom sheet ----
    if (ds.showPromptSheet) {
        PromptSelectSheet(
            state = state,
            onDismiss = { ds.showPromptSheet = false },
            onToggleEnabled = { promptId ->
                viewModel.onIntent(AiChatIntent.TogglePromptEnabled(promptId))
            },
            onEdit = { prompt ->
                ds.editingPrompt = prompt
                ds.showPromptEditDialog = true
                ds.showPromptSheet = false
            },
            onDelete = { prompt ->
                ds.showDeletePromptConfirm = prompt
            },
            onNew = {
                ds.editingPrompt = null
                ds.showPromptEditDialog = true
                ds.showPromptSheet = false
            },
        )
    }

    if (ds.showSkillSheet) {
        SkillSelectSheet(
            state = state,
            onDismiss = { ds.showSkillSheet = false },
            onToggleSkill = { skillId ->
                viewModel.onIntent(AiChatIntent.ToggleConversationSkill(skillId))
            },
            onResetToAll = {
                viewModel.onIntent(AiChatIntent.ResetConversationSkills)
            },
        )
    }

    // ---- Character edit dialog ----
    if (ds.showCharacterEditDialog) {
        val isToolPreview = ds.isSheetPreviewMode
        CharacterEditDialog(
            existing = ds.editingCharacter,
            availableWorldBooks = state.enabledWorldBooks.toList(),
            previewChanges = ds.sheetPreviewChanges,
            readOnly = isToolPreview,
            showPerformanceFields = state.conversationType != "writing",
            readOnlyMessage = if (isToolPreview) {
                stringResource(R.string.ai_sheet_tool_preview_readonly)
            } else {
                null
            },
            onDismiss = {
                ds.sheetPreviewChanges = emptyList()
                ds.showCharacterEditDialog = false
            },
            onSave = { name, desc, opening, worldBookIds, personality, scenario, exampleDialogues, postHistory, alternateOpenings, aliasesJson, voiceGender, voiceAgeBand, bookUrl, bookName, bookAuthor, dramaticRole, avatarPath ->
                viewModel.onIntent(
                    AiChatIntent.SaveCharacterCard(
                        name, desc, opening, worldBookIds, ds.editingCharacter?.id,
                        personality, scenario, exampleDialogues, postHistory, alternateOpenings,
                        aliasesJson, voiceGender, voiceAgeBand, bookUrl, bookName, bookAuthor, dramaticRole, avatarPath,
                    ),
                )
                ds.sheetPreviewChanges = emptyList()
                ds.showCharacterEditDialog = false
            },
        )
    }

    if (ds.showUserCardEditDialog) {
        val targetConvId = ds.sheetConversationId ?: state.currentConversationId
        val isForeignConv = !targetConvId.isNullOrBlank() && targetConvId != state.currentConversationId
        LaunchedEffect(ds.showUserCardEditDialog, targetConvId) {
            if (!ds.showUserCardEditDialog) return@LaunchedEffect
            if (isForeignConv && targetConvId != null) {
                val (name, description, enabled) = viewModel.getUserCardForConversation(targetConvId)
                ds.sheetUserName = name
                ds.sheetUserDescription = description
                ds.sheetUserCardEnabled = enabled
            } else {
                ds.sheetUserName = null
                ds.sheetUserDescription = null
                ds.sheetUserCardEnabled = null
            }
        }
        UserCardEditDialog(
            userName = ds.sheetUserName ?: state.userName,
            userDescription = ds.sheetUserDescription ?: state.userDescription,
            userCardEnabled = ds.sheetUserCardEnabled ?: state.userCardEnabled,
            readOnly = isForeignConv || ds.isSheetPreviewMode,
            readOnlyMessage = when {
                ds.isSheetPreviewMode -> stringResource(R.string.ai_sheet_tool_preview_readonly)
                isForeignConv -> stringResource(R.string.ai_sheet_viewing_other_conversation)
                else -> null
            },
            previewChanges = ds.sheetPreviewChanges,
            onDismiss = {
                ds.clearSheetTarget()
                ds.showUserCardEditDialog = false
            },
            onSave = { name, description, enabled ->
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.SaveUserCard(name, description, enabled))
                }
                ds.clearSheetTarget()
                ds.showUserCardEditDialog = false
            },
            onToggleEnabled = {
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.ToggleUserCardEnabled)
                }
            },
        )
    }

    // ---- Prompt edit dialog ----
    if (ds.showPromptEditDialog) {
        val restoreDefault = ds.editingPrompt?.let { prompt ->
            val seed = io.legado.app.data.entities.AiWritingPrompt.SEED_PROMPTS.find { it.id == prompt.id }
            if (seed != null) {
                {
                    viewModel.onIntent(AiChatIntent.DeleteWritingPrompt(prompt.id))
                    viewModel.onIntent(AiChatIntent.SaveWritingPrompt(seed.name, seed.content, seed.category, prompt.id))
                    ds.showPromptEditDialog = false
                    ds.showPromptSheet = true
                }
            } else null
        }
        PromptEditDialog(
            existing = ds.editingPrompt,
            existingCategories = state.writingPrompts.map { it.category }.distinct(),
            onRestoreDefault = restoreDefault,
            onDismiss = { ds.showPromptEditDialog = false },
            onSave = { name, content, category ->
                viewModel.onIntent(AiChatIntent.SaveWritingPrompt(name, content, category, ds.editingPrompt?.id))
                ds.showPromptEditDialog = false
                ds.showPromptSheet = true
            },
        )
    }

    // ---- Action prompt edit dialog ----
    if (ds.showActionPromptEditDialog) {
        val existingActionPrompt = state.writingPrompts.firstOrNull {
            it.category == ds.editingActionCategory && it.enabled
        }
        val restoreDefault = existingActionPrompt?.let { prompt ->
            val seed = io.legado.app.data.entities.AiWritingPrompt.SEED_PROMPTS.find { it.id == prompt.id }
            if (seed != null) {
                {
                    viewModel.onIntent(AiChatIntent.DeleteWritingPrompt(prompt.id))
                    viewModel.onIntent(AiChatIntent.SaveWritingPrompt(seed.name, seed.content, seed.category, prompt.id))
                    ds.showActionPromptEditDialog = false
                }
            } else null
        }
        PromptEditDialog(
            existing = existingActionPrompt,
            forceCategory = ds.editingActionCategory,
            onRestoreDefault = restoreDefault,
            onDismiss = { ds.showActionPromptEditDialog = false },
            onSave = { name, content, category ->
                viewModel.onIntent(AiChatIntent.SaveWritingPrompt(name, content, category, existingActionPrompt?.id))
                ds.showActionPromptEditDialog = false
            },
        )
    }

    // ---- Delete character confirmation ----
    ds.showDeleteCharacterConfirm?.let { card ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ds.showDeleteCharacterConfirm = null },
            title = {
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(io.legado.app.R.string.ai_delete_character)
                )
            },
            text = {
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(io.legado.app.R.string.ai_delete_character_confirm, card.name)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DeleteCharacterCard(card.id))
                    ds.showDeleteCharacterConfirm = null
                }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.ok),
                        color = colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ds.showDeleteCharacterConfirm = null }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.cancel)
                    )
                }
            },
        )
    }

    // ---- Delete prompt confirmation ----
    ds.showDeletePromptConfirm?.let { prompt ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ds.showDeletePromptConfirm = null },
            title = { androidx.compose.material3.Text("Delete Prompt") },
            text = {
                androidx.compose.material3.Text("Delete prompt \"${prompt.name}\"?")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DeleteWritingPrompt(prompt.id))
                    ds.showDeletePromptConfirm = null
                }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.ok),
                        color = colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ds.showDeletePromptConfirm = null }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.cancel)
                    )
                }
            },
        )
    }

    // ---- World book sheet ----
    if (ds.showWorldBookSheet) {
        io.legado.app.ui.ai.worldbook.AiWorldBookSheet(
            highlightWorldBookId = ds.highlightWorldBookId,
            readOnly = ds.isSheetPreviewMode,
            onDismiss = {
                ds.highlightWorldBookId = null
                ds.showWorldBookSheet = false
            },
        )
    }

    // ---- Memory table sheet ----
    if (ds.showMemoryTableSheet) {
        io.legado.app.ui.ai.table.AiMemoryTableSheet(
            onDismiss = {
                ds.memoryTableHighlightRowId = null
                ds.memoryTableHighlightTableId = null
                ds.memoryTableHighlightMatch = null
                ds.clearSheetTarget()
                ds.showMemoryTableSheet = false
            },
            conversationId = ds.sheetConversationId ?: state.currentConversationId ?: "",
            highlightRowId = ds.memoryTableHighlightRowId,
            highlightTableId = ds.memoryTableHighlightTableId,
            highlightMatch = ds.memoryTableHighlightMatch,
            previewChanges = ds.sheetPreviewChanges,
            readOnly = ds.isSheetPreviewMode,
        )
    }

    // ---- Activity log sheet ----
    if (ds.showExecutionHistorySheet) {
        AiExecutionHistorySheet(
            messages = state.messages,
            streamingMessage = state.streamingMessage,
            conversationId = state.currentConversationId,
            onDismiss = { ds.showExecutionHistorySheet = false },
            onOpenInEditor = { toolName, input ->
                ds.showExecutionHistorySheet = false
                openToolInEditor(
                    ds = ds,
                    toolName = toolName,
                    inputJson = input,
                    conversationId = state.currentConversationId,
                    characterCards = state.characterCards,
                    selectedCharacterCards = state.selectedCharacterCards,
                )
            },
            onUndo = { snapshotId ->
                ds.showExecutionHistorySheet = false
                viewModel.onIntent(AiChatIntent.RestoreSnapshot(snapshotId))
            },
        )
    }

    // ---- Outline sheet ----
    if (ds.showOutlineSheet) {
        val targetConvId = ds.sheetConversationId ?: state.currentConversationId
        val isForeignConv = !targetConvId.isNullOrBlank() && targetConvId != state.currentConversationId
        LaunchedEffect(ds.showOutlineSheet, targetConvId) {
            if (!ds.showOutlineSheet) return@LaunchedEffect
            if (isForeignConv && targetConvId != null) {
                val (content, enabled) = viewModel.getOutlineForConversation(targetConvId)
                val (_, bookName, bookAuthor) = viewModel.getOutlineBookForConversation(targetConvId)
                ds.sheetOutlineContent = content
                ds.sheetOutlineEnabled = enabled
                ds.sheetOutlineBookName = bookName
                ds.sheetOutlineBookAuthor = bookAuthor
            } else {
                ds.sheetOutlineContent = null
                ds.sheetOutlineEnabled = null
                ds.sheetOutlineBookName = null
                ds.sheetOutlineBookAuthor = null
            }
        }
        OutlineSheet(
            outlineContent = ds.sheetOutlineContent ?: state.outlineContent,
            outlineEnabled = ds.sheetOutlineEnabled ?: state.outlineEnabled,
            bookName = ds.sheetOutlineBookName ?: state.outlineBookName,
            bookAuthor = ds.sheetOutlineBookAuthor ?: state.outlineBookAuthor,
            isLoading = state.suggestionsLoading && !isForeignConv,
            generationPreview = state.outlineGenerationPreview,
            generationReasoning = state.outlineGenerationReasoning,
            highlightSection = ds.outlineHighlightSection,
            readOnly = isForeignConv || ds.isSheetPreviewMode,
            readOnlyMessage = when {
                ds.isSheetPreviewMode -> stringResource(R.string.ai_sheet_tool_preview_readonly)
                isForeignConv -> stringResource(R.string.ai_sheet_viewing_other_conversation)
                else -> null
            },
            previewChanges = ds.sheetPreviewChanges,
            writingSubMode = state.writingSubMode,
            branchChoice = state.outlineBranchChoice,
            onDismiss = {
                ds.outlineHighlightSection = null
                ds.clearSheetTarget()
                ds.showOutlineSheet = false
            },
            onSave = { content, enabled ->
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.SaveOutline(content, enabled))
                }
                ds.clearSheetTarget()
                ds.showOutlineSheet = false
            },
            onToggleEnabled = {
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.ToggleOutlineEnabled)
                }
            },
            onGenerate = { outlineKind ->
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.GenerateOutline(outlineKind))
                }
            },
            onSupplement = { content, outlineKind ->
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.SupplementOutline(content, outlineKind))
                }
            },
            onDelete = {
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.DeleteOutline)
                    ds.clearSheetTarget()
                    ds.showOutlineSheet = false
                }
            },
            onCleanupOrphans = {
                viewModel.onIntent(AiChatIntent.CleanupOrphanOutlines)
            },
            onExportJson = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ShowOutlineExportDialog)
            },
            onImportJson = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ShowOutlineImportDialog)
            },
            onImportFromConversation = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ShowOutlineImportFromConversation)
            },
            onExportToConversation = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ShowOutlineExportToConversation)
            },
            onShowVersionHistory = {
                if (!isForeignConv) viewModel.showOutlineVersionHistory()
            },
            onShowBookPicker = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ShowOutlineBookPicker)
            },
            onClearBookSource = {
                if (!isForeignConv) viewModel.onIntent(AiChatIntent.ClearOutlineBookSource)
            },
            onSelectBranch = { optionId ->
                if (!isForeignConv) {
                    viewModel.onIntent(AiChatIntent.SelectOutlineBranch(optionId))
                }
            },
        )
    }

    if (state.showOutlineBookPicker && !ds.isSheetPreviewMode) {
        SourceBookPickerSheet(
            books = state.outlineBookshelfBooks,
            onSelect = { book -> viewModel.onIntent(AiChatIntent.SelectOutlineBook(book)) },
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissOutlineBookPicker) },
        )
    }

    if (state.showOutlineVersionHistory) {
        io.legado.app.ui.ai.outline.OutlineVersionHistorySheet(
            versions = state.outlineVersions,
            currentContent = state.outlineContent,
            onDismiss = { viewModel.dismissOutlineVersionHistory() },
            onRestore = { viewModel.restoreOutlineVersion(it) },
            loadSnapshotContent = { viewModel.loadOutlineSnapshotContent(it) },
        )
    }

    if (state.showOutlineExportDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissOutlineExportDialog) },
            title = { androidx.compose.material3.Text(stringResource(R.string.ai_outline_export_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Text(
                        stringResource(R.string.ai_outline_export_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.outlineExportJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                io.legado.app.ui.common.compose.TooltipIconButton(
                    onClick = { viewModel.onIntent(AiChatIntent.CopyOutlineExport) },
                    label = "复制",
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.TextButton(onClick = onSaveOutlineExportFile) {
                        androidx.compose.material3.Text(stringResource(R.string.ai_outline_save_file))
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.onIntent(AiChatIntent.ShareOutlineExport)
                    }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.share)) }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.onIntent(AiChatIntent.DismissOutlineExportDialog)
                    }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
                }
            },
        )
    }

    if (state.showOutlineImportDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissOutlineImportDialog) },
            title = { androidx.compose.material3.Text(stringResource(R.string.ai_outline_import_dialog_title)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Text(
                        stringResource(R.string.ai_outline_import_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.outlineImportJson,
                        onValueChange = { viewModel.onIntent(AiChatIntent.UpdateOutlineImportJson(it)) },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        placeholder = { androidx.compose.material3.Text("Paste JSON here...") },
                    )
                    androidx.compose.material3.TextButton(onClick = onOpenOutlineImportFile) {
                        androidx.compose.material3.Text(stringResource(R.string.ai_outline_open_file))
                    }
                }
            },
            confirmButton = {
                io.legado.app.ui.common.compose.TooltipIconButton(
                    onClick = { viewModel.onIntent(AiChatIntent.ConfirmOutlineImport) },
                    label = stringResource(R.string.ai_outline_import_json),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DismissOutlineImportDialog)
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
            },
        )
    }

    if (state.showOutlineConversationPicker) {
        val isImport = state.outlineConversationPickerMode == "import"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissOutlineConversationPicker) },
            title = {
                androidx.compose.material3.Text(
                    stringResource(
                        if (isImport) R.string.ai_outline_picker_import_title
                        else R.string.ai_outline_picker_export_title
                    )
                )
            },
            text = {
                Column(modifier = Modifier.height(360.dp)) {
                    if (state.outlinePickerConversations.isEmpty()) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Text(
                                stringResource(R.string.ai_outline_picker_empty),
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        androidx.compose.material3.Text(
                            stringResource(
                                if (isImport) R.string.ai_outline_picker_import_hint
                                else R.string.ai_outline_picker_export_hint
                            ),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                            items(state.outlinePickerConversations, key = { it.id }) { conv ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = colorScheme.surfaceVariant,
                                    onClick = {
                                        viewModel.onIntent(AiChatIntent.SelectConversationForOutlineTransfer(conv.id))
                                    },
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        androidx.compose.material3.Text(conv.title.ifBlank { "Chat ${conv.id.take(8)}" })
                                        androidx.compose.material3.Text(
                                            formatRelativeTime(conv.updatedAt),
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DismissOutlineConversationPicker)
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
            },
        )
    }

    if (state.showOutlineOverwriteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissOutlineOverwriteConfirm) },
            title = { androidx.compose.material3.Text(stringResource(R.string.ai_outline_overwrite_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.ai_outline_overwrite_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.ConfirmOutlineOverwrite)
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DismissOutlineOverwriteConfirm)
                }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
            },
        )
    }

    // ---- Context usage dialog ----
    if (ds.showContextUsageDialog) {
        ContextUsageDialog(
            state = state,
            onDismiss = { ds.showContextUsageDialog = false },
            onCompress = {
                ds.showContextUsageDialog = false
                ds.showCompressConfirm = true
            },
        )
    }

    // ---- Compress context confirmation ----
    if (ds.showCompressConfirm) {
        val cacheHitStr = if (state.cacheHitRatio > 0f) {
            "\n缓存命中率: ${(state.cacheHitRatio * 100).toInt()}%"
        } else ""
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ds.showCompressConfirm = false },
            title = { androidx.compose.material3.Text("压缩上下文") },
            text = {
                val budget = state.contextInputBudget.takeIf { it > 0 } ?: state.contextWindow
                val budgetStr = when {
                    budget >= 1_000_000 -> "${budget / 1_000_000}M"
                    budget >= 1_000 -> "${budget / 1_000}K"
                    else -> budget.toString()
                }
                val usagePercent = if (budget > 0) {
                    (state.contextTokensUsed.toFloat() / budget * 100).toInt().coerceAtMost(100)
                } else 0
                androidx.compose.material3.Text(
                    "上下文已使用 ${state.contextTokensUsed}/$budgetStr tokens（$usagePercent%）。" +
                        cacheHitStr +
                        "\n\nAI 将总结较早的对话历史，为新消息腾出空间。是否继续？"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ds.showCompressConfirm = false
                    viewModel.onIntent(AiChatIntent.CompressContext)
                }) { androidx.compose.material3.Text("压缩") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ds.showCompressConfirm = false }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.cancel)
                    )
                }
            },
        )
    }

    // ---- Delete conversation confirmation ----
    ds.showDeleteConversationConfirm?.let { conv ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ds.showDeleteConversationConfirm = null },
            title = { androidx.compose.material3.Text("删除会话") },
            text = {
                androidx.compose.material3.Text(
                    "确定删除会话「${conv.title.ifBlank { "Chat ${conv.id.take(8)}" }}」？\n\n" +
                        "该会话的消息记录、大纲和记忆表格将被永久删除，且无法恢复。"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.onIntent(AiChatIntent.DeleteConversation(conv.id))
                    ds.showDeleteConversationConfirm = null
                    onConversationDeleted?.invoke()
                }) {
                    androidx.compose.material3.Text(
                        "删除",
                        color = colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ds.showDeleteConversationConfirm = null }) {
                    androidx.compose.material3.Text(
                        androidx.compose.ui.res.stringResource(io.legado.app.R.string.cancel)
                    )
                }
            },
        )
    }

    if (state.showWorkspaceSheet) {
        WorkspaceSheet(
            state = state,
            onDismiss = { viewModel.onIntent(AiChatIntent.DismissWorkspaceSheet) },
            onExport = { viewModel.onIntent(AiChatIntent.ExportWorkspace) },
            onShowClonePicker = { viewModel.onIntent(AiChatIntent.ShowWorkspaceClonePicker) },
            onImportJsonChange = { viewModel.onIntent(AiChatIntent.UpdateWorkspaceImportJson(it)) },
            onImport = { viewModel.onIntent(AiChatIntent.ImportWorkspaceJson) },
            onWorldBookIdsChange = { viewModel.onIntent(AiChatIntent.UpdateWorkspaceWorldBookIds(it)) },
            onStructuredAutoMaintainChange = {
                viewModel.onIntent(AiChatIntent.SetStructuredAutoMaintain(it))
            },
            onShowAdoptPreview = { viewModel.onIntent(AiChatIntent.ShowAdoptPreview) },
            onDismissAdoptPreview = { viewModel.onIntent(AiChatIntent.DismissAdoptPreview) },
            onConfirmAdopt = { viewModel.onIntent(AiChatIntent.ConfirmAdoptToBook) },
            onToggleAdoptType = { viewModel.onIntent(AiChatIntent.ToggleAdoptType(it)) },
            onShowBookPicker = { viewModel.onIntent(AiChatIntent.ShowWorkspaceBookPicker) },
            onSelectBook = { viewModel.onIntent(AiChatIntent.SelectWorkspaceBook(it)) },
            onDismissBookPicker = { viewModel.onIntent(AiChatIntent.DismissWorkspaceBookPicker) },
        )
    }

    if (state.showWorkspaceClonePicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissWorkspaceClonePicker) },
            title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.ai_workspace_clone_title)) },
            text = {
                if (state.workspaceCloneSources.isEmpty()) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.ai_workspace_clone_empty))
                } else {
                    LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        items(state.workspaceCloneSources, key = { it.id }) { conv ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.surfaceVariant,
                                onClick = {
                                    viewModel.onIntent(AiChatIntent.CloneWorkspaceFromConversation(conv.id))
                                },
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    androidx.compose.material3.Text(conv.title.ifBlank { "Chat ${conv.id.take(8)}" })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onIntent(AiChatIntent.DismissWorkspaceClonePicker) },
                ) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                }
            },
        )
    }

    state.pendingSnapshotUndo?.let { pending ->
        val isMemory = pending.resourceType == "memory_table"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiChatIntent.DismissSnapshotUndo) },
            title = {
                androidx.compose.material3.Text(
                    stringResource(
                        if (isMemory) R.string.ai_memory_undo_title
                        else R.string.ai_snapshot_undo_title,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    if (pending.summary.isNotBlank()) {
                        androidx.compose.material3.Text(
                            pending.summary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pending.changes.isNotEmpty()) {
                        androidx.compose.material3.Text(
                            stringResource(R.string.ai_memory_undo_preview_hint),
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        FieldChangeDiffList(
                            changes = pending.changes,
                            maxHeightDp = 280,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onIntent(AiChatIntent.ConfirmSnapshotUndo) },
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.ai_memory_undo_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onIntent(AiChatIntent.DismissSnapshotUndo) },
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
