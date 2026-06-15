package com.neo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.theme.NeoLime
import com.neo.ui.theme.TextWhite40
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NEO MESH RADAR ────────────────────────────────────────────────────────────
// Real-time radar visualization of nearby mesh peers. The current device is the
// pulsing centre; discovered peers orbit it on rings whose radius encodes signal.
//
// NOTE ON DATA: the app exposes only peer IDs + counts (no RSSI/routing metrics).
// Per-node angle and signal are therefore derived DETERMINISTICALLY from the peer
// ID hash, so each peer keeps a stable position/quality across recompositions (no
// flicker / jumps); discovery and loss are detected by diffing the live id set.
// `meshNodesFrom` is the single seam where real radio/routing data plugs in later.
// ═══════════════════════════════════════════════════════════════════════════════

enum class MeshNodeState { CONNECTED, RELAY, DISCOVERABLE, WEAK, LOST }

data class MeshNode(
    val id: String,
    val state: MeshNodeState,
    val signal: Float,   // 0..1 — 1 = strong (near centre), 0 = weak (outer ring)
    val angle: Float,    // radians — stable placement around the centre
    val label: String = id.takeLast(4),
)

/** Stable 0..1 pseudo-value from a string + salt (no Math.random → no flicker). */
private fun hashUnit(id: String, salt: Int): Float {
    var h = 1125899906842597L  // prime
    for (c in id) h = 31 * h + c.code
    h = 31 * h + salt
    // xorshift mix for better distribution, then fold to 0..1
    var x = h
    x = x xor (x ushr 33); x *= -0xae502812aa7333L
    x = x xor (x ushr 29); x *= -0x3b314601e57a13adL
    x = x xor (x ushr 32)
    return ((x ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
}

/**
 * Pure mapping from the raw mesh inputs to radar nodes. Deterministic: same inputs
 * always yield the same angles/signals/states. This is the extension seam — when
 * real RSSI / routing / battery data exists, enrich the [MeshNode]s here without
 * touching the radar UI.
 *
 * @param maxDiscoverable cap on DHT "discoverable" dots rendered (density guard).
 */
fun meshNodesFrom(
    connectedPeers: List<String>,
    dhtPeerCount: Int,
    maxDiscoverable: Int = 14,
): List<MeshNode> {
    val nodes = ArrayList<MeshNode>(connectedPeers.size + min(dhtPeerCount, maxDiscoverable))

    connectedPeers.forEachIndexed { index, id ->
        val signalRaw = hashUnit(id, 1)
        // Bias connected peers toward stronger signal so they sit nearer the centre.
        val signal = (0.45f + signalRaw * 0.55f).coerceIn(0f, 1f)
        val state = when {
            signal >= 0.72f -> MeshNodeState.CONNECTED
            signal >= 0.50f -> MeshNodeState.RELAY
            else -> MeshNodeState.WEAK
        }
        // Golden-angle spread + per-id jitter → even, stable, non-overlapping angles.
        val angle = (index * 2.399963f + hashUnit(id, 7) * 0.9f)
        nodes += MeshNode(id = id, state = state, signal = signal, angle = angle)
    }

    // Synthesize bounded "discoverable" WAN/DHT peers on the outer band.
    val discoverable = min(dhtPeerCount, maxDiscoverable)
    val connectedSet = connectedPeers.toHashSet()
    var made = 0
    var i = 0
    while (made < discoverable) {
        val id = "dht:$i"
        if (id !in connectedSet) {
            val signal = 0.08f + hashUnit(id, 3) * 0.28f   // outer rings, low signal
            val angle = (connectedPeers.size + made) * 2.399963f + hashUnit(id, 11)
            nodes += MeshNode(
                id = id,
                state = MeshNodeState.DISCOVERABLE,
                signal = signal,
                angle = angle,
                label = "",
            )
            made++
        }
        i++
    }
    return nodes
}

/**
 * Radar visualization. Renders [nodes] around a pulsing centre device with rings,
 * a rotating sweep, connection lines, and per-node state encoding. Discovery and
 * loss animate via per-node presence; positions/signal animate smoothly.
 *
 * @param onNodeClick reserved for future "tap node → details"; currently optional.
 */
@Composable
fun MeshRadar(
    nodes: List<MeshNode>,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") onNodeClick: ((MeshNode) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    // Per-node presence (0 hidden → 1 shown). Drives discovery-in / loss-out fades
    // so nodes never pop or vanish abruptly.
    val presence = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    // Latest known node data, kept until its fade-out completes (for graceful loss).
    val nodeData = remember { mutableStateMapOf<String, MeshNode>() }

    LaunchedEffect(nodes) {
        val liveIds = nodes.map { it.id }.toHashSet()
        nodes.forEach { node ->
            nodeData[node.id] = node
            val anim = presence.getOrPut(node.id) { Animatable(0f) }
            if (anim.targetValue != 1f) {
                scope.launch { anim.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            }
        }
        // Fade out + prune nodes that disappeared from the live set.
        presence.keys.filter { it !in liveIds }.forEach { goneId ->
            val anim = presence[goneId] ?: return@forEach
            if (anim.targetValue != 0f) {
                scope.launch {
                    anim.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
                    presence.remove(goneId)
                    nodeData.remove(goneId)
                }
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "radar")
    val sweepAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val centerPulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "center_pulse",
    )
    val commsPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "comms",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val maxR = min(cx, cy) * 0.92f
            val visibleCount = presence.values.count { it.value > 0.05f }
            // Adaptive de-emphasis as the field gets busy → less clutter.
            val density = (visibleCount / 18f).coerceIn(0f, 1f)

            drawRadarRings(center, maxR)
            drawSweep(center, maxR, sweepAngle)

            // Connection lines first (behind nodes).
            nodeData.values.forEach { node ->
                val p = presence[node.id]?.value ?: 0f
                if (p <= 0.02f) return@forEach
                if (node.state == MeshNodeState.CONNECTED || node.state == MeshNodeState.RELAY) {
                    drawConnection(center, node, maxR, p, density, commsPhase)
                }
            }

            // Peer nodes.
            nodeData.values.forEach { node ->
                val p = presence[node.id]?.value ?: 0f
                if (p <= 0.02f) return@forEach
                drawPeerNode(center, node, maxR, p, density, sweepAngle)
            }

            drawCenterNode(center, centerPulse)
        }

        // Empty state — radar stays alive; caption sits below the centre.
        if (nodes.isEmpty()) {
            Text(
                text = "Scanning for nearby peers…",
                color = TextWhite40,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .wrapContentSize()
                    .padding(top = 64.dp),
            )
        }
    }
}

// ── Drawing helpers ─────────────────────────────────────────────────────────

private fun DrawScope.drawRadarRings(center: Offset, maxR: Float) {
    val rings = 4
    for (i in 1..rings) {
        val r = maxR * i / rings
        drawCircle(
            color = NeoLime.copy(alpha = 0.05f + 0.03f * (rings - i)),
            radius = r,
            center = center,
            style = Stroke(width = 1f),
        )
    }
    // Faint cross-hair for orientation.
    drawLine(NeoLime.copy(alpha = 0.04f), Offset(center.x - maxR, center.y), Offset(center.x + maxR, center.y), 1f)
    drawLine(NeoLime.copy(alpha = 0.04f), Offset(center.x, center.y - maxR), Offset(center.x, center.y + maxR), 1f)
}

private fun DrawScope.drawSweep(center: Offset, maxR: Float, sweepAngle: Float) {
    // Soft trailing wedge built from a sweep gradient; lime, low alpha.
    val brush = Brush.sweepGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.06f to NeoLime.copy(alpha = 0.16f),
            0.12f to Color.Transparent,
            1.0f to Color.Transparent,
        ),
        center = center,
    )
    // Rotate the gradient by drawing into a rotated frame.
    val sweepDegrees = (sweepAngle * 180f / Math.PI).toFloat()
    rotate(degrees = sweepDegrees, pivot = center) {
        drawCircle(brush = brush, radius = maxR, center = center)
    }
    // Leading edge line.
    val end = Offset(center.x + cos(sweepAngle) * maxR, center.y + sin(sweepAngle) * maxR)
    drawLine(NeoLime.copy(alpha = 0.22f), center, end, 1.5f)
}

