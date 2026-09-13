package io.legado.app.ui.config.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.AppConfigStore
import io.legado.app.model.BookCover
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverConfigScreen(
    onBackClick: () -> Unit,
    onCoverRuleClick: () -> Unit,
    onDefaultCoverClick: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    var loadCoverOnlyWifi by remember {
        mutableStateOf(AppConfig.loadCoverOnlyWifi)
    }
    var useDefaultCover by remember {
        mutableStateOf(AppConfig.useDefaultCover)
    }
    var coverShowName by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.coverShowName, true))
    }
    var coverShowAuthor by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.coverShowAuthor, true))
    }
    var coverShowNameN by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.coverShowNameN, true))
    }
    var coverShowAuthorN by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.coverShowAuthorN, true))
    }
    val defaultCover = remember {
        AppConfigStore.getString(PreferKey.defaultCover) ?: ""
    }
    val defaultCoverDark = remember {
        AppConfigStore.getString(PreferKey.defaultCoverDark) ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_config)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp) // 新增上下内边距
        ) {
            // Basic settings
            item {
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.only_wifi),
                        description = stringResource(R.string.only_wifi_summary),
                        checked = loadCoverOnlyWifi,
                        onCheckedChange = { v ->
                            loadCoverOnlyWifi = v
                            AppConfig.loadCoverOnlyWifi = v
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.cover_rule),
                        description = stringResource(R.string.cover_rule_summary),
                        onClick = onCoverRuleClick,
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.use_default_cover),
                        description = stringResource(R.string.use_default_cover_s),
                        checked = useDefaultCover,
                        onCheckedChange = { v ->
                            useDefaultCover = v
                            AppConfig.useDefaultCover = v
                        },
                    )
                }
            }

            // Day category
            item {
                SectionTitle(stringResource(R.string.day))
            }
            item {
                SectionCard {
                    ClickableSettingItem(
                        title = stringResource(R.string.default_cover),
                        description = defaultCover.ifEmpty { stringResource(R.string.select_image) },
                        onClick = { onDefaultCoverClick(false) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.cover_show_name),
                        description = stringResource(R.string.cover_show_name_summary),
                        checked = coverShowName,
                        onCheckedChange = { v ->
                            coverShowName = v
                            context.putPrefBoolean(PreferKey.coverShowName, v)
                            BookCover.upDefaultCover()
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.cover_show_author),
                        description = stringResource(R.string.cover_show_author_summary),
                        checked = coverShowAuthor,
                        enabled = coverShowName,
                        onCheckedChange = { v ->
                            coverShowAuthor = v
                            context.putPrefBoolean(PreferKey.coverShowAuthor, v)
                            BookCover.upDefaultCover()
                        },
                    )
                }
            }

            // Night category
            item {
                SectionTitle(stringResource(R.string.night))
            }
            item {
                SectionCard {
                    ClickableSettingItem(
                        title = stringResource(R.string.default_cover),
                        description = defaultCoverDark.ifEmpty { stringResource(R.string.select_image) },
                        onClick = { onDefaultCoverClick(true) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.cover_show_name),
                        description = stringResource(R.string.cover_show_name_summary),
                        checked = coverShowNameN,
                        onCheckedChange = { v ->
                            coverShowNameN = v
                            context.putPrefBoolean(PreferKey.coverShowNameN, v)
                            BookCover.upDefaultCover()
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.cover_show_author),
                        description = stringResource(R.string.cover_show_author_summary),
                        checked = coverShowAuthorN,
                        enabled = coverShowNameN,
                        onCheckedChange = { v ->
                            coverShowAuthorN = v
                            context.putPrefBoolean(PreferKey.coverShowAuthorN, v)
                            BookCover.upDefaultCover()
                        },
                    )
                }
            }

            item { Modifier.padding(bottom = 16.dp) }
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
