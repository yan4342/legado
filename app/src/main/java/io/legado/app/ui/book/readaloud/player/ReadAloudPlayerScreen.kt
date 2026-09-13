package io.legado.app.ui.book.readaloud.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.common.compose.LegadoTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ReadAloudPlayerRouteScreen(
    onBack: () -> Unit,
    onNavigateToCasting: (() -> Unit)? = null,
    onNavigateToCloudTts: (() -> Unit)? = null,
    viewModel: ReadAloudPlayerViewModel = koinViewModel(),
) {
    LegadoTheme {
        LaunchedEffect(viewModel.effects) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    ReadAloudPlayerEffect.OpenToc,
                    ReadAloudPlayerEffect.ReturnToClassic,
                    ReadAloudPlayerEffect.ReturnToReaderSettings -> Unit
                }
            }
        }
        ReadAloudPlayerScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            onNavigateToCasting = onNavigateToCasting,
            onNavigateToCloudTts = onNavigateToCloudTts,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudPlayerScreen(
    state: ReadAloudPlayerUiState,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    onBack: () -> Unit,
    onNavigateToCasting: (() -> Unit)? = null,
    onNavigateToCloudTts: (() -> Unit)? = null,
) {
    var speedPreview by remember(state.speed) { mutableFloatStateOf(state.speed.toFloat()) }
    var timerPreview by remember(state.timerMinutes) {
        mutableFloatStateOf(state.timerMinutes.toFloat())
    }
    var showSpeedSlider by remember { mutableStateOf(false) }
    var showTimerSlider by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.bookName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.chapterTitle.isNotBlank()) {
                            Text(
                                text = state.chapterTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (onNavigateToCasting != null) {
                        IconButton(onClick = onNavigateToCasting) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        }
                    }
                    if (onNavigateToCloudTts != null) {
                        IconButton(onClick = onNavigateToCloudTts) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { onIntent(ReadAloudPlayerIntent.OpenSettings) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.setting))
                    }
                },
            )
        },
        bottomBar = {
            PlayerControls(
                state = state,
                onIntent = onIntent,
                speedPreview = speedPreview,
                timerPreview = timerPreview,
                showSpeedSlider = showSpeedSlider,
                showTimerSlider = showTimerSlider,
                onSpeedPreviewChange = { speedPreview = it },
                onTimerPreviewChange = { timerPreview = it },
                onToggleSpeedSlider = { showSpeedSlider = !showSpeedSlider },
                onToggleTimerSlider = { showTimerSlider = !showTimerSlider },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.currentText.ifBlank {
                        stringResource(R.string.read_aloud_preparing_content)
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.nextText.isNotBlank()) {
                Text(
                    text = state.nextText,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.read_aloud_speaker_engine,
                    state.speakerName.ifBlank { stringResource(R.string.voice_role_narrator) },
                    state.engineName.ifBlank { stringResource(R.string.read_aloud_default_tts) },
                ),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPosition(state.chapterPosition),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChapterTextList(
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ChapterTextList(
    state: ReadAloudPlayerUiState,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeTextLine) {
        if (state.activeTextLine in state.textLines.indices) {
            listState.animateScrollToItem(state.activeTextLine)
        }
    }
    if (state.textLines.isEmpty()) {
        Text(
            text = stringResource(R.string.read_aloud_preparing_content),
            modifier = modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = state.textLines,
            key = { _, line -> line.chapterPosition },
        ) { index, line ->
            val active = index == state.activeTextLine
            Text(
                text = line.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .clickable { onIntent(ReadAloudPlayerIntent.SeekTo(line.chapterPosition)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = if (active) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: ReadAloudPlayerUiState,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    speedPreview: Float,
    timerPreview: Float,
    showSpeedSlider: Boolean,
    showTimerSlider: Boolean,
    onSpeedPreviewChange: (Float) -> Unit,
    onTimerPreviewChange: (Float) -> Unit,
    onToggleSpeedSlider: () -> Unit,
    onToggleTimerSlider: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Slider(
            value = state.chapterPosition.coerceIn(0, state.chapterLength).toFloat(),
            onValueChange = { onIntent(ReadAloudPlayerIntent.SeekTo(it.toInt())) },
            valueRange = 0f..state.chapterLength.coerceAtLeast(1).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPosition(state.chapterPosition),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = if (state.timerMinutes > 0) {
                    stringResource(R.string.set_timer) + ": ${state.timerMinutes}"
                } else {
                    "${(state.chapterPosition * 100 / state.chapterLength.coerceAtLeast(1))}%"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onIntent(ReadAloudPlayerIntent.PreviousParagraph) }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.prev_sentence))
            }
            IconButton(onClick = { onIntent(ReadAloudPlayerIntent.PreviousChapter) }) {
                Icon(Icons.Default.FastRewind, contentDescription = stringResource(R.string.previous_chapter))
            }
            IconButton(onClick = { onIntent(ReadAloudPlayerIntent.TogglePause) }) {
                Icon(
                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (state.isPaused) R.string.resume else R.string.pause),
                )
            }
            IconButton(onClick = { onIntent(ReadAloudPlayerIntent.NextChapter) }) {
                Icon(Icons.Default.FastForward, contentDescription = stringResource(R.string.next_chapter))
            }
            IconButton(onClick = { onIntent(ReadAloudPlayerIntent.NextParagraph) }) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_sentence))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { onIntent(ReadAloudPlayerIntent.OpenToc) }) {
                Text(stringResource(R.string.chapter_list))
            }
            TextButton(onClick = { onIntent(ReadAloudPlayerIntent.SwitchToClassic) }) {
                Text(stringResource(R.string.switch_to_classic_read_aloud))
            }
            TextButton(onClick = onToggleSpeedSlider) {
                Text(stringResource(R.string.read_aloud_adjust_speed))
            }
            TextButton(onClick = onToggleTimerSlider) {
                Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.set_timer))
            }
        }
        if (showSpeedSlider) {
            Slider(
                value = speedPreview.coerceIn(5f, 20f),
                onValueChange = onSpeedPreviewChange,
                onValueChangeFinished = {
                    onIntent(ReadAloudPlayerIntent.SetSpeed(speedPreview.roundToInt()))
                },
                valueRange = 5f..20f,
                steps = 14,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showTimerSlider) {
            Slider(
                value = timerPreview.coerceIn(0f, 180f),
                onValueChange = { onTimerPreviewChange((it / 10f).roundToInt() * 10f) },
                onValueChangeFinished = {
                    onIntent(ReadAloudPlayerIntent.SetTimer(timerPreview.roundToInt()))
                },
                valueRange = 0f..180f,
                steps = 17,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun formatPosition(value: Int): String = if (value < 1000) {
    stringResource(R.string.read_aloud_position_chars, value)
} else {
    stringResource(R.string.read_aloud_position_kchars, value / 1000f)
}
