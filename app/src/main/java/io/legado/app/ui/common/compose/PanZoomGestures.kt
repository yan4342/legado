package io.legado.app.ui.common.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Pinch-zoom about [centroid] then apply [panDelta].
 * Keeps the world point under the fingers stable while scaling.
 */
fun applyCentroidPanZoom(
    centroid: Offset,
    panDelta: Offset,
    zoom: Float,
    scale: Float,
    offset: Offset,
    minScale: Float,
    maxScale: Float,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(minScale, maxScale)
    val ratio = if (scale == 0f) 1f else newScale / scale
    val newOffset = centroid - (centroid - offset) * ratio + panDelta
    return newScale to newOffset
}

/**
 * Clamp pan [offset] so [content] (world AABB) keeps at least [margin] px overlapping [viewport].
 * Screen transform: `screen = world * scale + offset`.
 */
fun clampPanOffset(
    offset: Offset,
    scale: Float,
    content: Rect,
    viewport: Size,
    margin: Float,
): Offset {
    if (viewport.width <= 0f || viewport.height <= 0f) return offset
    if (content.width <= 0f || content.height <= 0f) return Offset.Zero
    val safeMarginX = margin.coerceIn(0f, viewport.width / 2f)
    val safeMarginY = margin.coerceIn(0f, viewport.height / 2f)
    val minX = safeMarginX - content.right * scale
    val maxX = viewport.width - safeMarginX - content.left * scale
    val minY = safeMarginY - content.bottom * scale
    val maxY = viewport.height - safeMarginY - content.top * scale
    val x = if (minX <= maxX) offset.x.coerceIn(minX, maxX) else (minX + maxX) / 2f
    val y = if (minY <= maxY) offset.y.coerceIn(minY, maxY) else (minY + maxY) / 2f
    return Offset(x, y)
}
