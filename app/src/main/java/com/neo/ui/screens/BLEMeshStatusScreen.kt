package com.neo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.theme.*

/**
 * BLE Mesh Status Screen with network topology visualization
 */
@Composable
fun BLEMeshStatusScreen(
    connectedPeers: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack)
    ) {
        // Animated wave background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeoPurple.copy(alpha = 0.3f),
                            NeoCyan.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    
                    Text(
                        text = "BLE Mesh Network",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeoCyan.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, NeoCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeoCyan)
                            )
                            Text(
                                text = "ACTIVE",
                                color = NeoCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Central Sync Icon
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(128.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pulse rings
                            repeat(3) { index ->
                                Box(
                                    modifier = Modifier
                                        .size((80 + index * 40).dp)
                                        .border(
                                            2.dp,
                                            NeoCyan.copy(alpha = 0.3f - index * 0.1f),
                                            CircleShape
                                        )
                                )
                            }
                            
                            // Center icon
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(NeoCyan, NeoPurple, NeoPink)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Syncing",
                                    tint = TextWhite,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .rotate(rotation)
                                )
                            }
                        }
                    }
                }
                
                // Status text
                item {
                    Text(
                        text = if (connectedPeers.isNotEmpty()) "Syncing mesh network..." else "Network idle",
                        color = if (connectedPeers.isNotEmpty()) NeoCyan else TextWhite60,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                // Metrics Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            icon = Icons.Default.People,
                            label = "Nearby Devices",
                            value = connectedPeers.size.toString(),
                            color = NeoCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            icon = Icons.Default.Schedule,
                            label = "Last Sync",
                            value = "0s ago",
                            color = NeoPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            icon = Icons.Default.Speed,
                            label = "Data Rate",
                            value = "0 kb/s",
                            color = NeoPink,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            icon = Icons.Default.SignalCellularAlt,
                            label = "Signal Quality",
                            value = "Good",
                            color = StatusOnline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Network Topology
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceWhite5
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = null,
                                        tint = NeoCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Network Topology",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StatusOnline.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, StatusOnline.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "${connectedPeers.size} Online",
                                        color = StatusOnline,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            
                            if (connectedPeers.isEmpty()) {
                                Text(
                                    text = "No peers connected",
                                    color = TextWhite40,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Device list
                items(connectedPeers) { peer ->
                    DeviceNodeCard(
                        deviceName = peer,
                        rssi = -50,
                        isOnline = true
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Text(
                text = label,
                color = TextWhite40,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = value,
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeviceNodeCard(
    deviceName: String,
    rssi: Int,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) StatusOnline else StatusOffline)
                )
                
                Column {
                    Text(
                        text = deviceName,
                        color = TextWhite,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "RSSI: $rssi dBm",
                        color = TextWhite40,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Signal strength bars
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((8 + index * 4).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (Math.abs(rssi) < 50 + index * 10) NeoCyan else SurfaceWhite20
                            )
                    )
                }
            }
        }
    }
}
