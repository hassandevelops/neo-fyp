package com.neo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.theme.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.CreatePostViewModel
import com.neo.ui.viewmodel.FeedViewModel
import com.neo.ui.viewmodel.PostDetailViewModel
import com.neo.ui.viewmodel.PostStats
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

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
    val pagedPosts = viewModel.pagedPosts.collectAsLazyPagingItems()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    val notificationCount by viewModel.notificationCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val postCreationState by viewModel.postCreationState.collectAsState()
    val postIds by remember {
        derivedStateOf {
            pagedPosts.itemSnapshotList.items.map { it.id }
        }
    }
    val postStatsMap by remember(postIds) {
        postDetailViewModel.getPostStatsMapFlow(postIds)
    }.collectAsState(initial = emptyMap())
    val savedPostIds by viewModel.savedPostIds.collectAsState()
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    val refreshLoadState = pagedPosts.loadState.refresh
    val appendLoadState = pagedPosts.loadState.append
    val isFeedRefreshing = isRefreshing || refreshLoadState is LoadState.Loading

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isFeedRefreshing,
        onRefresh = {
            viewModel.refresh()
            pagedPosts.refresh()
        }
    )

    var headerHeightPx by remember { mutableIntStateOf(120) }

    val lazyListState = rememberLazyListState()
    var isScrollingDown by remember { mutableStateOf(false) }
    LaunchedEffect(lazyListState) {
        var prevIndex = lazyListState.firstVisibleItemIndex
        var prevOffset = lazyListState.firstVisibleItemScrollOffset
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                val goingDown = when {
                    currentIndex > prevIndex -> true
                    currentIndex < prevIndex -> false
                    else -> currentOffset > prevOffset
                }
                if (goingDown != isScrollingDown) {
                    isScrollingDown = goingDown
                }
                prevIndex = currentIndex
                prevOffset = currentOffset
            }
    }
    val headerTranslationY by animateFloatAsState(
        targetValue = if (isScrollingDown) -headerHeightPx.toFloat() else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "header_offset"
    )

    Column(modifier = modifier.fillMaxSize()) {
        EnhancedHeader(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    headerHeightPx = coords.size.height
                }
                .graphicsLayer {
                    translationY = headerTranslationY
                },
            onProfileClick = onNavigateToProfile,
            onSearchClick = onNavigateToSearch,
            onNotificationsClick = onNavigateToNotifications,
            onSettingsClick = onNavigateToSettings,
            notificationCount = notificationCount
        )

        GradientBackground(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 120.dp)) {
                    when {
                    refreshLoadState is LoadState.Loading && pagedPosts.itemCount == 0 -> {
                        LoadingShimmer()
                    }

                    refreshLoadState is LoadState.Error && pagedPosts.itemCount == 0 -> {
                        ErrorState(
                            message = refreshLoadState.error.message ?: "Unable to load feed",
                            onRetry = {
                                viewModel.refresh()
                                pagedPosts.retry()
                            }
                        )
                    }

                    pagedPosts.itemCount == 0 -> {
                        EmptyState(
                            onRefresh = {
                                viewModel.refresh()
                                pagedPosts.refresh()
                            }
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {

                            if (postCreationState is CreatePostViewModel.UiState.Loading) {
                                item {
                                    BackgroundPostingCard()
                                }
                            }

                            // Posts
                            items(
                                count = pagedPosts.itemCount,
                                key = pagedPosts.itemKey { it.id }
                            ) { index ->
                                val post = pagedPosts[index]
                                if (post == null) {
                                    ShimmerCard(
                                        brush = Brush.linearGradient(
                                            colors = listOf(NeoGray800, NeoGray700, NeoGray800)
                                        ),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    return@items
                                }
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
                                    onSaveClick = { viewModel.toggleSave(post.id) },
                                    isLiked = stats.hasLiked,
                                    isSaved = post.id in savedPostIds,
                                    likeCount = stats.likeCount,
                                    commentCount = stats.commentCount
                                )
                            }

                            if (appendLoadState is LoadState.Loading) {
                                item {
                                    CircularProgressIndicator(
                                        color = NeoLime,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }

                            if (appendLoadState is LoadState.Error) {
                                item {
                                    AppendErrorState(
                                        message = appendLoadState.error.message ?: "Unable to load more posts",
                                        onRetry = { pagedPosts.retry() }
                                    )
                                }
                            }

                            // Bottom padding for nav bar
                            item {
                                Spacer(modifier = Modifier.height(90.dp))
                            }
                        }
                    }
                    }
                }

                // BLE Mesh Status Card — always visible regardless of feed state
                BLEMeshCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    connectedPeers = connectedPeersCount,
                    onClick = onNavigateToBLEStatus
                )

                PullRefreshIndicator(
                    refreshing = isFeedRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = NeoGray900,
                    contentColor = NeoLime
                )
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
private fun ShimmerCard(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
private fun AppendErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = TextWhite60,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Text("Retry", color = NeoLime)
        }
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
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "ble_card_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                fillColor = GlassWhite16,
                shadowElevation = if (isPressed) 6.dp else 16.dp,
                cornerRadius = 72f
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(20.dp)
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
                // Icon in glass container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = NeoLimeGlow10,
                            spotColor = NeoLimeGlow10
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassWhite20)
                        .drawBehind {
                            // Specular highlight on icon container
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to GlassReflectionEdge,
                                        0.1f to Color.Transparent,
                                        1.0f to Color.Transparent
                                    )
                                ),
                                cornerRadius = CornerRadius(42f, 42f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "BLE Mesh",
                        tint = NeoLime,
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

            // Arrow icon — unified NeoLime
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "View",
                tint = NeoLime,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BackgroundPostingCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhite8)
            .border(BorderStroke(0.5.dp, GlassBorderMid), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = NeoLime,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Column {
                Text(
                    text = "Publishing to mesh network...",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Syncing encrypted post with nearby peers",
                    color = TextWhite60,
                    fontSize = 12.sp
                )
            }
        }
    }
}

