package io.legado.app.ui.config.compose

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.common.compose.NumberPickerDialog
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.ui.common.compose.settingItem.ClickableSettingItem
import io.legado.app.ui.common.compose.settingItem.ListSettingItem
import io.legado.app.ui.common.compose.settingItem.SettingItem
import io.legado.app.ui.common.compose.settingItem.SwitchSettingItem
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getCompatDrawable
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString

private fun readColor(context: android.content.Context, key: String): Color {
    val v = context.getPrefInt(key, -1)
    return if (v != -1) Color(v) else Color.Unspecified
}

private fun Color.toArgb(): Int {
    val r = (this.red * 255).toInt()
    val g = (this.green * 255).toInt()
    val b = (this.blue * 255).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun drawableToImageBitmap(drawable: android.graphics.drawable.Drawable): ImageBitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap.asImageBitmap()
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

private fun loadMipmapIcon(context: android.content.Context, name: String): ImageBitmap? {
    val resId = context.resources.getIdentifier(name, "mipmap", context.packageName)
    if (resId == 0) return null
    val drawable = context.getCompatDrawable(resId) ?: return null
    return drawableToImageBitmap(drawable)
}

data class ColorKey(val prefKey: String, val titleRes: Int, val descRes: Int)

val dayColorKeys = listOf(
    ColorKey(PreferKey.cPrimary, R.string.primary, R.string.day_color_primary),
    ColorKey(PreferKey.cAccent, R.string.accent, R.string.day_color_accent),
    ColorKey(PreferKey.cBackground, R.string.background_color, R.string.day_background_color),
    ColorKey(PreferKey.cBBackground, R.string.navbar_color, R.string.day_navbar_color),
    ColorKey(PreferKey.cCardBg, R.string.card_background_color, R.string.card_bg_desc),
    ColorKey(PreferKey.cPopupBg, R.string.popup_background_color, R.string.popup_bg_desc),
    ColorKey(PreferKey.cTextAccent, R.string.text_accent_color, R.string.text_accent_desc),
)

val nightColorKeys = listOf(
    ColorKey(PreferKey.cNPrimary, R.string.primary, R.string.night_primary),
    ColorKey(PreferKey.cNAccent, R.string.accent, R.string.night_accent),
    ColorKey(PreferKey.cNBackground, R.string.background_color, R.string.night_background_color),
    ColorKey(PreferKey.cNBBackground, R.string.navbar_color, R.string.night_navbar_color),
    ColorKey(PreferKey.cNCardBg, R.string.card_background_color, R.string.card_bg_desc),
    ColorKey(PreferKey.cNPopupBg, R.string.popup_background_color, R.string.popup_bg_desc),
    ColorKey(PreferKey.cNTextAccent, R.string.text_accent_color, R.string.text_accent_desc),
)

@Composable
private fun ColorSection(
    title: String,
    colorKeys: List<ColorKey>,
    context: android.content.Context,
    onChange: () -> Unit,
    onRequestColorPicker: (String, Color, (Color) -> Unit) -> Unit,
    onBgImageClick: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
        SectionCard {
            colorKeys.forEach { ck ->
                ColorPreferenceItem(
                    title = stringResource(ck.titleRes),
                    description = stringResource(ck.descRes),
                    color = readColor(context, ck.prefKey),
                    onChange = { newColor ->
                        context.putPrefInt(ck.prefKey, newColor.toArgb())
                        onChange()
                    },
                    onRequestColorPicker = onRequestColorPicker,
                )
            }
            val isNight = colorKeys === nightColorKeys
            // background image
            val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
            val bgPath = context.getPrefString(bgKey, "") ?: ""
            ClickableSettingItem(
                title = stringResource(R.string.background_image),
                description = bgPath.ifEmpty { stringResource(R.string.select_image) },
                onClick = { onBgImageClick(isNight) },
            )
            // save theme
            val saveKey = if (isNight) "saveNightTheme" else "saveDayTheme"
            val saveDesc = if (isNight) R.string.save_night_theme_summary else R.string.save_day_theme_summary
            SaveThemeItem(
                prefKey = saveKey,
                summaryRes = saveDesc,
            )
        }
    }
}

