package com.neo.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.neo.data.model.Post
import com.neo.ui.theme.*
import java.io.File
import kotlinx.coroutines.launch

/**
 * Post card — horizontal layout: media on the left, content on the right, with a
 * circular author-avatar cutout balanced on the top-right corner. Clean matte
 * surface (no texture). Image tap opens the full-screen viewer; double-tap likes;
 * tapping the avatar opens the author's profile.
 */
@Composable
fun EnhancedPostCard(
    post: Post,
    onImageClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
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
    val scope = rememberCoroutineScope()

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

    // Double-tap heart burst feedback
    var heartVisible by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartVisible) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_scale"
    )
    val heartAlpha by animateFloatAsState(
        targetValue = if (heartVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "heart_alpha"
    )
    // Instagram convention: double-tap only likes (never unlikes); always bursts.
    val onDoubleTapLike: () -> Unit = {
        if (!isLiked) onLikeClick()
        scope.launch {
            heartVisible = true
            kotlinx.coroutines.delay(650)
            heartVisible = false
        }
    }

    val imagePath = post.imageHash?.let {
        java.io.File(context.filesDir, "images/$it.jpg").takeIf { f -> f.exists() }
    }

    // Outer Box hosts the notched card and the avatar embedded in its cutout.
    Box(modifier = modifier.fillMaxWidth()) {
        NeoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PostCardTopInset),
            shape = PostCardShape,
            tone = SurfaceElevated2,
            textured = false
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    // Double-tap anywhere on the card (text posts) likes the post.
                    .pointerInput(post.id) {
                        detectTapGestures(onDoubleTap = { onDoubleTapLike() })
                    }
            ) {
                // ── Media (left) · content (right) ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (imagePath != null) {
                        Box(
                            modifier = Modifier
                                .width(118.dp)
                                .height(152.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceElevated3)
                                // Single tap → viewer, double tap → like
                                .pointerInput(post.id) {
                                    detectTapGestures(
                                        onTap = { onImageClick() },
                                        onDoubleTap = { onDoubleTapLike() }
                                    )
                                }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = imagePath),
                                contentDescription = "Post image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (isLive) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .background(NeoBlack.copy(alpha = 0.6f), NeoShapes.pill)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .scale(livePulse)
                                            .background(NeoGreen, CircleShape)
                                    )
                                    Text("LIVE", color = TextWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Content column on the RIGHT
                    Column(modifier = Modifier.weight(1f)) {
                        // Name + timestamp reserve clearance for the avatar cutout
                        Text(
                            text = post.authorName,
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 52.dp)
                        )
                        Row(
                            modifier = Modifier.padding(end = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isLive && imagePath == null) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .scale(livePulse)
                                        .background(NeoGreen, CircleShape)
                                )
                            }
                            Text(
                                text = formatTimestamp(post.timestamp),
                                color = TextWhite40,
                                fontSize = 13.sp
                            )
                        }

                        if (!post.locationName.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = NeoLime,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = post.locationName,
                                    color = TextWhite60,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (post.content.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = post.content,
                                color = TextWhite80,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (imagePath != null) 4 else 6,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Action row — full width, consistent for all post types ──────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionItem(
                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = likeCount.toString(),
                        tint = likeColor,
                        iconScale = likeScale,
                        onClick = onLikeClick
                    )
                    Spacer(Modifier.width(24.dp))
                    ActionItem(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = commentCount.toString(),
                        tint = TextWhite60,
                        onClick = onCommentClick
                    )
                    Spacer(Modifier.width(24.dp))
                    ActionItem(
                        icon = Icons.Outlined.Share,
                        label = null,
                        tint = TextWhite60,
                        onClick = { sharePost(context, post) }
                    )
                    Spacer(Modifier.weight(1f))
                    ActionItem(
                        icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = null,
                        tint = bookmarkColor,
                        onClick = onSaveClick
                    )
                }
            }
        }

        // Author avatar — embedded in the card's concave notch (concentric).
        PostAvatarCutout(
            authorImageUri = authorImageUri,
            onClick = onAuthorClick,
            size = PostAvatarSize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = PostAvatarOffsetX, y = PostAvatarOffsetY)
        )

        // Double-tap heart burst — centered over the card
        if (heartAlpha > 0f) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                        alpha = heartAlpha
                    }
            )
        }
    }
}

/**
 * A single feed action (icon + optional count). Uniform sizing/spacing so the
 * action row reads consistently across image and text posts.
 */
@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconScale: Float = 1f
) {
    Row(
        modifier = modifier
            .clip(NeoShapes.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale)
        )
        if (label != null) {
            Text(label, color = TextWhite80, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Post card geometry ──────────────────────────────────────────────────────
// The card's top-right CORNER curves inward (concave notch). The notch circle
// (radius 34dp) is centred 24dp in from both the top and right edges; the avatar
// (Ø56 → radius 28) is placed concentric with it (uniform 6dp gap) so it nests into
// the corner, poking ~4dp diagonally past the edges. Offsets derived from that centre.
private val PostAvatarSize = 56.dp
private val PostCardTopInset = 10.dp       // room for the avatar's slight top poke
private val PostAvatarOffsetX = 4.dp       // avatarRadius 28 - notchInset 24
private val PostAvatarOffsetY = 6.dp       // (topInset 10 + notchInset 24) - avatarRadius 28
private val PostCardShape = NotchedCardShape(
    cornerRadius = 24.dp,
    notchRadius = 34.dp,
    notchInset = 24.dp,
    filletRadius = 12.dp
)

/**
 * Circular lime avatar/button embedded in the card's concave cutout. The cutout
 * (a background-coloured circle behind it) provides the separation, so this is a
 * plain lime circle.
 */
@Composable
private fun PostAvatarCutout(
    authorImageUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(NeoLime)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (authorImageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = authorImageUri),
                contentDescription = "Author",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(id = com.neo.R.drawable.ic_default_avatar),
                contentDescription = "Author",
                tint = NeoBlack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(11.dp)
            )
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
