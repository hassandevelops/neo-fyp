package com.neo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.theme.*

/**
 * Bottom navigation bar with gradient theme
 */
@Composable
fun EnhancedBottomNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NeoBlack.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NeoGray900.copy(alpha = 0.8f),
                            NeoBlack
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "Feed",
                    selected = selectedRoute == "feed",
                    onClick = { onNavigate("feed") }
                )
                
                BottomNavItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = selectedRoute == "search",
                    onClick = { onNavigate("search") }
                )
                
                BottomNavItem(
                    icon = Icons.Default.Radio,
                    label = "Network",
                    selected = selectedRoute == "ble_status",
                    onClick = { onNavigate("ble_status") }
                )
                
                BottomNavItem(
                    icon = Icons.Default.Notifications,
                    label = "Alerts",
                    selected = selectedRoute == "notifications",
                    onClick = { onNavigate("notifications") }
                )
                
                BottomNavItem(
                    icon = Icons.Default.Person,
                    label = "Profile",
                    selected = selectedRoute == "profile",
                    onClick = { onNavigate("profile") }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) SurfaceWhite10 else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (selected) 40.dp else 32.dp)
                    .background(
                        brush = if (selected) {
                            Brush.linearGradient(
                                colors = listOf(NeoPurple, NeoOrange, NeoTeal)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(SurfaceWhite5, SurfaceWhite5)
                            )
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) TextWhite else TextWhite60,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        if (selected) {
            Text(
                text = label,
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