@Composable
private fun ColorPreferenceItem(
    title: String,
    description: String,
    color: Color,
    onChange: (Color) -> Unit,
    onRequestColorPicker: (String, Color, (Color) -> Unit) -> Unit,
) {
    ClickableSettingItem(
        title = title,
        description = description,
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (color != Color.Unspecified) color else Color.Gray),
            )
        },
        onClick = {
            onRequestColorPicker(title, color, onChange)
        },
    )
}

@Composable
private fun SaveThemeItem(prefKey: String, summaryRes: Int) {
    var showDialog by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf("") }
    val context = LocalContext.current

    ClickableSettingItem(
        title = stringResource(R.string.save_theme_config),
        description = stringResource(summaryRes),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.theme_name)) },
            text = {
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (themeName.isNotBlank()) {
                        if (prefKey == "saveDayTheme") {
                            ThemeConfig.saveDayTheme(context, themeName)
                        } else {
                            ThemeConfig.saveNightTheme(context, themeName)
                        }
                        showDialog = false
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeConfigScreen(
    onBackClick: () -> Unit,
    onRequestColorPicker: (String, Color, (Color) -> Unit) -> Unit,
    onThemeListClick: () -> Unit,
    onBgImageClick: (Boolean) -> Unit,
    onThemeModeToggle: () -> Unit = {},
    onWelcomeStyleClick: () -> Unit = {},
    onCoverConfigClick: () -> Unit = {},
) {
    val context = LocalContext.current

    var transparentStatusBar by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.transparentStatusBar, false))
    }
    var immNavigationBar by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.immNavigationBar, false))
    }
    var predictiveBack by remember {
        mutableStateOf(context.getPrefBoolean(PreferKey.predictiveBack, true))
    }
    var barElevation by remember {
        mutableStateOf(AppConfig.elevation)
    }
    var fontScale by remember {
        mutableStateOf(context.getPrefInt(PreferKey.fontScale, 0))
    }
    var numPicker by remember { mutableStateOf<NumPickerInfo?>(null) }
    var launcherIcon by remember {
        mutableStateOf(context.getPrefString(PreferKey.launcherIcon, "ic_launcher") ?: "ic_launcher")
    }

    val iconNames = stringArrayResource(R.array.icon_names)
    val iconValues = stringArrayResource(R.array.icons)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onThemeModeToggle) {
                        Icon(
                            painterResource(R.drawable.ic_daytime),
                            contentDescription = stringResource(R.string.theme_mode),
                        )
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp) // 新增上下内边距
        ) {
            // 1. launcher icon (API 26+)
                item {
                    SectionCard {
                        if (Build.VERSION.SDK_INT >= 26) {
                            var iconDropdownExpanded by remember { mutableStateOf(false) }
                            val currentIconName = iconNames.getOrNull(iconValues.indexOf(launcherIcon)) ?: launcherIcon
                            val currentIconResId = context.resources.getIdentifier(
                                launcherIcon, "mipmap", context.packageName
                            )
                            SettingItem(
                                title = stringResource(R.string.change_icon),
                                description = stringResource(R.string.change_icon_summary),
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val iconBitmap = remember(launcherIcon) {
                                            loadMipmapIcon(context, launcherIcon)
                                        }
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = currentIconName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                },
                                expanded = iconDropdownExpanded,
                                onExpandChange = { iconDropdownExpanded = it },
                                dropdownMenu = { onDismiss ->
                                    iconValues.forEachIndexed { index, value ->
                                        val iconBitmap = remember(value) {
                                            loadMipmapIcon(context, value)
                                        }
                                        RoundDropdownMenuItem(
                                            text = iconNames.getOrNull(index) ?: value,
                                            onClick = {
                                                launcherIcon = value
                                                context.putPrefString(PreferKey.launcherIcon, value)
                                                LauncherIconHelp.changeIcon(value)
                                                onDismiss()
                                            },
                                            isSelected = launcherIcon == value,
                                            leadingIcon = if (iconBitmap != null) {
                                                {
                                                    Image(
                                                        bitmap = iconBitmap,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            } else null,
                                        )
                                    }
                                },
                            )
                        }
                    
            //2. welcome style

                        ClickableSettingItem(
                            title = stringResource(R.string.welcome_style),
                            description = stringResource(R.string.welcome_style_summary),
                            onClick = onWelcomeStyleClick,
                        )
                    
            // 3-4. switches

                        SwitchSettingItem(
                            title = stringResource(R.string.immersion_status_bar),
                            description = stringResource(R.string.status_bar_immersion),
                            checked = transparentStatusBar,
                            onCheckedChange = { v ->
                                transparentStatusBar = v
                                context.putPrefBoolean(PreferKey.transparentStatusBar, v)
                                postEvent(EventBus.RECREATE, "")
                            },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.imm_navigation_bar),
                            description = stringResource(R.string.imm_navigation_bar_s),
                            checked = immNavigationBar,
                            onCheckedChange = { v ->
                                immNavigationBar = v
                                context.putPrefBoolean(PreferKey.immNavigationBar, v)
                                postEvent(EventBus.RECREATE, "")
                            },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.predictive_back),
                            description = stringResource(R.string.predictive_back_summary),
                            checked = predictiveBack,
                            onCheckedChange = { v ->
                                predictiveBack = v
                                context.putPrefBoolean(PreferKey.predictiveBack, v)
                                context.toastOnUi(context.getString(R.string.restart_required))
                            },
                        )
                    
            // 5. bar elevation

                        ClickableSettingItem(
                            title = stringResource(R.string.bar_elevation),
                            description = stringResource(R.string.bar_elevation_s, barElevation.toString()),
                            onClick = {
                                numPicker = NumPickerInfo(
                                    title = context.getString(R.string.bar_elevation),
                                    value = barElevation,
                                    min = 0,
                                    max = 32,
                                    onConfirm = { v ->
                                        barElevation = v
                                        AppConfig.elevation = v
                                        context.toastOnUi(context.getString(R.string.restart_required))
                                    },
                                    defaultButton = {
                                        TextButton(onClick = {
                                            AppConfig.elevation = AppConst.sysElevation
                                            barElevation = AppConst.sysElevation
                                            numPicker = null
                                        }) {
                                            Text(stringResource(R.string.btn_default_s))
                                        }
                                    },
                                )
                            },
                        )
                    
            // 6. font scale
  
                        ClickableSettingItem(
                            title = stringResource(R.string.font_scale),
                            description = stringResource(
                                R.string.font_scale_summary,
                                (if (fontScale == 0) 10 else fontScale) / 10f,
                            ),
                            onClick = {
                                numPicker = NumPickerInfo(
                                    title = context.getString(R.string.font_scale),
                                    value = if (fontScale == 0) 10 else fontScale,
                                    min = 8,
                                    max = 16,
                                    onConfirm = { v ->
                                        fontScale = v
                                        context.putPrefInt(PreferKey.fontScale, v)
                                        context.toastOnUi(context.getString(R.string.restart_required))
                                    },
                                    defaultButton = {
                                        TextButton(onClick = {
                                            context.putPrefInt(PreferKey.fontScale, 0)
                                            fontScale = 0
                                            numPicker = null
                                        }) {
                                            Text(stringResource(R.string.btn_default_s))
                                        }
                                    },
                                )
                            },
                        )
                    

            // 7. cover config

                        ClickableSettingItem(
                            title = stringResource(R.string.cover_config),
                            description = stringResource(R.string.cover_config_summary),
                            onClick = onCoverConfigClick,
                        )
                    
            // 8. theme list

                        ClickableSettingItem(
                            title = stringResource(R.string.theme_list),
                            description = stringResource(R.string.theme_list_summary),
                            onClick = onThemeListClick,
                        )
                    }
                }

            // 9. Day theme colors
            item {
                ColorSection(
                    title = stringResource(R.string.day),
                    colorKeys = dayColorKeys,
                    context = context,
                    onChange = {
                        ThemeConfig.applyDayNight(context)
                    },
                    onRequestColorPicker = onRequestColorPicker,
                    onBgImageClick = onBgImageClick,
                )
            }

            // 10. Night theme colors
            item {
                ColorSection(
                    title = stringResource(R.string.night),
                    colorKeys = nightColorKeys,
                    context = context,
                    onChange = {
                        ThemeConfig.applyDayNight(context)
                    },
                    onRequestColorPicker = onRequestColorPicker,
                    onBgImageClick = onBgImageClick,
                )
            }

            item { Modifier.padding(bottom = 16.dp) }
        }

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
    }
}