private fun DrawScope.drawConnection(
    center: Offset,
    node: MeshNode,
    maxR: Float,
    presence: Float,
    density: Float,
    commsPhase: Float,
) {
    val r = nodeRadius(node, maxR)
    val pos = Offset(center.x + cos(node.angle) * r, center.y + sin(node.angle) * r)
    val baseAlpha = (if (node.state == MeshNodeState.CONNECTED) 0.20f else 0.12f) *
        presence * (1f - density * 0.5f)
    drawLine(NeoLime.copy(alpha = baseAlpha), center, pos, 1f)
    // Subtle "comet" travelling outward to imply active comms.
    val t = commsPhase
    val cometPos = Offset(center.x + (pos.x - center.x) * t, center.y + (pos.y - center.y) * t)
    drawCircle(
        color = NeoLime.copy(alpha = (0.5f * presence * (1f - t)).coerceIn(0f, 1f)),
        radius = 2.2f,
        center = cometPos,
    )
}

private fun DrawScope.drawPeerNode(
    center: Offset,
    node: MeshNode,
    maxR: Float,
    presence: Float,
    density: Float,
    sweepAngle: Float,
) {
    val r = nodeRadius(node, maxR)
    val pos = Offset(center.x + cos(node.angle) * r, center.y + sin(node.angle) * r)

    // State → visual treatment (size / glow / opacity / motion), not colour alone.
    val (coreR, glowAlpha, coreAlpha) = when (node.state) {
        MeshNodeState.CONNECTED -> Triple(4.2f, 0.45f, 1f)
        MeshNodeState.RELAY -> Triple(3.6f, 0.32f, 0.92f)
        MeshNodeState.DISCOVERABLE -> Triple(2.4f, 0.16f, 0.55f)
        MeshNodeState.WEAK -> Triple(2.6f, 0.18f, 0.6f)
        MeshNodeState.LOST -> Triple(2.4f, 0.08f, 0.3f)
    }
    val densityScale = 1f - density * 0.25f

    // Sweep highlight — boost as the beam passes over this node's angle.
    val da = angularDistance(sweepAngle, node.angle)
    val sweepBoost = (1f - (da / 0.5f)).coerceIn(0f, 1f)   // within ~0.5 rad
    val glow = (glowAlpha + sweepBoost * 0.35f) * presence

    // Discovery ripple — a ring that expands as the node fades in.
    if (presence < 0.999f) {
        val ripple = (1f - presence)
        drawCircle(
            color = NeoLime.copy(alpha = 0.35f * presence),
            radius = coreR + ripple * 26f,
            center = pos,
            style = Stroke(width = 1.5f),
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeoLime.copy(alpha = glow), Color.Transparent),
            center = pos,
            radius = coreR * 4.5f * densityScale,
        ),
        radius = coreR * 4.5f * densityScale,
        center = pos,
    )
    // RELAY gets a hollow ring to read as a forwarding node.
    if (node.state == MeshNodeState.RELAY) {
        drawCircle(
            color = NeoLime.copy(alpha = 0.5f * coreAlpha * presence),
            radius = coreR + 2.5f,
            center = pos,
            style = Stroke(width = 1.2f),
        )
    }
    drawCircle(
        color = NeoLime.copy(alpha = coreAlpha * presence),
        radius = coreR * densityScale * (0.6f + 0.4f * presence),
        center = pos,
    )
}

private fun DrawScope.drawCenterNode(center: Offset, pulse: Float) {
    // Outer ambient pulse glow.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                NeoLime.copy(alpha = 0.40f * pulse),
                NeoLime.copy(alpha = 0.10f * pulse),
                Color.Transparent,
            ),
            center = center,
            radius = 46f * pulse,
        ),
        radius = 46f * pulse,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeoLime.copy(alpha = 0.6f * pulse), Color.Transparent),
            center = center,
            radius = 26f,
        ),
        radius = 26f,
        center = center,
    )
    // Solid core — the focal point.
    drawCircle(color = NeoLime, radius = 7.5f, center = center)
}

// signal 1 → near centre (0.18 of maxR); signal 0 → outer ring (0.95 of maxR).
private fun nodeRadius(node: MeshNode, maxR: Float): Float =
    maxR * (0.95f - node.signal * 0.77f)

private fun angularDistance(a: Float, b: Float): Float {
    val twoPi = (2f * Math.PI).toFloat()
    var d = ((a - b) % twoPi + twoPi) % twoPi
    if (d > Math.PI) d = twoPi - d
    return d
}
