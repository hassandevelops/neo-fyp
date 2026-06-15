package com.neo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Notification
import com.neo.ui.theme.*

/**
 * Subtle, non-blocking in-app banner that slides down from the top to announce a
 * new notification (like / comment / follow / mention / repost). Matte Neo surface
 * with a lime accent and the actor's avatar; tap opens the related content.
 */
@Composable
fun NeoNotificationBanner(
    notification: Notification?,
    avatarUri: String?,
    visible: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && notification != null,
        enter = slideInVertically(animationSpec = tween(280)) { -it } + fadeIn(tween(280)),
        exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(220)),
        modifier = modifier
    ) {
        val n = notification ?: return@AnimatedVisibility
        val accent = when (n.type) {
            "like" -> NeoRed
            "comment", "reply", "follow" -> NeoLime
            "peer" -> NeoTeal
            "system" -> NeoOrange
            else -> NeoLime
        }
        val icon = when (n.type) {
            "like" -> Icons.Filled.Favorite
            "comment" -> Icons.Filled.Chat
            "reply" -> Icons.Filled.Reply
            "follow" -> Icons.Filled.PersonAdd
            "peer" -> Icons.Filled.Radio
            "system" -> Icons.Filled.Info
            else -> Icons.Filled.Notifications
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .neoSurfaceElevated(shape = NeoShapes.cardLarge, tone = SurfaceElevated2)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                UserAvatar(
                    imageUri = avatarUri,
                    size = 40.dp,
                    contentDescription = n.authorName
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(NeoBlack)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(10.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = n.message,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to view",
                    color = TextWhite40,
                    fontSize = 11.sp
                )
            }
        }
    }
}
