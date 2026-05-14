package com.neo.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.CommentsBottomSheet
import com.neo.ui.components.GradientBackground
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.PostDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    post: Post,
    viewModel: PostDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    var showCommentsSheet by remember { mutableStateOf(false) }
    val hasLiked by viewModel.hasUserLikedPostFlow(post.id).collectAsState(initial = false)
    val uiState by viewModel.uiState.collectAsState()

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
    
    // Mock comment data for now
    val comments = remember { mutableStateListOf<com.neo.data.model.Comment>() }
    
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
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceWhite5
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Author
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceWhite10),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = TextWhite60,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
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
                                            userName = "Current User",
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
                
                // Display existing comments
                val topLevelComments = comments.filter { it.parentCommentId == null }
                items(topLevelComments.size) { index ->
                    val comment = topLevelComments[index]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = SurfaceWhite5,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = comment.authorName,
                                color = NeoLime,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comment.content,
                                color = TextWhite,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                // Empty state if no comments
                if (comments.isEmpty()) {
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
                            unfocusedBorderColor = BorderWhite10,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                val newComment = com.neo.data.model.Comment(
                                    id = "comment_${System.currentTimeMillis()}",
                                    postId = post.id,
                                    authorId = "mock_user_id",
                                    authorName = "Current User",
                                    content = commentText.trim(),
                                    timestamp = System.currentTimeMillis(),
                                    signature = "mock_signature",
                                    publicKey = "mock_public_key",
                                    ttl = 10,
                                    firstSeenTimestamp = System.currentTimeMillis(),
                                    parentCommentId = null
                                )
                                comments.add(newComment)
                                commentText = ""
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
                topLevelComments = comments.filter { it.parentCommentId == null },
                getReplies = { parentId -> comments.filter { it.parentCommentId == parentId } },
                onDismiss = { showCommentsSheet = false },
                onCommentSubmit = { content, parentCommentId ->
                    // Add comment to mock list
                    val newComment = com.neo.data.model.Comment(
                        id = "comment_${System.currentTimeMillis()}",
                        postId = post.id,
                        authorId = "mock_user_id",
                        authorName = "Current User",
                        content = content,
                        timestamp = System.currentTimeMillis(),
                        signature = "mock_signature",
                        publicKey = "mock_public_key",
                        ttl = 10,
                        firstSeenTimestamp = System.currentTimeMillis(),
                        parentCommentId = parentCommentId
                    )
                    comments.add(newComment)
                }
            )
        }
    }
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
