package io.legado.app.ui.config.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem
import io.legado.app.ui.config.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupConfigScreen(
    onBackClick: () -> Unit,
    viewModel: ConfigViewModel,
    backupPath: String = "",
    onBackupPathClick: () -> Unit = {},
    onRestoreIgnoreClick: () -> Unit = {},
    onImportOldClick: () -> Unit = {},
    onLocalRestoreClick: () -> Unit = {},
    onWebDavRestoreClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onLogClick: () -> Unit = {},
) {
    val context = LocalContext.current

    var syncBookProgress by remember {
        mutableStateOf(AppConfig.syncBookProgress)
    }
    var syncBookProgressPlus by remember {
        mutableStateOf(AppConfig.syncBookProgressPlus)
    }
    var onlyLatestBackup by remember {
        mutableStateOf(AppConfig.onlyLatestBackup)
    }
    var autoCheckNewBackup by remember {
        mutableStateOf(AppConfig.autoCheckNewBackup)
    }
    var webDavUrl by remember {
        mutableStateOf(AppConfig.webDavUrl ?: "")
    }
    var webDavAccount by remember {
        mutableStateOf(AppConfig.webDavAccount ?: "")
    }
    val displayBackupPath = backupPath.ifEmpty {
        AppConfig.backupPath ?: ""
    }

    var showMenu by remember { mutableStateOf(false) }

    // Dialog states
    var editDialogInfo by remember { mutableStateOf<EditDialogInfo?>(null) }
    var showWebDavPwdDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(painterResource(R.drawable.ic_help), contentDescription = stringResource(R.string.help))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log)) },
                                onClick = {
                                    showMenu = false
                                    onLogClick()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Category 1: WebDav settings
            item { SectionTitle(stringResource(R.string.web_dav_set)) }
            item {
                SectionCard {
                    ClickableSettingItem(
                        title = stringResource(R.string.web_dav_url),
                        description = webDavUrl.ifEmpty { stringResource(R.string.web_dav_url_s) },
                        onClick = {
                            editDialogInfo = EditDialogInfo(context.getString(R.string.web_dav_url), webDavUrl) { v ->
                                webDavUrl = v
                                AppConfig.webDavUrl = v
                                viewModel.upWebDavConfig()
                            }
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.web_dav_account),
                        description = webDavAccount.ifEmpty { stringResource(R.string.web_dav_account_s) },
                        onClick = {
                            editDialogInfo = EditDialogInfo(context.getString(R.string.web_dav_account), webDavAccount) { v ->
                                webDavAccount = v
                                AppConfig.webDavAccount = v
                                viewModel.upWebDavConfig()
                            }
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.web_dav_pw),
                        description = "••••••",
                        onClick = { showWebDavPwdDialog = true },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.sub_dir),
                        description = AppConfig.webDavDir ?: "legado",
                        onClick = {
                            editDialogInfo = EditDialogInfo(context.getString(R.string.sub_dir), AppConfig.webDavDir ?: "legado") { v ->
                                AppConfig.webDavDir = v
                                viewModel.upWebDavConfig()
                            }
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.webdav_device_name),
                        description = AppConfig.webDavDeviceName ?: "",
                        onClick = {
                            editDialogInfo = EditDialogInfo(context.getString(R.string.webdav_device_name), AppConfig.webDavDeviceName ?: "") { v ->
                                AppConfig.webDavDeviceName = v
                                viewModel.upWebDavConfig()
                            }
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.sync_book_progress_t),
                        description = stringResource(R.string.sync_book_progress_s),
                        checked = syncBookProgress,
                        onCheckedChange = { v ->
                            syncBookProgress = v
                            AppConfig.syncBookProgress = v
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.web_dav_test),
                        description = stringResource(R.string.web_dav_test_s),
                        onClick = { viewModel.testWebDav() },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.sync_book_progress_plus_t),
                        description = stringResource(R.string.sync_book_progress_plus_s),
                        checked = syncBookProgressPlus,
                        enabled = syncBookProgress,
                        onCheckedChange = { v ->
                            syncBookProgressPlus = v
                            AppConfig.syncBookProgressPlus = v
                        },
                    )
                }
            }

            // Category 2: Local backup and restore
            item { SectionTitle(stringResource(R.string.local_backup_restore)) }
            item {
                SectionCard {
                    ClickableSettingItem(
                        title = stringResource(R.string.backup_path),
                        description = displayBackupPath.ifEmpty { stringResource(R.string.select_backup_path) },
                        onClick = onBackupPathClick,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.backup),
                        description = stringResource(R.string.local_backup_summary),
                        onClick = { viewModel.backupLocal() },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.restore),
                        description = stringResource(R.string.local_restore_summary),
                        onClick = onLocalRestoreClick,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.restore_ignore),
                        description = stringResource(R.string.restore_ignore_summary),
                        onClick = onRestoreIgnoreClick,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.menu_import_old_version),
                        description = stringResource(R.string.import_old_summary),
                        onClick = onImportOldClick,
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.only_latest_backup_t),
                        description = stringResource(R.string.only_latest_backup_s),
                        checked = onlyLatestBackup,
                        onCheckedChange = { v ->
                            onlyLatestBackup = v
                            AppConfig.onlyLatestBackup = v
                        },
                    )
                }
            }

            // Category 3: WebDav backup and restore
            item { SectionTitle(stringResource(R.string.web_dav_backup_restore)) }
            item {
                SectionCard {
                    ClickableSettingItem(
                        title = stringResource(R.string.backup),
                        description = stringResource(R.string.web_dav_backup_summary),
                        onClick = { viewModel.backupWebDav() },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.restore),
                        description = stringResource(R.string.web_dav_restore_summary),
                        onClick = onWebDavRestoreClick,
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.auto_check_new_backup_t),
                        description = stringResource(R.string.auto_check_new_backup_s),
                        checked = autoCheckNewBackup,
                        onCheckedChange = { v ->
                            autoCheckNewBackup = v
                            AppConfig.autoCheckNewBackup = v
                        },
                    )
                }
            }

            item { Modifier.padding(bottom = 16.dp) }
        }

        // Dialogs
        editDialogInfo?.let { info ->
            var text by remember(info) { mutableStateOf(info.currentValue) }
            AlertDialog(
                onDismissRequest = { editDialogInfo = null },
                title = { Text(info.title) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { editDialogInfo = null; info.onSave(text) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editDialogInfo = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        if (showWebDavPwdDialog) {
            var pwd by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showWebDavPwdDialog = false },
                title = { Text(stringResource(R.string.web_dav_pw)) },
                text = {
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showWebDavPwdDialog = false
                        if (pwd.isNotEmpty()) {
                            AppConfig.webDavPassword = pwd
                            viewModel.upWebDavConfig()
                        }
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWebDavPwdDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 0.dp),
    )
}

private data class EditDialogInfo(
    val title: String,
    val currentValue: String,
    val onSave: (String) -> Unit,
)
