package com.neo.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.data.model.Post
import com.neo.ui.theme.*

/**
 * Enhanced post card with gradient borders and glow effects
 */
@Composable
fun EnhancedPostCard(
    post: Post,
    onPostClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    commentCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var liked by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var likes by remember { mutableStateOf(0) }
    
    // Animated scale for like button
    val likeScale by animateFloatAsState(
        targetValue = if (liked) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "like_scale"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = NeoPurple.copy(alpha = 0.15f),
                spotColor = NeoOrange.copy(alpha = 0.1f)
            )
            .clickable(onClick = onPostClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, BorderWhite20, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Author",
                            tint = TextWhite60,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = post.authorName,
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTimestamp(post.timestamp),
                            color = TextWhite40,
                            fontSize = 12.sp
                        )
                    }
                }
                
                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = TextWhite60
                    )
                }
            }
            
            // Content
            if (post.content.isNotEmpty()) {
                Text(
                    text = post.content,
                    color = TextWhite80,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Image if present
            if (!post.imageUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    NeoOrange.copy(alpha = 0.3f),
                                    NeoPink.copy(alpha = 0.3f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = java.io.File(post.imageUri)
                        ),
                        contentDescription = "Post image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Like button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            liked = !liked
                            likes = if (liked) likes + 1 else likes - 1
                            onLikeClick()
                        }
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (liked) NeoOrange else TextWhite60,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = likes.toString(),
                            color = TextWhite80,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Comment button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onCommentClick)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comment",
                            tint = TextWhite60,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = commentCount.toString(),
                            color = TextWhite80,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Share button
                    // Share button
                    Box(
                        modifier = Modifier
                            .clickable { sharePost(context, post) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextWhite60,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                }
                
                // Bookmark button
                IconButton(onClick = { saved = !saved }) {
                    Icon(
                        imageVector = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (saved) NeoPurple else TextWhite60,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

/**
 * Share post content using Android's native share dialog
 */
private fun sharePost(context: Context, post: Post) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Check out this post by ${post.authorName}:\n\n${post.content}")
        putExtra(Intent.EXTRA_SUBJECT, "Neo Post")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share post via"))
}
