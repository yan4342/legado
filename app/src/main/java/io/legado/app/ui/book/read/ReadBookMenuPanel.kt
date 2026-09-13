package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.putPrefBoolean

/**
 * 阶段 4-M3：阅读菜单 Compose 化——严格对照原 view_read_menu.xml 布局：
 * 顶栏 = TitleBar（书名点击→详情 + addition 行：章节名/URL/源操作 chip）+ options 菜单图标
 * （换源·长按章节换源 / 刷新·长按三连 / 离线缓存 / ⋮ 溢出=never 项全量）；
 * 竖直亮度条 = 悬浮于顶栏与底栏之间的左/右（brightnessVwPos 切换），bgColor 50% 圆角 5dp，
 * 自动亮度开关 + 旋转 270° 滑条（255 级）+ 位置切换（textColor tint）；
 * 底栏 = FAB 行（搜索/自动翻页/替换规则/日夜切换）+ ll_bottom_bg（进度条行 + 功能行 60dp 列 1:2:1:2:1:2:1）。
 * 配色经 upColorConfig：沉浸模式（readBarStyleFollowPage+纯色背景）用页面配色，否则底栏色/主题 primary；
 * E-Ink：无动画 + 顶栏底边/底栏顶边 1dp divider 边线。
 * 动作全部回调 [ReadBookController]；弹窗保持弹窗形态（用户决定，不转 sheet）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.ReadBookMenuPanel(controller: ReadBookController) {
    val activity = controller.activity
    val state = controller.menuUiState
    val eInk = AppConfig.isEInkMode
    val fadeSpec = if (eInk) snap() else tween<Float>(250)
    val slideSpec = if (eInk) snap<IntOffset>() else tween<IntOffset>(250)

    // ── 配色（原 upColorConfig） ──
    val immersive = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
    val bgColor = if (immersive) {
        runCatching {
            Color(android.graphics.Color.parseColor(ReadBookConfig.durConfig.curBgStr()))
        }.getOrDefault(Color(activity.bottomBackground))
    } else {
        Color(activity.bottomBackground)
    }
    val textColor = if (immersive) {
        Color(ReadBookConfig.durConfig.curTextColor())
    } else {
        Color(activity.getPrimaryTextColor(ColorUtils.isColorLight(activity.bottomBackground)))
    }
    val lightTextColor = Color(ColorUtils.withAlpha(ColorUtils.lightenColor(textColor.toArgb()), 0.75f))
    // 顶栏底色：沉浸=页面配色；否则主题 primary（原 TitleBar ?attr/actionBarStyle）
    val topBarBg = if (immersive) bgColor else MaterialTheme.colorScheme.primary
    val topBarText = if (immersive) textColor else MaterialTheme.colorScheme.onPrimary

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 点击层（原 vw_menu_bg：透明、点击收起） ──
        androidx.compose.animation.AnimatedVisibility(visible = controller.menuVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { controller.hideMenu() },
                    ),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏（原 title_bar + title_bar_addition） ──
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.menuVisible,
                enter = slideInVertically(slideSpec, initialOffsetY = { -it }) + fadeIn(fadeSpec),
                exit = slideOutVertically(slideSpec, targetOffsetY = { -it }) + fadeOut(fadeSpec),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topBarBg)
                        .statusBarsPadding(),
                ) {
                    // 书名（原 toolbar.title，点击 → 详情）
                    Text(
                        text = state.bookName.orEmpty(),
                        color = topBarText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { controller.openBookInfoActivity() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    if (AppConfig.showReadTitleBarAddition) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (state.chapterName != null) {
                                    Text(
                                        text = state.chapterName,
                                        color = lightTextColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { controller.onChapterViewClick() },
                                    )
                                }
                                if (state.chapterUrl != null) {
                                    Text(
                                        text = state.chapterUrl,
                                        color = lightTextColor,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { controller.onChapterViewClick() },
                                    )
                                }
                            }
                            // 源操作 chip（原 tv_source_action：AccentBgTextView，非本地书显示）
                            if (!state.isLocalBook) {
                                Box {
                                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                                    Text(
                                        text = ReadBook.bookSource?.bookSourceName
                                            ?: stringResource(R.string.book_source),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .width(120.dp)
                                            .background(Color(activity.accentColor), RoundedCornerShape(2.dp))
                                            .clickable { sourceMenuExpanded = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    DropdownMenu(
                                        expanded = sourceMenuExpanded,
                                        onDismissRequest = { sourceMenuExpanded = false },
                                    ) {
                                        if (!ReadBook.bookSource?.loginUrl.isNullOrEmpty()) {
                                            RoundDropdownMenuItem(
                                                text = stringResource(R.string.login),
                                                onClick = { controller.showLogin() },
                                            )
                                            RoundDropdownMenuItem(
                                                text = stringResource(R.string.chapter_pay),
                                                onClick = { controller.payAction() },
                                            )
                                        }
                                        RoundDropdownMenuItem(
                                            text = stringResource(R.string.edit_source),
                                            onClick = { controller.openSourceEditActivity() },
                                        )
                                        RoundDropdownMenuItem(
                                            text = stringResource(R.string.disable_source),
                                            onClick = { controller.disableSource() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 顶栏 options 菜单图标（原 TitleBar 承载 book_read 菜单：3 always 图标 + 溢出）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 换源（点击=书籍换源；长按=章节换源/书籍换源）
                        Box {
                            var changeSourceMenu by remember { mutableStateOf(false) }
                            Icon(
                                painter = painterResource(R.drawable.ic_exchange),
                                contentDescription = stringResource(R.string.change_origin),
                                tint = topBarText,
                                modifier = Modifier
                                    .size(24.dp)
                                    .combinedClickable(
                                        onClick = { controller.showBookChangeSource() },
                                        onLongClick = { changeSourceMenu = true },
                                    ),
                            )
                            DropdownMenu(
                                expanded = changeSourceMenu,
                                onDismissRequest = { changeSourceMenu = false },
                            ) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.chapter_change_source),
                                    onClick = { controller.showChapterChangeSource() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.book_change_source),
                                    onClick = { controller.showBookChangeSource() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 刷新（点击=当前章；长按=当前章/后文/全部）
                        Box {
                            var refreshMenu by remember { mutableStateOf(false) }
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh_black_24dp),
                                contentDescription = stringResource(R.string.refresh),
                                tint = topBarText,
                                modifier = Modifier
                                    .size(24.dp)
                                    .combinedClickable(
                                        onClick = { controller.refreshCurrentChapter() },
                                        onLongClick = { refreshMenu = true },
                                    ),
                            )
                            DropdownMenu(expanded = refreshMenu, onDismissRequest = { refreshMenu = false }) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.menu_refresh_dur),
                                    onClick = { controller.refreshCurrentChapter() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.menu_refresh_after),
                                    onClick = { controller.refreshContentAfter() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.menu_refresh_all),
                                    onClick = { controller.refreshContentAll() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 离线缓存
                        Icon(
                            painter = painterResource(R.drawable.ic_download_line),
                            contentDescription = stringResource(R.string.offline_cache),
                            tint = topBarText,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { controller.showDownloadDialog() },
                        )
                        Spacer(Modifier.weight(1f))
                        // 溢出菜单（原 showAsAction=never 项全量）
                        Box {
                            var overflowMenuExpanded by remember { mutableStateOf(false) }
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.more_menu),
                                tint = topBarText,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { overflowMenuExpanded = true },
                            )
                            RoundDropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) { dismiss ->
                                val book = ReadBook.book
                                val onLine = book != null && !book.isLocal
                                if (book?.isLocal == true) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.set_charset),
                                        onClick = { dismiss(); controller.showCharsetConfig() },
                                    )
                                }
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.bookmark_add),
                                    onClick = { dismiss(); controller.addBookmark() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.edit_content),
                                    onClick = { dismiss(); controller.showEditContent() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.book_page_anim),
                                    onClick = {
                                        dismiss()
                                        controller.showPageAnimConfig {
                                            controller.upPageAnim()
                                            ReadBook.loadContent(false)
                                        }
                                    },
                                )
                                if (ReadBook.inBookshelf && AppWebDav.isOk) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.get_book_progress),
                                        onClick = { dismiss(); controller.getBookProgress() },
                                    )
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.cover_book_progress),
                                        onClick = { dismiss(); controller.coverBookProgress() },
                                    )
                                }
                                if (onLine) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.reverse_content),
                                        onClick = { dismiss(); controller.reverseContent() },
                                    )
                                }
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.simulated_reading),
                                    onClick = { dismiss(); controller.showSimulatedReading() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.replace_rule_title),
                                    isSelected = book?.getUseReplaceRule() == true,
                                    onClick = { dismiss(); controller.changeReplaceRuleState() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.same_title_removed),
                                    isSelected = ReadBook.curTextChapter?.sameTitleRemoved == true,
                                    onClick = { dismiss(); controller.reverseRemoveSameTitle() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.re_segment),
                                    isSelected = book?.getReSegment() == true,
                                    onClick = { dismiss(); controller.toggleReSegment() },
                                )
                                if (book?.isEpub == true) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.del_ruby_tag),
                                        isSelected = book.getDelTag(Book.rubyTag),
                                        onClick = { dismiss(); controller.toggleDelTag(Book.rubyTag) },
                                    )
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.del_h_tag),
                                        isSelected = book.getDelTag(Book.hTag),
                                        onClick = { dismiss(); controller.toggleDelTag(Book.hTag) },
                                    )
                                }
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.image_style),
                                    onClick = { dismiss(); controller.showImageStyleSelector() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.update_toc),
                                    onClick = { dismiss(); controller.updateToc() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.re_chapter),
                                    isSelected = book?.reChapterEnabled == true,
                                    onClick = { dismiss(); controller.handleReChapter() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.effective_replaces),
                                    onClick = { dismiss(); controller.showEffectiveReplaces() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.log),
                                    onClick = { dismiss(); controller.showLog() },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.help),
                                    onClick = { dismiss(); controller.showHelp() },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (eInk) {
                        HorizontalDivider(color = colorResource(R.color.divider), thickness = 1.dp)
                    }
                }
            }

            // ── 中间区（亮度条容器，原 constraint：title_bar ↔ bottom_menu 之间） ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = controller.menuVisible, enter = fadeIn(), exit = fadeOut()) {
                    val barAlign =
                        if (AppConfig.brightnessVwPos) Alignment.CenterEnd else Alignment.CenterStart
                    Column(
                        modifier = Modifier
                            .align(barAlign)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .background(bgColor.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                            .width(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        var vBrightness by remember(controller.menuVisible) {
                            mutableFloatStateOf(AppConfig.readBrightness.toFloat())
                        }
                        // 自动亮度（原 iv_brightness_auto：accent=跟随 / disabled=手动）
                        Icon(
                            painter = painterResource(R.drawable.ic_brightness_auto),
                            contentDescription = stringResource(R.string.brightness_auto),
                            tint = if (controller.brightnessAuto()) {
                                Color(activity.accentColor)
                            } else {
                                textColor.copy(alpha = 0.38f)
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                                .clickable {
                                    activity.putPrefBoolean("brightnessAuto", !controller.brightnessAuto())
                                    controller.updateMenuState()
                                },
                        )
                        // 竖直滑条（原 VerticalSeekBar CW270：下小上大，max 255）
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Slider(
                                value = vBrightness.coerceIn(0f, 255f),
                                onValueChange = {
                                    vBrightness = it
                                    controller.setScreenBrightness(it)
                                },
                                onValueChangeFinished = { AppConfig.readBrightness = vBrightness.toInt() },
                                valueRange = 0f..255f,
                                enabled = !controller.brightnessAuto(),
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = 270f }
                                    .width(180.dp),
                            )
                        }
                        // 位置切换（原 vw_brightness_pos_adjust：initView 刷成 textColor）
                        Icon(
                            painter = painterResource(R.drawable.ic_swap_horiz),
                            contentDescription = "调整位置",
                            tint = textColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                                .clickable { AppConfig.brightnessVwPos = !AppConfig.brightnessVwPos },
                        )
                    }
                }
            }

            // ── 底栏（原 bottom_menu） ──
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.menuVisible,
                enter = slideInVertically(slideSpec, initialOffsetY = { it }) + fadeIn(fadeSpec),
                exit = slideOutVertically(slideSpec, targetOffsetY = { it }) + fadeOut(fadeSpec),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // FAB 行（原 ll_floating_button：4 mini FAB，margin 8，weight 均分）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MenuMiniFab(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search_content),
                            containerColor = bgColor,
                            contentColor = textColor,
                            onClick = { controller.hideMenu { controller.openSearchActivity(null) } },
                        )
                        Spacer(Modifier.weight(1f))
                        MenuMiniFab(
                            painter = painterResource(
                                if (state.autoPageActive) R.drawable.ic_auto_page_stop else R.drawable.ic_auto_page
                            ),
                            contentDescription = stringResource(R.string.auto_next_page),
                            containerColor = bgColor,
                            contentColor = textColor,
                            onClick = { controller.hideMenu { controller.autoPage() } },
                        )
                        Spacer(Modifier.weight(1f))
                        MenuMiniFab(
                            painter = painterResource(R.drawable.ic_find_replace),
                            contentDescription = stringResource(R.string.replace_rule_title),
                            containerColor = bgColor,
                            contentColor = textColor,
                            onClick = { controller.openReplaceRule() },
                        )
                        Spacer(Modifier.weight(1f))
                        MenuMiniFab(
                            painter = painterResource(
                                if (AppConfig.isNightTheme) R.drawable.ic_daytime else R.drawable.ic_brightness
                            ),
                            contentDescription = stringResource(R.string.dark_theme),
                            containerColor = bgColor,
                            contentColor = textColor,
                            onClick = {
                                AppConfig.isNightTheme = !AppConfig.isNightTheme
                                ThemeConfig.applyDayNight(activity)
                            },
                        )
                    }
                    // ll_bottom_bg
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor),
                    ) {
                        if (eInk) {
                            HorizontalDivider(color = colorResource(R.color.divider), thickness = 1.dp)
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(vertical = 5.dp),
                        ) {
                            // 进度条行（原章节设置行：20dp 水平边距，5dp 垂直）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.previous_chapter),
                                    color = if (state.preEnabled) textColor else textColor.copy(alpha = 0.38f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable(enabled = state.preEnabled) {
                                            ReadBook.moveToPrevChapter(upContent = true, toLast = false)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                )
                                var dragProgress by remember(state.seekProgress) {
                                    mutableIntStateOf(state.seekProgress)
                                }
                                AppSlider(
                                    value = dragProgress.toFloat()
                                        .coerceIn(0f, state.seekMax.coerceAtLeast(1).toFloat()),
                                    onValueChange = { dragProgress = it.toInt() },
                                    onValueChangeFinished = { controller.onSeekReleased(dragProgress) },
                                    valueRange = 0f..state.seekMax.coerceAtLeast(1).toFloat(),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(R.string.next_chapter),
                                    color = if (state.nextEnabled) textColor else textColor.copy(alpha = 0.38f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clickable(enabled = state.nextEnabled) {
                                            ReadBook.moveToNextChapter(true)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                )
                            }
                            // 功能行（原 1:2:1:2:1:2:1 权重行）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.weight(1f))
                                MenuBarButton(
                                    label = stringResource(R.string.chapter_list),
                                    tint = textColor,
                                    painter = painterResource(R.drawable.ic_toc),
                                    onClick = { controller.hideMenu { controller.openChapterList() } },
                                )
                                Spacer(Modifier.weight(2f))
                                MenuBarButton(
                                    label = stringResource(R.string.read_aloud),
                                    tint = textColor,
                                    painter = painterResource(R.drawable.ic_read_aloud),
                                    onClick = { controller.hideMenu { controller.onClickReadAloud() } },
                                    onLongClick = { controller.hideMenu { controller.showReadAloudDialog() } },
                                )
                                Spacer(Modifier.weight(2f))
                                MenuBarButton(
                                    label = stringResource(R.string.interface_setting),
                                    tint = textColor,
                                    painter = painterResource(R.drawable.ic_interface_setting),
                                    onClick = { controller.hideMenu { controller.showReadStyle() } },
                                )
                                Spacer(Modifier.weight(2f))
                                MenuBarButton(
                                    label = stringResource(R.string.setting),
                                    tint = textColor,
                                    painter = painterResource(R.drawable.ic_settings),
                                    onClick = { controller.hideMenu { controller.showMoreSetting() } },
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 迷你 FAB（对齐原 fabSize=mini 的 M3 实际渲染：56dp、elevation 2dp 恒定） */
@Composable
private fun MenuMiniFab(
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    icon: ImageVector? = null,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 2.dp,
            pressedElevation = 2.dp,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        if (painter != null) {
            Icon(painter, contentDescription)
        } else {
            Icon(requireNotNull(icon), contentDescription)
        }
    }
}

/** 底栏"图标+文字"按钮（原 60dp 列：图标 20dp + 标签 12sp，paddingBottom 7dp） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuBarButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (painter != null) {
            Icon(painter, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        } else {
            Icon(requireNotNull(icon), contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
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
