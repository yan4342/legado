package io.legado.app.ui.ai.outline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineNodeType
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.applyCentroidPanZoom
import io.legado.app.ui.common.compose.clampPanOffset
import io.legado.app.ui.common.compose.legadoCardBackgroundColor

/**
 * Horizontal outline tree (left→right) with pan/zoom. Tapping a writable node sets it as the
 * story "current node" (when [onSetCurrent] provided); tapping a pending option selects it.
 */
@Composable
fun OutlineGraphCanvas(
    graph: OutlineGraph,
    modifier: Modifier = Modifier,
    onSelectOption: ((optionId: String) -> Unit)? = null,
    onSetCurrent: ((nodeId: String) -> Unit)? = null,
    onRegressCurrent: (() -> Unit)? = null,
    canRegress: Boolean = false,
) {
    val writable = remember(graph) { OutlineGraphEngine.writableIds(graph) }
    val pending = remember(graph) {
        if (graph.awaitingChoice) OutlineGraphEngine.findPendingBranch(graph) else null
    }
    val pendingIds = remember(pending) { pending?.second?.map { it.id }.orEmpty().toSet() }
    val pathIds = remember(graph) { OutlineGraphEngine.activeSpineIds(graph) }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val nodeFillActive = MaterialTheme.colorScheme.surface
    val cardBg = legadoCardBackgroundColor()
    val density = LocalDensity.current

    val nodeH = with(density) { 48.dp.toPx() }
    val nodeW = with(density) { 180.dp.toPx() }
    val accentW = with(density) { 8.dp.toPx() }
    val padH = with(density) { 20.dp.toPx() }
    val layout = remember(graph, nodeW, nodeH) { layoutTree(graph, nodeW = nodeW, nodeH = nodeH) }
    val contentBounds = remember(layout, nodeW, nodeH) {
        contentBoundsOf(layout, nodeW = nodeW, nodeH = nodeH)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    val eInk = AppConfig.isEInkMode
    // Absorb scroll so sheet/list parents do not steal one/two-finger pan-zoom on the canvas.
    val nestScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPreFling(available: Velocity): Velocity = available
        }
    }

    fun panMargin(viewport: Size): Float =
        minOf(viewport.width, viewport.height).coerceAtLeast(1f) * 0.22f

    fun clampOffset(raw: Offset, s: Float, viewport: Size): Offset {
        if (viewport.width <= 0f || contentBounds.isEmpty) return raw
        return clampPanOffset(
            offset = raw,
            scale = s,
            content = contentBounds,
            viewport = viewport,
            margin = panMargin(viewport),
        )
    }

    LaunchedEffect(contentBounds, viewportSize, scale) {
        if (viewportSize.width > 0f) {
            offset = clampOffset(offset, scale, viewportSize)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .nestedScroll(nestScrollLock)
            .onSizeChanged { size: IntSize ->
                viewportSize = Size(size.width.toFloat(), size.height.toFloat())
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(contentBounds) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val viewport = Size(size.width.toFloat(), size.height.toFloat())
                        val (newScale, newOffset) = applyCentroidPanZoom(
                            centroid = centroid,
                            panDelta = pan,
                            zoom = zoom,
                            scale = scale,
                            offset = offset,
                            minScale = 0.35f,
                            maxScale = 3f,
                        )
                        scale = newScale
                        offset = clampPanOffset(
                            offset = newOffset,
                            scale = newScale,
                            content = contentBounds,
                            viewport = viewport,
                            margin = panMargin(viewport),
                        )
                    }
                }
                .pointerInput(
                    layout,
                    pendingIds,
                    scale,
                    offset,
                    onSelectOption,
                    onSetCurrent,
                    writable,
                    nodeW,
                    nodeH,
                ) {
                    detectTapGestures { tap ->
                        val world = Offset(
                            (tap.x - offset.x) / scale,
                            (tap.y - offset.y) / scale,
                        )
                        // 1. Pending option selection takes priority.
                        val selectHandler = onSelectOption
                        if (selectHandler != null) {
                            layout.nodes.forEach { (id, pos) ->
                                if (id !in pendingIds) return@forEach
                                val rect = Rect(offset = Offset(pos.x, pos.y), size = Size(nodeW, nodeH))
                                if (rect.contains(world)) {
                                    selectHandler(id)
                                    return@detectTapGestures
                                }
                            }
                        }
                        // 2. Set current node on any writable non-root node (manual regress / fix advance).
                        val currentHandler = onSetCurrent
                        if (currentHandler != null) {
                            layout.nodes.forEach { (id, pos) ->
                                if (id !in writable) return@forEach
                                val node = graph.nodes[id] ?: return@forEach
                                if (node.type == OutlineNodeType.ROOT) return@forEach
                                val rect = Rect(offset = Offset(pos.x, pos.y), size = Size(nodeW, nodeH))
                                if (rect.contains(world)) {
                                    currentHandler(id)
                                    return@detectTapGestures
                                }
                            }
                        }
                    }
                },
        ) {
            // Edges in column gaps (drawn under cards). Spine uses chain links so
            // elbows do not cross intermediate chapter cards.
            fun drawElbow(fromPos: Offset, toPos: Offset, color: Color, strokeW: Float, dashed: Boolean) {
                val start = Offset(
                    (fromPos.x + nodeW) * scale + offset.x,
                    (fromPos.y + nodeH / 2) * scale + offset.y,
                )
                val end = Offset(
                    toPos.x * scale + offset.x,
                    (toPos.y + nodeH / 2) * scale + offset.y,
                )
                val midX = (start.x + end.x) / 2f
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(midX, start.y)
                    lineTo(midX, end.y)
                    lineTo(end.x, end.y)
                }
                // Halo clears crossings with other strokes before the colored line.
                drawPath(
                    path = path,
                    color = cardBg,
                    style = Stroke(width = strokeW * 2.8f),
                )
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeW,
                        pathEffect = if (dashed && !eInk) {
                            PathEffect.dashPathEffect(floatArrayOf(8f * scale, 6f * scale))
                        } else null,
                    ),
                )
            }

            layout.edges.forEach { (from, to) ->
                val a = layout.nodes[from] ?: return@forEach
                val b = layout.nodes[to] ?: return@forEach
                val onPath = from in pathIds && to in pathIds
                drawElbow(
                    fromPos = a,
                    toPos = b,
                    color = if (onPath) {
                        primary.copy(alpha = 0.9f)
                    } else {
                        outline.copy(alpha = 0.35f)
                    },
                    strokeW = (if (onPath) 3.25f else 1.25f) * scale,
                    dashed = !onPath,
                )
            }

            layout.nodes.forEach { (id, pos) ->
                val node = graph.nodes[id] ?: return@forEach
                if (node.type == OutlineNodeType.ROOT) return@forEach
                val topLeft = Offset(pos.x * scale + offset.x, pos.y * scale + offset.y)
                val size = Size(nodeW * scale, nodeH * scale)
                val radius = CornerRadius(10f * scale, 10f * scale)
                val active = id in writable
                val isPending = id in pendingIds
                val isCurrent = id == graph.currentNodeId
                val isSelectedOpt = node.type == OutlineNodeType.OPTION && node.selected == true
                val onPath = id in pathIds

                val typeColor = when (node.type) {
                    OutlineNodeType.VOLUME -> secondary
                    OutlineNodeType.BRANCH -> tertiary
                    OutlineNodeType.OPTION -> primary
                    OutlineNodeType.CHAPTER, OutlineNodeType.SECTION -> primary.copy(alpha = 0.75f)
                    OutlineNodeType.ROOT -> outline
                }

                val borderColor = when {
                    isCurrent -> primary
                    isPending -> primary
                    onPath -> primary
                    active -> onSurface.copy(alpha = 0.28f)
                    else -> outline.copy(alpha = 0.35f)
                }
                val borderWidth = when {
                    isCurrent -> 3.25f
                    isPending -> 2.75f
                    onPath -> 2.75f
                    else -> 1.1f
                } * scale

                // Opaque base so edge strokes never show through cards.
                drawRoundRect(color = nodeFillActive, topLeft = topLeft, size = size, cornerRadius = radius)
                when {
                    isCurrent -> drawRoundRect(
                        color = primary.copy(alpha = 0.26f),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = radius,
                    )
                    isPending -> drawRoundRect(
                        color = primary.copy(alpha = 0.16f),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = radius,
                    )
                    onPath -> drawRoundRect(
                        color = primary.copy(alpha = 0.12f),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = radius,
                    )
                    !active -> drawRoundRect(
                        color = outline.copy(alpha = 0.18f),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = radius,
                    )
                }
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = radius,
                    style = Stroke(
                        width = borderWidth,
                        pathEffect = if (isPending && !eInk) {
                            PathEffect.dashPathEffect(floatArrayOf(10f * scale, 6f * scale))
                        } else null,
                    ),
                )
                // Left type accent
                drawRoundRect(
                    color = typeColor.copy(alpha = if (onPath || isPending || active) 1f else 0.28f),
                    topLeft = topLeft,
                    size = Size(accentW * scale, size.height),
                    cornerRadius = CornerRadius(radius.x, 0f),
                )

                val typeTag = when (node.type) {
                    OutlineNodeType.VOLUME -> "卷"
                    OutlineNodeType.CHAPTER -> "章"
                    OutlineNodeType.SECTION -> "节"
                    OutlineNodeType.BRANCH -> "叉"
                    OutlineNodeType.OPTION -> if (isSelectedOpt) "✓" else "○"
                    OutlineNodeType.ROOT -> ""
                }
                val title = node.title
                    .removePrefix("分支：")
                    .removePrefix("分支:")
                    .trim()
                    .let { if (it.length > 14) it.take(13) + "…" else it }
                val currentMark = if (isCurrent) "▶ " else ""
                val label = "$currentMark$typeTag  $title"
                val textAlpha = when {
                    isCurrent || isPending || onPath -> 1f
                    active -> 0.85f
                    else -> 0.38f
                }
                val paint = android.graphics.Paint().apply {
                    color = onSurface.copy(alpha = textAlpha).toArgbCompat()
                    textSize = 13f * density.density * scale
                    isAntiAlias = !eInk
                    isFakeBoldText = isCurrent || isPending || onPath
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    topLeft.x + padH * scale + accentW * scale,
                    topLeft.y + (nodeH * 0.62f) * scale,
                    paint,
                )
            }
        }
        if (onRegressCurrent != null) {
            FloatingActionButton(
                onClick = { if (canRegress) onRegressCurrent() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
                    .copy(alpha = if (canRegress) 1f else 0.35f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    .copy(alpha = if (canRegress) 1f else 0.45f),
            ) {
                Icon(Icons.Default.Undo, contentDescription = "回退", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Color.toArgbCompat(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

private data class TreeLayout(
    val nodes: Map<String, Offset>,
    val edges: List<Pair<String, String>>,
)

private fun contentBoundsOf(layout: TreeLayout, nodeW: Float, nodeH: Float): Rect {
    if (layout.nodes.isEmpty()) return Rect.Zero
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    layout.nodes.values.forEach { pos ->
        minX = minOf(minX, pos.x)
        minY = minOf(minY, pos.y)
        maxX = maxOf(maxX, pos.x + nodeW)
        maxY = maxOf(maxY, pos.y + nodeH)
    }
    if (!minX.isFinite() || !maxX.isFinite()) return Rect.Zero
    return Rect(minX, minY, maxX, maxY)
}

/**
 * Horizontal story tree:
 * - Main spine (volume/chapter/section/option children): sequence left→right
 * - BRANCH→OPTION only: fan top→bottom, then each option continues right
 */
private fun layoutTree(graph: OutlineGraph, nodeW: Float, nodeH: Float): TreeLayout {
    val positions = linkedMapOf<String, Offset>()
    val edges = mutableListOf<Pair<String, String>>()
    val xStep = nodeW + 72f
    val seqGap = 48f
    val fanGap = 88f
    val topPad = 16f
    val leftPad = 16f

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun linkSequence(ids: List<String>) {
        if (ids.isEmpty()) return
        for (i in 0 until ids.lastIndex) {
            edges.add(ids[i] to ids[i + 1])
        }
    }

    fun place(nodeId: String, originX: Float, originY: Float): Box {
        val node = graph.nodes[nodeId] ?: return Box(originX, originY, originX, originY)
        val childIds = node.children

        if (node.type == OutlineNodeType.ROOT) {
            if (childIds.isEmpty()) return Box(originX, originY, originX, originY)
            var cursorX = leftPad
            var minTop = Float.POSITIVE_INFINITY
            var maxBottom = Float.NEGATIVE_INFINITY
            var maxRight = leftPad
            childIds.forEachIndexed { index, childId ->
                if (index > 0) cursorX += seqGap
                val box = place(childId, cursorX, topPad)
                cursorX = box.right
                minTop = minOf(minTop, box.top)
                maxBottom = maxOf(maxBottom, box.bottom)
                maxRight = maxOf(maxRight, box.right)
            }
            // Root is not drawn — chain top-level volumes instead of root→each.
            linkSequence(childIds)
            return Box(leftPad, minTop, maxRight, maxBottom)
        }

        if (childIds.isEmpty()) {
            positions[nodeId] = Offset(originX, originY)
            return Box(originX, originY, originX + nodeW, originY + nodeH)
        }

        if (node.type == OutlineNodeType.BRANCH) {
            // Fork: options share the next column, stacked vertically
            val childX = originX + xStep
            var cursorY = originY
            val boxes = ArrayList<Box>(childIds.size)
            childIds.forEachIndexed { index, childId ->
                if (index > 0) cursorY += fanGap
                val box = place(childId, childX, cursorY)
                boxes.add(box)
                edges.add(nodeId to childId)
                cursorY = box.bottom
            }
            val spanTop = boxes.minOf { it.top }
            val spanBottom = boxes.maxOf { it.bottom }
            val parentY = ((spanTop + spanBottom) / 2f - nodeH / 2f)
                .coerceIn(spanTop, (spanBottom - nodeH).coerceAtLeast(spanTop))
            positions[nodeId] = Offset(originX, parentY)
            return Box(
                originX,
                minOf(parentY, spanTop),
                maxOf(originX + nodeW, boxes.maxOf { it.right }),
                maxOf(parentY + nodeH, spanBottom),
            )
        }

        // Spine sequence: chain edges parent→first→…→last (avoid crossing cards)
        var cursorX = originX + xStep
        val boxes = ArrayList<Box>(childIds.size)
        childIds.forEachIndexed { index, childId ->
            if (index > 0) cursorX += seqGap
            val box = place(childId, cursorX, originY)
            boxes.add(box)
            cursorX = box.right
        }
        edges.add(nodeId to childIds.first())
        linkSequence(childIds)
        val spanTop = boxes.minOf { it.top }
        val spanBottom = boxes.maxOf { it.bottom }
        val parentY = ((spanTop + spanBottom) / 2f - nodeH / 2f)
            .coerceIn(spanTop, (spanBottom - nodeH).coerceAtLeast(spanTop))
        positions[nodeId] = Offset(originX, parentY)
        return Box(
            originX,
            minOf(parentY, spanTop),
            maxOf(originX + nodeW, boxes.maxOf { it.right }),
            maxOf(parentY + nodeH, spanBottom),
        )
    }

    place(graph.rootId, leftPad, topPad)
    val minY = positions.values.minOfOrNull { it.y } ?: topPad
    val shift = if (minY < topPad) topPad - minY else 0f
    val normalized = if (shift == 0f) {
        positions
    } else {
        positions.mapValues { (_, p) -> Offset(p.x, p.y + shift) }
    }
    return TreeLayout(normalized, edges.toList())
}
