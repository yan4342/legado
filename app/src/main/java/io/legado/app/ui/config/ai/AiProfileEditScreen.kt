package io.legado.app.ui.config.ai

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderPresets
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.launch

/**
 * Derive /v1/models URL from a base URL, stripping chat/responses/messages suffixes.
 * Matches the logic in [io.legado.app.ui.dict.rule.ai.AiDictRuleEditViewModel.buildModelsUrl].
 */
private fun buildModelsUrl(baseUrl: String): String {
    val base = baseUrl.trimEnd('/')
        .removeSuffix("/v1/chat/completions")
        .removeSuffix("/chat/completions")
        .removeSuffix("/v1/responses")
        .removeSuffix("/responses")
        .removeSuffix("/v1/messages")
        .removeSuffix("/messages")
        .removeSuffix("/v1")
        .trimEnd('/')
    return "$base/v1/models"
}

@Suppress("UNCHECKED_CAST")
private fun parseOpenAiModelsResponse(body: String): List<AiAvailableModel> {
    val json = GSON.fromJsonObject<Map<String, Any>>(body).getOrNull() ?: return emptyList()
    val data = json["data"] as? List<Map<String, Any>> ?: return emptyList()
    return data.mapNotNull { item ->
        val id = item["id"] as? String ?: return@mapNotNull null
        AiAvailableModel(
            id = id,
            name = (item["name"] as? String) ?: id,
            contextWindow = (item["context_window"] as? Number)?.toInt() ?: 0,
            maxOutputTokens = (item["max_output_tokens"] as? Number)?.toInt() ?: 0
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProfileEditScreen(
    providerId: String?,
    aiProfileGateway: AiProfileGateway,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onNavigateToModelEdit: (providerId: String, modelProfileId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = providerId == null

    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Provider fields
    var providerName by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(AiProtocol.OPENAI_CHAT_COMPLETIONS) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelsUrl by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // Inline model fields (only for new providers)
    var inlineModelName by remember { mutableStateOf("") }
    var inlineModelId by remember { mutableStateOf("") }

    // Protocol dropdown
    var protocolExpanded by remember { mutableStateOf(false) }
    val protocolOptions = listOf(
        AiProtocol.OPENAI_CHAT_COMPLETIONS to "OpenAI Chat Completions",
        AiProtocol.OPENAI_RESPONSES to "OpenAI Responses",
        AiProtocol.ANTHROPIC_MESSAGES to "Anthropic Messages",
    )
    val selectedProtocolLabel = protocolOptions.find { it.first == protocol }?.second ?: protocol

    val filteredPresets = AiProviderPresets.items.filter { it.protocol == protocol }

    // Model list (for existing providers)
    var providerModels by remember { mutableStateOf<List<AiModelProfile>>(emptyList()) }

    // Fetch models
    var fetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<AiAvailableModel>?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    // Test connection
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Load existing data
    LaunchedEffect(providerId) {
        if (providerId != null) {
            val provider = aiProfileGateway.getProvider(providerId)
            if (provider != null) {
                providerName = provider.name
                protocol = provider.protocol
                baseUrl = provider.baseUrl
                apiKey = provider.apiKey
                modelsUrl = provider.modelsUrl.orEmpty()
            }
        }
        loaded = true
    }

    // Observe models for existing providers
    LaunchedEffect(providerId) {
        if (providerId != null) {
            aiProfileGateway.observeModels().collect { models ->
                providerModels = models.filter { it.providerId == providerId }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) stringResource(R.string.ai_new_provider)
                        else stringResource(R.string.ai_provider_edit)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        if (!loaded) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // === Provider fields ===
                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text(stringResource(R.string.ai_provider_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("OpenAI") },
                )

                // Protocol selector
                Box {
                    OutlinedTextField(
                        value = selectedProtocolLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_protocol)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { protocolExpanded = !protocolExpanded }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        enabled = isNew,
                    )
                    DropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false },
                    ) {
                        protocolOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { protocol = value; protocolExpanded = false },
                            )
                        }
                    }
                }

                // Provider presets
                if (isNew && filteredPresets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_provider_preset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filteredPresets.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    providerName = preset.name
                                    protocol = preset.protocol
                                    baseUrl = preset.baseUrl
                                    modelsUrl = preset.modelsUrl
                                    inlineModelName = preset.modelName
                                    inlineModelId = preset.modelId
                                },
                                label = { Text("${preset.name} (${preset.modelName})") },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_base_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://api.openai.com/v1") },
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (apiKeyVisible) "Hide" else "Show",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { apiKeyVisible = !apiKeyVisible }.padding(8.dp),
                        )
                    },
                )

                OutlinedTextField(
                    value = modelsUrl,
                    onValueChange = { modelsUrl = it },
                    label = { Text(stringResource(R.string.ai_models_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ai_models_url_summary)) },
                )

                // === Inline model for new provider ===
                if (isNew) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "${stringResource(R.string.ai_model_database)} (${stringResource(R.string.ai_new_model)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = inlineModelName,
                        onValueChange = { inlineModelName = it },
                        label = { Text(stringResource(R.string.ai_model_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("GPT-4.1 mini") },
                    )
                    OutlinedTextField(
                        value = inlineModelId,
                        onValueChange = { inlineModelId = it },
                        label = { Text(stringResource(R.string.ai_model_id)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("gpt-4.1-mini") },
                        trailingIcon = {
                            if (fetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                            fetchingModels = true
                                            scope.launch {
                                                try {
                                                    val url = modelsUrl.ifBlank { buildModelsUrl(baseUrl) }
                                                    val response = okHttpClient.newCallStrResponse {
                                                        url(url)
                                                        addHeader("Authorization", "Bearer $apiKey")
                                                    }
                                                    if (!response.isSuccessful()) {
                                                        syncMessage = "HTTP ${response.code()}: ${response.message()}\nURL: $url"
                                                        fetchedModels = emptyList()
                                                    } else {
                                                        fetchedModels = parseOpenAiModelsResponse(response.body.orEmpty())
                                                            .ifEmpty { listOf(AiAvailableModel(id = "__empty__")) }
                                                    }
                                                } catch (e: Exception) {
                                                    syncMessage = e.message ?: "Failed to fetch models"
                                                    fetchedModels = emptyList()
                                                }
                                                fetchingModels = false
                                            }
                                        }
                                    },
                                    enabled = baseUrl.isNotBlank() && apiKey.isNotBlank(),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Fetch models", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                    )
                }

                // === Model list (for existing providers) ===
                if (!isNew) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stringResource(R.string.ai_model_database)} (${providerModels.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                        fetchingModels = true
                                        scope.launch {
                                            try {
                                                val url = modelsUrl.ifBlank { buildModelsUrl(baseUrl) }
                                                val response = okHttpClient.newCallStrResponse {
                                                    url(url)
                                                    addHeader("Authorization", "Bearer $apiKey")
                                                }
                                                if (!response.isSuccessful()) {
                                                    syncMessage = "HTTP ${response.code()}: ${response.message()}\nURL: $url"
                                                    fetchedModels = emptyList()
                                                } else {
                                                    fetchedModels = parseOpenAiModelsResponse(response.body.orEmpty())
                                                        .filter { m -> providerModels.none { it.modelId == m.id } }
                                                        .ifEmpty { listOf(AiAvailableModel(id = "__empty__")) }
                                                }
                                            } catch (e: Exception) {
                                                syncMessage = e.message ?: "Failed to fetch models"
                                                fetchedModels = emptyList()
                                            }
                                            fetchingModels = false
                                        }
                                    }
                                },
                                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank(),
                            ) {
                                if (fetchingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Fetch models", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { onNavigateToModelEdit(providerId, null) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_new_model), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (providerModels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_model_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    providerModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToModelEdit(providerId, model.id) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(model.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (model.contextWindow > 0 || model.maxOutputTokens > 0) {
                                Text(
                                    buildString {
                                        if (model.contextWindow > 0) append("${model.contextWindow / 1000}K")
                                        if (model.maxOutputTokens > 0) {
                                            if (isNotEmpty()) append(" / ")
                                            append("${model.maxOutputTokens / 1000}K out")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onNavigateToModelEdit(providerId, model.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }

                    // Sync models from API (save → fetch → import all)
                    OutlinedButton(
                        onClick = {
                            if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                fetchingModels = true
                                scope.launch {
                                    try {
                                        // Save provider first to persist apiKey and get valid ID
                                        val saved = aiProfileGateway.saveProvider(
                                            AiProviderDraft(
                                                providerId = providerId, providerName = providerName,
                                                protocol = protocol, baseUrl = baseUrl,
                                                modelsUrl = modelsUrl.takeIf { it.isNotBlank() }, apiKey = apiKey,
                                            )
                                        )
                                        // Direct fetch like AiDictRuleEditScreen
                                        val url = modelsUrl.ifBlank { buildModelsUrl(baseUrl) }
                                        val response = okHttpClient.newCallStrResponse {
                                            url(url)
                                            addHeader("Authorization", "Bearer $apiKey")
                                        }
                                        if (!response.isSuccessful()) {
                                            throw Exception("HTTP ${response.code()}: ${response.message()}\nURL: $url")
                                        }
                                        val models = parseOpenAiModelsResponse(response.body.orEmpty())
                                        if (models.isNotEmpty()) {
                                            aiProfileGateway.importProviderModels(saved.id, models)
                                        }
                                        syncMessage = "Imported ${models.size} models"
                                    } catch (e: Exception) {
                                        syncMessage = e.message ?: "Failed to sync models"
                                    }
                                    fetchingModels = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !fetchingModels && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                    ) {
                        if (fetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.ai_fetch_models))
                    }
                }

                // Test connection
                OutlinedButton(
                    onClick = {
                        if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                            testing = true
                            testResult = null
                            scope.launch {
                                try {
                                    val url = modelsUrl.ifBlank { buildModelsUrl(baseUrl) }
                                    val response = okHttpClient.newCallStrResponse {
                                        url(url)
                                        addHeader("Authorization", "Bearer $apiKey")
                                    }
                                    if (!response.isSuccessful()) {
                                        throw Exception("HTTP ${response.code()}: ${response.message()}\nURL: $url")
                                    }
                                    val models = parseOpenAiModelsResponse(response.body.orEmpty())
                                    testResult = Pair(true, "OK, ${models.size} models available")
                                } catch (e: Exception) {
                                    testResult = Pair(false, e.message ?: "Failed")
                                }
                                testing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Text(stringResource(R.string.ai_test_connection))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Save / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    val savedProvider = aiProfileGateway.saveProvider(
                                        AiProviderDraft(
                                            providerId = providerId, providerName = providerName,
                                            protocol = protocol, baseUrl = baseUrl,
                                            modelsUrl = modelsUrl.takeIf { it.isNotBlank() }, apiKey = apiKey,
                                        )
                                    )
                                    if (isNew && inlineModelId.isNotBlank()) {
                                        aiProfileGateway.saveModel(
                                            AiModelDraft(
                                                providerId = savedProvider.id,
                                                modelName = inlineModelName.ifBlank { inlineModelId },
                                                modelId = inlineModelId,
                                            )
                                        )
                                    }
                                    onSaved()
                                } catch (_: Exception) { }
                                saving = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !saving && providerName.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }

                // Delete
                if (!isNew) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                aiProfileGateway.deleteProvider(providerId)
                                onDeleted()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Model selection dialog
    fetchedModels?.let { models ->
        if (models.isEmpty() || models.any { it.id == "__empty__" }) {
            AlertDialog(
                onDismissRequest = { fetchedModels = null },
                title = { Text("Fetch Models") },
                text = { Text(if (isNew) "No models found. Check API URL and key." else "No new models found. All available models are already added.") },
                confirmButton = { TextButton(onClick = { fetchedModels = null }) { Text(stringResource(R.string.ok)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { fetchedModels = null },
                title = { Text("Select Model (${models.size})") },
                text = {
                    LazyColumn {
                        items(models) { m ->
                            TextButton(
                                onClick = {
                                    if (isNew) {
                                        inlineModelId = m.id; inlineModelName = m.name.ifBlank { m.id }
                                    } else {
                                        scope.launch {
                                            aiProfileGateway.saveModel(
                                                AiModelDraft(
                                                    providerId = providerId,
                                                    modelName = m.name.ifBlank { m.id },
                                                    modelId = m.id,
                                                    contextWindow = m.contextWindow,
                                                    maxOutputTokens = m.maxOutputTokens,
                                                )
                                            )
                                        }
                                    }
                                    fetchedModels = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Text(
                                        m.name,
                                        color = if (m.id == inlineModelId) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        m.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (m.contextWindow > 0 || m.maxOutputTokens > 0) {
                                        Text(
                                            buildString {
                                                if (m.contextWindow > 0) append("ctx: ${m.contextWindow / 1000}K")
                                                if (m.maxOutputTokens > 0) {
                                                    if (isNotEmpty()) append(" / ")
                                                    append("out: ${m.maxOutputTokens / 1000}K")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { fetchedModels = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    // Sync result / error toast
    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            syncMessage = null
        }
    }

    // Test result dialog
    testResult?.let { (ok, msg) ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(if (ok) "Connection OK" else "Connection Failed") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
