package io.legado.app.ui.book.info.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.model.DramaticRole
import io.legado.app.domain.usecase.structured.CharacterCardMutator
import io.legado.app.ui.ai.chat.AiCharacterCardUi
import io.legado.app.ui.ai.chat.CharacterEditDialog
import io.legado.app.ui.ai.chat.toUi
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun BookCharacterListRouteScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: BookCharacterListViewModel = koinViewModel { parametersOf(bookUrl) },
) {
    val characterCardGateway: AiCharacterCardGateway = koinInject()
    val characterCardMutator: CharacterCardMutator = koinInject()
    val readAloudCharacterGateway: ReadAloudCharacterGateway = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingCharacter by remember { mutableStateOf<AiCharacterCardUi?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    LegadoTheme {
        BookCharacterListScreen(
            state = state,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = onBack,
            onOpenEdit = { characterId ->
                if (characterId.isNullOrBlank()) {
                    creatingNew = true
                    editingCharacter = AiCharacterCardUi(
                        id = "",
                        name = "",
                        description = "",
                        openingLine = "",
                        bookUrl = state.bookUrl,
                        bookName = state.bookName,
                        bookAuthor = state.bookAuthor,
                    )
                } else {
                    scope.launch(Dispatchers.IO) {
                        val card = characterCardGateway.getById(characterId) ?: return@launch
                        val dramaticRole = readAloudCharacterGateway.getDramaticRole(state.bookUrl, characterId)
                        withContext(Dispatchers.Main) {
                            creatingNew = false
                            editingCharacter = card.toUi(dramaticRole = dramaticRole)
                        }
                    }
                }
            },
        )
    }

    editingCharacter?.let { draft ->
        CharacterEditDialog(
            // Blank id = new card; keeps bookUrl/name/author prefilled from draft.
            existing = draft,
            onDismiss = {
                editingCharacter = null
                creatingNew = false
            },
            onSave = { name, desc, opening, worldBookIds, personality, scenario,
                exampleDialogues, postHistory, alternateOpenings, aliasesJson,
                voiceGender, voiceAgeBand, boundBookUrl, boundBookName, boundBookAuthor, dramaticRole, avatarPath ->
                scope.launch(Dispatchers.IO) {
                    val resolvedBookUrl = boundBookUrl.ifBlank { state.bookUrl }
                    val result = characterCardMutator.applyPatch(
                        CharacterCardPatch(
                            cardId = draft.id.takeIf { it.isNotBlank() },
                            name = name,
                            description = desc,
                            openingLine = opening,
                            worldBookIds = AiIdListCodec.toCsv(worldBookIds),
                            personality = personality,
                            scenario = scenario,
                            exampleDialogues = exampleDialogues,
                            postHistoryInstructions = postHistory,
                            alternateOpenings = alternateOpenings,
                            aliasesJson = aliasesJson,
                            voiceGender = voiceGender,
                            voiceAgeBand = voiceAgeBand,
                            bookUrl = resolvedBookUrl,
                            bookName = boundBookName.ifBlank { state.bookName },
                            bookAuthor = boundBookAuthor.ifBlank { state.bookAuthor },
                            avatarPath = avatarPath,
                        ),
                        // 本书角色列表:新建/编辑的都是正典卡。
                        canonical = true,
                    )
                    if (result.success && creatingNew && result.cardId.isNotBlank()) {
                        readAloudCharacterGateway.addToCast(state.bookUrl, result.cardId)
                    }
                    if (result.success) {
                        val cardId = result.cardId.ifBlank { draft.id }
                        if (cardId.isNotBlank() && state.bookUrl.isNotBlank()) {
                            // Cast role is scoped to this book's cast list.
                            readAloudCharacterGateway.updateDramaticRole(
                                bookUrl = state.bookUrl,
                                characterCardId = cardId,
                                dramaticRole = dramaticRole,
                            )
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (!result.success) {
                            context.toastOnUi(result.message)
                        } else {
                            editingCharacter = null
                            creatingNew = false
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCharacterListScreen(
    state: BookCharacterListUiState,
    onIntent: (BookCharacterListIntent) -> Unit,
    effects: Flow<BookCharacterListEffect>,
    onBack: () -> Unit,
    onOpenEdit: (characterId: String?) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookCharacterListEffect.OpenCharacterEdit -> onOpenEdit(effect.characterId)
                is BookCharacterListEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_characters)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(BookCharacterListIntent.OpenAiIdentify) }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.identify_book_characters),
                        )
                    }
                    IconButton(onClick = { onIntent(BookCharacterListIntent.Refresh) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(BookCharacterListIntent.AddCharacter) },
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_character),
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading && state.characters.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> CharacterListContent(
                state = state,
                onIntent = onIntent,
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    CharacterIdentifySheet(
        sheet = state.identifySheet,
        onIntent = onIntent,
    )

    state.deleteConfirm?.let { target ->
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookCharacterListIntent.DismissDeleteConfirm) },
            dialogTitle = stringResource(R.string.ai_delete_character),
            content = {
                Text(stringResource(R.string.ai_delete_character_confirm, target.name))
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = { onIntent(BookCharacterListIntent.ConfirmDeleteCharacter) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(BookCharacterListIntent.DismissDeleteConfirm) },
        )
    }
}

@Composable
private fun CharacterListContent(
    state: BookCharacterListUiState,
    onIntent: (BookCharacterListIntent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.characters.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.character_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onIntent(BookCharacterListIntent.OpenAiIdentify) },
                    ) {
                        Text(stringResource(R.string.ai_identify_characters_start))
                    }
                }
            }
        }
        items(
            items = state.characters,
            key = { it.id },
        ) { character ->
            CharacterListItem(
                character = character,
                onClick = { onIntent(BookCharacterListIntent.OpenCharacter(character.id)) },
                onDelete = { onIntent(BookCharacterListIntent.RequestDeleteCharacter(character.id)) },
            )
        }
    }
}

