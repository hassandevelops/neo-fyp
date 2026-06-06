package com.neo.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.neo.data.model.Post
import com.neo.ui.theme.*
import java.io.File

/**
 * Post card — full-bleed image with dark scrim overlay.
 * Author info and stats overlaid at the bottom of the image.
 */
@Composable
fun EnhancedPostCard(
    post: Post,
    onPostClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit = {},
    isLiked: Boolean = false,
    isSaved: Boolean = false,
    likeCount: Int = 0,
    commentCount: Int = 0,
    isLive: Boolean = false,
    authorImageUri: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val likeColor by animateColorAsState(
        targetValue = if (isLiked) NeoLime else TextWhite60,
        label = "like_color"
    )
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "like_scale"
    )
    val bookmarkColor by animateColorAsState(
        targetValue = if (isSaved) NeoLime else TextWhite60,
        label = "bookmark_color"
    )

    val livePulse by if (isLive) {
        val t = rememberInfiniteTransition(label = "live_pulse")
        t.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_pulse"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onPostClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeoGray900)
    ) {
        Column {
            val imagePath = post.imageHash?.let {
                java.io.File(context.filesDir, "images/$it.jpg").takeIf { f -> f.exists() }
            }

            Box {
                if (imagePath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imagePath),
                        contentDescription = "Post image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = post.content,
                        color = TextWhite,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // LIVE badge — top-left
                if (isLive) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(NeoBlack.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(livePulse)
                                .background(NeoGreen, CircleShape)
                        )
                        Text("LIVE", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // View count — top-right
                if (imagePath != null && commentCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(NeoBlack.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.People, null, tint = TextWhite60, modifier = Modifier.size(13.dp))
                        Text(
                            text = if (commentCount > 999) "${commentCount / 1000}K" else commentCount.toString(),
                            color = TextWhite,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Author + action section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeoGray800),
                        contentAlignment = Alignment.Center
                    ) {
                        if (authorImageUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(model = authorImageUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TextWhite60,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = post.authorName,
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (post.content.isNotEmpty() && imagePath != null) {
                            Text(
                                text = post.content,
                                color = TextWhite60,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onLikeClick()
                            }
                        ) {
                            Box(modifier = Modifier.scale(likeScale)) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = likeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(likeCount.toString(), color = TextWhite80, fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable(onClick = onCommentClick)
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, "Comment", tint = TextWhite60, modifier = Modifier.size(20.dp))
                            Text(commentCount.toString(), color = TextWhite80, fontSize = 13.sp)
                        }

                        Icon(
                            Icons.Outlined.Share,
                            "Share",
                            tint = TextWhite60,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { sharePost(context, post) }
                        )
                    }

                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = bookmarkColor,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onSaveClick() }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val m = diff / 60000; val h = m / 60; val d = h / 24
    return when { d > 0 -> "${d}d ago"; h > 0 -> "${h}h ago"; m > 0 -> "${m}m ago"; else -> "Just now" }
}

private fun sharePost(context: Context, post: Post) {
    val deepLink = "neo://posts/${post.id}"
    val preview = post.content.take(120)
    val shareText = buildString {
        appendLine("Check out this post on Neo")
        appendLine()
        appendLine(preview)
        appendLine()
        append(deepLink)
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "Neo Post by ${post.authorName}")

        if (post.imageHash != null) {
            val imageFile = File(context.filesDir, "images/${post.imageHash}.jpg")
            if (imageFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", imageFile
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Share post via"))
    }
}
