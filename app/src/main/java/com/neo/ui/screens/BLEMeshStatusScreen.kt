package com.neo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.R
import com.neo.ui.theme.*
import androidx.compose.ui.res.painterResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * BLE Mesh Status Screen – "Neo Mesh Status" with orbital animation and 2×2 stat grid.
 * Matches the dark-mode isometric design mockup.
 */
@Composable
fun BLEMeshStatusScreen(
    connectedPeers: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Animations ───────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "orbit_anim")

    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_progress"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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
                        painter = painterResource(id = R.drawable.splash),
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

            // ── Orbital Canvas ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .drawBehind {
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        // ── Central glow ──
                        val glowRadius = 55f * glowPulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeoLime.copy(alpha = 0.45f * glowPulse),
                                    NeoLime.copy(alpha = 0.15f * glowPulse),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = glowRadius * 2.5f
                            ),
                            radius = glowRadius * 2.5f,
                            center = Offset(cx, cy)
                        )

                        // Inner solid core
                        drawCircle(
                            color = NeoLime,
                            radius = 14f,
                            center = Offset(cx, cy)
                        )
                        // Outer glow ring
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeoLime.copy(alpha = 0.7f * glowPulse),
                                    NeoLime.copy(alpha = 0.0f)
                                ),
                                center = Offset(cx, cy),
                                radius = 32f
                            ),
                            radius = 32f,
                            center = Offset(cx, cy)
                        )

                        // ── Orbits (3 tilted ellipses) ──
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                        data class OrbitSpec(
                            val rx: Float,
                            val ry: Float,
                            val rotation: Float,
                            val satAngleOffset: Float
                        )

                        val orbits = listOf(
                            OrbitSpec(rx = 160f, ry = 60f, rotation = -15f, satAngleOffset = 0f),
                            OrbitSpec(rx = 200f, ry = 75f, rotation = 10f, satAngleOffset = 2.1f),
                            OrbitSpec(rx = 240f, ry = 90f, rotation = -5f, satAngleOffset = 4.2f)
                        )

                        for (orbit in orbits) {
                            withTransform({
                                translate(cx - orbit.rx, cy - orbit.ry)
                                rotate(
                                    degrees = orbit.rotation,
                                    pivot = Offset(orbit.rx, orbit.ry)
                                )
                            }) {
                                drawOval(
                                    color = TextWhite20,
                                    topLeft = Offset.Zero,
                                    size = Size(orbit.rx * 2f, orbit.ry * 2f),
                                    style = Stroke(
                                        width = 1.5f,
                                        pathEffect = dashEffect
                                    )
                                )
                            }

                            // Satellite dot
                            val angle = orbitProgress + orbit.satAngleOffset
                            val rad = Math.toRadians(orbit.rotation.toDouble())
                            val baseX = orbit.rx * cos(angle)
                            val baseY = orbit.ry * sin(angle)
                            // apply rotation transform manually
                            val sx = cx + (baseX * cos(rad) - baseY * sin(rad)).toFloat()
                            val sy = cy + (baseX * sin(rad) + baseY * cos(rad)).toFloat()

                            // Small glow behind satellite
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        NeoLime.copy(alpha = 0.5f),
                                        Color.Transparent
                                    ),
                                    center = Offset(sx, sy),
                                    radius = 14f
                                ),
                                radius = 14f,
                                center = Offset(sx, sy)
                            )
                            drawCircle(
                                color = NeoLime,
                                radius = 5f,
                                center = Offset(sx, sy)
                            )
                        }
                    }
            )

            Spacer(Modifier.height(12.dp))

            // ── Pager Indicator ──────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) NeoLime else TextWhite20)
                    )
                }
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
                        badgeBackgroundColor = SurfaceWhite20,
                        badgeTextColor = TextWhite80,
                        showProgressBar = false,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        backgroundColor = NeoGray900,
                        icon = Icons.Default.BarChart,
                        title = "Throughput",
                        mainValue = "--",
                        subValue = "",
                        badgeText = null,
                        showProgressBar = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        backgroundColor = NeoGray900,
                        icon = Icons.Default.GraphicEq,
                        title = "Avg Latency",
                        mainValue = "--",
                        subValue = "",
                        badgeText = null,
                        showProgressBar = false,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        backgroundColor = NeoGray900,
                        icon = Icons.Default.Sensors,
                        title = "Transfer Rate",
                        mainValue = "--",
                        subValue = "",
                        badgeText = null,
                        showProgressBar = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
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
    badgeBackgroundColor: Color = SurfaceWhite20,
    badgeTextColor: Color = TextWhite80,
    showProgressBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    tint = TextWhite60,
                    modifier = Modifier.size(20.dp)
                )

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBackgroundColor
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextColor,
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
                    color = TextWhite60,
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
                    color = TextWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subValue.isNotEmpty()) {
                    Text(
                        text = subValue,
                        color = TextWhite40,
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
                        .background(SurfaceWhite10)
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
