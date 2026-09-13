package io.legado.app.ui.book.readRecord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AiUsageSourcePieChart(
    slices: List<AiUsageSourceSliceUi>,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val orderedSlices = remember(slices) { sortSourceSlices(slices) }
    val total = orderedSlices.sumOf { it.tokens }
    val colors = orderedSlices.mapIndexed { index, slice -> aiSourcePieColor(slice.source, index) }

    if (total <= 0) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 10.dp.toPx()
                val diameter = this.size.minDimension - stroke
                val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
                drawArc(
                    color = scheme.surfaceVariant,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
        }
        return
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 10.dp.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            orderedSlices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.tokens.toFloat() / total.toFloat()
                if (sweep <= 0f) return@forEachIndexed
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
    }
}

@Composable
fun AiUsageSourcePieWithLegend(
    slices: List<AiUsageSourceSliceUi>,
    modifier: Modifier = Modifier,
    pieSize: Dp = 56.dp,
    containerSize: Dp = 112.dp,
) {
    val orderedSlices = remember(slices) { sortSourceSlices(slices) }
    val total = orderedSlices.sumOf { it.tokens }
    var showLegend by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val legendRadiusPx = with(density) { (pieSize / 2 + 22.dp).toPx() }

    val legendItems = remember(orderedSlices, total) {
        if (total <= 0 || orderedSlices.isEmpty()) emptyList()
        else {
            val slot = 360f / orderedSlices.size
            orderedSlices.mapIndexed { rank, slice ->
                val midAngle = if (orderedSlices.size == 1) {
                    -90f
                } else {
                    -90f + (rank + 0.5f) * slot
                }
                Triple(rank, slice, midAngle)
            }
        }
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = total > 0,
            ) {
                showLegend = !showLegend
            },
        contentAlignment = Alignment.Center,
    ) {
        AiUsageSourcePieChart(slices = orderedSlices, size = pieSize)

        if (showLegend && legendItems.isNotEmpty()) {
            legendItems.forEach { (colorIndex, slice, midAngleDeg) ->
                val percent = (slice.tokens * 100 / total).toInt()
                val angleRad = Math.toRadians(midAngleDeg.toDouble())
                val offsetX = (cos(angleRad) * legendRadiusPx).roundToInt()
                val offsetY = (sin(angleRad) * legendRadiusPx).roundToInt()
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(offsetX, offsetY) },
                    contentAlignment = Alignment.Center,
                ) {
                    AiUsageSourceLegendChip(
                        source = slice.source,
                        percent = percent,
                        colorIndex = colorIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiUsageSourceLegendChip(
    source: String,
    percent: Int,
    colorIndex: Int,
) {
    val label = if (source.isBlank()) {
        stringResource(R.string.ai_usage_source_other)
    } else {
        aiSourceDisplayName(source)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (AppConfig.isEInkMode) 1f else 0.94f),
        shadowElevation = if (AppConfig.isEInkMode) 0.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .widthIn(max = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(aiSourcePieColor(source, colorIndex)),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.ai_usage_source_legend_percent, percent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun aiSourcePieColor(source: String, index: Int): Color {
    val scheme = MaterialTheme.colorScheme
    if (AppConfig.isEInkMode) {
        val alphas = listOf(1f, 0.72f, 0.52f, 0.36f, 0.22f)
        return scheme.onSurface.copy(alpha = alphas[index % alphas.size])
    }
    val palette = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.primary.copy(alpha = 0.72f),
        scheme.secondary.copy(alpha = 0.72f),
        scheme.primaryContainer,
        scheme.secondaryContainer,
    )
    if (source.isBlank()) return scheme.outline
    return when (source) {
        io.legado.app.domain.model.AiCallSource.CHAT -> scheme.primary
        io.legado.app.domain.model.AiCallSource.TITLE -> scheme.secondary
        io.legado.app.domain.model.AiCallSource.SUGGESTION -> scheme.primary.copy(alpha = 0.8f)
        io.legado.app.domain.model.AiCallSource.GALGAME -> scheme.secondary.copy(alpha = 0.8f)
        io.legado.app.domain.model.AiCallSource.MEMORY -> scheme.primaryContainer
        io.legado.app.domain.model.AiCallSource.CHARACTER -> scheme.secondaryContainer
        else -> palette[index % palette.size]
    }
}
