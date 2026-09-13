package io.legado.app.ui.book.info.network

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.domain.model.DramaticRole
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.applyCentroidPanZoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val DOT_SPACING_DP = 36f
private const val NAME_MAX_CHARS = 6
private const val LABEL_NORMAL_OFFSET_PX = 12f
/** Cycle: fit-all ↔ one closer zoom. */
private val ZOOM_CYCLE_SCALES = floatArrayOf(1.5f)

@Composable
fun ForceDirectedGraph(
    nodes: ImmutableList<NetworkNodeUi>,
    edges: ImmutableList<NetworkEdgeUi>,
    hubNodeId: String? = null,
    selectedNodeId: String? = null,
    simulationEnabled: Boolean = true,
    onSimulationSettled: () -> Unit = {},
    onSelectNode: (String?) -> Unit = {},
    onNodeDragStart: () -> Unit = {},
    fitTrigger: Int = 0,
    centerHubTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val nodeColor = scheme.primary
    val edgeColor = scheme.outline
    val outlineSoft = scheme.outlineVariant
    val surfaceColor = scheme.surface
    val nameColor = scheme.onSurface
    val relationColor = scheme.onSurfaceVariant
    val isEInk = AppConfig.isEInkMode
    val density = LocalDensity.current
    val dotSpacingPx = with(density) { DOT_SPACING_DP.dp.toPx() }
    val strokeDefault = with(density) { 1.25.dp.toPx() }
    val strokeSelected = with(density) { 1.75.dp.toPx() }
    val strokeHubInner = with(density) { 1.25.dp.toPx() }
    val strokeEInk = with(density) { 1.75.dp.toPx() }
    val strokeEInkSelected = with(density) { 2.dp.toPx() }
    val hubRingWidth = with(density) { 2.5.dp.toPx() }
    val selectedOuterWidth = with(density) { 2.5.dp.toPx() }
    val selectedMidWidth = with(density) { 1.25.dp.toPx() }
    val edgeWidthDefault = with(density) { 1.5.dp.toPx() }
    val edgeWidthHot = with(density) { 2.dp.toPx() }
    val dotRadius = with(density) { 1.75.dp.toPx() }

    fun paintColor(c: Color): Int = android.graphics.Color.argb(
        (c.alpha * 255).toInt(),
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
    )

    val namePaint = remember(nameColor, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 12.sp.toPx() }
            typeface = android.graphics.Typeface.DEFAULT
            color = paintColor(nameColor)
        }
    }
    val relationPaint = remember(relationColor, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 10.sp.toPx() }
            typeface = android.graphics.Typeface.DEFAULT
            color = paintColor(relationColor)
        }
    }

    val positions = remember { mutableStateMapOf<String, Offset>() }
    val velocities = remember { mutableStateMapOf<String, Offset>() }
    val avatarBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(Offset(1f, 1f)) }
    var quietFrames by remember { mutableIntStateOf(0) }
    /** 0 = fit-all; 1..N = [ZOOM_CYCLE_SCALES]. Advances on each fitTrigger. */
    var zoomCycleIndex by remember { mutableIntStateOf(0) }

    val neighborIds = remember(selectedNodeId, edges) {
        if (selectedNodeId == null) emptySet()
        else {
            val set = mutableSetOf(selectedNodeId)
            edges.forEach { edge ->
                when (selectedNodeId) {
                    edge.fromNodeId -> set.add(edge.toNodeId)
                    edge.toNodeId -> set.add(edge.fromNodeId)
                }
            }
            set
        }
    }

    // Cache by node id; invalidate when path or file mtime changes. State writes on Main.
    val avatarLoadedMeta = remember { mutableMapOf<String, Pair<String, Long>>() }
    val avatarLoadKey = remember(nodes) {
        nodes.map { node ->
            val path = node.avatarPath
            val mtime = if (path.isNotBlank()) File(path).lastModified() else 0L
            Triple(node.id, path, mtime)
        }
    }
    LaunchedEffect(avatarLoadKey) {
        val wanted = avatarLoadKey.associate { (id, path, mtime) -> id to (path to mtime) }
        avatarBitmaps.keys.toList().forEach { id ->
            val meta = wanted[id]
            if (meta == null || meta.first.isBlank()) {
                avatarBitmaps.remove(id)
                avatarLoadedMeta.remove(id)
            }
        }
        val toLoad = wanted.filter { (id, meta) ->
            val (path, mtime) = meta
            if (path.isBlank()) return@filter false
            val loaded = avatarLoadedMeta[id]
            loaded == null ||
                loaded.first != path ||
                loaded.second != mtime ||
                avatarBitmaps[id] == null
        }
        if (toLoad.isEmpty()) return@LaunchedEffect
        // Drop stale entries before reload so UI does not keep old bitmaps.
        toLoad.keys.forEach { id ->
            avatarBitmaps.remove(id)
            avatarLoadedMeta.remove(id)
        }
        val loaded = withContext(Dispatchers.IO) {
            toLoad.mapNotNull { (id, meta) ->
                val (path, mtime) = meta
                val file = File(path)
                if (!file.exists()) return@mapNotNull null
                val bitmap = runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull() ?: return@mapNotNull null
                Triple(id, bitmap, path to mtime)
            }
        }
        loaded.forEach { (id, bitmap, meta) ->
            val current = wanted[id] ?: return@forEach
            if (current != meta) return@forEach
            avatarBitmaps[id] = bitmap
            avatarLoadedMeta[id] = meta
        }
    }

    fun radiusFor(role: String, isHub: Boolean): Float {
        val base = when (DramaticRole.normalize(role)) {
            DramaticRole.MALE_LEAD, DramaticRole.FEMALE_LEAD -> 22f
            DramaticRole.MALE_SUPPORTING, DramaticRole.FEMALE_SUPPORTING -> 16f
            else -> 12f
        }
        return if (isHub) base + 2f else base
    }

    fun displayName(raw: String): String {
        val t = raw.trim()
        if (t.length <= NAME_MAX_CHARS) return t
        return t.take(NAME_MAX_CHARS) + "…"
    }

    fun contentCenter(): Offset? {
        if (positions.isEmpty()) return null
        val xs = positions.values.map { it.x }
        val ys = positions.values.map { it.y }
        val minX = xs.minOrNull() ?: return null
        val maxX = xs.maxOrNull() ?: return null
        val minY = ys.minOrNull() ?: return null
        val maxY = ys.maxOrNull() ?: return null
        return Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
    }

    fun panToCenter(world: Offset) {
        val w = canvasSize.x
        val h = canvasSize.y
        pan = Offset(w / 2f - world.x * scale, h / 2f - world.y * scale)
    }

    fun fitAll() {
        val w = canvasSize.x
        val h = canvasSize.y
        if (w < 2f || h < 2f || positions.isEmpty()) {
            scale = 1f
            pan = Offset.Zero
            return
        }
        val xs = positions.values.map { it.x }
        val ys = positions.values.map { it.y }
        val minX = xs.minOrNull() ?: return
        val maxX = xs.maxOrNull() ?: return
        val minY = ys.minOrNull() ?: return
        val maxY = ys.maxOrNull() ?: return
        val bw = (maxX - minX).coerceAtLeast(1f)
        val bh = (maxY - minY).coerceAtLeast(1f)
        val pad = 48f
        scale = min((w - pad) / bw, (h - pad) / bh).coerceIn(0.4f, 3f)
        contentCenter()?.let { panToCenter(it) }
    }

    /** Cycle: fit → 1.5x → fit … */
    fun cycleZoom() {
        val steps = 1 + ZOOM_CYCLE_SCALES.size
        if (zoomCycleIndex == 0) {
            fitAll()
        } else {
            scale = ZOOM_CYCLE_SCALES[zoomCycleIndex - 1].coerceIn(0.4f, 3f)
            val focus = hubNodeId?.let { positions[it] } ?: contentCenter()
            if (focus != null) panToCenter(focus) else {
                pan = Offset.Zero
            }
        }
        zoomCycleIndex = (zoomCycleIndex + 1) % steps
    }

    fun centerHub() {
        val hub = hubNodeId?.let { positions[it] } ?: return
        panToCenter(hub)
    }

    LaunchedEffect(fitTrigger) {
        if (fitTrigger > 0) cycleZoom()
    }
    LaunchedEffect(centerHubTrigger) {
        if (centerHubTrigger > 0) centerHub()
    }

    LaunchedEffect(nodes.map { it.id }.joinToString(), canvasSize, hubNodeId) {
        val cx = canvasSize.x / 2f
        val cy = canvasSize.y / 2f
        val radius = min(cx, cy) * 0.5f
        nodes.forEachIndexed { index, node ->
            if (positions[node.id] == null) {
                val angle = if (node.id == hubNodeId) {
                    0.0
                } else {
                    2.0 * Math.PI * index / max(nodes.size, 1)
                }
                val dist = if (node.id == hubNodeId) 0f else radius
                positions[node.id] = Offset(
                    cx + (dist * cos(angle)).toFloat() + if (node.id == hubNodeId) 0f else Random.nextFloat() * 8f,
                    cy + (dist * sin(angle)).toFloat() + if (node.id == hubNodeId) 0f else Random.nextFloat() * 8f,
                )
                velocities[node.id] = Offset.Zero
            }
        }
        val keep = nodes.map { it.id }.toSet()
        positions.keys.filter { it !in keep }.forEach { positions.remove(it) }
        velocities.keys.filter { it !in keep }.forEach { velocities.remove(it) }
    }

    LaunchedEffect(nodes, edges, draggingId, canvasSize, hubNodeId, simulationEnabled) {
        if (!simulationEnabled) return@LaunchedEffect
        quietFrames = 0
        while (true) {
            withFrameNanos {
                val w = canvasSize.x
                val h = canvasSize.y
                if (w < 2f || h < 2f || nodes.isEmpty()) return@withFrameNanos
                val repulsion = 8000f
                val spring = 0.02f
                val idealLen = 140f
                val damping = 0.82f
                val centerPull = 0.005f
                val hubPull = 0.02f
                val cx = w / 2f
                val cy = h / 2f
                val hubPos = hubNodeId?.let { positions[it] }
                val forces = nodes.associate { it.id to Offset.Zero }.toMutableMap()

                for (i in nodes.indices) {
                    for (j in i + 1 until nodes.size) {
                        val a = nodes[i]
                        val b = nodes[j]
                        val pa = positions[a.id] ?: continue
                        val pb = positions[b.id] ?: continue
                        var dx = pa.x - pb.x
                        var dy = pa.y - pb.y
                        var dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist < 1f) {
                            dx = Random.nextFloat() - 0.5f
                            dy = Random.nextFloat() - 0.5f
                            dist = 1f
                        }
                        val force = repulsion / (dist * dist)
                        val fx = dx / dist * force
                        val fy = dy / dist * force
                        forces[a.id] = forces.getValue(a.id) + Offset(fx, fy)
                        forces[b.id] = forces.getValue(b.id) - Offset(fx, fy)
                    }
                }

                edges.forEach { edge ->
                    val pa = positions[edge.fromNodeId] ?: return@forEach
                    val pb = positions[edge.toNodeId] ?: return@forEach
                    val dx = pb.x - pa.x
                    val dy = pb.y - pa.y
                    val dist = max(hypot(dx.toDouble(), dy.toDouble()).toFloat(), 1f)
                    val stretch = dist - idealLen
                    val fx = dx / dist * stretch * spring
                    val fy = dy / dist * stretch * spring
                    forces[edge.fromNodeId] = forces.getValue(edge.fromNodeId) + Offset(fx, fy)
                    forces[edge.toNodeId] = forces.getValue(edge.toNodeId) - Offset(fx, fy)
                }

                var maxSpeed = 0f
                nodes.forEach { node ->
                    if (node.id == draggingId) return@forEach
                    val p = positions[node.id] ?: return@forEach
                    var v = velocities[node.id] ?: Offset.Zero
                    val anchor = when {
                        node.id == hubNodeId -> Offset(cx, cy)
                        hubPos != null -> hubPos
                        else -> Offset(cx, cy)
                    }
                    val pull = when {
                        node.id == hubNodeId -> hubPull
                        else -> centerPull
                    }
                    val f = forces.getValue(node.id) +
                        Offset((anchor.x - p.x) * pull, (anchor.y - p.y) * pull)
                    v = Offset((v.x + f.x) * damping, (v.y + f.y) * damping)
                    val np = Offset(
                        (p.x + v.x).coerceIn(36f, w - 36f),
                        (p.y + v.y).coerceIn(36f, h - 36f),
                    )
                    positions[node.id] = np
                    velocities[node.id] = v
                    maxSpeed = max(maxSpeed, hypot(v.x.toDouble(), v.y.toDouble()).toFloat())
                }
                if (draggingId == null && maxSpeed < 0.35f) {
                    quietFrames += 1
                    if (quietFrames >= 30) {
                        onSimulationSettled()
                    }
                } else {
                    quietFrames = 0
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .nestedScroll(
                remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                            available
                        override suspend fun onPreFling(available: Velocity): Velocity = available
                    }
                },
            )
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val (newScale, newPan) = applyCentroidPanZoom(
                        centroid = centroid,
                        panDelta = panChange,
                        zoom = zoom,
                        scale = scale,
                        offset = pan,
                        minScale = 0.4f,
                        maxScale = 3f,
                    )
                    scale = newScale
                    pan = newPan
                }
            }
            .pointerInput(nodes, scale, pan, positions.toMap()) {
                detectTapGestures { tap ->
                    val world = Offset(
                        (tap.x - pan.x) / scale,
                        (tap.y - pan.y) / scale,
                    )
                    val hit = nodes.minByOrNull { node ->
                        val p = positions[node.id] ?: Offset.Zero
                        hypot((p.x - world.x).toDouble(), (p.y - world.y).toDouble())
                    }?.takeIf { node ->
                        val p = positions[node.id] ?: return@takeIf false
                        val r = radiusFor(node.dramaticRole, node.id == hubNodeId) + 16f
                        hypot((p.x - world.x).toDouble(), (p.y - world.y).toDouble()) < r
                    }
                    onSelectNode(hit?.id)
                }
            }
            .pointerInput(nodes, scale, pan) {
                detectDragGestures(
                    onDragStart = { start ->
                        val world = Offset(
                            (start.x - pan.x) / scale,
                            (start.y - pan.y) / scale,
                        )
                        draggingId = nodes.minByOrNull { node ->
                            val p = positions[node.id] ?: Offset.Zero
                            hypot((p.x - world.x).toDouble(), (p.y - world.y).toDouble())
                        }?.takeIf { node ->
                            val p = positions[node.id] ?: return@takeIf false
                            hypot((p.x - world.x).toDouble(), (p.y - world.y).toDouble()) < 48.0
                        }?.id
                        if (draggingId != null) onNodeDragStart()
                    },
                    onDragEnd = { draggingId = null },
                    onDragCancel = { draggingId = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val id = draggingId ?: return@detectDragGestures
                        val cur = positions[id] ?: return@detectDragGestures
                        positions[id] = Offset(
                            cur.x + dragAmount.x / scale,
                            cur.y + dragAmount.y / scale,
                        )
                        velocities[id] = Offset.Zero
                    },
                )
            },
    ) {
        canvasSize = Offset(size.width, size.height)

        // P1: screen-space dot grid (disabled on E-Ink). Use outline so it reads on surfaceContainer*.
        if (!isEInk) {
            val dotColor = outlineSoft.copy(alpha = 0.55f)
            var x = dotSpacingPx * 0.5f
            while (x < size.width) {
                var y = dotSpacingPx * 0.5f
                while (y < size.height) {
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                    y += dotSpacingPx
                }
                x += dotSpacingPx
            }
        }

        drawContext.canvas.save()
        drawContext.canvas.translate(pan.x, pan.y)
        drawContext.canvas.scale(scale, scale)
        val radiusById = nodes.associate { it.id to radiusFor(it.dramaticRole, it.id == hubNodeId) }
        edges.forEach { edge ->
            val a = positions[edge.fromNodeId] ?: return@forEach
            val b = positions[edge.toNodeId] ?: return@forEach
            val ra = radiusById[edge.fromNodeId] ?: 12f
            val rb = radiusById[edge.toNodeId] ?: 12f
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < ra + rb + 2f) return@forEach
            val ux = dx / len
            val uy = dy / len
            // Start/end at node rims so gradient fade meets the circle cleanly.
            val start = Offset(a.x + ux * ra, a.y + uy * ra)
            val end = Offset(b.x - ux * rb, b.y - uy * rb)
            val onFocusPath = selectedNodeId != null &&
                edge.fromNodeId in neighborIds &&
                edge.toNodeId in neighborIds
            val edgeAlpha = when {
                selectedNodeId == null -> 1f
                onFocusPath -> 1f
                else -> if (isEInk) 0.35f else 0.2f
            }
            val hot = onFocusPath
            val midAlpha = (if (hot) 0.7f else edgeColor.alpha) * edgeAlpha
            val solid = if (hot) nodeColor else edgeColor
            if (isEInk) {
                drawLine(
                    color = solid.copy(alpha = midAlpha),
                    start = start,
                    end = end,
                    strokeWidth = if (hot) edgeWidthHot else edgeWidthDefault,
                )
            } else {
                // Fade both ends: transparent near nodes → opaque mid-span.
                drawLine(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to solid.copy(alpha = 0f),
                            0.18f to solid.copy(alpha = midAlpha),
                            0.82f to solid.copy(alpha = midAlpha),
                            1f to solid.copy(alpha = 0f),
                        ),
                        start = start,
                        end = end,
                    ),
                    start = start,
                    end = end,
                    strokeWidth = if (hot) edgeWidthHot else edgeWidthDefault,
                )
            }
            if (edge.label.isNotBlank() && edgeAlpha > 0.5f) {
                val segLen = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())
                    .toFloat()
                    .coerceAtLeast(1f)
                val nx = -(end.y - start.y) / segLen
                val ny = (end.x - start.x) / segLen
                val mid = Offset(
                    (start.x + end.x) / 2f + nx * LABEL_NORMAL_OFFSET_PX,
                    (start.y + end.y) / 2f + ny * LABEL_NORMAL_OFFSET_PX,
                )
                drawContext.canvas.nativeCanvas.drawText(edge.label, mid.x, mid.y, relationPaint)
            }
        }
        nodes.forEach { node ->
            val p = positions[node.id] ?: return@forEach
            val isHub = node.id == hubNodeId
            val selected = node.id == selectedNodeId
            val dimAlpha = if (isEInk) 0.35f else 0.22f
            val alpha = when {
                selectedNodeId == null -> 1f
                node.id in neighborIds -> 1f
                else -> dimAlpha
            }
            val r = radiusFor(node.dramaticRole, isHub)
            val fill = nodeColor.copy(alpha = nodeColor.alpha * alpha)

            // Outer glow: narrower + more faded than before
            when {
                selected && isEInk -> {
                    drawCircle(
                        color = nodeColor.copy(alpha = alpha),
                        radius = r + 4f,
                        center = p,
                        style = Stroke(width = strokeEInkSelected),
                    )
                }
                selected && !isEInk -> {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.10f * alpha),
                        radius = r + 7f,
                        center = p,
                        style = Stroke(width = selectedOuterWidth),
                    )
                    drawCircle(
                        color = surfaceColor.copy(alpha = 0.7f * alpha),
                        radius = r + 2.5f,
                        center = p,
                        style = Stroke(width = selectedMidWidth),
                    )
                }
                isHub && isEInk -> {
                    drawCircle(
                        color = nodeColor.copy(alpha = alpha),
                        radius = r + 3f,
                        center = p,
                        style = Stroke(width = strokeEInk),
                    )
                }
                isHub && !isEInk -> {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.08f * alpha),
                        radius = r + 5.5f,
                        center = p,
                        style = Stroke(width = hubRingWidth),
                    )
                }
            }

            val rimColor = when {
                selected || isHub -> nodeColor.copy(alpha = alpha)
                else -> outlineSoft.copy(alpha = outlineSoft.alpha * alpha)
            }
            val rimWidth = when {
                selected -> if (isEInk) strokeEInkSelected else strokeSelected
                isHub -> if (isEInk) strokeEInk else strokeHubInner
                else -> if (isEInk) strokeEInk else strokeDefault
            }

            val avatar = avatarBitmaps[node.id]
            if (avatar != null) {
                drawContext.canvas.nativeCanvas.save()
                val path = android.graphics.Path().apply {
                    addCircle(p.x, p.y, r, android.graphics.Path.Direction.CW)
                }
                drawContext.canvas.nativeCanvas.clipPath(path)
                drawImage(
                    image = avatar,
                    dstSize = androidx.compose.ui.unit.IntSize((r * 2).toInt(), (r * 2).toInt()),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (p.x - r).toInt(),
                        (p.y - r).toInt(),
                    ),
                    alpha = alpha,
                )
                drawContext.canvas.nativeCanvas.restore()
                drawCircle(
                    color = rimColor,
                    radius = r,
                    center = p,
                    style = Stroke(width = rimWidth),
                )
            } else {
                drawCircle(
                    color = fill.copy(alpha = 0.18f * alpha),
                    radius = r * 1.55f,
                    center = p,
                )
                drawCircle(color = fill, radius = r, center = p)
                drawCircle(
                    color = rimColor,
                    radius = r,
                    center = p,
                    style = Stroke(width = rimWidth),
                )
            }
            if (alpha > 0.4f) {
                drawContext.canvas.nativeCanvas.drawText(
                    displayName(node.name),
                    p.x,
                    p.y + r + 18f,
                    namePaint,
                )
            }
        }
        drawContext.canvas.restore()
    }
}
