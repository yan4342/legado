package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
) {
    val scrollState = rememberScrollState()
    val heroHeight = 300.dp
    val surfaceOverlap = 36.dp
    val accent = Color(LocalContext.current.accentColor)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        HeroHeader(
            book = book,
            modifier = Modifier.align(Alignment.TopCenter),
        )

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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp)
                        .verticalScroll(scrollState),
                ) {
                    InfoChipRow(
                        kindList = book.getKindList(),
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
                }

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
                            modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.add_to_bookshelf),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            FilledTonalButton(
                                onClick = onReadClick,
                                modifier = Modifier.weight(1f),
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
