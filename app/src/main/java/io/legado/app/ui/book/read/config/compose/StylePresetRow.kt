@file:Suppress("DEPRECATION")

package io.legado.app.ui.book.read.config.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StylePresetRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val configList = ReadBookConfig.configList
    val borderColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(configList) { index, config ->
            val isSelected = index == selectedIndex
            val textColor = Color(config.curTextColor())
            val bgDrawable: Drawable? = remember(config) {
                try {
                    config.curBgDrawable(100, 150)
                } catch (_: Exception) {
                    null
                }
            }
            val bgColor = remember(bgDrawable, config) {
                val bitmap = bgDrawable?.toBitmap(100, 150)
                bitmap?.asImageBitmap()
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.border(2.dp, borderColor, RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, surfaceColor, RoundedCornerShape(8.dp))
                    )
                    .background(
                        if (bgColor != null) Color.Transparent
                        else Color(config.curTextColor()).copy(alpha = 0.1f)
                    )
                    .combinedClickable(
                        onClick = { onSelect(index) },
                        onLongClick = { onLongPress(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (bgColor != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bgColor,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Text(
                    text = config.name.ifBlank { "Aa" },
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(2.dp),
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add style",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
