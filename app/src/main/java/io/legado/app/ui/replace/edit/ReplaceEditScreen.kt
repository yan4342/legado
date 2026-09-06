package io.legado.app.ui.replace.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.TooltipIconButton
import io.legado.app.ui.common.compose.LegadoAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceEditRouteScreen(
    viewModel: ReplaceEditViewModel,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var groupToDelete by remember { mutableStateOf<String?>(null) }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReplaceEditEffect.NavigateBack -> onSaveSuccess()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑替换规则", color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = onSurfaceColor
                        )
                    }
                },
                actions = {
                    TooltipIconButton(
                        onClick = { viewModel.onIntent(ReplaceEditIntent.CopyRule) },
                        label = stringResource(R.string.copy_rule),
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = onSurfaceColor
                        )
                    }
                    TooltipIconButton(
                        onClick = { viewModel.onIntent(ReplaceEditIntent.PasteRule) },
                        label = stringResource(R.string.paste_rule),
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            tint = onSurfaceColor
                        )
                    }
                    TooltipIconButton(
                        onClick = { viewModel.onIntent(ReplaceEditIntent.Save) },
                        label = stringResource(R.string.action_save),
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = onSurfaceColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnNameChange(it)) },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.pattern,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnPatternChange(it)) },
                label = { Text("替换模式") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.replacement,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnReplacementChange(it)) },
                label = { Text("替换为") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.scope,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnScopeChange(it)) },
                label = { Text("作用范围") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.excludeScope,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnExcludeScopeChange(it)) },
                label = { Text("排除范围") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.group,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnGroupChange(it)) },
                label = { Text("分组") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.onIntent(ReplaceEditIntent.ToggleGroupDialog(true)) }) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.group),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("正则表达式", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.isRegex,
                    onCheckedChange = { viewModel.onIntent(ReplaceEditIntent.OnRegexChange(it)) }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("作用于标题", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.scopeTitle,
                    onCheckedChange = { viewModel.onIntent(ReplaceEditIntent.OnScopeTitleChange(it)) }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("作用于正文", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.scopeContent,
                    onCheckedChange = { viewModel.onIntent(ReplaceEditIntent.OnScopeContentChange(it)) }
                )
            }

            OutlinedTextField(
                value = uiState.timeout,
                onValueChange = { viewModel.onIntent(ReplaceEditIntent.OnTimeoutChange(it)) },
                label = { Text("超时时间(ms)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }

    if (uiState.showGroupDialog) {
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { viewModel.onIntent(ReplaceEditIntent.ToggleGroupDialog(false)) },
            dialogTitle = "选择分组",
            dismissText = "关闭",
            onDismiss = { viewModel.onIntent(ReplaceEditIntent.ToggleGroupDialog(false)) },
            content = {
                uiState.allGroups.forEach { group ->
                    val isCurrent = group == uiState.group
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onIntent(ReplaceEditIntent.OnGroupChange(group))
                                viewModel.onIntent(ReplaceEditIntent.ToggleGroupDialog(false))
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (group != "默认") {
                            IconButton(onClick = { groupToDelete = group }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除分组",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    groupToDelete?.let { group ->
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { groupToDelete = null },
            dialogTitle = "删除分组",
            text = "确定删除分组「$group」？该分组下的规则将变为未分组。",
            onConfirm = { viewModel.onIntent(ReplaceEditIntent.DeleteGroups(listOf(group))) },
            onDismiss = { groupToDelete = null },
        )
    }
}
