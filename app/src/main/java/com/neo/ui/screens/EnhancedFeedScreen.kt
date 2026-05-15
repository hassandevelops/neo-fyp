package com.neo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.FeedViewModel
import com.neo.ui.viewmodel.PostDetailViewModel
import com.neo.ui.viewmodel.PostStats

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EnhancedFeedScreen(
    viewModel: FeedViewModel,
    postDetailViewModel: PostDetailViewModel,
    currentUserName: String,
    onNavigateToProfile: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBLEStatus: () -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val posts by viewModel.posts.collectAsState()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    val notificationCount by viewModel.notificationCount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val postStatsMap by postDetailViewModel.getPostStatsMapFlow(posts.map { it.id })
        .collectAsState(initial = emptyMap())
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                EnhancedHeader(
                    onProfileClick = onNavigateToProfile,
                    onSearchClick = onNavigateToSearch,
                    onNotificationsClick = onNavigateToNotifications,
                    onSettingsClick = onNavigateToSettings,
                    notificationCount = notificationCount
                )

                when (val state = uiState) {
                    is FeedViewModel.UiState.Loading -> {
                        LoadingShimmer()
                    }

                    is FeedViewModel.UiState.Error -> {
                        ErrorState(
                            message = state.message,
                            onRetry = { viewModel.refresh() }
                        )
                    }

                    is FeedViewModel.UiState.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pullRefresh(pullRefreshState)
                        ) {
                            if (state.isEmpty && posts.isEmpty()) {
                                EmptyState(onRefresh = { viewModel.refresh() })
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // BLE Mesh Status Card
                                    item {
                                        BLEMeshCard(
                                            connectedPeers = connectedPeersCount,
                                            onClick = onNavigateToBLEStatus
                                        )
                                    }

                                    // Posts
                                    items(posts, key = { it.id }) { post ->
                                        val stats = postStatsMap[post.id] ?: PostStats()

                                        EnhancedPostCard(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            post = post,
                                            onPostClick = { onNavigateToPostDetail(post) },
                                            onLikeClick = {
                                                postDetailViewModel.toggleLike(
                                                    postId = post.id,
                                                    userName = currentUserName
                                                )
                                            },
                                            onCommentClick = { selectedPostForComments = post },
                                            isLiked = stats.hasLiked,
                                            likeCount = stats.likeCount,
                                            commentCount = stats.commentCount
                                        )
                                    }

                                    // Bottom padding for nav bar
                                    item {
                                        Spacer(modifier = Modifier.height(90.dp))
                                    }
                                }
                            }

                            PullRefreshIndicator(
                                refreshing = isRefreshing,
                                state = pullRefreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                                backgroundColor = NeoGray900,
                                contentColor = NeoLime
                            )
                        }
                    }
                }
            }
        }
    }

    // Comments Bottom Sheet
    selectedPostForComments?.let { post ->
        val topLevelComments by postDetailViewModel
            .getTopLevelCommentsForPost(post.id)
            .collectAsState(initial = emptyList())

        CommentsBottomSheet(
            postId = post.id,
            topLevelComments = topLevelComments,
            getReplies = { parentId ->
                postDetailViewModel
                    .getRepliesForComment(parentId)
                    .collectAsState(initial = emptyList())
                    .value
            },
            onDismiss = { selectedPostForComments = null },
            onCommentSubmit = { content, parentCommentId ->
                postDetailViewModel.createComment(
                    postId = post.id,
                    content = content,
                    authorName = currentUserName,
                    parentCommentId = parentCommentId
                )
            }
        )
    }
}

@Composable
private fun LoadingShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) {
            ShimmerCard(
                brush = Brush.linearGradient(
                    colors = listOf(
                        NeoGray800,
                        NeoGray700,
                        NeoGray800
                    ),
                    start = Offset(shimmerTranslate, 0f),
                    end = Offset(shimmerTranslate + 200f, 0f)
                )
            )
        }
    }
}

@Composable
private fun ShimmerCard(brush: Brush) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NeoGray900)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No posts yet",
            color = TextWhite60,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Posts from your mesh network will appear here",
            color = TextWhite40,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = onRefresh,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = NeoGray800,
                contentColor = NeoLime
            )
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = NeoRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = TextWhite60,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = onRetry,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = NeoGray800,
                contentColor = NeoLime
            )
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
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
            .padding(horizontal = 16.dp)
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
                    imageVector = Icons.Default.Add,
                    contentDescription = "View",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
