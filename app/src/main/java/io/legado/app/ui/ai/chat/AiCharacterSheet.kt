package io.legado.app.ui.ai.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.gson.reflect.TypeToken
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.DramaticRole
import io.legado.app.help.ai.CharacterAvatarStore
import io.legado.app.ui.common.compose.legadoSheetInsets
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import io.legado.app.utils.toastOnUi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Character select sheet ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterSelectSheet(
    state: AiChatUiState,
    onDismiss: () -> Unit,
    onSelect: (AiCharacterCardUi) -> Unit,
    onUpdateCurrent: (String?) -> Unit,
    onEdit: (AiCharacterCardUi) -> Unit,
    onDelete: (AiCharacterCardUi) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit = {},
    selectedIds: Set<String> = emptySet(),
    onConfirm: ((Set<String>) -> Unit)? = null,
) {
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme
    var tempSelected by remember(selectedIds) { mutableStateOf(selectedIds) }
    val isMultiSelect = onConfirm != null
    val customLabel = stringResource(R.string.ai_character_category_custom)
    val allLabel = stringResource(R.string.ai_character_filter_all)
    val categories = remember(state.characterCards, customLabel) {
        characterCardCategories(state.characterCards, customLabel)
    }
    var filterKey by remember(categories) {
        mutableStateOf(categories.firstOrNull { it.key == CUSTOM_CATEGORY_KEY }?.key)
    }
    val visibleCategories = remember(categories, filterKey) {
        if (filterKey == null) categories
        else categories.filter { it.key == filterKey }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isMultiSelect && tempSelected.isNotEmpty()) {
                onConfirm.invoke(tempSelected)
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).legadoSheetInsets()) {
            Text(
                stringResource(R.string.ai_select_character),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (categories.size > 1) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = filterKey == null,
                        onClick = { filterKey = null },
                        label = { Text(allLabel) },
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = filterKey == cat.key,
                            onClick = { filterKey = cat.key },
                            label = {
                                Text(
                                    buildString {
                                        append(cat.label)
                                        append(" (")
                                        append(cat.cards.size)
                                        append(")")
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (state.selectedCharacterCard != null && !isMultiSelect) {
                TextButton(
                    onClick = { onUpdateCurrent(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ai_no_character), color = colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                visibleCategories.forEach { category ->
                    item(key = "header_${category.key}") {
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(category.cards, key = { it.id }) { card ->
                        val isChecked = card.id in tempSelected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    if (isMultiSelect) {
                                        tempSelected = if (isChecked) tempSelected - card.id else tempSelected + card.id
                                    } else {
                                        onSelect(card)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked && isMultiSelect) colorScheme.primaryContainer
                                else if (card.id == state.selectedCharacterCard?.id && !isMultiSelect) colorScheme.primaryContainer
                                else colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isMultiSelect) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            tempSelected = if (checked) tempSelected + card.id else tempSelected - card.id
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(card.name, style = MaterialTheme.typography.titleSmall)
                                    if (card.description.isNotBlank()) {
                                        Text(
                                            card.description.take(80),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (card.conversationCount > 0) {
                                        Text(
                                            stringResource(R.string.ai_character_conversations, card.conversationCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { onEdit(card) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(card) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (isMultiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onNew,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_new_character))
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.ai_character_import_st))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            onConfirm.invoke(tempSelected)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = tempSelected.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.ok) + " (${tempSelected.size})")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onNew,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_new_character))
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.ai_character_import_st))
                    }
                }
            }
        }
    }
}

private data class CharacterCardCategory(
    val key: String,
    val label: String,
    val cards: List<AiCharacterCardUi>,
)

/** Category key for character cards not bound to any book. */
private const val CUSTOM_CATEGORY_KEY = "__custom__"

private fun characterCardCategories(
    cards: List<AiCharacterCardUi>,
    customLabel: String,
): List<CharacterCardCategory> {
    val customKey = CUSTOM_CATEGORY_KEY
    val grouped = linkedMapOf<String, Pair<String, MutableList<AiCharacterCardUi>>>()
    cards.forEach { card ->
        val (key, label) = when {
            card.bookUrl.isNotBlank() || card.bookName.isNotBlank() -> {
                val key = card.bookUrl.ifBlank {
                    "name:${card.bookName.trim()}"
                }
                val label = card.bookName.ifBlank { card.bookUrl.take(32) }.ifBlank { card.bookUrl }
                key to label
            }
            else -> customKey to customLabel
        }
        val bucket = grouped.getOrPut(key) { label to mutableListOf() }
        bucket.second.add(card)
    }
    val bookCats = grouped.filterKeys { it != customKey }
        .map { (key, pair) ->
            CharacterCardCategory(
                key = key,
                label = pair.first,
                cards = pair.second.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.label.lowercase() }
    val custom = grouped[customKey]?.let { (_, list) ->
        CharacterCardCategory(
            key = customKey,
            label = customLabel,
            cards = list.sortedBy { it.name.lowercase() },
        )
    }
    return bookCats + listOfNotNull(custom)
}

// ---- Character edit sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditDialog(
    existing: AiCharacterCardUi?,
    availableWorldBooks: List<AiWorldBook> = emptyList(),
    previewChanges: List<FieldChangeUi> = emptyList(),
    readOnly: Boolean = false,
    readOnlyMessage: String? = null,
    /** When true, swipe-away dismiss persists edits; Cancel still calls [onDismiss] only. */
    saveOnDismiss: Boolean = true,
    /** When false (writing mode), hide aliases, voice, and dramatic role fields. */
    showPerformanceFields: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        openingLine: String,
        worldBookIds: String,
        personality: String,
        scenario: String,
        exampleDialogues: String,
        postHistoryInstructions: String,
        alternateOpenings: String,
        aliasesJson: String,
        voiceGender: String,
        voiceAgeBand: String,
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
        dramaticRole: String,
        avatarPath: String,
    ) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var openingLine by remember(existing?.id) { mutableStateOf(existing?.openingLine.orEmpty()) }
    var bookUrl by remember(existing?.id) { mutableStateOf(existing?.bookUrl.orEmpty()) }
    var bookName by remember(existing?.id) { mutableStateOf(existing?.bookName.orEmpty()) }
    var bookAuthor by remember(existing?.id) { mutableStateOf(existing?.bookAuthor.orEmpty()) }
    var personality by remember(existing?.id) { mutableStateOf(existing?.personality.orEmpty()) }
    var scenario by remember(existing?.id) { mutableStateOf(existing?.scenario.orEmpty()) }
    var exampleDialogues by remember(existing?.id) { mutableStateOf(existing?.exampleDialogues.orEmpty()) }
    var postHistoryInstructions by remember(existing?.id) { mutableStateOf(existing?.postHistoryInstructions.orEmpty()) }
    var alternateOpeningsText by remember(existing?.id) {
        mutableStateOf(alternateOpeningsJsonToText(existing?.alternateOpenings.orEmpty()))
    }
    var aliasesText by remember(existing?.id) {
        mutableStateOf(aliasesJsonToText(existing?.aliasesJson.orEmpty()))
    }
    var voiceGender by remember(existing?.id) { mutableStateOf(existing?.voiceGender ?: "unknown") }
    var voiceAgeBand by remember(existing?.id) { mutableStateOf(existing?.voiceAgeBand ?: "unknown") }
    var dramaticRole by remember(existing?.id) { mutableStateOf(existing?.dramaticRole.orEmpty()) }
    var avatarPath by remember(existing?.id) { mutableStateOf(existing?.avatarPath.orEmpty()) }
    var pendingAvatarUri by remember(existing?.id) { mutableStateOf<android.net.Uri?>(null) }
    var selectedWorldBookIds by remember(existing?.id) {
        mutableStateOf(io.legado.app.utils.AiIdListCodec.parse(existing?.worldBookIds).toSet())
    }
    var showBookPicker by remember { mutableStateOf(false) }
    var bookshelfBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    val isNew = existing == null || existing.id.isBlank()
    val sheetState = rememberLegadoBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme
    val fieldMap = fieldChangesByLeaf(previewChanges)
    val useInlineGit = canInlineGitHighlightCharacterFields(previewChanges)
    val showBanner = shouldShowSheetPreviewBanner(previewChanges, useInlineGit)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savingAvatar by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingAvatarUri = uri
            avatarPath = uri.toString()
        }
    }

    fun saveWorldBookIds(): String =
        io.legado.app.utils.AiIdListCodec.toCsv(selectedWorldBookIds)

    fun saveAlternateOpenings(): String =
        alternateOpeningsTextToJson(alternateOpeningsText)

    fun saveAliasesJson(): String =
        aliasesTextToJson(aliasesText)

    fun persistAvatarPath(): String {
        if (!showPerformanceFields) return existing?.avatarPath.orEmpty()
        val pending = pendingAvatarUri
        if (pending != null) {
            val cardId = existing?.id?.takeIf { it.isNotBlank() }
                ?: "charcard_tmp_${System.currentTimeMillis()}"
            return CharacterAvatarStore.copyFromUri(cardId, pending, context)
        }
        // Never persist transient content/http URIs from the picker preview.
        if (avatarPath.startsWith("content:") || avatarPath.startsWith("http")) {
            return existing?.avatarPath.orEmpty()
        }
        return avatarPath
    }

    fun invokeSave() {
        if (savingAvatar) return
        scope.launch {
            val resolvedAvatar = try {
                if (showPerformanceFields && pendingAvatarUri != null) {
                    savingAvatar = true
                    withContext(Dispatchers.IO) { persistAvatarPath() }
                } else {
                    persistAvatarPath()
                }
            } catch (_: Throwable) {
                context.toastOnUi(R.string.ai_character_avatar_save_failed)
                return@launch
            } finally {
                savingAvatar = false
            }
            onSave(
                name, description, openingLine, saveWorldBookIds(), personality, scenario,
                exampleDialogues, postHistoryInstructions, saveAlternateOpenings(),
                if (showPerformanceFields) saveAliasesJson() else (existing?.aliasesJson ?: "[]"),
                if (showPerformanceFields) voiceGender else (existing?.voiceGender ?: "unknown"),
                if (showPerformanceFields) voiceAgeBand else (existing?.voiceAgeBand ?: "unknown"),
                bookUrl, bookName, bookAuthor,
                if (showPerformanceFields) dramaticRole else (existing?.dramaticRole.orEmpty()),
                resolvedAvatar,
            )
        }
    }

    LaunchedEffect(showBookPicker) {
        if (showBookPicker) {
            bookshelfBooks = withContext(Dispatchers.IO) {
                appDb.bookDao.all.sortedByDescending { it.durChapterTime }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            when {
                readOnly -> onDismiss()
                saveOnDismiss -> invokeSave()
                else -> onDismiss()
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
            Text(
                if (isNew) stringResource(R.string.ai_new_character) else stringResource(R.string.ai_edit_character),
                style = MaterialTheme.typography.titleMedium,
            )
            if (showPerformanceFields) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val avatarModel = when {
                        pendingAvatarUri != null -> pendingAvatarUri
                        avatarPath.isNotBlank() -> File(avatarPath).takeIf { it.exists() }
                        else -> null
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.ai_character_avatar),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!readOnly) {
                            TextButton(
                                onClick = {
                                    pickAvatar.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.ai_character_avatar_pick))
                            }
                            if (avatarPath.isNotBlank() || pendingAvatarUri != null) {
                                TextButton(
                                    onClick = {
                                        pendingAvatarUri = null
                                        avatarPath = ""
                                        existing?.id?.takeIf { it.isNotBlank() }?.let {
                                            CharacterAvatarStore.delete(it, context)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.ai_character_avatar_remove))
                                }
                            }
                        }
                    }
                }
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
            if (useInlineGit && fieldMap["name"] != null) {
                GitStyleFieldPreview(stringResource(R.string.ai_character_name), fieldMap["name"])
            }
            OutlinedTextField(
                value = name,
                onValueChange = { if (!readOnly) name = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (useInlineGit && fieldMap["description"] != null) {
                GitStyleFieldPreview(stringResource(R.string.ai_character_description), fieldMap["description"])
            }
            OutlinedTextField(
                value = description,
                onValueChange = { if (!readOnly) description = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_description)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            if (useInlineGit && fieldMap["openingLine"] != null) {
                GitStyleFieldPreview(stringResource(R.string.ai_opening_line), fieldMap["openingLine"])
            }
            OutlinedTextField(
                value = openingLine,
                onValueChange = { if (!readOnly) openingLine = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_opening_line)) },
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = personality,
                onValueChange = { if (!readOnly) personality = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_personality)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = scenario,
                onValueChange = { if (!readOnly) scenario = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_scenario)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = exampleDialogues,
                onValueChange = { if (!readOnly) exampleDialogues = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_example_dialogues)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = postHistoryInstructions,
                onValueChange = { if (!readOnly) postHistoryInstructions = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_post_history)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = alternateOpeningsText,
                onValueChange = { if (!readOnly) alternateOpeningsText = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.ai_character_alternate_openings)) },
                placeholder = { Text(stringResource(R.string.ai_character_alternate_openings_hint)) },
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showPerformanceFields) {
                OutlinedTextField(
                    value = aliasesText,
                    onValueChange = { if (!readOnly) aliasesText = it },
                    readOnly = readOnly,
                    label = { Text(stringResource(R.string.ai_character_aliases)) },
                    placeholder = { Text(stringResource(R.string.ai_character_aliases_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var dramaticRoleExpanded by remember { mutableStateOf(false) }
                val dramaticRoleLabel = when (DramaticRole.normalize(dramaticRole)) {
                    DramaticRole.MALE_LEAD -> stringResource(R.string.dramatic_role_male_lead)
                    DramaticRole.FEMALE_LEAD -> stringResource(R.string.dramatic_role_female_lead)
                    DramaticRole.MALE_SUPPORTING -> stringResource(R.string.dramatic_role_male_supporting)
                    DramaticRole.FEMALE_SUPPORTING -> stringResource(R.string.dramatic_role_female_supporting)
                    else -> stringResource(R.string.ai_not_set)
                }
                ExposedDropdownMenuBox(
                    expanded = dramaticRoleExpanded && !readOnly,
                    onExpandedChange = { if (!readOnly) dramaticRoleExpanded = it },
                ) {
                    OutlinedTextField(
                        value = dramaticRoleLabel,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !readOnly,
                        label = { Text(stringResource(R.string.ai_character_dramatic_role)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dramaticRoleExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = dramaticRoleExpanded && !readOnly,
                        onDismissRequest = { dramaticRoleExpanded = false },
                    ) {
                        listOf(
                            DramaticRole.NONE to R.string.ai_not_set,
                            DramaticRole.MALE_LEAD to R.string.dramatic_role_male_lead,
                            DramaticRole.FEMALE_LEAD to R.string.dramatic_role_female_lead,
                            DramaticRole.MALE_SUPPORTING to R.string.dramatic_role_male_supporting,
                            DramaticRole.FEMALE_SUPPORTING to R.string.dramatic_role_female_supporting,
                        ).forEach { (value, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    dramaticRole = value
                                    dramaticRoleExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = voiceGender,
                    onValueChange = { if (!readOnly) voiceGender = it },
                    readOnly = readOnly,
                    label = { Text(stringResource(R.string.ai_character_voice_gender)) },
                    placeholder = { Text(stringResource(R.string.ai_character_voice_gender_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = voiceAgeBand,
                    onValueChange = { if (!readOnly) voiceAgeBand = it },
                    readOnly = readOnly,
                    label = { Text(stringResource(R.string.ai_character_voice_age_band)) },
                    placeholder = { Text(stringResource(R.string.ai_character_voice_age_band_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SourceBookBindingRow(
                bookName = bookName,
                bookAuthor = bookAuthor,
                readOnly = readOnly,
                onSelectBook = { if (!readOnly) showBookPicker = true },
                onClearBook = {
                    if (!readOnly) {
                        bookUrl = ""
                        bookName = ""
                        bookAuthor = ""
                    }
                },
            )
            // World book selection
            if (availableWorldBooks.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Text(stringResource(R.string.ai_character_bound_world_books), style = MaterialTheme.typography.labelLarge)
                availableWorldBooks.take(if (expanded) availableWorldBooks.size else 3).forEach { wb ->
                    val isSelected = wb.id in selectedWorldBookIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!readOnly) {
                                    Modifier.clickable {
                                        selectedWorldBookIds = if (isSelected) {
                                            selectedWorldBookIds - wb.id
                                        } else {
                                            selectedWorldBookIds + wb.id
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            enabled = !readOnly,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(wb.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                if (availableWorldBooks.size > 3 && !readOnly) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            if (expanded) {
                                stringResource(R.string.ai_show_less)
                            } else {
                                stringResource(R.string.ai_show_all_world_books, availableWorldBooks.size)
                            },
                        )
                    }
                }
            }
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
                        onClick = { invokeSave() },
                        enabled = name.isNotBlank() && !savingAvatar,
                    ) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }

    if (showBookPicker && !readOnly) {
        SourceBookPickerSheet(
            books = bookshelfBooks,
            onSelect = { book ->
                bookUrl = book.bookUrl
                bookName = book.name
                bookAuthor = book.author
                showBookPicker = false
            },
            onDismissRequest = { showBookPicker = false },
        )
    }
}

private val alternateOpeningsListType = object : TypeToken<List<String>>() {}.type

private fun alternateOpeningsJsonToText(json: String): String =
    runCatching {
        GSON.fromJson<List<String>>(json.ifBlank { "[]" }, alternateOpeningsListType).joinToString("\n")
    }.getOrNull().orEmpty()

private fun alternateOpeningsTextToJson(text: String): String {
    val items = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return GSON.toJson(items)
}

private fun aliasesJsonToText(json: String): String =
    runCatching {
        GSON.fromJson<List<String>>(json.ifBlank { "[]" }, alternateOpeningsListType).joinToString(", ")
    }.getOrNull().orEmpty()

private fun aliasesTextToJson(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "[]"
    if (trimmed.startsWith("[")) {
        return runCatching {
            GSON.fromJson<List<String>>(trimmed, alternateOpeningsListType)
            trimmed
        }.getOrElse {
            GSON.toJson(trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }
    }
    return GSON.toJson(trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() })
}
