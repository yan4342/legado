package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.WebSearchRateLimiter
import io.legado.app.domain.usecase.WebSearchUseCase
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.WebSearchPageProfiles
import io.legado.app.ui.common.compose.CategorySection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWebSearchConfigScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webSearchUseCase: WebSearchUseCase = koinInject()
    val rateLimiter: WebSearchRateLimiter = koinInject()

    var mode by remember { mutableStateOf(AppConfig.aiWebSearchMode) }
    var profiles by remember { mutableStateOf(WebSearchPageProfiles.list()) }
    var activeProfileId by remember { mutableStateOf(WebSearchPageProfiles.activeId()) }
    var profileExpanded by remember { mutableStateOf(false) }
    var profileName by remember {
        mutableStateOf(WebSearchPageProfiles.getActive().name)
    }
    var pageUrlTemplate by remember { mutableStateOf(AppConfig.aiWebSearchPageUrlTemplate) }
    var pageDelayMs by remember { mutableStateOf(AppConfig.aiWebSearchPageDelayMs.toString()) }
    var pageResultSelector by remember { mutableStateOf(AppConfig.aiWebSearchPageResultSelector) }
    var pageTitleSelector by remember { mutableStateOf(AppConfig.aiWebSearchPageTitleSelector) }
    var pageSnippetSelector by remember { mutableStateOf(AppConfig.aiWebSearchPageSnippetSelector) }
    var pageRedirectParam by remember { mutableStateOf(AppConfig.aiWebSearchPageRedirectParam) }
    var pageExcludeHosts by remember { mutableStateOf(AppConfig.aiWebSearchPageExcludeHosts) }
    var provider by remember { mutableStateOf(AppConfig.aiWebSearchProvider) }
    var apiKey by remember { mutableStateOf(AppConfig.aiWebSearchApiKey) }
    var braveBaseUrl by remember { mutableStateOf(AppConfig.aiWebSearchBraveBaseUrl) }
    var tavilyBaseUrl by remember { mutableStateOf(AppConfig.aiWebSearchTavilyBaseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(rateLimiter.usage()) }

    var searchConvLimit by remember { mutableStateOf(AppConfig.aiWebSearchConvLimit.toString()) }
    var readConvLimit by remember { mutableStateOf(AppConfig.aiWebReadConvLimit.toString()) }
    var weekLimit by remember { mutableStateOf(AppConfig.aiWebSearchWeekLimit.toString()) }
    var monthLimit by remember { mutableStateOf(AppConfig.aiWebSearchMonthLimit.toString()) }
    var testDayLimit by remember { mutableStateOf(AppConfig.aiWebSearchTestDayLimit.toString()) }

    // 原生联网搜索调优（Responses 协议内置 web_search；默认值 = 不下发任何调优字段）。
    var nativeMaxUses by remember { mutableStateOf(AppConfig.aiNativeSearchMaxUses.toString()) }
    var nativeContextSize by remember { mutableStateOf(AppConfig.aiNativeSearchContextSize) }
    var nativeContextExpanded by remember { mutableStateOf(false) }
    var nativeAllowedDomains by remember { mutableStateOf(AppConfig.aiNativeSearchAllowedDomains) }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary
    val containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.primary
    val isPageMode = mode == "page"

    fun reloadActiveFields() {
        profiles = WebSearchPageProfiles.list()
        activeProfileId = WebSearchPageProfiles.activeId()
        val active = WebSearchPageProfiles.getActive()
        profileName = active.name
        pageUrlTemplate = active.pageUrlTemplate
        pageDelayMs = active.delayMs.toString()
        pageResultSelector = active.resultSelector
        pageTitleSelector = active.titleSelector
        pageSnippetSelector = active.snippetSelector
        pageRedirectParam = active.redirectParam
        pageExcludeHosts = active.excludeHosts
    }

    fun persistMode() {
        AppConfig.aiWebSearchMode = mode
        mode = AppConfig.aiWebSearchMode
    }

    fun persistPage() {
        val current = WebSearchPageProfiles.getActive()
        WebSearchPageProfiles.update(
            current.copy(
                name = profileName,
                pageUrlTemplate = pageUrlTemplate,
                delayMs = pageDelayMs.toIntOrNull() ?: current.delayMs,
                resultSelector = pageResultSelector,
                titleSelector = pageTitleSelector,
                snippetSelector = pageSnippetSelector,
                redirectParam = pageRedirectParam,
                excludeHosts = pageExcludeHosts,
            ),
        )
        reloadActiveFields()
    }

    fun activateProfile(id: String) {
        persistPage()
        WebSearchPageProfiles.setActive(id)
        reloadActiveFields()
    }

    fun createProfile() {
        persistPage()
        val result = WebSearchPageProfiles.create(
            name = context.getString(R.string.ai_web_search_page_profile_new_name),
            activate = true,
        )
        result.onSuccess {
            reloadActiveFields()
        }.onFailure {
            android.widget.Toast.makeText(
                context,
                it.message ?: "error",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun deleteProfile() {
        val result = WebSearchPageProfiles.delete(activeProfileId)
        result.onSuccess {
            reloadActiveFields()
        }.onFailure {
            android.widget.Toast.makeText(
                context,
                it.message ?: "error",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun persistApi() {
        AppConfig.aiWebSearchProvider = provider
        AppConfig.aiWebSearchApiKey = apiKey.trim()
        AppConfig.aiWebSearchBraveBaseUrl = braveBaseUrl
        AppConfig.aiWebSearchTavilyBaseUrl = tavilyBaseUrl
        braveBaseUrl = AppConfig.aiWebSearchBraveBaseUrl
        tavilyBaseUrl = AppConfig.aiWebSearchTavilyBaseUrl
    }

    fun persistNative() {
        AppConfig.aiNativeSearchContextSize = nativeContextSize
        AppConfig.aiNativeSearchAllowedDomains = nativeAllowedDomains
    }

    fun refreshUsage() {
        usage = rateLimiter.usage()
    }

    fun runTest() {
        if (isPageMode) {
            persistPage()
            if (!AppConfig.aiWebSearchConfigured) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.ai_web_search_configure_page_first),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return
            }
        } else {
            persistApi()
            if (apiKey.isBlank()) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.ai_web_search_configure_first),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return
            }
        }
        testing = true
        scope.launch {
            val msg = runCatching {
                val result = webSearchUseCase.testConnection()
                context.getString(
                    R.string.ai_web_search_test_ok,
                    result.provider,
                    result.results.size,
                )
            }.getOrElse {
                context.getString(
                    R.string.ai_web_search_test_fail,
                    it.message ?: "error",
                )
            }
            testing = false
            refreshUsage()
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_web_search_config_title), color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.ai_web_search_config_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            CategorySection(stringResource(R.string.ai_web_search_mode_section)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    ModeRadioRow(
                        selected = !isPageMode,
                        label = stringResource(R.string.ai_web_search_mode_api),
                        onClick = {
                            mode = "api"
                            persistMode()
                        },
                    )
                    ModeRadioRow(
                        selected = isPageMode,
                        label = stringResource(R.string.ai_web_search_mode_page),
                        onClick = {
                            mode = "page"
                            persistMode()
                        },
                    )
                }
            }
            if (isPageMode) {
                CategorySection(stringResource(R.string.ai_web_search_page_section)) {
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { profileExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = profiles.firstOrNull { it.id == activeProfileId }?.name
                                ?: profileName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ai_web_search_page_profile)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = profileExpanded,
                            onDismissRequest = { profileExpanded = false },
                        ) {
                            profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        profileExpanded = false
                                        activateProfile(p.id)
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = ::createProfile,
                            enabled = profiles.size < WebSearchPageProfiles.MAX_PROFILES,
                        ) {
                            Text(stringResource(R.string.ai_web_search_page_profile_add))
                        }
                        TextButton(
                            onClick = ::deleteProfile,
                            enabled = profiles.size > 1,
                        ) {
                            Text(stringResource(R.string.ai_web_search_page_profile_delete))
                        }
                    }
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_profile_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pageUrlTemplate,
                        onValueChange = {
                            pageUrlTemplate = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_url_template)) },
                        supportingText = {
                            Text(stringResource(R.string.ai_web_search_page_url_template_hint))
                        },
                        placeholder = { Text(AppConfig.DEFAULT_PAGE_URL_TEMPLATE) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = false,
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = pageDelayMs,
                        onValueChange = { raw ->
                            pageDelayMs = raw.filter { it.isDigit() }.take(5)
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_delay_ms)) },
                        supportingText = {
                            Text(stringResource(R.string.ai_web_search_page_delay_ms_hint))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pageResultSelector,
                        onValueChange = {
                            pageResultSelector = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_result_selector)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = false,
                        minLines = 1,
                    )
                    OutlinedTextField(
                        value = pageTitleSelector,
                        onValueChange = {
                            pageTitleSelector = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_title_selector)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = false,
                        minLines = 1,
                    )
                    OutlinedTextField(
                        value = pageSnippetSelector,
                        onValueChange = {
                            pageSnippetSelector = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_snippet_selector)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = false,
                        minLines = 1,
                    )
                    OutlinedTextField(
                        value = pageRedirectParam,
                        onValueChange = {
                            pageRedirectParam = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_redirect_param)) },
                        supportingText = {
                            Text(stringResource(R.string.ai_web_search_page_redirect_param_hint))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pageExcludeHosts,
                        onValueChange = {
                            pageExcludeHosts = it
                            persistPage()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_page_exclude_hosts)) },
                        supportingText = {
                            Text(stringResource(R.string.ai_web_search_page_exclude_hosts_hint))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = false,
                        minLines = 1,
                    )
                    TestConnectionRow(testing = testing, onClick = ::runTest)
                }
            } else {
                CategorySection(stringResource(R.string.ai_web_search_api_section)) {
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = when (provider) {
                                "brave" -> "Brave"
                                else -> "Tavily"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ai_web_search_provider)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tavily") },
                                onClick = {
                                    provider = "tavily"
                                    providerExpanded = false
                                    persistApi()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Brave") },
                                onClick = {
                                    provider = "brave"
                                    providerExpanded = false
                                    persistApi()
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            persistApi()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_api_key)) },
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }, modifier = Modifier.padding(end = 4.dp)) {
                                Text(
                                    if (showKey) stringResource(R.string.ai_web_search_hide_key)
                                    else stringResource(R.string.ai_web_search_show_key),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = if (provider == "brave") braveBaseUrl else tavilyBaseUrl,
                        onValueChange = {
                            if (provider == "brave") {
                                braveBaseUrl = it
                            } else {
                                tavilyBaseUrl = it
                            }
                            persistApi()
                        },
                        label = { Text(stringResource(R.string.ai_web_search_base_url)) },
                        placeholder = {
                            Text(
                                if (provider == "brave") AppConfig.DEFAULT_BRAVE_BASE_URL
                                else AppConfig.DEFAULT_TAVILY_BASE_URL,
                            )
                        },
                        supportingText = {
                            Text(stringResource(R.string.ai_web_search_base_url_hint))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    TestConnectionRow(testing = testing, onClick = ::runTest)
                }
            }
            CategorySection(stringResource(R.string.ai_native_search_section)) {
                QuotaLimitField(
                    label = stringResource(R.string.ai_native_search_max_uses),
                    value = nativeMaxUses,
                    maxDigits = 2,
                    onValueChange = { nativeMaxUses = it },
                    onCommit = { n ->
                        AppConfig.aiNativeSearchMaxUses = n.coerceIn(0, 20)
                        nativeMaxUses = AppConfig.aiNativeSearchMaxUses.toString()
                    },
                )
                ExposedDropdownMenuBox(
                    expanded = nativeContextExpanded,
                    onExpandedChange = { nativeContextExpanded = it },
                ) {
                    OutlinedTextField(
                        value = when (nativeContextSize) {
                            "low" -> "low"
                            "medium" -> "medium"
                            "high" -> "high"
                            else -> stringResource(R.string.ai_native_search_context_size_default)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_native_search_context_size)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(nativeContextExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = nativeContextExpanded,
                        onDismissRequest = { nativeContextExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_native_search_context_size_default)) },
                            onClick = {
                                nativeContextSize = ""
                                persistNative()
                                nativeContextExpanded = false
                            },
                        )
                        listOf("low", "medium", "high").forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size) },
                                onClick = {
                                    nativeContextSize = size
                                    persistNative()
                                    nativeContextExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = nativeAllowedDomains,
                    onValueChange = {
                        nativeAllowedDomains = it
                        persistNative()
                    },
                    label = { Text(stringResource(R.string.ai_native_search_allowed_domains)) },
                    supportingText = {
                        Text(stringResource(R.string.ai_native_search_allowed_domains_hint))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    singleLine = false,
                    minLines = 1,
                )
            }
            CategorySection(stringResource(R.string.ai_web_search_quota_section)) {
                Text(
                    stringResource(
                        R.string.ai_web_search_quota_summary,
                        usage.weekUsed,
                        usage.weekLimit,
                        usage.monthUsed,
                        usage.monthLimit,
                        usage.testDayUsed,
                        usage.testDayLimit,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    stringResource(R.string.ai_web_search_quota_week_month_api_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                QuotaLimitField(
                    label = stringResource(R.string.ai_web_search_quota_search_conv),
                    value = searchConvLimit,
                    maxDigits = 3,
                    onValueChange = { searchConvLimit = it },
                    onCommit = { n ->
                        AppConfig.aiWebSearchConvLimit = n
                        searchConvLimit = AppConfig.aiWebSearchConvLimit.toString()
                        refreshUsage()
                    },
                )
                QuotaLimitField(
                    label = stringResource(R.string.ai_web_search_quota_read_conv),
                    value = readConvLimit,
                    maxDigits = 3,
                    onValueChange = { readConvLimit = it },
                    onCommit = { n ->
                        AppConfig.aiWebReadConvLimit = n
                        readConvLimit = AppConfig.aiWebReadConvLimit.toString()
                        refreshUsage()
                    },
                )
                QuotaLimitField(
                    label = stringResource(R.string.ai_web_search_quota_week),
                    value = weekLimit,
                    maxDigits = 5,
                    onValueChange = { weekLimit = it },
                    onCommit = { n ->
                        AppConfig.aiWebSearchWeekLimit = n
                        weekLimit = AppConfig.aiWebSearchWeekLimit.toString()
                        refreshUsage()
                    },
                )
                QuotaLimitField(
                    label = stringResource(R.string.ai_web_search_quota_month),
                    value = monthLimit,
                    maxDigits = 5,
                    onValueChange = { monthLimit = it },
                    onCommit = { n ->
                        AppConfig.aiWebSearchMonthLimit = n
                        monthLimit = AppConfig.aiWebSearchMonthLimit.toString()
                        refreshUsage()
                    },
                )
                QuotaLimitField(
                    label = stringResource(R.string.ai_web_search_quota_test_day),
                    value = testDayLimit,
                    maxDigits = 2,
                    onValueChange = { testDayLimit = it },
                    onCommit = { n ->
                        AppConfig.aiWebSearchTestDayLimit = n
                        testDayLimit = AppConfig.aiWebSearchTestDayLimit.toString()
                        refreshUsage()
                    },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TestConnectionRow(
    testing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onClick, enabled = !testing) {
            Text(
                if (testing) stringResource(R.string.ai_web_search_testing)
                else stringResource(R.string.ai_web_search_test),
            )
        }
    }
}

@Composable
private fun QuotaLimitField(
    label: String,
    value: String,
    maxDigits: Int,
    onValueChange: (String) -> Unit,
    onCommit: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(maxDigits)
            onValueChange(filtered)
            filtered.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
