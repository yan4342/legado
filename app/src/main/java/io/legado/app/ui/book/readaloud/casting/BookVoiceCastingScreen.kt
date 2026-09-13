package io.legado.app.ui.book.readaloud.casting

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookVoiceCastingRouteScreen(
    bookUrl: String,
    onBack: () -> Unit,
    onManageCloudTts: () -> Unit = {},
    viewModel: BookVoiceCastingViewModel = koinViewModel { parametersOf(bookUrl) },
) {
    LegadoTheme {
        BookVoiceCastingScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = onBack,
            onManageCloudTts = onManageCloudTts,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookVoiceCastingScreen(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    effects: Flow<BookVoiceCastingEffect>,
    onBack: () -> Unit,
    onManageCloudTts: () -> Unit,
) {
    val context = LocalContext.current

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookVoiceCastingEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_voice_casting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(BookVoiceCastingIntent.OpenAiIdentify) }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.identify_book_characters),
                        )
                    }
                    IconButton(onClick = onManageCloudTts) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(R.string.read_aloud_engines_and_voices),
                        )
                    }
                    IconButton(
                        onClick = { onIntent(BookVoiceCastingIntent.SyncVoices) },
                        enabled = !state.isSyncingVoices,
                    ) {
                        if (state.isSyncingVoices) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.sync_voices),
                            )
                        }
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
    ) { paddingValues ->
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> VoiceCastingList(
                state = state,
                onIntent = onIntent,
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    VoicePickerSheet(
        picker = state.picker,
        voices = state.voices,
        onIntent = onIntent,
    )
    CharacterIdentifySheet(
        sheet = state.identifySheet,
        onIntent = onIntent,
    )
}

@Composable
private fun VoiceCastingList(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val specialItems = state.items.filter { it.kind != CastingSubjectKind.Character }
    val characters = state.items.filter { it.kind == CastingSubjectKind.Character }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(contentType = "intro") {
            Text(
                text = stringResource(R.string.book_voice_casting_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item(contentType = "section") {
            Text(
                text = stringResource(R.string.voice_fallback_roles),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(
            items = specialItems,
            key = { "${it.subjectType}:${it.subjectId}" },
            contentType = { "casting" },
        ) { item ->
            VoiceCastingCard(item = item, onIntent = onIntent)
        }
        item(contentType = "section") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.book_characters),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { onIntent(BookVoiceCastingIntent.OpenAiIdentify) }) {
                    Text(stringResource(R.string.identify_book_characters))
                }
            }
        }
        if (characters.isEmpty()) {
            item(contentType = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.character_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onIntent(BookVoiceCastingIntent.OpenAiIdentify) },
                    ) {
                        Text(stringResource(R.string.ai_identify_characters_start))
                    }
                }
            }
        } else {
            items(
                items = characters,
                key = { "${it.subjectType}:${it.subjectId}" },
                contentType = { "casting" },
            ) { item ->
                VoiceCastingCard(item = item, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun VoiceCastingCard(
    item: VoiceCastingItemUi,
    onIntent: (BookVoiceCastingIntent) -> Unit,
) {
    val title = subjectTitle(item.kind, item.name)
    val voiceText = when {
        !item.hasBinding -> stringResource(R.string.voice_not_assigned)
        item.voiceAvailable -> item.voiceName
        item.voiceName.isNotBlank() -> stringResource(R.string.voice_unavailable_named, item.voiceName)
        else -> stringResource(R.string.voice_unavailable)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onIntent(
                    BookVoiceCastingIntent.OpenVoicePicker(
                        subjectType = item.subjectType,
                        subjectId = item.subjectId,
                    )
                )
            },
    ) {
        ListItem(
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = voiceText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.hasBinding && !item.voiceAvailable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = when (item.kind) {
                        CastingSubjectKind.Narrator -> Icons.AutoMirrored.Filled.MenuBook
                        CastingSubjectKind.Character -> Icons.Default.Person
                        else -> Icons.Default.RecordVoiceOver
                    },
                    contentDescription = null,
                )
            },
            trailingContent = if (item.hasBinding && !item.voiceAvailable) {
                {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.voice_unavailable),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else null,
        ) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    picker: VoicePickerUi?,
    voices: ImmutableList<VoiceOptionUi>,
    onIntent: (BookVoiceCastingIntent) -> Unit,
) {
    if (picker == null) return
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(BookVoiceCastingIntent.DismissVoicePicker) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = subjectTitle(picker.kind, picker.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (voices.none { it.selectable }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.no_available_voices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { onIntent(BookVoiceCastingIntent.SyncVoices) }) {
                        Text(stringResource(R.string.sync_voices))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = voices,
                        key = VoiceOptionUi::id,
                    ) { voice ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = voice.selectable) {
                                    onIntent(BookVoiceCastingIntent.AssignVoice(voice.id))
                                },
                            supportingContent = { Text(voiceDescription(voice)) },
                            trailingContent = if (picker.selectedVoiceId == voice.id) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else null,
                        ) {
                            Text(voice.name)
                        }
                    }
                }
            }
            if (picker.selectedVoiceId != null) {
                TextButton(
                    onClick = { onIntent(BookVoiceCastingIntent.ClearBinding) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.clear_voice_binding),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterIdentifySheet(
    sheet: CharacterIdentifySheetUi?,
    onIntent: (BookVoiceCastingIntent) -> Unit,
) {
    if (sheet == null) return
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(BookVoiceCastingIntent.DismissAiIdentify) },
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
                        onClick = { onIntent(BookVoiceCastingIntent.RunAiIdentify) },
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
                        onClick = { onIntent(BookVoiceCastingIntent.RunAiIdentify) },
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
                                        onIntent(BookVoiceCastingIntent.ToggleAiCandidate(candidate.id))
                                    },
                                supportingContent = if (candidate.summary.isNotBlank()) {
                                    {
                                        Text(
                                            text = candidate.summary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                } else null,
                                leadingContent = {
                                    Checkbox(
                                        checked = candidate.selected,
                                        onCheckedChange = {
                                            onIntent(BookVoiceCastingIntent.ToggleAiCandidate(candidate.id))
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
                        TextButton(
                            onClick = { onIntent(BookVoiceCastingIntent.RunAiIdentify) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        FilledTonalButton(
                            onClick = { onIntent(BookVoiceCastingIntent.SaveAiCandidates) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.ai_identify_characters_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun subjectTitle(kind: CastingSubjectKind, name: String): String = when (kind) {
    CastingSubjectKind.Narrator -> stringResource(R.string.voice_role_narrator)
    CastingSubjectKind.UnknownMale -> stringResource(R.string.voice_role_unknown_male)
    CastingSubjectKind.UnknownFemale -> stringResource(R.string.voice_role_unknown_female)
    CastingSubjectKind.Unknown -> stringResource(R.string.voice_role_unknown)
    CastingSubjectKind.Character -> name
}

@Composable
private fun voiceDescription(voice: VoiceOptionUi): String {
    val engineType = when (voice.engineType) {
        ReadAloudVoice.ENGINE_SYSTEM -> stringResource(R.string.system_tts)
        ReadAloudVoice.ENGINE_HTTP -> stringResource(R.string.http_tts)
        ReadAloudVoice.ENGINE_CLOUD -> stringResource(R.string.cloud_tts_engine)
        else -> voice.engineType
    }
    return if (voice.selectable) {
        listOf(engineType, voice.engineName).filter(String::isNotBlank).joinToString(" · ")
    } else {
        stringResource(R.string.voice_unavailable_named, engineType)
    }
}
