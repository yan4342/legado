package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.ui.common.compose.BookCoverImage
import io.legado.app.ui.common.compose.SectionCard
import io.legado.app.utils.toTimeAgo

/**
 * 书架列表模式卡片。
 *
 * 结构：封面 | 书名 + 作者(+未读标志) + 上次更新时间 + 当前章节 + 最新章节
 *
 * @param isUpdating      该书正在更新中（显示加载动画替代未读数字）
 * @param lastUpdateVersion 版本号，用于触发「上次更新时间」定时刷新
 * @param showUnread      是否显示未读标志
 * @param showLastUpdateTime 是否显示上次更新时间
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
    lastUpdateVersion: Int = 0,
    showUnread: Boolean = true,
    showLastUpdateTime: Boolean = false,
) {
    val context = LocalContext.current
    // 30 秒版本号变化时重新计算时间文本
    val lastUpdateTimeText = remember(lastUpdateVersion, book.latestChapterTime) {
        if (showLastUpdateTime && !book.isLocal) {
            book.latestChapterTime.toTimeAgo()
        } else {
            null
        }
    }

    SectionCard(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            BookCoverImage(
                coverUrl = book.getDisplayCover(),
                name = book.name,
                author = book.getRealAuthor(),
                modifier = Modifier
                    .width(72.dp)
                    .height(102.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 作者 + 未读标志（同一行）
                if (book.author.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_author),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = book.getRealAuthor(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showUnread) {
                            Spacer(modifier = Modifier.width(6.dp))
                            BookBadge(
                                count = book.getUnreadChapterNum(),
                                highlight = book.lastCheckCount > 0,
                                isUpdating = isUpdating,
                            )
                        }
                    }
                }

                // 上次更新时间（紧跟作者行，仅非本地书籍）
                if (lastUpdateTimeText != null) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_timer_black_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = lastUpdateTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                book.durChapterTitle?.let { title ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_update),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (book.getUnreadChapterNum() > 0) {
                    book.latestChapterTitle?.let { title ->
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_book_last),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
