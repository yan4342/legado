package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt

/**
 * 书籍详情页主 Composable。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    book: Book,
    latestChapterTitle: String?,
    totalChapterNum: Int,
    onBack: () -> Unit,
    onReadClick: () -> Unit,
    onTocClick: () -> Unit,
    onEditClick: () -> Unit,
    onMenuAction: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                        )
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = null,
                        )
                    }
                    BookDetailMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onAction = onMenuAction,
                        isLoginVisible = false,
                        isSourceVariableVisible = false,
                        isBookVariableVisible = false,
                        isLocalTxt = book.isLocalTxt,
                        isLocal = book.isLocal,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .systemBarsPadding(),
            ) {
                FilledTonalButton(
                    onClick = onReadClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.start_read),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
        ) {
            HeroHeader(
                book = book,
            )

            InfoChipRow(
                wordCount = book.wordCount,
                readProgress = if (totalChapterNum > 0) {
                    (book.durChapterIndex * 100 / totalChapterNum)
                } else 0,
                lastUpdateTime = book.latestChapterTime,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            val intro = book.getDisplayIntro()
            if (!intro.isNullOrBlank()) {
                IntroCard(
                    intro = intro,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            ChapterCard(
                latestChapterTitle = latestChapterTitle ?: stringResource(R.string.no_last_chapter),
                totalChapterNum = totalChapterNum,
                onClick = onTocClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
