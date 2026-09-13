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
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeConfigScreen(
    onBackClick: () -> Unit,
    onWelcomeImageClick: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    var customWelcome by remember {
        mutableStateOf(AppConfig.customWelcome)
    }
    val welcomeImage = remember {
        AppConfig.welcomeImage ?: ""
    }
    val welcomeImageDark = remember {
        AppConfig.welcomeImageDark ?: ""
    }
    var welcomeShowText by remember {
        mutableStateOf(AppConfig.welcomeShowText)
    }
    var welcomeShowIcon by remember {
        mutableStateOf(AppConfig.welcomeShowIcon)
    }
    var welcomeShowTextDark by remember {
        mutableStateOf(AppConfig.welcomeShowTextDark)
    }
    var welcomeShowIconDark by remember {
        mutableStateOf(AppConfig.welcomeShowIconDark)
    }

    val dayImageEnabled = welcomeImage.isNotEmpty()
    val nightImageEnabled = welcomeImageDark.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.welcome_style)) },
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
            // custom welcome switch
            item {
                SectionCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.custom_welcome),
                        description = stringResource(R.string.custom_welcome_summary),
                        checked = customWelcome,
                        onCheckedChange = { v ->
                            customWelcome = v
                            AppConfig.customWelcome = v
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
                        title = stringResource(R.string.background_image),
                        description = welcomeImage.ifEmpty { stringResource(R.string.select_image) },
                        onClick = { onWelcomeImageClick(false) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_welcome_text),
                        description = stringResource(R.string.welcome_text),
                        checked = welcomeShowText,
                        enabled = dayImageEnabled,
                        onCheckedChange = { v ->
                            welcomeShowText = v
                            AppConfig.welcomeShowText = v
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_icon),
                        description = stringResource(R.string.show_default_book_icon),
                        checked = welcomeShowIcon,
                        enabled = dayImageEnabled,
                        onCheckedChange = { v ->
                            welcomeShowIcon = v
                            AppConfig.welcomeShowIcon = v
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
                        title = stringResource(R.string.background_image),
                        description = welcomeImageDark.ifEmpty { stringResource(R.string.select_image) },
                        onClick = { onWelcomeImageClick(true) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_welcome_text),
                        description = stringResource(R.string.welcome_text),
                        checked = welcomeShowTextDark,
                        enabled = nightImageEnabled,
                        onCheckedChange = { v ->
                            welcomeShowTextDark = v
                            AppConfig.welcomeShowTextDark = v
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_icon),
                        description = stringResource(R.string.show_default_book_icon),
                        checked = welcomeShowIconDark,
                        enabled = nightImageEnabled,
                        onCheckedChange = { v ->
                            welcomeShowIconDark = v
                            AppConfig.welcomeShowIconDark = v
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
