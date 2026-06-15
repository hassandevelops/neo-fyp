package com.neo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme

data class StoryUser(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
    val hasStory: Boolean = false,
    val isLive: Boolean = false
)

@Composable
fun StoryRow(
    users: List<StoryUser>,
    onStoryClick: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // Add story button for current user
        item {
            AddStoryButton(onClick = onAddStoryClick)
        }

        // User stories
        items(users) { user ->
            StoryItem(
                user = user,
                onClick = { onStoryClick(user.id) }
            )
        }
    }
}

@Composable
private fun AddStoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NeoLime)
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Your Story",
                tint = NeoBlack,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "Your Story",
            color = TextWhite60,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoryItem(
    user: StoryUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(70.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp)
        ) {
            // Static border ring
            val ringColor = if (user.hasStory) NeoLime else NeoHairline
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .border(2.dp, ringColor, CircleShape)
            )

            // Avatar
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated3),
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    imageUri = user.avatarUrl,
                    size = 58.dp,
                    contentDescription = user.username
                )

                // Live badge
                if (user.isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .background(NeoGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("LIVE", color = NeoBlack, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = user.username,
            color = TextWhite,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}