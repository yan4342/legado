package io.legado.app.ui.book.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.AppSlider

@Composable
internal fun BoxScope.MangaFooter(state: MangaReaderUiState) {
    val page = state.pages.getOrNull(state.currentItemIndex) as? MangaReaderItemUi.Page ?: return
    val settings = state.settings
    if (settings.hideFooter) return
    val progress = if (page.chapterCount <= 0 || page.pageCount <= 0) 0.0 else {
        (page.chapterIndex.toDouble() + (page.pageIndex + 1.0) / page.pageCount) / page.chapterCount
    }
    val pageLabel = stringResource(R.string.manga_reader_page_label)
    val chapterLabel = stringResource(R.string.manga_reader_chapter_label)
    val progressLabel = stringResource(R.string.manga_reader_progress_label)
    val text = buildString {
        if (!settings.hideChapterName) append(page.chapterName).append(' ')
        if (!settings.hidePageNumber) {
            if (!settings.hidePageNumberLabel) append(pageLabel).append(' ')
            append("${page.pageIndex + 1}/${page.pageCount} ")
        }
        if (!settings.hideChapter) {
            if (!settings.hideChapterLabel) append(chapterLabel).append(' ')
            append("${page.chapterIndex + 1}/${page.chapterCount} ")
        }
        if (!settings.hideProgress) {
            if (!settings.hideProgressLabel) append(progressLabel).append(' ')
            append("%.1f%%".format((progress * 100).coerceAtMost(100.0)))
        }
    }.trim()
    val alignment = when (settings.footerAlignment) {
        1 -> Alignment.BottomCenter
        2 -> Alignment.BottomEnd
        else -> Alignment.BottomStart
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .align(alignment)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * 阅读器菜单：顶栏 + 底栏，按本项目 Compose 页面规范构建——
 * 顶栏用 Material3 [TopAppBar]（主色容器，与搜索页等一致），
 * 底栏用 Material3 [Surface]，不再依赖 fork 自造的 ReaderMenu* 组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.MangaReaderMenu(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val readingPageDescription = stringResource(R.string.manga_reader_page_semantics)
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    // 底栏配色对齐文本阅读器默认（非沉浸）底栏：背景 = 主题底栏色 bottomBackground，
    // 文字色按底栏背景明暗选择（ReadMenu: getPrimaryTextColor(isColorLight(bgColor))）
    val context = LocalContext.current
    val barBg = Color(context.bottomBackground)
    val barText = Color(context.getPrimaryTextColor(ColorUtils.isColorLight(barBg.toArgb())))

    // 半透明点击层：点空白收起菜单
    AnimatedVisibility(visible = state.menuVisible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onIntent(MangaReaderIntent.HideMenu) },
                )
        )
    }

    // 顶栏
    AnimatedVisibility(
        visible = state.menuVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        state.bookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenBookInfo) },
                        ),
                    )
                    Text(
                        state.chapterName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenChapterUrl) },
                        ),
                    )
                    if (state.sourceName.isNotBlank()) {
                        Text(
                            state.sourceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.combinedClickable(
                                onClick = { onIntent(MangaReaderIntent.ChangeSource) },
                            ),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { onIntent(MangaReaderIntent.ExitReader) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
            actions = {
                IconButton(onClick = { onIntent(MangaReaderIntent.ChangeSource) }) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(R.string.change_origin),
                    )
                }
                IconButton(onClick = { onIntent(MangaReaderIntent.RefreshChapter) }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                    )
                }
                // 溢出菜单：预下载数量
                Box {
                    IconButton(onClick = { overflowMenuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                        )
                    }
                    RoundDropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                    ) { dismiss ->
                        val preload = state.settings.preDownloadCount
                        listOf(5, 10, 15, 20, 30).forEach { count ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.pre_download_m, count),
                                isSelected = preload == count,
                                onClick = {
                                    dismiss()
                                    onIntent(
                                        MangaReaderIntent.UpdateSetting(
                                            MangaReaderSettingKey.PRE_DOWNLOAD,
                                            count
                                        )
                                    )
                                },
                            )
                        }
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.manga_reader_no_preload),
                            isSelected = preload == 0,
                            onClick = {
                                dismiss()
                                onIntent(
                                    MangaReaderIntent.UpdateSetting(
                                        MangaReaderSettingKey.PRE_DOWNLOAD,
                                        0
                                    )
                                )
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
    }

    // 底栏：对齐文本阅读器 view_read_menu.xml 的规范——
    // 底栏本体用项目自己的底栏色 bottomBackground，上方一行 4 个 FAB 悬浮按钮。
    // 设置（界面/设置/自动阅读）改为模态弹层 MangaSettingsSheet，底栏不再内嵌展开。
    // Surface 不加 inset，背景一直铺到屏幕底沿（edge-to-edge），导航栏 inset 加在内容上，
    // 与 Material3 NavigationBar 的做法一致，避免底部留空隙。
    AnimatedVisibility(
        visible = state.menuVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(Modifier.fillMaxWidth()) {
            MangaReaderFabRow(state, onIntent, barBg, barText)
            // 底栏本体
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = barBg,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    if (state.pageCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.previous_chapter),
                                color = barText,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { onIntent(MangaReaderIntent.PreviousChapter) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            )
                            AppSlider(
                                value = state.currentPage.toFloat()
                                    .coerceIn(0f, (state.pageCount - 1).toFloat()),
                                onValueChange = {
                                    onIntent(MangaReaderIntent.SeekToPage(it.toInt()))
                                },
                                valueRange = 0f..(state.pageCount - 1).toFloat(),
                                steps = (state.pageCount - 2).coerceAtLeast(0),
                                accessibilityLabel = readingPageDescription,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.next_chapter),
                                color = barText,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { onIntent(MangaReaderIntent.NextChapter) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // 四个"图标+文字"按钮：目录 / 自动阅读 / 界面 / 设置，
                    // 布局参数对齐文本阅读器（60dp 列宽，权重 1:2:1:2:1:2:1 均匀分布）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        MangaReaderBottomBarButton(
                            label = stringResource(R.string.chapter_list),
                            tint = barText,
                            // 目录图标与文本阅读器一致（ic_toc）
                            painter = painterResource(R.drawable.ic_toc),
                            onClick = { onIntent(MangaReaderIntent.OpenCatalog) },
                        )
                        Spacer(Modifier.weight(2f))
                        MangaReaderBottomBarButton(
                            icon = Icons.Filled.AutoMode,
                            label = stringResource(R.string.manga_reader_auto_short),
                            tint = if (state.autoReadEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                barText
                            },
                            onClick = { onIntent(MangaReaderIntent.ToggleAutoRead) },
                            onLongClick = {
                                onIntent(
                                    MangaReaderIntent.OpenSettings(
                                        MangaReaderSettingsCategory.AUTO_READ
                                    )
                                )
                            },
                        )
                        Spacer(Modifier.weight(2f))
                        MangaReaderBottomBarButton(
                            icon = Icons.Filled.Tune,
                            label = stringResource(R.string.interface_setting),
                            tint = barText,
                            onClick = {
                                onIntent(
                                    MangaReaderIntent.OpenSettings(
                                        MangaReaderSettingsCategory.INTERFACE
                                    )
                                )
                            },
                        )
                        Spacer(Modifier.weight(2f))
                        MangaReaderBottomBarButton(
                            icon = Icons.Outlined.Settings,
                            label = stringResource(R.string.setting),
                            tint = barText,
                            onClick = {
                                onIntent(
                                    MangaReaderIntent.OpenSettings(
                                        MangaReaderSettingsCategory.SETTINGS
                                    )
                                )
                            },
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 底栏上方悬浮行：4 个 FAB（点击区域设置 / 离线缓存 / 滤镜 / 日夜间切换），
 * 对齐文本阅读器 view_read_menu.xml 的 ll_floating_button——
 * 背景=底栏色、图标=primaryText、elevation=2dp、等权间距。
 * 离线缓存为计划中功能（占位，点击提示"该功能计划中"）。
 */
@Composable
private fun MangaReaderFabRow(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    container: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        // 点击区域设置：直接打开全屏 3×3 遮罩编辑器
        MangaReaderMiniFab(
            icon = Icons.Outlined.TouchApp,
            contentDescription = stringResource(R.string.manga_reader_click_area_short),
            containerColor = container,
            contentColor = content,
            onClick = { onIntent(MangaReaderIntent.OpenClickAreaConfig) },
        )
        Spacer(Modifier.weight(1f))
        MangaReaderMiniFab(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = stringResource(R.string.manga_reader_offline_cache),
            containerColor = container,
            contentColor = content,
            onClick = { onIntent(MangaReaderIntent.OpenCacheActions) },
        )
        Spacer(Modifier.weight(1f))
        // 滤镜：位于「界面」分类（页脚 + 滤镜/亮度），直接打开该分类
        MangaReaderMiniFab(
            icon = Icons.Outlined.FilterBAndW,
            contentDescription = stringResource(R.string.manga_reader_filter_short),
            containerColor = container,
            contentColor = content,
            onClick = {
                onIntent(MangaReaderIntent.OpenSettings(MangaReaderSettingsCategory.INTERFACE))
            },
        )
        Spacer(Modifier.weight(1f))
        // 日夜间切换：图标随当前主题切换（同文本阅读器 fabNightTheme 的 ic_brightness/ic_daytime）
        MangaReaderMiniFab(
            painter = painterResource(
                if (AppConfig.isNightTheme) R.drawable.ic_daytime else R.drawable.ic_brightness
            ),
            contentDescription = stringResource(R.string.manga_reader_toggle_day_night),
            containerColor = container,
            contentColor = content,
            onClick = { onIntent(MangaReaderIntent.ToggleDayNight) },
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun MangaReaderMiniFab(
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 2.dp,
            pressedElevation = 2.dp,
        ),
        // 与文本阅读器悬浮行按钮一致：M3 主题默认样式 fabCustomSize=56dp 会覆盖布局里的
        // fabSize="mini"，实际渲染为 56dp 常规 FAB（16dp 圆角、24dp 图标）——
        // 即 M3 FloatingActionButton 默认参数，不固定 size（等价原版 wrap_content）
    ) {
        if (painter != null) {
            Icon(painter, contentDescription)
        } else {
            Icon(requireNotNull(icon), contentDescription)
        }
    }
}

/** 底栏"图标+文字"按钮：60dp 列宽，图标 20dp + 标签 12sp，对齐文本阅读器底栏按钮样式 */
@Composable
private fun MangaReaderBottomBarButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (painter != null) {
            Icon(
                painter,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                requireNotNull(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = tint,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BoxScope.ReaderStatusOverlay(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    if (state.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    state.errorMessage?.let { message ->
        val errorText = when (message) {
            is MangaReaderText.Dynamic -> message.value
            is MangaReaderText.Resource -> stringResource(
                message.resId,
                *message.args.toTypedArray(),
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorText, modifier = Modifier.padding(24.dp))
                Button(onClick = { onIntent(MangaReaderIntent.Retry) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

/**
 * 点击区域设置：全屏 3×3 遮罩编辑器（与主仓库原版 ClickActionConfigDialog、
 * fork ClickActionConfigSheet 一致的交互）——整屏分成 9 个区域格子，
 * 每格显示当前动作，点击格子弹出动作选择器。
 */
@Composable
internal fun BoxScope.MangaClickAreaConfigOverlay(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val labels = mapOf(
        -1 to stringResource(R.string.non_action),
        0 to stringResource(R.string.manga_reader_menu),
        1 to stringResource(R.string.manga_reader_next_page),
        2 to stringResource(R.string.manga_reader_previous_page),
        3 to stringResource(R.string.next_chapter),
        4 to stringResource(R.string.previous_chapter),
    )
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    BackHandler { onIntent(MangaReaderIntent.DismissDialog) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.click_regional_config),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onIntent(MangaReaderIntent.DismissDialog) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                    )
                }
            }
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val action = state.settings.clickActions.getOrElse(index) { 0 }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                                .clickable { editingIndex = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                labels[action] ?: labels.getValue(-1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // 动作选择器：点击格子后弹出
        editingIndex?.let { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { editingIndex = null },
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.select_action),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    labels.forEach { (action, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onIntent(MangaReaderIntent.UpdateClickAction(index, action))
                                    editingIndex = null
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.settings.clickActions.getOrElse(index) { 0 } == action) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
