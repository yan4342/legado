package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.SectionCard

/**
 * 章节卡片：最新章节 + 点击进入目录。
 */
@Composable
fun ChapterCard(
    latestChapterTitle: String,
    totalChapterNum: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_toc),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
//                Text(
//                    text = if (totalChapterNum > 0) {
//                        stringResource(R.string.toc_s, "$totalChapterNum 章")
//                    } else {
//                        stringResource(R.string.chapter_list)
//                    },
//                    style = MaterialTheme.typography.titleSmall,
//                    color = MaterialTheme.colorScheme.onSurface,
//                )
                Text(
                    text = latestChapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
