package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neo.R
import com.neo.ui.theme.SurfaceElevated3
import com.neo.ui.theme.TextWhite60

/**
 * Centralized circular user avatar.
 *
 * Renders the user's profile image when [imageUri] is non-null, otherwise a clean
 * person-silhouette placeholder ([R.drawable.ic_default_avatar]). This is the single
 * source of truth for avatar fallback behavior across the app — feed, comments,
 * profile, header, stories, search, etc.
 *
 * @param imageUri profile image URI/path, or null to show the placeholder.
 * @param size diameter of the avatar.
 * @param ringColor optional border ring drawn around the avatar.
 */
@Composable
fun UserAvatar(
    imageUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color? = null,
    ringWidth: Dp = 2.dp,
    contentDescription: String? = null,
) {
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .let { if (ringColor != null) it.border(ringWidth, ringColor, CircleShape) else it }
        .background(SurfaceElevated3, CircleShape)

    Box(modifier = base, contentAlignment = Alignment.Center) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (ringColor != null) ringWidth else 0.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Silhouette fills the circle; shoulders are clipped by the CircleShape.
            Icon(
                painter = painterResource(id = R.drawable.ic_default_avatar),
                contentDescription = contentDescription,
                tint = TextWhite60,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
