package io.legado.app.ui.dict.rule.ai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.AiDictRule
import io.legado.app.ui.dict.DictPromptPresets
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDictRuleEditScreen(
    editName: String?,
    editViewModel: AiDictRuleEditViewModel,
    saving: Boolean,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onSaveRequested: () -> Unit,
) {
    val isNew = editName == null

    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("deepseek-v4-flash") }
    var systemPrompt by remember { mutableStateOf("") }
    var userPromptTemplate by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var maxTokens by remember { mutableStateOf("512") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var fetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var extraJson by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(Unit) {
        editViewModel.initData(editName) {
            val r = editViewModel.aiDictRule!!
            name = r.name
            endpoint = r.endpoint
            apiKey = r.apiKey
            model = r.model.ifBlank { "deepseek-v4-flash" }
            systemPrompt = r.systemPrompt
            userPromptTemplate = r.userPromptTemplate
            temperature = r.temperature
            maxTokens = r.maxTokens.toString()
            extraJson = r.extraJson
            loaded = true
        }
    }

    val maxTokensInt = maxTokens.toIntOrNull() ?: 512

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) stringResource(R.string.add_ai_dict_rule)
                        else stringResource(R.string.edit_ai_dict_rule)
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(stringResource(R.string.endpoint)) },
                    placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (apiKeyVisible) "隐藏" else "显示",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { apiKeyVisible = !apiKeyVisible }
                                .padding(8.dp),
                        )
                    },
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.model)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (fetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (endpoint.isNotBlank()) {
                                        fetchingModels = true
                                        editViewModel.fetchModels(endpoint, apiKey) { models ->
                                            fetchingModels = false
                                            fetchedModels = models
                                        }
                                    }
                                },
                                enabled = endpoint.isNotBlank(),
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "获取模型列表",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.system_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                Text(
                    text = stringResource(R.string.prompt_template_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DictPromptPresets.filter { it.key != "default" }.forEach { preset ->
                        AssistChip(
                            onClick = {
                                systemPrompt = preset.systemPrompt.orEmpty()
                                userPromptTemplate = preset.userPromptTemplate.orEmpty()
                            },
                            label = { Text(preset.displayName) },
                        )
                    }
                }

                OutlinedTextField(
                    value = userPromptTemplate,
                    onValueChange = { userPromptTemplate = it },
                    label = { Text(stringResource(R.string.user_prompt_template)) },
                    supportingText = {
                        Text("可用变量: {{word}} {{bookName}} {{chapterIndex}} {{chapterTitle}} {{earlierMentions}} {{bookMentions}}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                Text(
                    text = "${stringResource(R.string.temperature)}: ${"%.1f".format(temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = temperature,
                    onValueChange = { newValue ->
                                    temperature = (newValue * 10 + 0.5f).toInt() / 10f
                                    },
                    valueRange = 0f..2f,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.max_tokens)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                // 测试连接按钮
                OutlinedButton(
                    onClick = {
                        if (endpoint.isNotBlank()) {
                            testing = true
                            testResult = null
                            editViewModel.testConnection(
                                endpoint = endpoint,
                                apiKey = apiKey,
                                model = model,
                                systemPrompt = systemPrompt,
                                userPromptTemplate = userPromptTemplate,
                                temperature = temperature,
                                maxTokens = maxTokensInt,
                                extraJson = extraJson,
                            ) { ok, msg ->
                                testing = false
                                testResult = Pair(ok, msg)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && endpoint.isNotBlank(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("测试中...")
                    } else {
                        Text("测试连接")
                    }
                }

                // 完整请求体 JSON 预览
                Text(
                    text = "完整请求 JSON",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val requestBodyPreview = remember(
                    model, systemPrompt, userPromptTemplate, temperature, maxTokensInt, extraJson
                ) {
                    buildRequestBodyPreview(model, systemPrompt, userPromptTemplate, temperature, maxTokensInt, extraJson)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = requestBodyPreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = extraJson,
                    onValueChange = { extraJson = it },
                    label = { Text("额外参数 (JSON, 合并到请求体)") },
                    placeholder = { Text("{\"top_p\": 0.9, \"frequency_penalty\": 0.5}") },
                    supportingText = {
                        Text(
                            "可选: top_p, frequency_penalty, presence_penalty, stop, seed, " +
                                "stream, logit_bias, user, response_format 等。会覆盖上方同名字段。"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onSaveRequested()
                            editViewModel.save(
                                AiDictRule(
                                    name = name,
                                    endpoint = endpoint,
                                    apiKey = apiKey,
                                    model = model,
                                    systemPrompt = systemPrompt,
                                    userPromptTemplate = userPromptTemplate,
                                    temperature = temperature,
                                    maxTokens = maxTokensInt,
                                    enabled = editViewModel.aiDictRule?.enabled ?: true,
                                    sortNumber = editViewModel.aiDictRule?.sortNumber ?: 0,
                                    extraJson = extraJson,
                                )
                            ) { onSaved() }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !saving && name.isNotBlank() && endpoint.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                if (!isNew) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            editViewModel.delete(name) { onDeleted() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 模型选择对话框
    fetchedModels?.let { models ->
        if (models.isEmpty()) {
            AlertDialog(
                onDismissRequest = { fetchedModels = null },
                title = { Text("获取模型列表") },
                text = { Text("未能获取到模型列表，请检查 API 地址和 API Key。") },
                confirmButton = {
                    TextButton(onClick = { fetchedModels = null }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { fetchedModels = null },
                title = { Text("选择模型 (${models.size})") },
                text = {
                    LazyColumn {
                        items(models) { m ->
                            TextButton(
                                onClick = {
                                    model = m
                                    fetchedModels = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = m,
                                    color = if (m == model) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { fetchedModels = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    // 测试连接结果对话框
    testResult?.let { (ok, msg) ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text(if (ok) "连接成功" else "连接失败") },
            text = {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { testResult = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildRequestBodyPreview(
    model: String,
    systemPrompt: String,
    userPromptTemplate: String,
    temperature: Float,
    maxTokens: Int,
    extraJson: String,
): String {
    val messages = mutableListOf<Map<String, String>>()
    if (systemPrompt.isNotBlank()) {
        messages.add(mapOf("role" to "system", "content" to systemPrompt))
    }
    val userContent = if (userPromptTemplate.isNotBlank()) {
        userPromptTemplate
            .replace("{{word}}", "示例词语")
            .replace("{{bookName}}", "示例书名")
            .replace("{{chapterIndex}}", "1")
            .replace("{{chapterTitle}}", "示例章节")
            .replace("{{earlierMentions}}", "- 第1章《示例章节}: ...前文片段...")
            .replace("{{bookMentions}}", "- 【前文】第1章《示例章节}: ...前文片段...\n- 【后文】第3章《示例章节}: ...后文片段...")
    } else {
        "请解释词语: {{word}}"
    }
    messages.add(mapOf("role" to "user", "content" to userContent))
    val body = linkedMapOf<String, Any>(
        "model" to model,
        "messages" to messages,
        "temperature" to ("%.1f".format(temperature).toDouble()),
        "max_tokens" to maxTokens,
    )
    if (extraJson.isNotBlank()) {
        val extra = GSON.fromJsonObject<Map<String, Any>>(extraJson).getOrNull()
        extra?.let { body.putAll(it) }
    }
    return GSON.toJson(body)
}
