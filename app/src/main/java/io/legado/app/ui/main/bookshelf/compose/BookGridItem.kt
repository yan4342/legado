package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Book
import io.legado.app.ui.common.compose.BookCoverImage
import io.legado.app.ui.common.compose.legadoCardBackgroundColor

/**
 * 书架网格模式卡片。
 *
 * 结构：封面 + 书名
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = legadoCardBackgroundColor(),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            BookCoverImage(
                coverUrl = book.getDisplayCover(),
                name = book.name,
                author = book.getRealAuthor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                compact = true,
            )

            if (showTitle) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
