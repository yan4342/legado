package io.legado.app.ui.config.compose

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppFreezeMonitor
import io.legado.app.help.DispatchersMonitor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.CheckSource
import io.legado.app.model.ImageProvider
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.service.WebService
import io.legado.app.ui.common.compose.NumberPickerDialog
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.config.ConfigViewModel
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.ListSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.LogUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.restart
import io.legado.app.utils.startActivity
import com.jeremyliao.liveeventbus.LiveEventBus
import splitties.init.appCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherConfigScreen(
    onBackClick: () -> Unit,
    viewModel: ConfigViewModel,
    onCheckSourceClick: () -> Unit = {},
    onUploadRuleClick: () -> Unit = {},
) {
    val context = LocalContext.current

    var language by remember {
        mutableStateOf(context.getPrefString(PreferKey.language, "auto") ?: "auto")
    }
    var autoRefresh by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.autoRefresh, false))
    }
    var defaultToRead by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.defaultToRead, false))
    }
    var showDiscovery by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.showDiscovery, true))
    }
    var showRss by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.showRss, true))
    }
    var defaultHomePage by remember {
        mutableStateOf(context.getPrefString(PreferKey.defaultHomePage, "bookshelf") ?: "bookshelf")
    }
    var webServiceWakeLock by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.webServiceWakeLock, false))
    }
    var cronet by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.cronet, false))
    }
    var antiAlias by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.antiAlias, false))
    }
    var replaceEnableDefault by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.replaceEnableDefault, true))
    }
    var readAloudByMediaButton by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.readAloudByMediaButton, false))
    }
    var ignoreAudioFocus by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.ignoreAudioFocus, false))
    }
    var autoClearExpired by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.autoClearExpired, true))
    }
    var showAddToShelfAlert by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.showAddToShelfAlert, true))
    }
    var showMangaUi by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.showMangaUi, true))
    }
    var processText by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.processText, true))
    }
    var recordLog by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.recordLog, false))
    }
    var mediaButtonOnExit by remember {
        mutableStateOf(context.getPrefBoolean("mediaButtonOnExit", true))
    }
    var updateToVariant by remember {
        mutableStateOf(context.getPrefString("updateToVariant", "default_version") ?: "default_version")
    }
    var recordHeapDump by remember {
        mutableStateOf(context.getPrefBoolean("recordHeapDump", false))
    }

    // Number picker display values — backed by Compose State so description text recomposes on change
    var threadCount by remember { mutableStateOf(AppConfig.threadCount) }
    var webPort by remember { mutableStateOf(AppConfig.webPort) }
    var preDownloadNum by remember { mutableStateOf(AppConfig.preDownloadNum) }
    var bitmapCacheSize by remember { mutableStateOf(AppConfig.bitmapCacheSize) }
    var imageRetainNum by remember { mutableStateOf(AppConfig.imageRetainNum) }
    var sourceEditMaxLine by remember { mutableStateOf(AppConfig.sourceEditMaxLine) }

    // Dialog states
    var numPicker by remember { mutableStateOf<NumPickerInfo?>(null) }
    var showClearCache by remember { mutableStateOf(false) }
    var showClearWebView by remember { mutableStateOf(false) }
    var showShrinkDb by remember { mutableStateOf(false) }
    var showLocalPasswordDialog by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showBookTreePicker by remember { mutableStateOf(false) }

    // Pre-computed strings for non-Composable callbacks
    val threadsLabel = stringResource(R.string.threads_num_title)
    val webPortLabel = stringResource(R.string.web_port_title)
    val preDownloadLabel = stringResource(R.string.pre_download)
    val bitmapCacheLabel = stringResource(R.string.bitmap_cache_size)
    val imageRetainLabel = stringResource(R.string.image_retain_number)
    val sourceEditMaxLineLabel = stringResource(R.string.source_edit_text_max_line)

    val languageEntries = stringArrayResource(R.array.language)
    val languageValues = stringArrayResource(R.array.language_value)
    val homePageEntries = stringArrayResource(R.array.default_home_page)
    val homePageValues = stringArrayResource(R.array.default_home_page_value)
    val updateVariantEntries = stringArrayResource(R.array.default_app_variant)
    val updateVariantValues = stringArrayResource(R.array.default_app_variant_value)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.other_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp) // 新增上下内边距
        ) {
            // Language
            item {
                SectionCard {
                    ListSettingItem(
                        title = stringResource(R.string.language),
                        selectedValue = language,
                        displayEntries = languageEntries,
                        entryValues = languageValues,
                        onValueChange = { v ->
                            language = v
                            context.putPrefString(PreferKey.language, v)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ appCtx.restart() }, 1000)
                        }
                    )
                }
            }

            // Main Activity
            item {
                CategoryHeader(stringResource(R.string.main_activity))
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.pt_auto_refresh),
                        description = stringResource(R.string.ps_auto_refresh),
                        checked = autoRefresh,
                        onCheckedChange = { v ->
                            autoRefresh = v
                            context.putPrefBoolean(PreferKey.autoRefresh, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.pt_default_read),
                        description = stringResource(R.string.ps_default_read),
                        checked = defaultToRead,
                        onCheckedChange = { v ->
                            defaultToRead = v
                            context.putPrefBoolean(PreferKey.defaultToRead, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_discovery),
                        checked = showDiscovery,
                        onCheckedChange = { v ->
                            showDiscovery = v
                            context.putPrefBoolean(PreferKey.showDiscovery, v)
                            postEvent(EventBus.NOTIFY_MAIN, true)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_rss),
                        checked = showRss,
                        onCheckedChange = { v ->
                            showRss = v
                            context.putPrefBoolean(PreferKey.showRss, v)
                            postEvent(EventBus.NOTIFY_MAIN, true)
                        }
                    )
                    ListSettingItem(
                        title = stringResource(R.string.default_home_page),
                        selectedValue = defaultHomePage,
                        displayEntries = homePageEntries,
                        entryValues = homePageValues,
                        onValueChange = { v ->
                            defaultHomePage = v
                            context.putPrefString(PreferKey.defaultHomePage, v)
                        }
                    )
                }
            }

            // Other
            item {
                CategoryHeader(stringResource(R.string.other_setting))
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.web_service_wake_lock),
                        description = stringResource(R.string.web_service_wake_lock_summary),
                        checked = webServiceWakeLock,
                        onCheckedChange = { v ->
                            webServiceWakeLock = v
                            context.putPrefBoolean(PreferKey.webServiceWakeLock, v)
                        }
                    )
                    SwitchSettingItem(
                        title = "Cronet",
                        description = stringResource(R.string.pref_cronet_summary),
                        checked = cronet,
                        onCheckedChange = { v ->
                            cronet = v
                            context.putPrefBoolean(PreferKey.cronet, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.anti_alias),
                        description = stringResource(R.string.pref_anti_alias_summary),
                        checked = antiAlias,
                        onCheckedChange = { v ->
                            antiAlias = v
                            context.putPrefBoolean(PreferKey.antiAlias, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.replace_enable_default_t),
                        description = stringResource(R.string.replace_enable_default_s),
                        checked = replaceEnableDefault,
                        onCheckedChange = { v ->
                            replaceEnableDefault = v
                            context.putPrefBoolean(PreferKey.replaceEnableDefault, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.read_aloud_by_media_button_title),
                        description = stringResource(R.string.read_aloud_by_media_button_summary),
                        checked = readAloudByMediaButton,
                        onCheckedChange = { v ->
                            readAloudByMediaButton = v
                            context.putPrefBoolean(PreferKey.readAloudByMediaButton, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.ignore_audio_focus_title),
                        description = stringResource(R.string.ignore_audio_focus_summary),
                        checked = ignoreAudioFocus,
                        onCheckedChange = { v ->
                            ignoreAudioFocus = v
                            context.putPrefBoolean(PreferKey.ignoreAudioFocus, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.auto_clear_expired),
                        description = stringResource(R.string.auto_clear_expired_summary),
                        checked = autoClearExpired,
                        onCheckedChange = { v ->
                            autoClearExpired = v
                            context.putPrefBoolean(PreferKey.autoClearExpired, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_add_to_shelf_alert_title),
                        description = stringResource(R.string.show_add_to_shelf_alert_summary),
                        checked = showAddToShelfAlert,
                        onCheckedChange = { v ->
                            showAddToShelfAlert = v
                            context.putPrefBoolean(PreferKey.showAddToShelfAlert, v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.media_button_on_exit_title),
                        description = stringResource(R.string.media_button_on_exit_summary),
                        checked = mediaButtonOnExit,
                        onCheckedChange = { v ->
                            mediaButtonOnExit = v
                            context.putPrefBoolean("mediaButtonOnExit", v)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.record_heap_dump_t),
                        description = stringResource(R.string.record_heap_dump_s),
                        checked = recordHeapDump,
                        onCheckedChange = { v ->
                            recordHeapDump = v
                            context.putPrefBoolean("recordHeapDump", v)
                        }
                    )
                }
            }

            // More items (clickable, number pickers)
            item {
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.show_manga_ui),
                        checked = showMangaUi,
                        onCheckedChange = { v ->
                            showMangaUi = v
                            context.putPrefBoolean(PreferKey.showMangaUi, v)
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.threads_num_title),
                        description = threadCount.toString(),
                        onClick = { numPicker = NumPickerInfo(threadsLabel, threadCount, 1, 999) { threadCount = it; AppConfig.threadCount = it; postEvent(PreferKey.threadCount, "") } }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.web_port_title),
                        description = webPort.toString(),
                        onClick = { numPicker = NumPickerInfo(webPortLabel, webPort, 1024, 60000) { webPort = it; AppConfig.webPort = it; if (WebService.isRun) { WebService.stop(context); WebService.start(context) } } }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.pre_download),
                        description = preDownloadNum.toString(),
                        onClick = { numPicker = NumPickerInfo(preDownloadLabel, preDownloadNum, 0, 9999) { preDownloadNum = it; AppConfig.preDownloadNum = it } }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.bitmap_cache_size),
                        description = bitmapCacheSize.toString(),
                        onClick = { numPicker = NumPickerInfo(bitmapCacheLabel, bitmapCacheSize, 1, 1024) { bitmapCacheSize = it; AppConfig.bitmapCacheSize = it; ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize) } }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.image_retain_number),
                        description = imageRetainNum.toString(),
                        onClick = { numPicker = NumPickerInfo(imageRetainLabel, imageRetainNum, 0, 999) { imageRetainNum = it; AppConfig.imageRetainNum = it } }
                    )
                }
            }

            // More settings
            item {
                SectionCard {
                    ListSettingItem(
                        title = stringResource(R.string.update_to_variant_title),
                        selectedValue = updateToVariant,
                        displayEntries = updateVariantEntries,
                        entryValues = updateVariantValues,
                        description = stringResource(R.string.update_to_variant_summary),
                        onValueChange = { v ->
                            updateToVariant = v
                            context.putPrefString("updateToVariant", v)
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.source_edit_text_max_line),
                        description = sourceEditMaxLine.toString(),
                        onClick = { numPicker = NumPickerInfo(sourceEditMaxLineLabel, sourceEditMaxLine, 10, Int.MAX_VALUE) { sourceEditMaxLine = it; AppConfig.sourceEditMaxLine = it } }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.set_local_password),
                        description = stringResource(R.string.set_local_password_summary),
                        onClick = { showLocalPasswordDialog = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.user_agent),
                        description = AppConfig.userAgent,
                        onClick = { showUserAgentDialog = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.book_tree_uri_t),
                        description = AppConfig.defaultBookTreeUri ?: stringResource(R.string.book_tree_uri_s),
                        onClick = { showBookTreePicker = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.check_source_config),
                        description = CheckSource.summary,
                        onClick = onCheckSourceClick
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.direct_link_upload_rule),
                        description = stringResource(R.string.direct_link_upload_rule_summary),
                        onClick = onUploadRuleClick
                    )
                }
            }

            // Actions
            item {
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.add_to_text_context_menu_t),
                        description = stringResource(R.string.add_to_text_context_menu_s),
                        checked = processText,
                        onCheckedChange = { v ->
                            processText = v
                            context.putPrefBoolean(PreferKey.processText, v)
                            val pm = context.packageManager
                            val cn = ComponentName(context, SharedReceiverActivity::class.java)
                            pm.setComponentEnabledSetting(cn,
                                if (v) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP)
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.record_log),
                        description = stringResource(R.string.record_debug_log),
                        checked = recordLog,
                        onCheckedChange = { v ->
                            recordLog = v
                            context.putPrefBoolean(PreferKey.recordLog, v)
                            LogUtils.upLevel()
                            LogUtils.logDeviceInfo()
                            LiveEventBus.config().enableLogger(v)
                            AppFreezeMonitor.init(appCtx)
                            DispatchersMonitor.init()
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.clear_cache),
                        description = stringResource(R.string.clear_cache_summary),
                        onClick = { showClearCache = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.clear_webview_data),
                        description = stringResource(R.string.clear_webview_data_summary),
                        onClick = { showClearWebView = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.shrink_database),
                        description = stringResource(R.string.shrink_database_summary),
                        onClick = { showShrinkDb = true }
                    )
                }
            }

            item { Modifier.padding(bottom = 16.dp) }
}

        // Dialogs
        numPicker?.let { info ->
            NumberPickerDialog(
                title = info.title,
                value = info.value,
                minValue = info.min,
                maxValue = info.max,
                onDismiss = { numPicker = null },
                onConfirm = { v -> info.onConfirm(v); numPicker = null },
                defaultButton = info.defaultButton,
            )
        }
        if (showClearCache) {
            AlertDialog(
                onDismissRequest = { showClearCache = false },
                title = { Text(stringResource(R.string.clear_cache)) },
                text = { Text(stringResource(R.string.sure_del)) },
                confirmButton = { TextButton(onClick = { showClearCache = false; viewModel.clearCache() }) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = { TextButton(onClick = { showClearCache = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (showClearWebView) {
            AlertDialog(
                onDismissRequest = { showClearWebView = false },
                title = { Text(stringResource(R.string.clear_webview_data)) },
                text = { Text(stringResource(R.string.sure_del)) },
                confirmButton = { TextButton(onClick = { showClearWebView = false; viewModel.clearWebViewData() }) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = { TextButton(onClick = { showClearWebView = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (showShrinkDb) {
            AlertDialog(
                onDismissRequest = { showShrinkDb = false },
                title = { Text(stringResource(R.string.shrink_database)) },
                text = { Text(stringResource(R.string.sure)) },
                confirmButton = { TextButton(onClick = { showShrinkDb = false; viewModel.shrinkDatabase() }) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = { TextButton(onClick = { showShrinkDb = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (showLocalPasswordDialog) {
            var pwdText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showLocalPasswordDialog = false },
                title = { Text(stringResource(R.string.set_local_password)) },
                text = { OutlinedTextField(value = pwdText, onValueChange = { pwdText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { LocalConfig.password = pwdText; showLocalPasswordDialog = false }) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = { TextButton(onClick = { showLocalPasswordDialog = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
        if (showUserAgentDialog) {
            var uaText by remember { mutableStateOf(AppConfig.userAgent) }
            AlertDialog(
                onDismissRequest = { showUserAgentDialog = false },
                title = { Text(stringResource(R.string.user_agent)) },
                text = { OutlinedTextField(value = uaText, onValueChange = { uaText = it }, singleLine = false, modifier = Modifier.fillMaxWidth(),maxLines = 3,) },
                confirmButton = { TextButton(onClick = { AppConfig.userAgent = uaText; showUserAgentDialog = false }) { Text(stringResource(android.R.string.ok)) } },
                dismissButton = { TextButton(onClick = { showUserAgentDialog = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

data class NumPickerInfo(
    val title: String,
    val value: Int,
    val min: Int,
    val max: Int,
    val defaultButton: @Composable (() -> Unit)? = null,
    val onConfirm: (Int) -> Unit,
)
