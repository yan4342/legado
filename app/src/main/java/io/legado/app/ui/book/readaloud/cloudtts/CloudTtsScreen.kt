package io.legado.app.ui.book.readaloud.cloudtts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun CloudTtsRouteScreen(
    onBack: () -> Unit,
    viewModel: CloudTtsViewModel = koinViewModel(),
) {
    LegadoTheme {
        CloudTtsScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudTtsScreen(
    state: CloudTtsUiState,
    onIntent: (CloudTtsIntent) -> Unit,
    effects: Flow<CloudTtsEffect>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is CloudTtsEffect.ShowToast -> context.toastOnUi(effect.message)
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
                title = { Text(stringResource(R.string.read_aloud_engines_and_voices)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(CloudTtsIntent.SyncVoices) },
                        enabled = !state.syncing,
                    ) {
                        if (state.syncing) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = stringResource(R.string.cloud_tts_sync_voices),
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
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(CloudTtsIntent.AddEngine) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cloud_tts_add_engine))
            }
        },
    ) { paddingValues ->
        when {
            state.loading && state.engines.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.engines.isEmpty() -> {
                Text(
                    text = stringResource(R.string.cloud_tts_no_engines),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 88.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.cloud_tts_cloud_engines),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(state.engines, key = { it.id }) { engine ->
                        CloudTtsEngineRow(
                            engine = engine,
                            onEdit = { onIntent(CloudTtsIntent.EditEngine(engine.id)) },
                            onDelete = { onIntent(CloudTtsIntent.DeleteEngine(engine.id)) },
                            onExport = { onIntent(CloudTtsIntent.OpenExportPicker(engine.id)) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        EngineEditorDialog(editor = editor, onIntent = onIntent)
    }
    state.exportPicker?.let { picker ->
        ExportPickerDialog(
            picker = picker,
            exporting = state.exporting,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun CloudTtsEngineRow(
    engine: CloudTtsEngineItemUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        supportingContent = {
            Text(
                if (engine.exportable) {
                    engine.summary
                } else {
                    "${engine.summary} · ${stringResource(R.string.cloud_tts_export_use_native)}"
                }
            )
        },
        trailingContent = {
            Row {
                if (engine.exportable) {
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.cloud_tts_export_http),
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) {
        Text(engine.title)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineEditorDialog(
    editor: CloudTtsEngineEditorUi,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    var name by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.name) }
    var provider by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.provider) }
    var baseUrl by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.baseUrl) }
    var apiKey by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.apiKey) }
    var secretKey by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.secretKey) }
    var model by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.model) }
    var region by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.region) }
    var appId by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.appId) }
    var optionsJson by remember(editor.editingEngineId, editor.jsonMode) {
        mutableStateOf(editor.optionsJson)
    }
    var enabled by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.enabled) }
    var rawJson by remember(editor.editingEngineId, editor.jsonMode) { mutableStateOf(editor.rawJson) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    val providerType = CloudTtsProviderType.entries
        .firstOrNull { it.storageValue == provider }
    val providerLabel = providerType?.profile?.displayName ?: provider
    val profile = providerType?.profile

    // Keep local state in sync when ViewModel regenerates JSON / applies parse.
    LaunchedEffect(editor) {
        if (editor.jsonMode) {
            rawJson = editor.rawJson
        } else {
            name = editor.name
            provider = editor.provider
            baseUrl = editor.baseUrl
            apiKey = editor.apiKey
            secretKey = editor.secretKey
            model = editor.model
            region = editor.region
            appId = editor.appId
            optionsJson = editor.optionsJson
            enabled = editor.enabled
        }
    }

    fun currentEditor(): CloudTtsEngineEditorUi = if (editor.jsonMode) {
        editor.copy(rawJson = rawJson)
    } else {
        editor.copy(
            name = name,
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            secretKey = secretKey,
            model = model,
            region = region,
            appId = appId,
            optionsJson = optionsJson,
            enabled = enabled,
        )
    }

    AlertDialog(
        onDismissRequest = { onIntent(CloudTtsIntent.DismissEditor) },
        title = {
            Text(
                stringResource(
                    if (editor.editingEngineId == null) R.string.cloud_tts_add_engine
                    else R.string.cloud_tts_edit_engine
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onIntent(CloudTtsIntent.UpdateEditor(currentEditor()))
                            onIntent(CloudTtsIntent.ToggleEditorJsonMode)
                        },
                    ) {
                        Text(
                            stringResource(
                                if (editor.jsonMode) R.string.cloud_tts_edit_form
                                else R.string.cloud_tts_edit_json
                            )
                        )
                    }
                    if (editor.jsonMode) {
                        TextButton(
                            onClick = {
                                onIntent(CloudTtsIntent.UpdateEditor(currentEditor()))
                                onIntent(CloudTtsIntent.FormatEditorJson)
                            },
                        ) {
                            Text(stringResource(R.string.cloud_tts_format_json))
                        }
                    }
                }
                if (editor.jsonMode) {
                    Text(
                        text = stringResource(R.string.cloud_tts_json_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = rawJson,
                        onValueChange = { rawJson = it },
                        label = { Text(stringResource(R.string.cloud_tts_engine_json)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                        minLines = 14,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = providerMenuExpanded,
                        onExpandedChange = { providerMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = providerLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.cloud_tts_select_provider)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false },
                        ) {
                            CloudTtsProviderType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.profile.displayName) },
                                    onClick = {
                                        provider = option.storageValue
                                        if (name.isBlank() || name == providerLabel) {
                                            name = option.profile.displayName
                                        }
                                        if (model.isBlank() || model == profile?.defaultModel) {
                                            model = option.profile.defaultModel
                                        }
                                        providerMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.cloud_tts_engine_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = {
                            Text(profile?.modelLabel ?: stringResource(R.string.cloud_tts_model))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            val options = profile?.modelOptions.orEmpty()
                            if (options.isNotEmpty()) {
                                Text(options.joinToString(" / "))
                            }
                        },
                    )
                    if (profile?.regionOptions?.isNotEmpty() == true ||
                        providerType == CloudTtsProviderType.AzureSpeech ||
                        providerType == CloudTtsProviderType.AwsPolly
                    ) {
                        OutlinedTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text(stringResource(R.string.cloud_tts_region)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (providerType == CloudTtsProviderType.Volcengine ||
                        providerType == CloudTtsProviderType.AwsPolly
                    ) {
                        OutlinedTextField(
                            value = appId,
                            onValueChange = { appId = it },
                            label = { Text(stringResource(R.string.cloud_tts_app_id)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.cloud_tts_custom_base_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(profile?.apiKeyLabel ?: "API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (providerType == CloudTtsProviderType.AwsPolly ||
                        secretKey.isNotBlank()
                    ) {
                        OutlinedTextField(
                            value = secretKey,
                            onValueChange = { secretKey = it },
                            label = { Text(stringResource(R.string.cloud_tts_secret_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = optionsJson,
                        onValueChange = { optionsJson = it },
                        label = { Text(stringResource(R.string.cloud_tts_options_json)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        minLines = 3,
                        supportingText = {
                            Text(stringResource(R.string.cloud_tts_options_json_hint))
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                        Text(stringResource(R.string.cloud_tts_engine_enabled))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onIntent(CloudTtsIntent.UpdateEditor(currentEditor()))
                    onIntent(CloudTtsIntent.Save)
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(CloudTtsIntent.DismissEditor) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ExportPickerDialog(
    picker: CloudTtsExportPickerUi,
    exporting: Boolean,
    onIntent: (CloudTtsIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(CloudTtsIntent.DismissExportPicker) },
        title = { Text(stringResource(R.string.cloud_tts_export_http)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.cloud_tts_export_select_voice, picker.engineName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    picker.loadingVoices -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    picker.voices.isEmpty() -> Text(
                        stringResource(R.string.cloud_tts_export_default_voice),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> picker.voices.forEach { voice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onIntent(CloudTtsIntent.ToggleExportVoice(voice.id))
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = voice.id in picker.selectedVoiceIds,
                                onCheckedChange = {
                                    onIntent(CloudTtsIntent.ToggleExportVoice(voice.id))
                                },
                            )
                            Text(voice.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(CloudTtsIntent.ConfirmExport) },
                enabled = !exporting && !picker.loadingVoices,
            ) {
                Text(stringResource(R.string.cloud_tts_export_http))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(CloudTtsIntent.DismissExportPicker) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
