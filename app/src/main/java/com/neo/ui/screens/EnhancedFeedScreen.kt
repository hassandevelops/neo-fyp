package com.neo.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme
import com.neo.ui.viewmodel.CreatePostViewModel
import com.neo.ui.viewmodel.FeedViewModel
import com.neo.ui.viewmodel.PostDetailViewModel
import com.neo.ui.viewmodel.PostStats
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.io.File

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EnhancedFeedScreen(
    viewModel: FeedViewModel,
    postDetailViewModel: PostDetailViewModel,
    currentUserName: String,
    currentUserImageUri: String? = null,
    onNavigateToProfile: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBLEStatus: () -> Unit,
    onNavigateToUserProfile: (Post) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagedPosts = viewModel.pagedPosts.collectAsLazyPagingItems()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsStateWithLifecycle()
    val notificationCount by viewModel.notificationCount.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val postCreationState by viewModel.postCreationState.collectAsStateWithLifecycle()
    val postIds by remember {
        derivedStateOf {
            pagedPosts.itemSnapshotList.items.map { it.id }
        }
    }
    val postStatsMap by remember(postIds) {
        postDetailViewModel.getPostStatsMapFlow(postIds)
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val savedPostIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val profilesByDid by viewModel.profilesByDid.collectAsStateWithLifecycle()
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    var viewerImages by remember { mutableStateOf<List<File>>(emptyList()) }
    val context = LocalContext.current
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

    val lazyListState = rememberLazyListState()
    var isScrollingDown by remember { mutableStateOf(false) }
    LaunchedEffect(lazyListState) {
        // Pixels of sustained movement in one direction before we flip state —
        // filters out jitter / overscroll settle that caused the hide↔show bounce.
        val threshold = 18f
        var accumulated = 0f
        var prevIndex = lazyListState.firstVisibleItemIndex
        var prevOffset = lazyListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
                lazyListState.canScrollForward
            )
        }.collect { (currentIndex, currentOffset, canScrollForward) ->
            // Approximate signed pixel delta since the last emission.
            val delta = when {
                currentIndex > prevIndex -> threshold + 1f          // moved down a row
                currentIndex < prevIndex -> -(threshold + 1f)        // moved up a row
                else -> (currentOffset - prevOffset).toFloat()
            }
            prevIndex = currentIndex
            prevOffset = currentOffset

            // Near the very top: always show, and never hide here. This kills the
            // bounce on short feeds (a down-drag that can't scroll settles back to
            // the top) and keeps chrome available when there's nothing to scroll.
            val nearTop = currentIndex == 0 && currentOffset < 12
            if (nearTop || (!canScrollForward && !isScrollingDown)) {
                accumulated = 0f
                if (isScrollingDown) isScrollingDown = false
                return@collect
            }

            // Accumulate movement only while it stays in one direction.
            accumulated = if (delta > 0f == accumulated >= 0f) accumulated + delta else delta
            if (accumulated > threshold && !isScrollingDown) {
                isScrollingDown = true
                accumulated = 0f
            } else if (accumulated < -threshold && isScrollingDown) {
                isScrollingDown = false
                accumulated = 0f
            }
        }
    }

    // Collapse progress (0 = chrome shown, 1 = hidden). Drives a DRAW-ONLY slide of
    // the header overlay — the feed underneath is always full height, so hiding the
    // header never changes the feed's scrollability (no reflow feedback / bounce).
    val collapse by animateFloatAsState(
        targetValue = if (isScrollingDown) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chrome_collapse"
    )

    // Header is an OVERLAY on top of the full-height feed; its measured height sets
    // the feed's top contentPadding so content starts below it (and scrolls under it).
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    // BLE mesh card auto-hide: slide up + fade when scrolling down, restore on up.
    var bleCardHeightPx by remember { mutableIntStateOf(0) }
    val bleTranslationY by animateFloatAsState(
        targetValue = if (isScrollingDown) -(bleCardHeightPx.toFloat() + 24f) else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "ble_offset"
    )
    val bleAlpha by animateFloatAsState(
        targetValue = if (isScrollingDown) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "ble_alpha"
    )

    // Total top inset that the chrome (header overlay + BLE card) occupies; the feed
    // reserves this so its first item starts below the chrome.
    val chromeInset = headerHeightDp + 112.dp

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                    refreshLoadState is LoadState.Loading && pagedPosts.itemCount == 0 -> {
                        Box(modifier = Modifier.fillMaxSize().padding(top = chromeInset)) {
                            LoadingShimmer()
                        }
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
                            contentPadding = PaddingValues(top = chromeInset, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {

                            if (postCreationState is CreatePostViewModel.UiState.Creating || postCreationState is CreatePostViewModel.UiState.Broadcasting) {
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
                                    onImageClick = {
                                        val f = post.imageHash?.let {
                                            File(context.filesDir, "images/$it.jpg")
                                        }?.takeIf { it.exists() }
                                        if (f != null) viewerImages = listOf(f)
                                    },
                                    onAuthorClick = { onNavigateToUserProfile(post) },
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
                                    commentCount = stats.commentCount,
                                    authorImageUri = com.neo.ui.util.resolveAvatar(
                                        authorId = post.authorId,
                                        profiles = profilesByDid,
                                        selfIds = viewModel.selfIds,
                                        selfImageUri = currentUserImageUri
                                    )
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

                // BLE Mesh Status Card — sits just below the header overlay; auto-hides
                // on scroll down, returns on scroll up.
                BLEMeshCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = headerHeightDp + 8.dp)
                        .onGloballyPositioned { bleCardHeightPx = it.size.height }
                        .graphicsLayer {
                            translationY = bleTranslationY
                            alpha = bleAlpha
                        },
                    connectedPeers = connectedPeersCount,
                    onClick = onNavigateToBLEStatus
                )

                PullRefreshIndicator(
                    refreshing = isFeedRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = headerHeightDp),
                    backgroundColor = NeoGray900,
                    contentColor = NeoLime
                )
            }
        }

        // Header OVERLAY — drawn on top of the full-height feed and slid up (draw
        // only) to hide. Because it's an overlay, hiding it never reflows the feed,
        // so short feeds can't trigger the hide↔show bounce.
        EnhancedHeader(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .onGloballyPositioned { headerHeightPx = it.size.height }
                .graphicsLayer { translationY = -headerHeightPx * collapse },
            onProfileClick = onNavigateToProfile,
            onSearchClick = onNavigateToSearch,
            onNotificationsClick = onNavigateToNotifications,
            onSettingsClick = onNavigateToSettings,
            notificationCount = notificationCount,
            profileImageUri = currentUserImageUri
        )
    }

    // Full-screen image viewer — opened by tapping a post image
    FullScreenMediaViewer(
        images = viewerImages,
        initialIndex = 0,
        visible = viewerImages.isNotEmpty(),
        onDismiss = { viewerImages = emptyList() }
    )

    // Comments Bottom Sheet
    selectedPostForComments?.let { post ->
        val threads by postDetailViewModel
            .getCommentThreadsForPost(post.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        CommentsBottomSheet(
            postId = post.id,
            threads = threads,
            avatarFor = { comment ->
                com.neo.ui.util.resolveAvatar(
                    authorId = comment.authorId,
                    profiles = profilesByDid,
                    selfIds = viewModel.selfIds,
                    selfImageUri = currentUserImageUri
                )
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
        shape = NeoShapes.card,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
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
            .neoSurface(
                shape = NeoShapes.cardLarge,
                tone = NeoLime,
                elevation = if (isPressed) 6.dp else NeoElevation.high
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
                // Icon container — dark on lime for contrast
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(NeoShapes.control)
                        .background(NeoBlack.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "BLE Mesh",
                        tint = NeoBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "BLE Mesh Network",
                        color = NeoBlack,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$connectedPeers peers connected",
                        color = NeoBlack.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Arrow icon — dark on lime
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = NeoBlack,
                modifier = Modifier.size(24.dp)
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
            .neoSurface(shape = NeoShapes.card, tone = SurfaceElevated1)
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

