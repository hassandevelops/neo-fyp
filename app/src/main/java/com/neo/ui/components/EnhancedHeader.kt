package com.neo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.R
import com.neo.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale

/**
 * App header — solid dark surface, integrated naturally into the app layout.
 * No glass effect, no floating appearance.
 */
@Composable
fun EnhancedHeader(
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    notificationCount: Int,
    profileImageUri: String? = null,
    modifier: Modifier = Modifier
) {
    val badgeAlpha by if (notificationCount > 0) {
        val t = rememberInfiniteTransition(label = "badge")
        t.animateFloat(
            initialValue = 1f, targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "badge_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NeoBlack,          // Solid — matches app background
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Left: Avatar + wordmark ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Profile avatar circle — lime ring
                UserAvatar(
                    imageUri = profileImageUri,
                    size = 36.dp,
                    ringColor = NeoLime,
                    contentDescription = "Profile",
                    modifier = Modifier.clickable(onClick = onProfileClick)
                )

                Image(
                    painter = painterResource(id = R.drawable.splash_no_bg),
                    contentDescription = "Neo",
                    modifier = Modifier.height(56.dp)
                )
            }

            // ── Right: Bell ──────────────────────────────────────────────
            Box {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Pulsing red dot badge
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .size(8.dp)
                            .graphicsLayer { alpha = badgeAlpha }
                            .background(NeoRed, CircleShape)
                    )
                }
            }
        }
    }
}