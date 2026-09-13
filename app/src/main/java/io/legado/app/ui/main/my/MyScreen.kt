package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.ListSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem

@Composable
fun MyScreen(
    themeMode: String,
    webServiceRunning: Boolean,
    webServiceAddress: String,
    onBookSourceManage: () -> Unit,
    onTxtTocRuleManage: () -> Unit,
    onReplaceManage: () -> Unit,
    onDictRuleManage: () -> Unit,
    onAiDictRuleManage: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToChat: () -> Unit,
    onBookmark: () -> Unit,
    onReadRecord: () -> Unit,
    onBackupRestore: () -> Unit,
    onThemeSetting: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onOtherSetting: () -> Unit,
    onWebServiceChange: (Boolean) -> Unit,
    onWebServiceLongClick: () -> Unit,
    onFileManage: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeModeEntries = stringArrayResource(R.array.theme_mode)
    val themeModeValues = stringArrayResource(R.array.theme_mode_v)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 内容源管理
        item {
            CategorySection(stringResource(R.string.content_source_manage)) {
                ClickableSettingItem(
                    title = stringResource(R.string.book_source_manage),
                    description = stringResource(R.string.book_source_manage_desc),
                    painter = painterResource(R.drawable.ic_cfg_source),
                    onClick = onBookSourceManage,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.txt_toc_rule),
                    description = stringResource(R.string.config_txt_toc_rule),
                    painter = painterResource(R.drawable.ic_cfg_source),
                    onClick = onTxtTocRuleManage,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.replace_purify),
                    description = stringResource(R.string.replace_purify_desc),
                    painter = painterResource(R.drawable.ic_cfg_replace),
                    onClick = onReplaceManage,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.dict_rule),
                    description = stringResource(R.string.config_dict_rule),
                    painter = painterResource(R.drawable.ic_translate),
                    onClick = onDictRuleManage,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.ai_config),
                    painter = painterResource(R.drawable.ic_ai_setting),
                    onClick = onNavigateToAiSettings,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.ai_dict_rule),
                    description = stringResource(R.string.config_ai_dict_rule),
                    painter = painterResource(R.drawable.ic_ai_dictionary_rule),
                    onClick = onAiDictRuleManage,
                )
            }
        }

        // AI
        item {
            CategorySection("AI") {
                ClickableSettingItem(
                    title = stringResource(R.string.ai_chat),
                    painter = painterResource(R.drawable.ic_ai_chat),
                    onClick = onNavigateToChat,
                )
            }
        }

        // 我的数据
        item {
            CategorySection(stringResource(R.string.my_data)) {
                ClickableSettingItem(
                    title = stringResource(R.string.bookmark),
                    description = stringResource(R.string.all_bookmark),
                    painter = painterResource(R.drawable.ic_bookmark),
                    onClick = onBookmark,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.read_record),
                    description = stringResource(R.string.read_record_summary),
                    painter = painterResource(R.drawable.ic_history),
                    onClick = onReadRecord,
                )
            }
        }

        // 应用设置
        item {
            CategorySection(stringResource(R.string.setting)) {
                ClickableSettingItem(
                    title = stringResource(R.string.backup_restore),
                    description = stringResource(R.string.web_dav_set_import_old),
                    painter = painterResource(R.drawable.ic_cfg_backup),
                    onClick = onBackupRestore,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.theme_setting),
                    description = stringResource(R.string.theme_setting_s),
                    painter = painterResource(R.drawable.ic_cfg_theme),
                    onClick = onThemeSetting,
                )
                ListSettingItem(
                    title = stringResource(R.string.theme_mode),
                    selectedValue = themeMode,
                    displayEntries = themeModeEntries,
                    entryValues = themeModeValues,
                    description = stringResource(R.string.theme_mode_desc),
                    painter = painterResource(R.drawable.ic_cfg_theme),
                    onValueChange = onThemeModeChange,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.other_setting),
                    description = stringResource(R.string.other_setting_s),
                    painter = painterResource(R.drawable.ic_cfg_other),
                    onClick = onOtherSetting,
                )
                SwitchSettingItem(
                    title = stringResource(R.string.web_service),
                    checked = webServiceRunning,
                    description = if (webServiceRunning) webServiceAddress
                    else stringResource(R.string.web_service_desc),
                    painter = painterResource(R.drawable.ic_cfg_web),
                    onCheckedChange = onWebServiceChange,
                )
            }
        }

        // 工具与关于
        item {
            CategorySection(stringResource(R.string.tool_about)) {
                ClickableSettingItem(
                    title = stringResource(R.string.file_manage),
                    description = stringResource(R.string.file_manage_summary),
                    painter = painterResource(R.drawable.ic_folder_outline),
                    onClick = onFileManage,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.about),
                    painter = painterResource(R.drawable.ic_cfg_about),
                    onClick = onAbout,
                )
                ClickableSettingItem(
                    title = stringResource(R.string.exit),
                    painter = painterResource(R.drawable.ic_exit),
                    onClick = onExit,
                )
            }
        }

        item { Modifier.padding(bottom = 16.dp) }
    }
}

@Composable
private fun CategorySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard {
            content()
        }
    }
}
