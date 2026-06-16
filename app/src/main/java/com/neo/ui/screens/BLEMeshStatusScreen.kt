package com.neo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.R
import com.neo.ui.components.MeshRadar
import com.neo.ui.components.meshNodesFrom
import com.neo.ui.components.organicPattern
import com.neo.ui.theme.*
import androidx.compose.ui.res.painterResource

/**
 * BLE Mesh Status Screen – "Neo Mesh Status" with orbital animation and 2×2 stat grid.
 * Matches the dark-mode isometric design mockup.
 */
@Composable
fun BLEMeshStatusScreen(
    connectedPeers: List<String>,
    onForceSync: () -> Unit,
    onBack: () -> Unit,
    dhtPeerCount: Int = 0,
    bootstrapConnected: Boolean = false,
    lastError: String? = null,
    reachability: String = "unknown",
    onShowConnectQr: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top App Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: NEO brand
                Row(
                    modifier = Modifier.clickable(onClick = onBack),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.splash_no_bg),
                        contentDescription = "Neo logo",
                        modifier = Modifier.size(76.dp)
                    )
                }

                // Right: Bell
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = TextWhite60,
                    modifier = Modifier.size(24.dp)
                )
            }

            // ── Title ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Neo Mesh Status",
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "System Diagnostic & Topology",
                    color = TextWhite40,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Mesh Radar ───────────────────────────────────────────────
            // Real-time view of nearby peers around this device. Node positions /
            // signal are derived deterministically from peer IDs (no RSSI in the
            // data layer); discovery & loss animate as the live peer set changes.
            MeshRadar(
                nodes = meshNodesFrom(
                    connectedPeers = connectedPeers,
                    dhtPeerCount = dhtPeerCount,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── Force Sync ──────────────────────────────────────────────
            Button(
                onClick = onForceSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeoLime,
                    contentColor = NeoBlack
                ),
                shape = NeoShapes.pill
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Force Sync Now", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            // ── 2×2 Metrics Grid ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        backgroundColor = NeoCardTeal,
                        icon = Icons.Default.Hub,
                        title = "Active Nodes",
                        mainValue = connectedPeers.size.toString(),
                        subValue = "/ 32",
                        badgeText = "Optimal",
                        showProgressBar = false,
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                    ThroughputRingCard(
                        value = "--",
                        unit = "kb/s",
                        progress = 0f,
                        awaitingData = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LatencyTrendCard(
                        value = "--",
                        unit = "ms",
                        samples = emptyList(),
                        trendLabel = "Idle",
                        modifier = Modifier.weight(1f)
                    )
                    TransferRateCard(
                        value = "--",
                        unit = "kb/s",
                        activity = 0f,
                        awaitingData = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── WAN (libp2p) Status ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "WAN (libp2p)",
                    color = TextWhite60,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NeoShapes.card,
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .organicPattern(SurfaceElevated1, richness = 4)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WanStatusRow(
                            label = "Bootstrap",
                            value = if (bootstrapConnected) "Connected" else "Connecting…",
                            color = if (bootstrapConnected) NeoLime else TextWhite60
                        )
                        WanStatusRow(
                            label = "DHT peers",
                            value = dhtPeerCount.toString(),
                            color = TextWhite
                        )
                        val (reachLabel, reachColor) = when (reachability) {
                            "public" -> "Public — can relay for peers" to NeoLime
                            "private" -> "Private — behind NAT" to TextWhite60
                            else -> "Checking…" to TextWhite60
                        }
                        WanStatusRow(
                            label = "Reachability",
                            value = reachLabel,
                            color = reachColor
                        )
                        lastError?.let { err ->
                            WanStatusRow(
                                label = "Last error",
                                value = err.take(60),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onShowConnectQr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Connect a device (show my QR)")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WanStatusRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextWhite60, fontSize = 13.sp)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Reusable Stat Card ──────────────────────────────────────────────────────
@Composable
private fun StatCard(
    backgroundColor: Color,
    icon: ImageVector,
    title: String,
    mainValue: String,
    subValue: String,
    badgeText: String?,
    badgeBackgroundColor: Color = SurfaceElevated3,
    badgeTextColor: Color = TextWhite80,
    showProgressBar: Boolean = false,
    accent: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Lime accent variant uses dark content for contrast.
    val container = if (accent) NeoLime else backgroundColor
    val iconTint = if (accent) NeoBlack else TextWhite60
    val titleColor = if (accent) NeoBlack.copy(alpha = 0.7f) else TextWhite60
    val valueColor = if (accent) NeoBlack else TextWhite
    val subColor = if (accent) NeoBlack.copy(alpha = 0.55f) else TextWhite40
    Card(
        modifier = modifier,
        shape = NeoShapes.tile,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .organicPattern(container, richness = 5)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: icon + optional badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (accent) NeoBlack.copy(alpha = 0.12f) else badgeBackgroundColor
                    ) {
                        Text(
                            text = badgeText,
                            color = if (accent) NeoBlack else badgeTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Title (if any)
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Main value + sub value
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = mainValue,
                    color = valueColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subValue.isNotEmpty()) {
                    Text(
                        text = subValue,
                        color = subColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            // Optional progress bar
            if (showProgressBar) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SurfaceElevated3)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(NeoLime)
                    )
                }
            }
        }
    }
}

// ── Shared scaffold for visualization metric cards ──────────────────────────
// Keeps every diagnostic card in the same family: same tile shape, dark surface,
// organic pattern, header row (icon + title) and a visualization slot beneath.
@Composable
private fun MetricVizCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    viz: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = NeoShapes.tile,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .organicPattern(SurfaceElevated1, richness = 5)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = TextWhite60, modifier = Modifier.size(20.dp))
                    Text(title, color = TextWhite60, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (badgeText != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceElevated3) {
                        Text(
                            text = badgeText,
                            color = TextWhite80,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
                content = viz
            )
        }
    }
}

/**
 * Throughput — a clean solid 360° lime progress ring with the value in the centre.
 * [progress] (0..1). When [awaitingData] the ring softly pulses to read as "live,
 * no telemetry yet" rather than showing a fake fill.
 */
@Composable
private fun ThroughputRingCard(
    value: String,
    unit: String,
    progress: Float,
    awaitingData: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "throughput")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tp_pulse",
    )
    val sweep by animateFloatAsState(
        targetValue = if (awaitingData) 0.18f else progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "tp_sweep",
    )
    MetricVizCard(icon = Icons.Default.BarChart, title = "Throughput", modifier = modifier) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val d = size.minDimension - stroke
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val arcSize = Size(d, d)
                // Soft outer glow.
                drawArc(
                    brush = Brush.radialGradient(
                        listOf(NeoLime.copy(alpha = 0.12f * pulse), Color.Transparent),
                        center = center, radius = size.minDimension / 2f,
                    ),
                    startAngle = 0f, sweepAngle = 360f, useCenter = true,
                )
                // Track ring.
                drawArc(
                    color = SurfaceElevated3, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                // Progress ring.
                drawArc(
                    color = NeoLime.copy(alpha = if (awaitingData) 0.55f * pulse else 1f),
                    startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(unit, color = TextWhite40, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Transfer Rate — a segmented radial gauge (discrete ticks around a ring) with a
 * rotating radar-style scanner, so it reads as live activity and is visually
 * distinct from the solid Throughput ring.
 */
@Composable
private fun TransferRateCard(
    value: String,
    unit: String,
    activity: Float,
    awaitingData: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "transfer")
    val scan by infinite.animateFloat(
        initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "tr_scan",
    )
    val litFraction by animateFloatAsState(
        targetValue = if (awaitingData) 0.0f else activity.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "tr_lit",
    )
    MetricVizCard(icon = Icons.Default.Sensors, title = "Transfer Rate", modifier = modifier) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val segments = 28
                val r = size.minDimension / 2f - 4.dp.toPx()
                val inner = r - 9.dp.toPx()
                val twoPi = (2f * Math.PI).toFloat()
                for (i in 0 until segments) {
                    val a = -Math.PI.toFloat() / 2f + twoPi * i / segments
                    // Angular nearness to the rotating scanner → highlight.
                    var da = kotlin.math.abs(((a - scan + twoPi) % twoPi))
                    if (da > Math.PI) da = twoPi - da
                    val scanBoost = (1f - da / 0.6f).coerceIn(0f, 1f)
                    val lit = i < (segments * litFraction).toInt()
                    val base = when {
                        lit -> 0.85f
                        else -> 0.16f
                    }
                    val alpha = (base + scanBoost * 0.5f).coerceIn(0f, 1f)
                    val p1 = Offset(center.x + kotlin.math.cos(a) * inner, center.y + kotlin.math.sin(a) * inner)
                    val p2 = Offset(center.x + kotlin.math.cos(a) * r, center.y + kotlin.math.sin(a) * r)
                    drawLine(NeoLime.copy(alpha = alpha), p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                // Scanner sweep line.
                val end = Offset(center.x + kotlin.math.cos(scan) * inner, center.y + kotlin.math.sin(scan) * inner)
                drawLine(NeoLime.copy(alpha = 0.4f), center, end, strokeWidth = 1.5f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(unit, color = TextWhite40, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Avg Latency — a compact sparkline emphasising stability / spikes / trend, with
 * the current value shown prominently above it. [samples] are normalised 0..1
 * (higher = worse latency). When awaiting data a gentle animated baseline shows.
 */
@Composable
private fun LatencyTrendCard(
    value: String,
    unit: String,
    samples: List<Float>,
    trendLabel: String,
    modifier: Modifier = Modifier,
) {
    val phase by rememberInfiniteTransition(label = "latency").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "lat_phase",
    )
    val animatedSamples = if (samples.isEmpty()) {
        // Idle breathing baseline so the card still feels alive without faking data.
        List(24) { i -> 0.5f + 0.12f * kotlin.math.sin((i / 24f + phase) * 2f * Math.PI).toFloat() }
    } else samples
    MetricVizCard(icon = Icons.Default.GraphicEq, title = "Avg Latency", modifier = modifier, badgeText = trendLabel) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(unit, color = TextWhite40, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val n = animatedSamples.size
                if (n < 2) return@Canvas
                val stepX = size.width / (n - 1)
                fun y(v: Float) = size.height * (1f - v.coerceIn(0f, 1f)) * 0.9f + size.height * 0.05f
                val line = Path()
                val fill = Path()
                animatedSamples.forEachIndexed { i, v ->
                    val px = stepX * i
                    val py = y(v)
                    if (i == 0) { line.moveTo(px, py); fill.moveTo(px, size.height); fill.lineTo(px, py) }
                    else { line.lineTo(px, py); fill.lineTo(px, py) }
                }
                fill.lineTo(size.width, size.height)
                fill.close()
                // Soft area fill under the line.
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(listOf(NeoLime.copy(alpha = 0.18f), Color.Transparent)),
                )
                drawPath(line, color = NeoLime, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                // Highlight the latest point.
                val lastX = stepX * (n - 1)
                val lastY = y(animatedSamples.last())
                drawCircle(NeoLime.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(NeoLime, radius = 2.5f.dp.toPx(), center = Offset(lastX, lastY))
            }
        }
    }
}
