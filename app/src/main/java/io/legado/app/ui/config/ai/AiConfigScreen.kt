package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.ui.common.compose.CategorySection
import io.legado.app.ui.common.compose.InfoChip
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.SettingItem
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Same format as book-source `proxy`: `http://host:port` / `socks5://host:port` / `…@user@pass`. */
private val AI_PROXY_PATTERN =
    Regex("""^(http|socks4|socks5)://[^:]+:\d{2,5}(@[^@]+@[^@]+)?$""", RegexOption.IGNORE_CASE)

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

private data class AiBalanceInfo(
    val isAvailable: Boolean,
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String
)

private fun buildBalanceUrl(baseUrl: String): String {
    val base = baseUrl.trimEnd('/')
        .removeSuffix("/v1")
        .trimEnd('/')
    return "$base/user/balance"
}

@Suppress("UNCHECKED_CAST")
private fun parseBalanceResponse(body: String): AiBalanceInfo? {
    val json = GSON.fromJsonObject<Map<String, Any>>(body).getOrNull() ?: return null
    val isAvailable = json["is_available"] as? Boolean ?: false
    val balanceInfos = json["balance_infos"] as? List<Map<String, Any>> ?: return null
    val first = balanceInfos.firstOrNull() ?: return null
    return AiBalanceInfo(
        isAvailable = isAvailable,
        currency = first["currency"] as? String ?: "",
        totalBalance = first["total_balance"] as? String ?: "0",
        grantedBalance = first["granted_balance"] as? String ?: "0",
        toppedUpBalance = first["topped_up_balance"] as? String ?: "0"
    )
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiConfigScreen(
    viewModel: AiConfigViewModel,
    aiProfileGateway: AiProfileGateway,
    onBack: () -> Unit,
    onNavigateToProfileEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String, modelProfileId: String?) -> Unit,
    onNavigateToAbilityManagement: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToWebSearch: () -> Unit = {},
    onNavigateToPromptTemplates: () -> Unit = {},
    onNavigateToHtmlThemes: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Fetch models state
    var fetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<AiAvailableModel>?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var balanceQuerying by remember { mutableStateOf<String?>(null) }
    var balanceDialogInfo by remember { mutableStateOf<Pair<String, AiBalanceInfo?>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
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
                title = { Text(stringResource(R.string.ai_config), color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = onSurfaceColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, titleContentColor = onSurfaceColor, navigationIconContentColor = onSurfaceColor, actionIconContentColor = onSurfaceColor),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Tools & Settings
            item {
                CategorySection(stringResource(R.string.tool_about)) {
                    var proxyEnabled by remember { mutableStateOf(AppConfig.aiChatProxyEnabled) }
                    var chatProxy by remember { mutableStateOf(AppConfig.aiChatProxy) }
                    SettingItem(
                        title = stringResource(R.string.ai_chat_proxy_title),
                        description = stringResource(R.string.ai_chat_proxy_hint),
                        expanded = proxyEnabled,
                        onExpandChange = {
                            proxyEnabled = it
                            AppConfig.aiChatProxyEnabled = it
                        },
                        trailingContent = {
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = {
                                    proxyEnabled = it
                                    AppConfig.aiChatProxyEnabled = it
                                },
                            )
                        },
                        expandContent = {
                            OutlinedTextField(
                                value = chatProxy,
                                onValueChange = { chatProxy = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = proxyEnabled,
                                label = { Text(stringResource(R.string.ai_chat_proxy_label)) },
                                placeholder = { Text(stringResource(R.string.ai_chat_proxy_placeholder)) },
                            )
                            TextButton(
                                onClick = {
                                    val value = chatProxy.trim()
                                    if (value.isNotEmpty() && !AI_PROXY_PATTERN.matches(value)) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.ai_chat_proxy_invalid),
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                        return@TextButton
                                    }
                                    AppConfig.aiChatProxy = value
                                    chatProxy = value
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_chat_proxy_saved),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(stringResource(R.string.ai_chat_proxy_save))
                            }
                        },
                    )                    
                    var renderTrack by remember { mutableStateOf(AppConfig.aiChatRenderTrack) }
                    SettingItem(
                        title = stringResource(R.string.ai_chat_render_track),
                        description = stringResource(R.string.ai_chat_render_track_summary),
                        expanded = true,
                        onExpandChange = {},
                        expandContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = renderTrack == AppConfig.AI_CHAT_RENDER_COMPOSE,
                                    onClick = {
                                        AppConfig.aiChatRenderTrack = AppConfig.AI_CHAT_RENDER_COMPOSE
                                        renderTrack = AppConfig.AI_CHAT_RENDER_COMPOSE
                                    },
                                    label = { Text(stringResource(R.string.ai_chat_render_compose)) },
                                )
                                FilterChip(
                                    selected = renderTrack == AppConfig.AI_CHAT_RENDER_HTML,
                                    onClick = {
                                        AppConfig.aiChatRenderTrack = AppConfig.AI_CHAT_RENDER_HTML
                                        renderTrack = AppConfig.AI_CHAT_RENDER_HTML
                                    },
                                    label = { Text(stringResource(R.string.ai_chat_render_html)) },
                                )
                            }
                        },
                    )
                    var htmlAppAutoLaunch by remember { mutableStateOf(AppConfig.aiHtmlAppAutoLaunch) }
                    SettingItem(
                        title = stringResource(R.string.ai_html_app_auto_launch),
                        description = stringResource(R.string.ai_html_app_auto_launch_summary),
                        onExpandChange = {
                            htmlAppAutoLaunch = it
                            AppConfig.aiHtmlAppAutoLaunch = it
                        },
                        trailingContent = {
                            Switch(
                                checked = htmlAppAutoLaunch,
                                onCheckedChange = {
                                    htmlAppAutoLaunch = it
                                    AppConfig.aiHtmlAppAutoLaunch = it
                                },
                            )
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_html_theme_config),
                        description = stringResource(R.string.ai_html_theme_config_summary),
                        painter = painterResource(R.drawable.ic_ai_chat),
                        onClick = onNavigateToHtmlThemes,
                    )

                    ClickableSettingItem(
                        title = stringResource(R.string.ai_skills_page_title),
                        description = stringResource(R.string.ai_skills_page_summary),
                        painter = painterResource(R.drawable.ic_ai_tools),
                        onClick = onNavigateToSkills,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_ability_management_title),
                        description = stringResource(R.string.ai_ability_management_summary),
                        painter = painterResource(R.drawable.ic_ai_setting),
                        onClick = onNavigateToAbilityManagement,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_web_search_config_title),
                        description = stringResource(R.string.ai_web_search_config_summary),
                        painter = painterResource(R.drawable.ic_ai_provider),
                        onClick = onNavigateToWebSearch,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_templates_title),
                        description = stringResource(R.string.ai_prompt_templates_summary),
                        painter = painterResource(R.drawable.ic_ai_dictionary_rule),
                        onClick = onNavigateToPromptTemplates,
                    )
                }
            }

            // Current model info
            item {
                Text(
                    text = stringResource(R.string.ai_current_model) + ": " + state.currentModelName.ifBlank { stringResource(R.string.ai_model_not_configured) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }

            // Providers
            item {
                CategorySection(stringResource(R.string.ai_provider_database)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.ai_count_configured, state.providerCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = { onNavigateToProfileEdit(null) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.ai_new_provider),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (state.providers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_model_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    state.providers.forEach { provider ->
                        SettingItem(
                            painter = painterResource(R.drawable.ic_ai_provider),
                            title = provider.providerName,
                            description = provider.baseUrl,
                            onClick = { onNavigateToProfileEdit(provider.providerId) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (provider.modelCount > 0) {
                                        InfoChip(text = "${provider.modelCount}")
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            balanceQuerying = provider.providerId
                                            scope.launch {
                                                try {
                                                    val apiKey = aiProfileGateway.getProviderApiKey(provider.providerId)
                                                    val url = buildBalanceUrl(provider.baseUrl)
                                                    val response = okHttpClient.newCallStrResponse {
                                                        url(url)
                                                        addHeader("Authorization", "Bearer $apiKey")
                                                    }
                                                    if (!response.isSuccessful()) {
                                                        balanceDialogInfo = provider.providerName to null
                                                    } else {
                                                        val info = parseBalanceResponse(response.body.orEmpty())
                                                        balanceDialogInfo = provider.providerName to info
                                                    }
                                                } catch (e: Exception) {
                                                    balanceDialogInfo = provider.providerName to null
                                                }
                                                balanceQuerying = null
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = balanceQuerying != provider.providerId,
                                    ) {
                                        if (balanceQuerying == provider.providerId) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                "¥",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onNavigateToProfileEdit(provider.providerId) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Models
            item {
                CategorySection(stringResource(R.string.ai_model_database)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ai_count_configured, state.modelCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val firstProvider = state.providers.firstOrNull()
                            IconButton(
                                onClick = {
                                    val provider = firstProvider ?: return@IconButton
                                    fetchingModels = true
                                    scope.launch {
                                        try {
                                            val apiKey = aiProfileGateway.getProviderApiKey(provider.providerId)
                                            val modelsUrl = ""
                                            val url = buildModelsUrl(provider.baseUrl)
                                            val response = okHttpClient.newCallStrResponse {
                                                url(url)
                                                addHeader("Authorization", "Bearer $apiKey")
                                            }
                                            if (!response.isSuccessful()) {
                                                syncMessage = "HTTP ${response.code()}: ${response.message()}\nURL: $url"
                                                fetchedModels = emptyList()
                                            } else {
                                                val existingIds = state.models.map { it.modelId }.toSet()
                                                fetchedModels = parseOpenAiModelsResponse(response.body.orEmpty())
                                                    .filter { it.id !in existingIds }
                                                    .ifEmpty { listOf(AiAvailableModel(id = "__empty__")) }
                                            }
                                        } catch (e: Exception) {
                                            syncMessage = e.message ?: "Failed to fetch models"
                                            fetchedModels = emptyList()
                                        }
                                        fetchingModels = false
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = firstProvider != null,
                            ) {
                                if (fetchingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.ai_fetch_models),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (firstProvider != null) {
                                        onNavigateToModelEdit(firstProvider.providerId, null)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                                enabled = state.providers.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.ai_new_model),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (state.models.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_model_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    state.models.forEach { model ->
                        val isCurrent = model.isCurrent
                        SettingItem(
                            title = model.modelName,
                            description = "${model.providerName} | ${model.modelId}",
                            onClick = { viewModel.onIntent(AiConfigIntent.SetDefaultModel(model.modelProfileId)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        InfoChip(text = stringResource(R.string.ai_model_in_use), filled = true)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { onNavigateToModelEdit(model.providerId, model.modelProfileId) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }            

            item { Modifier.padding(bottom = 16.dp) }
        }
    }

    // Model selection bottom sheet
    fetchedModels?.let { models ->
        val firstProvider = state.providers.firstOrNull()
        ModalLegadoBottomSheet(
            show = true,
            onDismissRequest = { fetchedModels = null },
            title = stringResource(R.string.ai_select_model) + " (${models.size})",
            skipPartiallyExpanded = true,
        ) {
            if (models.isEmpty() || models.any { it.id == "__empty__" }) {
                Text(
                    text = stringResource(R.string.ai_models_up_to_date),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                ) {
                    items(models) { m ->
                        val ctxLabel = if (m.contextWindow > 0) {
                            stringResource(R.string.ai_model_ctx, m.contextWindow / 1000)
                        } else {
                            null
                        }
                        val outLabel = if (m.maxOutputTokens > 0) {
                            stringResource(R.string.ai_model_out, m.maxOutputTokens / 1000)
                        } else {
                            null
                        }
                        val capacity = listOfNotNull(ctxLabel, outLabel).joinToString("  /  ")
                        SettingItem(
                            title = m.name.ifBlank { m.id },
                            description = m.id + if (capacity.isNotEmpty()) "  ·  $capacity" else "",
                            onClick = {
                                val provider = firstProvider
                                if (provider != null) {
                                    scope.launch {
                                        aiProfileGateway.saveModel(
                                            AiModelDraft(
                                                providerId = provider.providerId,
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
                        )
                    }
                }
            }
        }
    }

    // Balance result dialog
    balanceDialogInfo?.let { (providerName, info) ->
        AlertDialog(
            onDismissRequest = { balanceDialogInfo = null },
            title = { Text("${stringResource(R.string.ai_balance)} · $providerName") },
            text = {
                if (info != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ai_balance_total, "${info.totalBalance} ${info.currency}"))
                        if (info.toppedUpBalance.toDoubleOrNull()?.let { it > 0 } == true) {
                            Text(stringResource(R.string.ai_balance_topped_up, "${info.toppedUpBalance} ${info.currency}"))
                        }
                        if (info.grantedBalance.toDoubleOrNull()?.let { it > 0 } == true) {
                            Text(stringResource(R.string.ai_balance_granted, "${info.grantedBalance} ${info.currency}"))
                        }
                    }
                } else {
                    Text(stringResource(R.string.ai_balance_query_failed))
                }
            },
            confirmButton = { TextButton(onClick = { balanceDialogInfo = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    // Sync result / error toast
    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            syncMessage = null
        }
    }
}
