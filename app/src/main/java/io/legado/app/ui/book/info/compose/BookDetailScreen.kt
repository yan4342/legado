package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.lib.theme.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    book: Book,
    latestChapterTitle: String?,
    totalChapterNum: Int,
    onBack: () -> Unit,
    onReadClick: () -> Unit,
    onShelfClick: () -> Unit,
    inBookshelf: Boolean = false,
    onTocClick: () -> Unit,
    onEditClick: () -> Unit,
    onMenuAction: (Int) -> Unit,
    canUpdate: Boolean = true,
    splitLongChapter: Boolean = true,
    isLoginVisible: Boolean = false,
    isSourceVariableVisible: Boolean = false,
    isBookVariableVisible: Boolean = false,
    coverTransitionName: String? = null,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    characters: List<BookDetailCharacterUi> = emptyList(),
    onCharacterClick: (String) -> Unit = {},
    onOpenVoiceCasting: () -> Unit = {},
    onOpenCharacterList: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
    onOpenWorldBook: () -> Unit = {},
    onOpenOutline: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    // 状态栏高度，用于让模糊背景延伸到状态栏区域
    val statusBarHeightDp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val context = LocalContext.current
    val accent = remember {
        try {
            Color(context.accentColor)
        } catch (_: Exception) {
            Color(0xFF263238.toInt())
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isLandscape = maxWidth > maxHeight
        // 【Hero 区域总高度】— 必须与 HeroHeader 中 heroTotalHeight 保持一致
        // 竖屏：260dp + 状态栏（大封面展示）；横屏：220dp + 状态栏（加大模糊背景）
        val heroHeight = if (isLandscape) 220.dp + statusBarHeightDp
            else 260.dp + statusBarHeightDp
        // 【Surface 与 Hero 的重叠量】— chip 始终单行，固定 52dp
        val surfaceOverlap = when {
            isLandscape -> 30.dp
            else -> 42.dp
        }

        // 【顶部深色渐变遮罩】— 叠加在模糊背景上，确保文字与图标可读性
        // 竖屏：160dp + 状态栏；横屏：140dp + 状态栏（配合更大的模糊背景）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 140.dp + statusBarHeightDp else 160.dp + statusBarHeightDp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        HeroHeader(
            book = book,
            modifier = Modifier.align(Alignment.TopCenter),
            isLandscape = isLandscape,
            coverTransitionName = coverTransitionName,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey,
        )

        // 【底部内容卡片】— 圆角 Surface 承载书籍信息、目录、操作按钮
        // 高度 = 屏幕高度 - Hero高度 + 重叠量，surfaceOverlap 控制"抬高"效果
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((maxHeight - heroHeight + surfaceOverlap).coerceAtLeast(0.dp)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 【可滚动内容区】— 信息标签、简介、目录
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                        .verticalScroll(scrollState),
                ) {
                    // 【信息标签行】— 分类、字数、阅读进度、更新时间
                    // 本地书优先展示文件类型芯片（如 txt / epub）
                    val kindList = remember(book.isLocal, book.originName, book.kind, book.wordCount) {
                        if (book.isLocal) {
                            val ext = book.originName
                                .substringAfterLast('.', missingDelimiterValue = "")
                                .lowercase()
                                .takeIf { it.isNotBlank() }
                            buildList {
                                if (ext != null) add(ext)
                                addAll(book.getKindList())
                            }
                        } else {
                            book.getKindList()
                        }
                    }
                    InfoChipRow(
                        kindList = kindList,
                        wordCount = book.wordCount,
                        readProgress = if (totalChapterNum > 0) {
                            (book.durChapterIndex * 100 / totalChapterNum)
                        } else 0,
                        lastUpdateTime = book.latestChapterTime,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    
                    // 【本书人物】— 横滑角色卡（约 3 张可见）+ 人物列表/关系网/世界书/大纲
                    BookDetailCastSection(
                        characters = characters,
                        onCharacterClick = onCharacterClick,
                        onOpenVoiceCasting = onOpenVoiceCasting,
                        onOpenCharacterList = onOpenCharacterList,
                        onOpenNetwork = onOpenNetwork,
                        onOpenWorldBook = onOpenWorldBook,
                        onOpenOutline = onOpenOutline,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    // 【简介卡片】— 书籍内容简介
                    val intro = book.getDisplayIntro()
                    IntroCard(
                        intro = intro?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_intro),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    // 【目录卡片】— 最新章节 + 总章节数，点击进入目录
                    ChapterCard(
                        latestChapterTitle = latestChapterTitle ?: stringResource(R.string.no_last_chapter),
                        totalChapterNum = totalChapterNum,
                        onClick = onTocClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // 【底部操作栏】— 加入书架 / 开始阅读按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    if (inBookshelf) {
                        FilledTonalButton(
                            onClick = onReadClick,
                            modifier = Modifier.fillMaxWidth()
                            .height(52.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = accent,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.start_read),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onShelfClick,
                                modifier = Modifier.weight(1f)
                                    .height(52.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.add_to_bookshelf),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            FilledTonalButton(
                                onClick = onReadClick,
                                modifier = Modifier.weight(1f)
                                    .height(52.dp),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = accent,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.start_read),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 【顶部导航栏】— 返回、编辑、更多菜单，透明背景叠加在 Hero 上
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            },
            actions = {
                IconButton(onClick = onEditClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                BookDetailMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onAction = onMenuAction,
                    canUpdate = canUpdate,
                    splitLongChapter = splitLongChapter,
                    isLoginVisible = isLoginVisible,
                    isSourceVariableVisible = isSourceVariableVisible,
                    isBookVariableVisible = isBookVariableVisible,
                    isLocalTxt = book.isLocalTxt,
                    isLocal = book.isLocal,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun BookDetailScreenPreview() {
    val sampleBook = Book(
        name = "凡人修仙传",
        author = "忘语",
        kind = "仙侠, 奇幻, 修仙",
        intro = "一个普通的山村少年，偶然进入一个神秘的修仙世界...",
        latestChapterTitle = "第一千三百章 仙界风云",
        latestChapterTime = System.currentTimeMillis(),
        durChapterIndex = 500,
        durChapterPos = 0,
        durChapterTitle = "第五百章 大乘之战",
        totalChapterNum = 1300,
        wordCount = "523.4万",
    )
    MaterialTheme {
        BookDetailScreen(
            book = sampleBook,
            latestChapterTitle = "第一千三百章 仙界风云",
            totalChapterNum = 1300,
            onBack = {},
            onReadClick = {},
            onShelfClick = {},
            inBookshelf = true,
            onTocClick = {},
            onEditClick = {},
            onMenuAction = {},
        )
    }
}
