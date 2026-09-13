package io.legado.app.ui.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ContextUsageDialog(
    state: AiChatUiState,
    onDismiss: () -> Unit,
    onCompress: () -> Unit,
) {
    val budget = state.contextInputBudget.takeIf { it > 0 } ?: state.contextWindow
    val used = state.contextTokensUsed
    val ratio = if (budget > 0) (used.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val slices = state.contextUsageSlices
    val sliceTotal = slices.sumOf { it.tokens }.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.ai_context_usage_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.ai_context_usage_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            stringResource(
                                R.string.ai_context_usage_summary,
                                formatTokenCount(used),
                                formatTokenCount(budget),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                ratio > 0.85f -> MaterialTheme.colorScheme.error
                                ratio > 0.6f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    val sourceLabel = when (state.contextTokensSource) {
                        AiChatUiState.CONTEXT_SOURCE_API ->
                            stringResource(R.string.ai_context_usage_source_api)
                        else -> stringResource(R.string.ai_context_usage_source_estimate)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.cacheHitRatio > 0f) {
                            Text(
                                stringResource(
                                    R.string.ai_context_usage_cache_hit,
                                    (state.cacheHitRatio * 100).toInt(),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.ai_context_usage_breakdown),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                if (slices.isEmpty()) {
                    Text(
                        stringResource(R.string.ai_context_usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ContextUsageStackedBar(slices = slices, sliceTotal = sliceTotal)
                    slices.forEach { slice ->
                        ContextUsageSliceRow(
                            slice = slice,
                            share = slice.tokens.toFloat() / sliceTotal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCompress,
                enabled = !state.isCompressing && used > 0,
            ) {
                Icon(
                    Icons.Default.Compress,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ai_context_usage_compress))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun ContextUsageStackedBar(
    slices: ImmutableList<AiContextUsageSliceUi>,
    sliceTotal: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        slices.forEach { slice ->
            val weight = (slice.tokens.toFloat() / sliceTotal).coerceAtLeast(0.01f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .height(12.dp)
                    .background(contextUsageCategoryColor(slice.category)),
            )
        }
    }
}

@Composable
private fun ContextUsageSliceRow(
    slice: AiContextUsageSliceUi,
    share: Float,
) {
    val color = contextUsageCategoryColor(slice.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contextUsageCategoryLabel(slice.category),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (slice.detail.isNotBlank()) {
                Text(
                    slice.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatTokenCount(slice.tokens),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${(share * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun contextUsageCategoryLabel(category: AiContextUsageCategoryUi): String = when (category) {
    AiContextUsageCategoryUi.SYSTEM -> stringResource(R.string.ai_context_usage_cat_system)
    AiContextUsageCategoryUi.RULES -> stringResource(R.string.ai_context_usage_cat_rules)
    AiContextUsageCategoryUi.CHARACTER -> stringResource(R.string.ai_context_usage_cat_character)
    AiContextUsageCategoryUi.KNOWLEDGE -> stringResource(R.string.ai_context_usage_cat_knowledge)
    AiContextUsageCategoryUi.HISTORY -> stringResource(R.string.ai_context_usage_cat_history)
    AiContextUsageCategoryUi.DRAFT -> stringResource(R.string.ai_context_usage_cat_draft)
    AiContextUsageCategoryUi.TOOLS -> stringResource(R.string.ai_context_usage_cat_tools)
}

fun contextUsageCategoryColor(category: AiContextUsageCategoryUi): Color {
    // Slightly vivid fixed palette so stacked bar / rows stay readable across themes.
    return when (category) {
        AiContextUsageCategoryUi.SYSTEM -> Color(0xFF4C8DFF)
        AiContextUsageCategoryUi.RULES -> Color(0xFF9B6CFF)
        AiContextUsageCategoryUi.CHARACTER -> Color(0xFF2EC4A0)
        AiContextUsageCategoryUi.KNOWLEDGE -> Color(0xFFFFB020)
        AiContextUsageCategoryUi.HISTORY -> Color(0xFF5AA9E6)
        AiContextUsageCategoryUi.DRAFT -> Color(0xFFFF7A59)
        AiContextUsageCategoryUi.TOOLS -> Color(0xFF8B95A8)
    }
}

private fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000f)
    tokens >= 10_000 -> "${tokens / 1000}K"
    tokens >= 1000 -> "%.1fK".format(tokens / 1000f)
    else -> tokens.toString()
}
