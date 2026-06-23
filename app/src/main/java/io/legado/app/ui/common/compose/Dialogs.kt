package io.legado.app.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerDialog(
    title: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    defaultButton: @Composable (() -> Unit)? = null,
) {
    var selectedValue by remember { mutableStateOf(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            WheelNumberPicker(
                value = selectedValue,
                onValueChange = { selectedValue = it },
                minValue = minValue,
                maxValue = maxValue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedValue) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (defaultButton != null) {
                    defaultButton()
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
) {
    val itemHeightDp = 44.dp
    val visibleCount = 3
    val halfVisible = visibleCount / 2
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeightDp.toPx() }

    val items = remember(minValue, maxValue) {
        (minValue..maxValue).toList()
    }

    val initialRealIndex = (value - minValue).coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialRealIndex
    )

    fun centerItemIndex(): Int {
        if (listState.layoutInfo.viewportSize.height == 0) return halfVisible + initialRealIndex
        val vpCenter = listState.layoutInfo.viewportSize.height / 2f
        val offsetFromFirst = vpCenter + listState.firstVisibleItemScrollOffset
        val raw = listState.firstVisibleItemIndex + (offsetFromFirst / itemHeightPx).toInt()
        return raw.coerceIn(halfVisible, halfVisible + items.lastIndex)
    }

    val centerIndex by remember {
        derivedStateOf { centerItemIndex() }
    }

    // Snap when user scrolling stops
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filterNot { it }
            .collectLatest {
                val snapped = centerItemIndex()
                val targetFirst = (snapped - halfVisible).coerceAtLeast(0)
                val currentCenter = centerItemIndex()
                if (snapped - halfVisible != currentCenter - halfVisible) {
                    listState.animateScrollToItem(targetFirst, 0)
                }
                val realIndex = snapped - halfVisible
                if (realIndex in items.indices && items[realIndex] != value) {
                    onValueChange(items[realIndex])
                }
            }
    }

    // React to external value changes (e.g. default button)
    LaunchedEffect(value) {
        if (!listState.isScrollInProgress) {
            val targetReal = (value - minValue).coerceIn(0, items.lastIndex)
            val currentReal = centerItemIndex() - halfVisible
            if (targetReal != currentReal) {
                listState.animateScrollToItem(targetReal, 0)
            }
        }
    }

    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val selectedTextColor = MaterialTheme.colorScheme.onSurface
    val unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .height(itemHeightDp * visibleCount)
            .drawWithContent {
                drawContent()
                val dividerYTop = size.height / visibleCount * halfVisible
                val dividerYBottom = size.height / visibleCount * (halfVisible + 1)
                val insetPx = 56.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(insetPx, dividerYTop),
                    end = Offset(size.width - insetPx, dividerYTop),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = dividerColor,
                    start = Offset(insetPx, dividerYBottom),
                    end = Offset(size.width - insetPx, dividerYBottom),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(itemHeightDp * halfVisible)) }
            itemsIndexed(items) { index, item ->
                val isSelected = index + halfVisible == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            textAlign = TextAlign.Center,
                        ),
                        color = if (isSelected) selectedTextColor else unselectedTextColor,
                        fontSize = if (isSelected) {
                            MaterialTheme.typography.titleLarge.fontSize
                        } else {
                            MaterialTheme.typography.bodyLarge.fontSize
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(itemHeightDp * halfVisible)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimpleColorPickerDialog(
    title: String,
    currentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var hexText by remember {
        mutableStateOf(
            String.format(
                "#%02X%02X%02X",
                (currentColor.red * 255).toInt(),
                (currentColor.green * 255).toInt(),
                (currentColor.blue * 255).toInt(),
            )
        )
    }
    // Material Design 500 colors + black/white
    val presetColors = listOf(
        Color(0xFFFFEBEE), Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFE57373),
        Color(0xFFEF5350), Color(0xFFF44336), Color(0xFFE53935), Color(0xFFD32F2F),
        Color(0xFFC62828), Color(0xFFB71C1C),
        Color(0xFFFCE4EC), Color(0xFFF8BBD0), Color(0xFFF48FB1), Color(0xFFF06292),
        Color(0xFFEC407A), Color(0xFFE91E63), Color(0xFFD81B60), Color(0xFFC2185B),
        Color(0xFFAD1457), Color(0xFF880E4F),
        Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFFBA68C8),
        Color(0xFFAB47BC), Color(0xFF9C27B0), Color(0xFF8E24AA), Color(0xFF7B1FA2),
        Color(0xFF6A1B9A), Color(0xFF4A148C),
        Color(0xFFEDE7F6), Color(0xFFD1C4E9), Color(0xFFB39DDB), Color(0xFF9575CD),
        Color(0xFF7E57C2), Color(0xFF673AB7), Color(0xFF5E35B1), Color(0xFF512DA8),
        Color(0xFF4527A0), Color(0xFF311B92),
        Color(0xFFE8EAF6), Color(0xFFC5CAE9), Color(0xFF9FA8DA), Color(0xFF7986CB),
        Color(0xFF5C6BC0), Color(0xFF3F51B5), Color(0xFF3949AB), Color(0xFF303F9F),
        Color(0xFF283593), Color(0xFF1A237E),
        Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9), Color(0xFF64B5F6),
        Color(0xFF42A5F5), Color(0xFF2196F3), Color(0xFF1E88E5), Color(0xFF1976D2),
        Color(0xFF1565C0), Color(0xFF0D47A1),
        Color(0xFFE1F5FE), Color(0xFFB3E5FC), Color(0xFF81D4FA), Color(0xFF4FC3F7),
        Color(0xFF29B6F6), Color(0xFF03A9F4), Color(0xFF039BE5), Color(0xFF0288D1),
        Color(0xFF0277BD), Color(0xFF01579B),
        Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF80DEEA), Color(0xFF4DD0E1),
        Color(0xFF26C6DA), Color(0xFF00BCD4), Color(0xFF00ACC1), Color(0xFF0097A7),
        Color(0xFF00838F), Color(0xFF006064),
        Color(0xFFE0F2F1), Color(0xFFB2DFDB), Color(0xFF80CBC4), Color(0xFF4DB6AC),
        Color(0xFF26A69A), Color(0xFF009688), Color(0xFF00897B), Color(0xFF00796B),
        Color(0xFF00695C), Color(0xFF004D40),
        Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784),
        Color(0xFF66BB6A), Color(0xFF4CAF50), Color(0xFF43A047), Color(0xFF388E3C),
        Color(0xFF2E7D32), Color(0xFF1B5E20),
        Color(0xFFF1F8E9), Color(0xFFDCEDC8), Color(0xFFC5E1A5), Color(0xFFAED581),
        Color(0xFF9CCC65), Color(0xFF8BC34A), Color(0xFF7CB342), Color(0xFF689F38),
        Color(0xFF558B2F), Color(0xFF33691E),
        Color(0xFFFFFDE7), Color(0xFFFFF9C4), Color(0xFFFFF59D), Color(0xFFFFF176),
        Color(0xFFFFEE58), Color(0xFFFFEB3B), Color(0xFFFDD835), Color(0xFFFBC02D),
        Color(0xFFF9A825), Color(0xFFF57F17),
        Color(0xFFFFF8E1), Color(0xFFFFECB3), Color(0xFFFFE082), Color(0xFFFFD54F),
        Color(0xFFFFCA28), Color(0xFFFFC107), Color(0xFFFFB300), Color(0xFFFFA000),
        Color(0xFFFF8F00), Color(0xFFFF6F00),
        Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFFFCC80), Color(0xFFFFB74D),
        Color(0xFFFFA726), Color(0xFFFF9800), Color(0xFFFB8C00), Color(0xFFF57C00),
        Color(0xFFEF6C00), Color(0xFFE65100),
        Color(0xFFFBE9E7), Color(0xFFFFCCBC), Color(0xFFFFAB91), Color(0xFFFF8A65),
        Color(0xFFFF7043), Color(0xFFFF5722), Color(0xFFF4511E), Color(0xFFE64A19),
        Color(0xFFD84315), Color(0xFFBF360C),
        Color(0xFFEFEBE9), Color(0xFFD7CCC8), Color(0xFFBCAAA4), Color(0xFFA1887F),
        Color(0xFF8D6E63), Color(0xFF795548), Color(0xFF6D4C41), Color(0xFF5D4037),
        Color(0xFF4E342E), Color(0xFF3E2723),
        Color(0xFFFAFAFA), Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFFBDBDBD),
        Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF616161), Color(0xFF424242),
        Color(0xFF212121), Color(0xFF000000),
        Color(0xFFECEFF1), Color(0xFFCFD8DC), Color(0xFFB0BEC5), Color(0xFF90A4AE),
        Color(0xFF78909C), Color(0xFF607D8B), Color(0xFF546E7A), Color(0xFF455A64),
        Color(0xFF37474F), Color(0xFF263238),
        Color(0xFFFFFFFF),
    )

    val columns = 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // Current color preview
                Surface(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = currentColor,
                    shape = MaterialTheme.shapes.small,
                ) {}
                Spacer(Modifier.height(8.dp))
                // Preset color grid
                val rows = presetColors.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowColors.forEach { color ->
                                Surface(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onConfirm(color) },
                                    color = color,
                                    shape = CircleShape,
                                    tonalElevation = if (color == Color.White) 2.dp else 0.dp,
                                ) {}
                            }
                            // Fill remaining space in row if incomplete
                            repeat(columns - rowColors.size) {
                                Spacer(Modifier.size(28.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { hexText = it },
                    label = { Text("#RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hex = hexText.removePrefix("#")
                if (hex.length == 6) {
                    try {
                        val color = Color(android.graphics.Color.parseColor("#$hex"))
                        onConfirm(color)
                    } catch (_: Exception) {
                    }
                }
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
