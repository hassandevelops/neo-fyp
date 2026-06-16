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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neo.ui.theme.*

/**
 * Bottom navigation bar — solid dark surface.
 * Layout: Home | Search | [LIME FAB] | Bell | Profile
 * Active icon: NeoLime  |  Inactive: white/40
 */
@Composable
fun EnhancedBottomNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    onFabClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NeoBlack,          // Solid — matches app background
        tonalElevation = 0.dp
    ) {
        // navigationBarsPadding lifts the controls above the device nav area
        // (gesture pill or 3-button bar); the black surface fills behind it.
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(NeoHairline)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                NavIcon(
                    icon = if (selectedRoute == "feed") Icons.Filled.Home else Icons.Outlined.Home,
                    label = "Home",
                    selected = selectedRoute == "feed",
                    onClick = { onNavigate("feed") }
                )

                // Search
                NavIcon(
                    icon = if (selectedRoute == "search") Icons.Filled.Search else Icons.Outlined.Search,
                    label = "Search",
                    selected = selectedRoute == "search",
                    onClick = { onNavigate("search") }
                )

                // ── Center NeoLime FAB ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = NeoLimeGlow20,
                            spotColor = NeoLimeGlow20
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
                    label = "Notifications",
                    selected = selectedRoute == "notifications",
                    onClick = { onNavigate("notifications") }
                )

                // Profile
                NavIcon(
                    icon = if (selectedRoute == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                    label = "Profile",
                    selected = selectedRoute == "profile",
                    onClick = { onNavigate("profile") }
                )
            }
        }
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) NeoLime else TextWhite40
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