@Composable
private fun CharacterListItem(
    character: BookCharacterListItemUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val roleLabelRes = DramaticRole.labelRes(character.dramaticRole)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatarFile = character.avatarPath.takeIf { it.isNotBlank() }
                        ?.let { java.io.File(it) }
                        ?.takeIf { it.exists() }
                    if (avatarFile != null) {
                        coil3.compose.AsyncImage(
                            model = coil3.request.ImageRequest.Builder(context)
                                .data(avatarFile)
                                .build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (roleLabelRes != null) {
                            Text(
                                text = stringResource(roleLabelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (character.summary.isNotBlank()) {
                        Text(
                            text = character.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.ai_delete_character),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterIdentifySheet(
    sheet: CharacterIdentifySheetUi?,
    onIntent: (BookCharacterListIntent) -> Unit,
) {
    if (sheet == null) return
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(BookCharacterListIntent.DismissAiIdentify) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.identify_book_characters),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                sheet.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    if (sheet.reasoning.isNotBlank()) {
                        Text(
                            text = sheet.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
                sheet.error != null -> {
                    Text(
                        text = sheet.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(
                        onClick = { onIntent(BookCharacterListIntent.RunAiIdentify) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
                sheet.candidates.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.ai_identify_characters_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onIntent(BookCharacterListIntent.RunAiIdentify) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ai_identify_characters_start))
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = sheet.candidates,
                            key = CharacterIdentifyCandidateUi::id,
                        ) { candidate ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onIntent(BookCharacterListIntent.ToggleAiCandidate(candidate.id))
                                    },
                                supportingContent = if (candidate.summary.isNotBlank()) {
                                    {
                                        Text(
                                            text = candidate.summary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else null,
                                trailingContent = {
                                    Checkbox(
                                        checked = candidate.selected,
                                        onCheckedChange = {
                                            onIntent(
                                                BookCharacterListIntent.ToggleAiCandidate(candidate.id),
                                            )
                                        },
                                    )
                                },
                            ) {
                                Text(candidate.name)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onIntent(BookCharacterListIntent.RunAiIdentify) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        FilledTonalButton(
                            onClick = { onIntent(BookCharacterListIntent.SaveAiCandidates) },
                            modifier = Modifier.weight(1f),
                            enabled = !sheet.loading,
                        ) {
                            Text(stringResource(R.string.ai_identify_characters_save))
                        }
                    }
                }
            }
        }
    }
}
