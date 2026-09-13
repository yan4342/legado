package io.legado.app.ui.config.ai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelEditScreen(
    providerId: String,
    modelProfileId: String?,
    aiProfileGateway: AiProfileGateway,
    aiTextGateway: AiTextGateway,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isNew = modelProfileId == null

    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    var modelName by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var contextWindow by remember { mutableStateOf("") }
    var maxOutputTokens by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(1.0f) }
    var defaultParamsJson by remember { mutableStateOf("") }
    var capabilities by remember { mutableStateOf<Set<String>>(emptySet()) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(modelProfileId) {
        if (modelProfileId != null) {
            val model = aiProfileGateway.getModel(modelProfileId)
            if (model != null) {
                modelName = model.displayName
                modelId = model.modelId
                contextWindow = model.contextWindow.takeIf { it > 0 }?.let { formatContextWindow(it) }.orEmpty()
                maxOutputTokens = model.maxOutputTokens.takeIf { it > 0 }?.toString().orEmpty()
                val params = runCatching {
                    model.defaultParamsJson?.let { GSON.fromJson(it, AiGenerationParams::class.java) }
                }.getOrNull()
                temperature = params?.temperature ?: 1.0f
                defaultParamsJson = model.defaultParamsJson.orEmpty()
                capabilities = model.capabilities.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        } else {
            defaultParamsJson = """{"temperature": 1.0}"""
        }
        loaded = true
    }

    val maxOutputTokensInt = maxOutputTokens.toIntOrNull() ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) stringResource(R.string.ai_new_model)
                        else stringResource(R.string.ai_edit_model)
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
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.ai_model_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("GPT-4.1 mini") },
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text(stringResource(R.string.ai_model_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("gpt-4.1-mini") },
                )

                OutlinedTextField(
                    value = contextWindow,
                    onValueChange = { contextWindow = it },
                    label = { Text(stringResource(R.string.ai_context_window)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("128000 (supports 128k, 1m)") },
                    supportingText = {
                        val parsed = parseContextWindow(contextWindow)
                        if (parsed > 0 && parsed.toString() != contextWindow) {
                            Text("= $parsed tokens")
                        }
                    },
                )

                OutlinedTextField(
                    value = maxOutputTokens,
                    onValueChange = { maxOutputTokens = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.ai_max_output_tokens)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("16384") },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ai_native_web_search),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.ai_native_web_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = AiCapability.WEB_SEARCH in capabilities,
                        onCheckedChange = { checked ->
                            capabilities = if (checked) {
                                capabilities + AiCapability.WEB_SEARCH
                            } else {
                                capabilities - AiCapability.WEB_SEARCH
                            }
                        },
                    )
                }

                Text(
                    text = "${stringResource(R.string.temperature)}: ${"%.1f".format(temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = (it * 10 + 0.5f).toInt() / 10f },
                    valueRange = 0f..2f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.ai_default_params_json),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                val fullRequestJson = remember(modelId, temperature, maxOutputTokens, defaultParamsJson) {
                    buildRequestJson(modelId, temperature, maxOutputTokens, defaultParamsJson)
                }

                var editableJson by remember { mutableStateOf(fullRequestJson) }
                LaunchedEffect(fullRequestJson) {
                    if (editableJson.isBlank() || editableJson == fullRequestJson.take(editableJson.length)) {
                        // keep user edits; only replace if the auto-generated version changed meaningfully
                    } else {
                        editableJson = fullRequestJson
                    }
                }

                OutlinedTextField(
                    value = editableJson,
                    onValueChange = {
                        editableJson = it
                        defaultParamsJson = it
                    },
                    label = { Text("Request JSON (editable)") },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp,
                    ),
                )

                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val provider = aiProfileGateway.getProvider(providerId)
                            if (provider == null) {
                                testResult = Pair(false, "Provider not found")
                                testing = false
                                return@launch
                            }
                            val cfg = AiProviderConfig(
                                id = provider.id, name = provider.name,
                                protocol = provider.protocol, baseUrl = provider.baseUrl,
                                apiKey = aiProfileGateway.getProviderApiKey(providerId),
                                modelsUrl = provider.modelsUrl,
                                chatPath = provider.chatPath ?: "/chat/completions",
                                responsesPath = provider.responsesPath ?: "/responses",
                                messagesPath = provider.messagesPath ?: "/v1/messages",
                                modelsPath = provider.modelsPath,
                            )
                            aiTextGateway.fetchModels(cfg)
                                .onSuccess { m -> testResult = Pair(true, "OK, ${m.size} models available") }
                                .onFailure { e -> testResult = Pair(false, e.message ?: "Failed") }
                            testing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing,
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
                                    aiProfileGateway.saveModel(
                                        AiModelDraft(
                                            modelProfileId = modelProfileId, providerId = providerId,
                                            modelName = modelName.ifBlank { modelId }, modelId = modelId,
                                            contextWindow = parseContextWindow(contextWindow),
                                            maxOutputTokens = maxOutputTokens.toIntOrNull() ?: 0,
                                            temperature = temperature,
                                            defaultParamsJson = editableJson.takeIf { it.isNotBlank() },
                                            capabilities = capabilities,
                                        )
                                    )
                                    onSaved()
                                } catch (_: Exception) { }
                                saving = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !saving && modelName.isNotBlank() && modelId.isNotBlank(),
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

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    testResult?.let { (ok, msg) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(if (ok) "Connection OK" else "Connection Failed") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { testResult = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

private fun buildRequestJson(modelId: String, temperature: Float, maxOutputTokens: String, existingJson: String): String {
    if (existingJson.isNotBlank()) {
        val parsed = runCatching { GSON.fromJson(existingJson, Map::class.java) }.getOrNull()
        if (parsed != null && parsed.isNotEmpty()) {
            return GSON.toJson(sortedMapOf<String, Any>().apply {
                putAll(parsed.mapKeys { it.key.toString() })
            }.also { map ->
                // Ensure core fields are synced from form
                if (modelId.isNotBlank()) map["model"] = modelId
                map["temperature"] = Math.round(temperature * 10.0) / 10.0
                val tokens = maxOutputTokens.toIntOrNull()
                if (tokens != null && tokens > 0) map["max_tokens"] = tokens
            })
        }
    }
    val tokens = maxOutputTokens.toIntOrNull()
    val json = linkedMapOf<String, Any>()
    if (modelId.isNotBlank()) json["model"] = modelId
    json["temperature"] = Math.round(temperature * 10.0) / 10.0
    if (tokens != null && tokens > 0) json["max_tokens"] = tokens
    return GSON.toJson(json)
}

private fun parseContextWindow(input: String): Int {
    val trimmed = input.trim().lowercase()
    if (trimmed.isEmpty()) return 0
    val multiplier = when {
        trimmed.endsWith("m") -> 1_000_000
        trimmed.endsWith("k") -> 1_000
        else -> 1
    }
    val numberPart = if (multiplier > 1) trimmed.dropLast(1).trim() else trimmed
    return (numberPart.toDoubleOrNull()?.toInt() ?: trimmed.toIntOrNull() ?: 0) * multiplier
}

private fun formatContextWindow(value: Int): String {
    return when {
        value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}m"
        value >= 1_000 && value % 1_000 == 0 -> "${value / 1_000}k"
        else -> value.toString()
    }
}
