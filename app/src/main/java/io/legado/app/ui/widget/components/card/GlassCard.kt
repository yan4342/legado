package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultCornerRadius: Dp = 16.dp

@Composable
private fun BaseCardContent(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, content = content)
}

@Composable
private fun BaseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = DefaultCornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    alpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedContainerColor = (containerColor ?: MaterialTheme.colorScheme.surfaceContainer)
        .let { it.copy(alpha = it.alpha * alpha) }
    val isTransparent = containerColor == Color.Transparent
    val resolvedShape = RoundedCornerShape(cornerRadius)
    val clickableModifier = if (onClick != null || onLongClick != null) {
        modifier
            .clip(resolvedShape)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick
            )
    } else {
        modifier
    }
    Surface(
        modifier = clickableModifier,
        shape = resolvedShape,
        color = if (isTransparent) Color.Transparent else resolvedContainerColor,
        contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
        border = border
    ) {
        BaseCardContent(
            shape = resolvedShape,
            content = content,
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = DefaultCornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = 1f,
        content = content
    )
}

@Composable
fun NormalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = DefaultCornerRadius,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BaseCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        alpha = 1f,
        content = content
    )
}
