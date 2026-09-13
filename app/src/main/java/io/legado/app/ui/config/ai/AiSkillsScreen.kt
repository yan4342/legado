package io.legado.app.ui.config.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.AiSkill
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSkillsScreen(
    viewModel: AiSkillsViewModel,
    onBack: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (skillId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val skillImportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
        }.getOrNull()?.let { text ->
            viewModel.onIntent(AiSkillsIntent.UpdateSkillImportText(text))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiSkillsEffect.ShowMessage -> {
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
                title = { Text(stringResource(R.string.ai_skills_page_title), color = onSurfaceColor) },
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
        val chatSkills = remember(state.skills) {
            state.skills.filter { it.mode == AiSkill.MODE_CHAT || it.mode == AiSkill.MODE_BOTH }
        }
        val writingSkills = remember(state.skills) {
            state.skills.filter { it.mode == AiSkill.MODE_WRITING || it.mode == AiSkill.MODE_BOTH }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategorySection(stringResource(R.string.ai_skills_section)) {
                    Text(
                        stringResource(R.string.ai_skills_page_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onNavigateToCreate) {
                            Text(stringResource(R.string.ai_skills_create))
                        }
                        TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.OpenSkillImport) }) {
                            Text(stringResource(R.string.ai_skills_import))
                        }
                    }
                    Text(
                        stringResource(R.string.ai_skills_proxy_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    Text(
                        stringResource(R.string.ai_skills_proxy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = state.downloadProxy,
                        onValueChange = { viewModel.onIntent(AiSkillsIntent.UpdateDownloadProxy(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.ai_skills_proxy_label)) },
                        placeholder = { Text(stringResource(R.string.ai_skills_proxy_placeholder)) },
                    )
                    TextButton(
                        onClick = { viewModel.onIntent(AiSkillsIntent.SaveDownloadProxy) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.ai_skills_proxy_save))
                    }
                    if (state.skills.isEmpty()) {
                        Text(
                            stringResource(R.string.ai_skills_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    if (chatSkills.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ai_skills_group_chat),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        chatSkills.forEachIndexed { index, skill ->
                            SkillRow(
                                skill = skill,
                                canMoveUp = index > 0,
                                canMoveDown = index < chatSkills.lastIndex,
                                onIntent = viewModel::onIntent,
                                onOpen = { onNavigateToEdit(skill.skillId) },
                            )
                        }
                    }
                    if (writingSkills.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ai_skills_group_writing),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        writingSkills.forEachIndexed { index, skill ->
                            SkillRow(
                                skill = skill,
                                canMoveUp = index > 0,
                                canMoveDown = index < writingSkills.lastIndex,
                                onIntent = viewModel::onIntent,
                                onOpen = { onNavigateToEdit(skill.skillId) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (state.showSkillImport) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiSkillsIntent.CloseSkillImport) },
            title = { Text(stringResource(R.string.ai_skills_import)) },
            text = {
                Column(
                    modifier = Modifier
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.ai_skills_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        skillImportFileLauncher.launch(arrayOf("text/*", "text/markdown", "*/*"))
                    }) {
                        Text(stringResource(R.string.ai_skills_import_from_file))
                    }
                    OutlinedTextField(
                        value = state.skillImportText,
                        onValueChange = { viewModel.onIntent(AiSkillsIntent.UpdateSkillImportText(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        minLines = 6,
                        maxLines = 16,
                        label = { Text(stringResource(R.string.ai_skills_import_paste)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.ConfirmSkillImport) }) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.CloseSkillImport) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (state.showSkillImportOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiSkillsIntent.DismissSkillImportOverwrite) },
            title = { Text(stringResource(R.string.ai_skills_import_overwrite_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_skills_import_overwrite_message,
                        state.skillImportConflictIds.joinToString(", "),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.ConfirmSkillImportOverwrite) }) {
                    Text(stringResource(R.string.ai_skills_import_overwrite_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.DismissSkillImportOverwrite) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (state.showSkillExport) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(AiSkillsIntent.CloseSkillExport) },
            title = { Text(stringResource(R.string.ai_skills_export_title)) },
            text = {
                OutlinedTextField(
                    value = state.skillExportText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.sendToClip(state.skillExportText)
                    viewModel.onIntent(AiSkillsIntent.CloseSkillExport)
                }) {
                    Text(stringResource(R.string.ai_pipeline_copy_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(AiSkillsIntent.CloseSkillExport) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun SkillRow(
    skill: AiSkill,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onIntent: (AiSkillsIntent) -> Unit,
    onOpen: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(end = 12.dp),
            ) {
                Text(skill.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    skill.description.ifBlank { skill.hint }.ifBlank { skill.skillId },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.ai_skills_mode_label, skill.mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(
                checked = skill.enabled,
                onCheckedChange = {
                    onIntent(AiSkillsIntent.SetSkillEnabled(skill.skillId, it))
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onIntent(AiSkillsIntent.MoveSkill(skill.skillId, -1)) },
                enabled = canMoveUp,
                modifier = Modifier.alpha(if (canMoveUp) 1f else 0.35f),
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null)
            }
            IconButton(
                onClick = { onIntent(AiSkillsIntent.MoveSkill(skill.skillId, 1)) },
                enabled = canMoveDown,
                modifier = Modifier.alpha(if (canMoveDown) 1f else 0.35f),
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null)
            }
            IconButton(onClick = { onIntent(AiSkillsIntent.ExportSkill(skill.skillId)) }) {
                Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.export))
            }
            IconButton(onClick = { onIntent(AiSkillsIntent.DeleteSkill(skill.skillId)) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
