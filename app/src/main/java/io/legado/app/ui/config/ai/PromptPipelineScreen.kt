package io.legado.app.ui.config.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import androidx.compose.ui.platform.LocalContext
import io.legado.app.domain.prompt.PromptBlockPosition
import io.legado.app.domain.prompt.PromptBlockSpec
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.utils.sendToClip
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPipelineScreen(
    onBack: () -> Unit,
    viewModel: PromptPipelineViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }

    val onSurfaceColor = if (AppConfig.isEInkMode) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val containerColor = if (AppConfig.isEInkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primary
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PromptPipelineEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ai_prompt_pipeline),
                        color = onSurfaceColor,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.requestBack(onBack) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = onSurfaceColor,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onIntent(PromptPipelineIntent.Save) },
                        enabled = state.hasUnsavedChanges,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = stringResource(R.string.action_save),
                            tint = onSurfaceColor,
                            modifier = Modifier.alpha(if (state.hasUnsavedChanges) 1f else 0.45f),
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                tint = onSurfaceColor,
                            )
                        }
                        RoundDropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) { dismiss ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.restore_default),
                                onClick = {
                                    dismiss()
                                    viewModel.onIntent(PromptPipelineIntent.ResetToDefault)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.export),
                                onClick = {
                                    dismiss()
                                    viewModel.onIntent(PromptPipelineIntent.ShowExport)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.import_action),
                                onClick = {
                                    dismiss()
                                    viewModel.onIntent(PromptPipelineIntent.ShowImport)
                                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text(
                        stringResource(R.string.ai_pipeline_switch_relation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.requestBack(onBack) }) {
                        Text(stringResource(R.string.ai_prompt_templates_title))
                    }
                }
            }
            item {
                CategorySection(title = stringResource(R.string.ai_pipeline_presets_section)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.presets.forEach { preset ->
                            val selected = preset.id == state.selectedPresetId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onIntent(PromptPipelineIntent.SelectPreset(preset.id)) },
                                label = {
                                    Column {
                                        Text(
                                            promptPresetTitle(preset),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            promptModeLabel(preset.mode),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                val enabledCount = state.blocks.count { it.enabled }
                CategorySection(
                    title = stringResource(R.string.ai_pipeline_blocks_section),
                ) {
                    Text(
                        stringResource(
                            R.string.ai_pipeline_blocks_summary,
                            enabledCount,
                            state.blocks.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    state.blocks.forEachIndexed { index, block ->
                        if (index > 0) HorizontalDivider()
                        PipelineBlockRow(
                            block = block,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.blocks.lastIndex,
                            onToggle = { viewModel.onIntent(PromptPipelineIntent.ToggleBlock(block.id)) },
                            onMoveUp = { viewModel.onIntent(PromptPipelineIntent.MoveBlock(block.id, up = true)) },
                            onMoveDown = { viewModel.onIntent(PromptPipelineIntent.MoveBlock(block.id, up = false)) },
                            onPosition = {
                                viewModel.onIntent(PromptPipelineIntent.UpdateBlockPosition(block.id, it))
                            },
                            onDepth = {
                                viewModel.onIntent(PromptPipelineIntent.UpdateBlockDepth(block.id, it))
                            },
                            onMaxTokens = { viewModel.onIntent(PromptPipelineIntent.UpdateMaxTokens(block.id, it)) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (state.showExportDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PromptPipelineIntent.DismissExport) },
            title = { Text(stringResource(R.string.export)) },
            text = {
                OutlinedTextField(
                    value = state.exportJson,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.sendToClip(state.exportJson)
                    viewModel.onIntent(PromptPipelineIntent.DismissExport)
                }) {
                    Text(stringResource(R.string.ai_pipeline_copy_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.DismissExport) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
    if (state.showImportDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PromptPipelineIntent.DismissImport) },
            title = { Text(stringResource(R.string.import_action)) },
            text = {
                OutlinedTextField(
                    value = state.importJson,
                    onValueChange = { viewModel.onIntent(PromptPipelineIntent.UpdateImportJson(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.ConfirmImport(false)) }) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.DismissImport) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PromptPipelineIntent.DismissDiscard) },
            title = { Text(stringResource(R.string.exit_no_save)) },
            text = { Text(stringResource(R.string.ai_pipeline_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.ConfirmDiscard) }) {
                    Text(stringResource(R.string.ai_pipeline_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.DismissDiscard) }) {
                    Text(stringResource(R.string.ai_pipeline_keep_editing))
                }
            },
        )
    }
    if (state.showPrefetchEnableDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PromptPipelineIntent.DismissPrefetchEnableDialog) },
            title = { Text(stringResource(R.string.ai_pipeline_prefetch_enable_title)) },
            text = { Text(stringResource(R.string.ai_pipeline_prefetch_enable_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.ConfirmEnablePrefetch) }) {
                    Text(stringResource(R.string.ai_pipeline_prefetch_enable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PromptPipelineIntent.DismissPrefetchEnableDialog) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PipelineBlockRow(
    block: PromptBlockSpec,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPosition: (PromptBlockPosition) -> Unit,
    onDepth: (Int) -> Unit,
    onMaxTokens: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .alpha(if (block.enabled) 1f else 0.55f),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    promptBlockTitle(block.id),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    promptBlockDescription(block.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val sourceInfo = promptBlockSourceInfo(block.id)
                Text(
                    promptBlockSourceKindLabel(sourceInfo.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ai_pipeline_source_label, promptBlockSourceText(block.id)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                promptBlockGateText(block.id)?.let { gate ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.ai_pipeline_gate_label, gate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Switch(checked = block.enabled, onCheckedChange = { onToggle() })
        }
        if (block.enabled) {
            var depthText by remember(block.id) { mutableStateOf(block.depth.toString()) }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ai_pipeline_position_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = block.position == PromptBlockPosition.Prefix,
                    onClick = { onPosition(PromptBlockPosition.Prefix) },
                    label = { Text(stringResource(R.string.ai_pipeline_position_prefix)) },
                )
                FilterChip(
                    selected = block.position == PromptBlockPosition.InChat,
                    onClick = { onPosition(PromptBlockPosition.InChat) },
                    label = { Text(stringResource(R.string.ai_pipeline_position_in_chat_short)) },
                )
            }
            if (block.position == PromptBlockPosition.InChat) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = depthText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }
                        depthText = filtered
                        filtered.toIntOrNull()?.let(onDepth)
                    },
                    label = { Text(stringResource(R.string.ai_pipeline_depth)) },
                    supportingText = {
                        Text(stringResource(R.string.ai_pipeline_depth_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.alpha(if (canMoveUp) 1f else 0.35f),
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.alpha(if (canMoveDown) 1f else 0.35f),
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = if (block.maxTokens == 0) "" else block.maxTokens.toString(),
                    onValueChange = { onMaxTokens(it.toIntOrNull() ?: 0) },
                    label = { Text(stringResource(R.string.ai_pipeline_max_tokens)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
    }
}
