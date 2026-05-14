package com.neo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.components.StoryUser
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.FeedViewModel

/**
 * Enhanced Feed Screen with gradient backgrounds and modern UI
 */
@Composable
fun EnhancedFeedScreen(
    viewModel: FeedViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBLEStatus: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val posts by viewModel.posts.collectAsState()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    
    // State for comments
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    val comments = remember { mutableStateListOf<com.neo.data.model.Comment>() }
    
    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                EnhancedHeader(
                    onProfileClick = onNavigateToProfile,
                    onSearchClick = onNavigateToSearch,
                    onNotificationsClick = onNavigateToNotifications,
                    onSettingsClick = onNavigateToSettings,
                    notificationCount = 0
                )

                // Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Stories Row
                    item {
                        StoryRow(
                            users = mockStoryUsers,
                            onStoryClick = { userId ->
                                // Handle story click
                            },
                            onAddStoryClick = {
                                // Open camera/add story
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // BLE Mesh Status Card
                    item {
                        BLEMeshCard(
                            connectedPeers = connectedPeersCount,
                            onClick = onNavigateToBLEStatus
                        )
                    }

                    // Posts
                    items(posts) { post ->
                        EnhancedPostCard(
                            post = post,
                            onPostClick = { onNavigateToPostDetail(post) },
                            onLikeClick = { /* Handle like */ },
                            onCommentClick = { selectedPostForComments = post }
                        )
                    }

                    // Bottom padding for nav bar
                    item {
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }
        
    }
    
    // Comments Bottom Sheet
    selectedPostForComments?.let { post ->
        CommentsBottomSheet(
            postId = post.id,
            topLevelComments = comments.filter { it.parentCommentId == null && it.postId == post.id },
            getReplies = { parentId -> comments.filter { it.parentCommentId == parentId } },
            onDismiss = { selectedPostForComments = null },
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

@Composable
private fun BLEMeshCard(
    connectedPeers: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "BLE Mesh",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = "BLE Mesh Network",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$connectedPeers peers connected",
                            color = TextWhite60,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Icon(
                    imageVector = Icons.Default.Add, // ChevronRight
                    contentDescription = "View",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Mock data for stories (replace with actual data from ViewModel)
private val mockStoryUsers = listOf(
    StoryUser("1", "alice", hasStory = true, isLive = true),
    StoryUser("2", "bob", hasStory = true),
    StoryUser("3", "charlie", hasStory = true),
    StoryUser("4", "diana", hasStory = false),
    StoryUser("5", "eve", hasStory = true),
    StoryUser("6", "frank", hasStory = true),
    StoryUser("7", "grace", hasStory = false),
    StoryUser("8", "henry", hasStory = true)
)
