package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.legadoCardBackgroundColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllBookmarkScreen(
    state: AllBookmarkUiState,
    onIntent: (AllBookmarkIntent) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }

    val isEInk = AppConfig.isEInkMode
    val topBarContainer = if (isEInk) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val topBarContent = if (isEInk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_bookmark), color = topBarContent) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainer,
                    navigationIconContentColor = topBarContent,
                    actionIconContentColor = topBarContent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                        }
                        RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { dismiss ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.export),
                                onClick = { dismiss(); onIntent(AllBookmarkIntent.ExportJson) },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.export_md),
                                onClick = { dismiss(); onIntent(AllBookmarkIntent.ExportMd) },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.bookmark_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                var lastBook: Pair<String, String>? = null
                state.bookmarks.forEach { bookmark ->
                    val key = bookmark.bookName to bookmark.bookAuthor
                    if (key != lastBook) {
                        stickyHeader(key = "header_${bookmark.bookName}_${bookmark.bookAuthor}") {
                            BookmarkHeader(bookmark)
                        }
                        lastBook = key
                    }
                    item(key = "bookmark_${bookmark.time}") {
                        BookmarkItem(
                            bookmark = bookmark,
                            onClick = { onIntent(AllBookmarkIntent.BookmarkClick(bookmark)) },
                        )
                    }
                }
            }
        }

        val editing = state.editingBookmark
        if (editing != null) {
            BookmarkEditDialog(
                bookmark = editing,
                onDismiss = { onIntent(AllBookmarkIntent.DismissDialog) },
                onSave = { bookText, content ->
                    onIntent(AllBookmarkIntent.SaveBookmark(editing.time, bookText, content))
                },
                onDelete = {
                    onIntent(AllBookmarkIntent.DeleteBookmark(editing.time))
                },
            )
        }
    }
}

@Composable
private fun BookmarkHeader(bookmark: Bookmark) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = "${bookmark.bookName}（${bookmark.bookAuthor}）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(legadoCardBackgroundColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(bookmark.chapterName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bookmark.bookText.isNotEmpty()) {
            Text(
                bookmark.bookText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (bookmark.content.isNotEmpty()) {
            Text(
                bookmark.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}
