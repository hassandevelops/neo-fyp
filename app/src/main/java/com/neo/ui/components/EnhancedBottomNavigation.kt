package com.neo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neo.ui.theme.*

/**
 * Bottom navigation bar.
 * Layout from design samples:
 *   Home | Search | [LIME FAB] | Bell | Profile
 * Active icon: NeoLime  |  Inactive: white/40
 */
@Composable
fun EnhancedBottomNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    onFabClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_glow")
    val fabGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fab_glow"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NeoBlack,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            NavIcon(
                icon = if (selectedRoute == "feed") Icons.Filled.Home else Icons.Outlined.Home,
                selected = selectedRoute == "feed",
                onClick = { onNavigate("feed") }
            )

            // Search
            NavIcon(
                icon = if (selectedRoute == "search") Icons.Filled.Search else Icons.Outlined.Search,
                selected = selectedRoute == "search",
                onClick = { onNavigate("search") }
            )

            // ── Center NeoLime FAB ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        ambientColor = NeoLime.copy(alpha = fabGlow),
                        spotColor = NeoLime.copy(alpha = fabGlow)
                    )
                    .clip(CircleShape)
                    .background(NeoLime)
                    .clickable(onClick = onFabClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create post",
                    tint = NeoBlack,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Bell
            NavIcon(
                icon = if (selectedRoute == "notifications") Icons.Filled.Notifications else Icons.Outlined.Notifications,
                selected = selectedRoute == "notifications",
                onClick = { onNavigate("notifications") }
            )

            // Profile
            NavIcon(
                icon = if (selectedRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                selected = selectedRoute == "profile",
                onClick = { onNavigate("profile") }
            )
        }
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) NeoLime else TextWhite40
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
