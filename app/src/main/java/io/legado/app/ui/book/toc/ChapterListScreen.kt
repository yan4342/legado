package io.legado.app.ui.book.toc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.usecase.ReChapterUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.eventObservable
import io.legado.app.utils.toastOnUi
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadState { NONE, DOWNLOADING, SUCCESS, ERROR, LOCAL }

@Composable
fun ChapterListPage(
    bookUrl: String,
    searchQuery: String,
    onChapterClick: (index: Int) -> Unit,
    refreshTrigger: MutableStateFlow<Int>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var chapterList by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
    var durChapterIndex by remember { mutableIntStateOf(0) }
    var displayTitleMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cachedFileNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var longPressChapter by remember { mutableStateOf<BookChapter?>(null) }
    var bookState by remember { mutableStateOf<io.legado.app.data.entities.Book?>(null) }

    val searchHint = searchQuery.ifBlank { null }
    val refresh by refreshTrigger.collectAsState()

    // Load book + chapters
    LaunchedEffect(bookUrl, searchHint, refresh) {
        isLoading = true
        val book = withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) }
        if (book != null) {
            bookState = book
            durChapterIndex = book.durChapterIndex
            val chapters = withContext(Dispatchers.IO) {
                val end = if (book.simulatedTotalChapterNum() > 0) book.simulatedTotalChapterNum() - 1 else Int.MAX_VALUE
                when (searchHint) {
                    null -> appDb.bookChapterDao.getChapterList(bookUrl, 0, end)
                    else -> appDb.bookChapterDao.search(bookUrl, searchHint, 0, end)
                }
            }
            chapterList = chapters
            withContext(Dispatchers.IO) {
                cachedFileNames = BookHelp.getChapterFiles(book).toSet()
            }
            val replaceRules = ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
            val useReplace = AppConfig.tocUiUseReplace && book.getUseReplaceRule()
            val map = mutableMapOf<String, String>()
            chapters.forEach { chapter ->
                if (chapter.title !in map) {
                    map[chapter.title] = chapter.getDisplayTitle(replaceRules, useReplace)
                }
            }
            displayTitleMap = map
        } else {
            chapterList = emptyList()
            cachedFileNames = emptySet()
            displayTitleMap = emptyMap()
        }
        isLoading = false
    }

    // 监听 SAVE_CONTENT 事件,章节落盘后实时更新缓存图标
    DisposableEffect(bookUrl) {
        val observer = Observer<Pair<Book, BookChapter>> { (savedBook, chapter) ->
            if (savedBook.bookUrl == bookUrl) {
                cachedFileNames = cachedFileNames + chapter.getFileName()
            }
        }
        eventObservable<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT).observeForever(observer)
        onDispose {
            eventObservable<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT).removeObserver(observer)
        }
    }

    // Auto-scroll to current chapter on first load
    LaunchedEffect(chapterList) {
        if (chapterList.isNotEmpty()) {
            val target = chapterList.indexOfFirst { it.index >= durChapterIndex }
            if (target >= 0) listState.scrollToItem(target.coerceAtMost(chapterList.size - 1))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Loading indicator
        AnimatedVisibility(isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            if (!isLoading && chapterList.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chapter_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // Chapter list with fast scroll
                val density = LocalDensity.current
                var thumbAlpha by remember { mutableFloatStateOf(0f) }
                val isDragging = remember { mutableStateOf(false) }
                val totalItems by remember(chapterList) { derivedStateOf { listState.layoutInfo.totalItemsCount } }
                val firstVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0 } }
                val viewportHeight by remember { derivedStateOf { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset } }

                LaunchedEffect(firstVisible) {
                    if (!isDragging.value) {
                        thumbAlpha = 0.3f
                        kotlinx.coroutines.delay(1500)
                        thumbAlpha = 0f
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(chapterList) { _, chapter ->
                            val downloadState = when {
                                chapter.isVolume -> DownloadState.LOCAL
                                chapter.getFileName() in cachedFileNames -> DownloadState.SUCCESS
                                else -> DownloadState.NONE
                            }
                            ChapterItem(
                                chapter = chapter,
                                displayTitle = displayTitleMap[chapter.title] ?: chapter.title,
                                isDur = chapter.index == durChapterIndex,
                                downloadState = downloadState,
                                showWordCount = AppConfig.tocCountWords,
                                onClick = { onChapterClick(chapter.index) },
                                onLongClick = { if (BookHelp.isReChaptered(chapter)) longPressChapter = chapter },
                            )
                        }
                    }

                    // Fast scroll thumb
                    if (totalItems > 0 && viewportHeight > 0) {
                        val thumbHeight = with(density) {
                            (viewportHeight.toFloat() * viewportHeight / (totalItems * 80.dp.toPx()))
                                .coerceIn(48.dp.toPx(), 160.dp.toPx()).toDp()
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(24.dp)
                                .fillMaxSize()
                                .alpha(thumbAlpha)
                                .pointerInput(totalItems) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isDragging.value = true
                                            thumbAlpha = 1f
                                        },
                                        onDragEnd = {
                                            isDragging.value = false
                                            thumbAlpha = 0.3f
                                            scope.launch {
                                                kotlinx.coroutines.delay(1500)
                                                thumbAlpha = 0f
                                            }
                                        },
                                    ) { _, dragAmount ->
                                        val scrollFraction = dragAmount / viewportHeight.toFloat()
                                        val targetItem = (firstVisible + totalItems * scrollFraction).toInt()
                                            .coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                                        scope.launch { listState.animateScrollToItem(targetItem) }
                                    }
                                },
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(thumbHeight)
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                            )
                        }
                    }
                }
            }
        }

        // Bottom bar
        val bbg = Color(context.bottomBackground)
        val btc = Color(context.getPrimaryTextColor(ColorUtils.isColorLight(context.bottomBackground)))
        val book = bookState
        val currentTitle = book?.durChapterTitle ?: chapterList.getOrNull(durChapterIndex)?.let {
            displayTitleMap[it.title] ?: it.title
        } ?: ""
        val totalNum = book?.simulatedTotalChapterNum() ?: chapterList.size
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).background(bbg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$currentTitle(${durChapterIndex + 1}/$totalNum)",
                modifier = Modifier.weight(1f).padding(start = 10.dp)
                    .clickable {
                        val target = chapterList.indexOfFirst { it.index >= durChapterIndex }
                        scope.launch { if (target >= 0) listState.animateScrollToItem(target) }
                    },
                color = btc,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowDropUp, contentDescription = stringResource(R.string.go_to_top), tint = btc)
            }
            IconButton(onClick = {
                scope.launch { if (chapterList.isNotEmpty()) listState.animateScrollToItem(chapterList.size - 1) }
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.go_to_bottom), tint = btc)
            }
        }
    }

    // 长按合并章:合并到上一章(修复误拆)
    LegadoAlertDialog(
        show = longPressChapter != null,
        onDismissRequest = { longPressChapter = null },
        dialogTitle = stringResource(R.string.re_chapter_merge_title),
        text = stringResource(R.string.re_chapter_merge_confirm, longPressChapter?.title.orEmpty()),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            val target = longPressChapter
            longPressChapter = null
            scope.launch {
                val book = withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) }
                val source = book?.let {
                    withContext(Dispatchers.IO) { appDb.bookSourceDao.getBookSource(it.origin) }
                }
                if (book != null && source != null && target != null) {
                    val ok = ReChapterUseCase(book, source).mergeWithPrevious(target)
                    context.toastOnUi(
                        if (ok) R.string.re_chapter_merge_done else R.string.re_chapter_merge_failed
                    )
                    if (ok) refreshTrigger.value++
                }
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { longPressChapter = null },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterItem(
    chapter: BookChapter,
    displayTitle: String,
    isDur: Boolean,
    downloadState: DownloadState,
    showWordCount: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val textColor = when {
        isDur -> Color(context.accentColor)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (chapter.isVolume) Color(context.bottomBackground) else Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chapter.isVip && !chapter.isPay) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(end = 6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!chapter.tag.isNullOrEmpty() && !chapter.isVolume) {
                        Text(chapter.tag!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }

                // Right side: word count + download state + current indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showWordCount && !chapter.wordCount.isNullOrEmpty() && !chapter.isVolume) {
                        Text(
                            chapter.wordCount!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    DownloadStateIcon(downloadState)
                }
            }
        }
    }
}

@Composable
private fun DownloadStateIcon(state: DownloadState) {
    when (state) {
        DownloadState.SUCCESS -> Icon(
            Icons.Filled.DownloadDone, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
        DownloadState.DOWNLOADING -> Icon(
            Icons.Filled.CloudDownload, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        DownloadState.ERROR -> Icon(
            Icons.Filled.Refresh, null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        DownloadState.NONE, DownloadState.LOCAL -> {}  // no icon for LOCAL or not-downloaded
    }
}
