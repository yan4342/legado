package io.legado.app.ui.book.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.common.compose.InfoChip
import io.legado.app.ui.common.compose.BookCoverCompose
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBookPreviewSheet(
    book: SearchBook?,
    shelfState: BookShelfState,
    onDismissRequest: () -> Unit,
    onOpenDetail: (SearchBook) -> Unit,
    onAddToShelf: (SearchBook) -> Unit,
    onExpandToDetail: ((SearchBook) -> Unit)? = null,
) {
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = LocalViewModelStoreOwner.current!!
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current!!

    ModalLegadoBottomSheet(
        show = book != null,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        contentContainerColor = MaterialTheme.colorScheme.background,
        onExpanded = onExpandToDetail?.let { callback ->
            { book?.let { b -> callback(b) } }
        },
        expandedContent = {
            book?.let { b ->
                CompositionLocalProvider(
                    LocalContext provides activityContext,
                    LocalLifecycleOwner provides lifecycleOwner,
                    LocalViewModelStoreOwner provides viewModelStoreOwner,
                    LocalSavedStateRegistryOwner provides savedStateRegistryOwner,
                    LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
                ) {
                    BookInfoRouteScreen(
                        bookUrl = b.bookUrl,
                        name = b.name,
                        author = b.author,
                        coverPath = b.coverUrl,
                        origin = b.origin,
                        onBack = {
                            scope.launch { sheetState.partialExpand() }
                        },
                        onNavigateToVoiceCasting = { bookUrl ->
                            activityContext.startActivity(
                                io.legado.app.ui.main.MainIntent.createBookVoiceCastingIntent(
                                    activityContext,
                                    bookUrl,
                                )
                            )
                        },
                        onNavigateToCharacterNetwork = { bookUrl, focusCharacterId ->
                            activityContext.startActivity(
                                io.legado.app.ui.main.MainIntent.createBookCharacterNetworkIntent(
                                    activityContext,
                                    bookUrl,
                                    focusCharacterId,
                                )
                            )
                        },
                        onNavigateToCharacterList = { bookUrl ->
                            activityContext.startActivity(
                                io.legado.app.ui.main.MainIntent.createBookCharacterListIntent(
                                    activityContext,
                                    bookUrl,
                                )
                            )
                        },
                    )
                }
            }
        },
        transitionOverlay = { progress ->
            book?.let { b ->
                val density = LocalDensity.current
                val overlayAlpha = when {
                    progress < 0.15f -> progress / 0.15f
                    progress > 0.85f -> (1f - progress) / 0.15f
                    else -> 1f
                }
                if (overlayAlpha <= 0f) return@ModalLegadoBottomSheet
                val isInShelf = shelfState == BookShelfState.IN_SHELF

                // Animated cover: shrinks and moves from preview position to HeroHeader position
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp , top = 16.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val scale = lerp(1f, 90f / 120f, progress)
                            scaleX = scale
                            scaleY = scale
                            translationY = -lerp(0f, with(density) { 56.dp.toPx() }, progress)
                            alpha = overlayAlpha
                        }
                        .width(120.dp)
                        .aspectRatio(5f / 7f)
                ) {
                    BookCoverCompose(
                        name = b.name,
                        author = b.author,
                        coverUrl = b.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Fixed buttons at bottom: stay in place during transition
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = overlayAlpha }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onAddToShelf(b) },
                        modifier = Modifier.weight(1f),
                        enabled = !isInShelf,
                    ) {
                        Text(
                            if (isInShelf) stringResource(R.string.already_in_bookshelf)
                            else stringResource(R.string.add_to_bookshelf)
                        )
                    }
                    Button(
                        onClick = { onOpenDetail(b) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.book_info)) }
                }
            }
        },
    ) {
        book?.let { book ->
            val isInShelf = shelfState == BookShelfState.IN_SHELF
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 800.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.width(120.dp).aspectRatio(5f / 7f)) {
                        BookCoverCompose(
                            name = book.name, author = book.author,
                            coverUrl = book.coverUrl,
                            modifier = Modifier.width(120.dp).aspectRatio(5f / 7f),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.author.isNotBlank()) {
                            Text(
                                text = book.author,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (book.originName.isNotBlank()) {
                            Text(
                                text = book.originName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val latestChapter = book.latestChapterTitle
                        if (!latestChapter.isNullOrBlank()) {
                            Text(
                                text = latestChapter,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (book.time > 0) {
                            val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                            Text(
                                text = dateFormat.format(Date(book.time)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                val kinds = book.getKindList()
                if (kinds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(kinds) { kind -> InfoChip(text = kind, filled = true) }
                    }
                }

                val intro = book.intro?.replace("\\s+".toRegex(), " ")?.trim()
                if (!intro.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            text = intro,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onAddToShelf(book) },
                        modifier = Modifier.weight(1f),
                        enabled = !isInShelf,
                    ) {
                        Text(
                            if (isInShelf) stringResource(R.string.already_in_bookshelf)
                            else stringResource(R.string.add_to_bookshelf)
                        )
                    }                    
                    Button(
                        onClick = { onOpenDetail(book) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.book_info)) }
                }
            }
        }
    }
}
