package com.neo.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.data.model.Post
import com.neo.ui.components.CommentsBottomSheet
import com.neo.ui.components.CommentThreadItem
import com.neo.ui.components.FullScreenMediaViewer
import com.neo.ui.components.GradientBackground
import com.neo.ui.components.UserAvatar
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.PostDetailViewModel
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    post: Post,
    viewModel: PostDetailViewModel,
    currentUserName: String,
    currentUserId: String,
    profileImageUri: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    var showCommentsSheet by remember { mutableStateOf(false) }
    val hasLiked by viewModel.hasUserLikedPostFlow(post.id).collectAsStateWithLifecycle(initialValue = false)
    val isSaved by viewModel.observeIsSaved(post.id).collectAsStateWithLifecycle(initialValue = false)
    val commentThreads by viewModel
        .getCommentThreadsForPost(post.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profilesByDid by viewModel.profilesByDid.collectAsStateWithLifecycle()

    // Resolve a comment author's avatar (deviceId-authored → only self resolves;
    // others fall back to the default avatar until keyed identities are unified).
    val commentAvatarFor: (com.neo.data.model.Comment) -> String? = { comment ->
        com.neo.ui.util.resolveAvatar(
            authorId = comment.authorId,
            profiles = profilesByDid,
            selfIds = viewModel.selfIds,
            selfImageUri = profileImageUri
        )
    }

    val imagePath = remember(post.imageHash) {
        post.imageHash?.let {
            File(context.filesDir, "images/$it.jpg").takeIf { f -> f.exists() }
        }
    }
    var selectedImageForViewer by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        viewModel.resetUiState()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PostDetailViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            is PostDetailViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        GradientBackground(modifier = modifier.padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    
                    Text(
                        text = "Post",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    IconButton(onClick = { sharePost(context, post) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextWhite
                        )
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Post content
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NeoShapes.card,
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceElevated1
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Author
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    imageUri = com.neo.ui.util.resolveAvatar(
                                        authorId = post.authorId,
                                        profiles = profilesByDid,
                                        selfIds = viewModel.selfIds,
                                        selfImageUri = profileImageUri
                                    ),
                                    size = 48.dp,
                                    contentDescription = "Profile"
                                )
                                
                                Column {
                                    Text(
                                        text = post.authorName,
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatTimestamp(post.timestamp),
                                        color = TextWhite40,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Post image
                            if (imagePath != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(NeoShapes.control)
                                        .clickable { selectedImageForViewer = imagePath },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = imagePath),
                                        contentDescription = "Post image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(
                                                if (post.imageWidth != null && post.imageHeight != null && post.imageHeight > 0)
                                                    post.imageWidth.toFloat() / post.imageHeight.toFloat()
                                                else 16f / 9f
                                            ),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            
                            // Content
                            Text(
                                text = post.content,
                                color = TextWhite80,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.toggleLike(
                                            postId = post.id,
                                            userName = currentUserName,
                                            onError = { error: String ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(message = error)
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (hasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (hasLiked) MaterialTheme.colorScheme.error else TextWhite60
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleSave(post.id) }) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Save",
                                        tint = if (isSaved) NeoLime else TextWhite60
                                    )
                                }
                                IconButton(onClick = { showCommentsSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = "Comment",
                                        tint = TextWhite60
                                    )
                                }
                                IconButton(onClick = { sharePost(context, post) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = TextWhite60
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Comments section
                item {
                    Text(
                        text = "Comments",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(commentThreads.size) { index ->
                    val thread = commentThreads[index]
                    CommentThreadItem(
                        thread = thread,
                        avatarFor = commentAvatarFor,
                        onReply = { showCommentsSheet = true },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Empty state if no comments
                if (commentThreads.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Be the first to comment!",
                            color = TextWhite40,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            
            // Comment input at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack.copy(alpha = 0.8f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a comment...", color = TextWhite40) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = NeoHairline,
                            focusedContainerColor = SurfaceElevated1,
                            unfocusedContainerColor = SurfaceElevated1,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = NeoShapes.pill
                    )
                    
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.createComment(
                                    postId = post.id,
                                    content = commentText.trim(),
                                    authorName = currentUserName,
                                    onSuccess = { commentText = "" },
                                    onError = { error: String ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(message = error)
                                        }
                                    }
                                )
                            }
                        },
                        enabled = commentText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else TextWhite40
                        )
                    }
                }
            }
        }
        
        // Comments Bottom Sheet
        if (showCommentsSheet) {
            CommentsBottomSheet(
                postId = post.id,
                threads = commentThreads,
                avatarFor = commentAvatarFor,
                onDismiss = { showCommentsSheet = false },
                onCommentSubmit = { content, parentCommentId ->
                    viewModel.createComment(
                        postId = post.id,
                        content = content,
                        authorName = currentUserName,
                        parentCommentId = parentCommentId
                    )
                }
            )
        }
        
        // Full screen media viewer
        FullScreenMediaViewer(
            images = listOfNotNull(imagePath),
            initialIndex = 0,
            visible = selectedImageForViewer != null,
            onDismiss = { selectedImageForViewer = null }
        )
    }
}
}

/**
 * Share post content using Android's native share dialog.
 * Includes a deep link (neo://posts/{postId}) so recipients can open the exact post.
 * Attaches the post image if available for richer previews.
 */
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
