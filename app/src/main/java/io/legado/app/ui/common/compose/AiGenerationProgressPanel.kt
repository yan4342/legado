package io.legado.app.ui.common.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.legado.app.R

@Composable
fun AiGenerationProgressPanel(
    visible: Boolean,
    previewText: String,
    reasoningText: String = "",
    useMonospace: Boolean = true,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 200,
) {
    if (!visible) return
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    LaunchedEffect(previewText.length, reasoningText.length) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.ai_generation_in_progress),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (reasoningText.isNotBlank()) {
                Text(
                    stringResource(R.string.ai_thinking_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    reasoningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxHeightDp / 2).coerceAtLeast(60).dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                )
            }
            val body = previewText.ifBlank { "…" }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
                ),
                color = colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp)
                    .verticalScroll(scrollState),
            )
        }
    }
}
