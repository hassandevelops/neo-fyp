package com.neo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.neo.R
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.theme.*
import com.neo.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    postDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    isCurrentUser: Boolean = true,
    modifier: Modifier = Modifier
) {
    val profileName by viewModel.profileName.collectAsState()
    val profileBio by viewModel.profileBio.collectAsState()
    val profileImageUri by viewModel.profileImageUri.collectAsState()
    val handle by viewModel.handle.collectAsState()
    val postCount by viewModel.postCount.collectAsState()
    val nodeCount by viewModel.connectedPeersCount.collectAsState()
    val followerCount by viewModel.followerCount.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isFollowLoading by viewModel.isFollowLoading.collectAsState()
    val userPosts by viewModel.userPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val savedPostIds by viewModel.savedPostIds.collectAsState()
    val currentUserName = viewModel.currentUserName

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val scrollStates = remember {
        mutableStateListOf(
            LazyListState(),
            LazyListState(),
            LazyListState()
        )
    }
    val currentScrollState = scrollStates[selectedTab]

    val contentPosts = when (selectedTab) {
        0 -> userPosts
        1 -> userPosts.filter { it.imageHash != null }
        2 -> savedPosts
        else -> emptyList()
    }

    // Like/comment counts + liked state for the visible posts (same source the feed uses).
    val contentPostIds = contentPosts.map { it.id }
    val statsMap by remember(contentPostIds) {
        postDetailViewModel.getPostStatsMapFlow(contentPostIds)
    }.collectAsState(initial = emptyMap())

    GradientBackground(modifier = modifier) {
        LazyColumn(
            state = currentScrollState,
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Profile Header (scrolls away) ──────────────────────────
            item {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
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
                            text = "Profile",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = TextWhite
                                )
                            }
                            MaterialTheme(
                                colorScheme = MaterialTheme.colorScheme.copy(
                                    surface = SurfaceElevated2
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .clip(NeoShapes.control)
                                        .background(SurfaceElevated2)
                                        .border(BorderStroke(0.5.dp, NeoHairline), NeoShapes.control)
                                ) {
                                    if (isCurrentUser) {
                                        NeoMenuItem(
                                            label = "Edit Profile",
                                            icon = Icons.Default.Edit,
                                            onClick = {
                                                showMenu = false
                                                onEditProfile()
                                            }
                                        )
                                    }
                                    NeoMenuItem(
                                        label = "Share Profile",
                                        icon = Icons.Default.Share,
                                        onClick = {
                                            showMenu = false
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Check out $profileName on Neo! ${handle}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                                        }
                                    )
                                    NeoMenuItem(
                                        label = "Copy Handle",
                                        icon = Icons.Default.ContentCopy,
                                        onClick = {
                                            showMenu = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("handle", handle))
                                            Toast.makeText(context, "Handle copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Profile card + stats pill. Inner gap matches the parent
                    // Column's 16dp so card→stats and stats→button margins are equal.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NeoSpacing.lg)
                    ) {
                        NeoElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NeoShapes.cardLarge,
                            tone = SurfaceElevated1,
                            elevation = NeoElevation.medium
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NeoSpacing.xxl)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(NeoLime, CircleShape)
                                        .organicPattern(NeoLime, richness = 2, intensity = 0.8f)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceElevated1, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UserAvatar(
                                        imageUri = profileImageUri,
                                        size = 92.dp,
                                        contentDescription = "Profile"
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = profileName,
                                    color = TextWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = handle,
                                    color = NeoLime,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = profileBio,
                                    color = TextWhite60,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Unified stat-pill container — supporting profile metadata.
                        ProfileStatStrip(
                            pulses = postCount,
                            nodes = nodeCount,
                            followers = followerCount
                        )
                    }

                    if (isCurrentUser) {
                        // Establish Link button (own profile only)
                        NeoPrimaryButton(
                            text = "Establish Link",
                            icon = Icons.Default.Link,
                            onClick = onEditProfile,
                            fillMaxWidth = true
                        )
                    } else if (isFollowing) {
                        NeoSecondaryButton(
                            text = if (isFollowLoading) "Updating…" else "Following",
                            icon = Icons.Default.Check,
                            onClick = { if (!isFollowLoading) viewModel.toggleFollow() },
                            fillMaxWidth = true
                        )
                    } else {
                        NeoPrimaryButton(
                            text = if (isFollowLoading) "Updating…" else "Follow",
                            icon = Icons.Default.PersonAdd,
                            onClick = { if (!isFollowLoading) viewModel.toggleFollow() },
                            enabled = !isFollowLoading,
                            fillMaxWidth = true
                        )
                    }
                }
            }

            // ── Sticky Tabs ────────────────────────────────────────────
            stickyHeader {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NeoBlack,
                    contentColor = NeoLime,
                    indicator = { tabPositions ->
                        if (tabPositions.isNotEmpty()) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTab])
                                    .height(2.dp)
                                    .background(NeoLime)
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Posts",
                                color = if (selectedTab == 0) NeoLime else TextWhite40,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Media",
                                color = if (selectedTab == 1) NeoLime else TextWhite40,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    if (isCurrentUser) {
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == 2) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedTab == 2) NeoLime else TextWhite40
                                    )
                                    Text(
                                        "Saved",
                                        color = if (selectedTab == 2) NeoLime else TextWhite40,
                                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ── Tab Content ────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(12.dp)) }

            if (contentPosts.isEmpty()) {
                item {
                    EmptyTabContent(
                        tabIndex = selectedTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            } else {
                // Same full social post card as the main feed — no grids/galleries.
                items(
                    count = contentPosts.size,
                    key = { index -> "${selectedTab}_${contentPosts[index].id}" }
                ) { index ->
                    val post = contentPosts[index]
                    val stats = statsMap[post.id] ?: com.neo.ui.viewmodel.PostStats()
                    EnhancedPostCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        post = post,
                        onImageClick = { onPostClick(post.id) },
                        onAuthorClick = { onPostClick(post.id) },
                        onLikeClick = {
                            postDetailViewModel.toggleLike(post.id, currentUserName)
                        },
                        onCommentClick = { onPostClick(post.id) },
                        onSaveClick = { viewModel.toggleSave(post.id) },
                        isLiked = stats.hasLiked,
                        isSaved = post.id in savedPostIds,
                        likeCount = stats.likeCount,
                        commentCount = stats.commentCount,
                        // All posts on a profile belong to that profile's owner, so
                        // their avatar is the profile image already resolved above.
                        authorImageUri = profileImageUri
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Bottom spacer for tab bar clearance
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * Unified statistics container — a single compact pill with three equal segments
 * (Pulses · Nodes · Followers) separated by subtle dividers. Supporting profile
 * metadata, styled to match the Neo Mesh design system (elevated surface, organic
 * pattern, lime accent on the primary metric).
 */
@Composable
private fun ProfileStatStrip(
    pulses: Int,
    nodes: Int,
    followers: Int,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier.fillMaxWidth(),
        shape = NeoShapes.pill,
        tone = SurfaceElevated2,
        elevation = NeoElevation.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatSegment(
                icon = Icons.Default.Bolt,
                value = pulses.toString(),
                label = "Pulses",
                accent = true,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatSegment(
                icon = Icons.Default.Hub,
                value = nodes.toString(),
                label = "Nodes",
                accent = false,
                modifier = Modifier.weight(1f)
            )
            StatDivider()
            StatSegment(
                icon = Icons.Default.People,
                value = followers.toString(),
                label = "Followers",
                accent = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * A Neo-styled dropdown menu row — lime leading icon, white label, consistent
 * spacing. Used for the profile overflow menu so it reads native to the app.
 */
@Composable
private fun NeoMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = NeoLime, modifier = Modifier.size(20.dp))
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) NeoLime else TextWhite60,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                color = if (accent) NeoLime else TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = TextWhite40,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(NeoHairline)
    )
}

@Composable
private fun EmptyTabContent(
    tabIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (tabIndex) {
            0 -> EmptyStateContent(
                icon = Icons.Outlined.BookmarkBorder,
                message = "No posts yet",
                subtitle = null
            )
            1 -> EmptyStateContent(
                icon = Icons.Default.Image,
                message = "No media yet",
                subtitle = null
            )
            2 -> EmptyStateContent(
                icon = Icons.Filled.Bookmark,
                message = "No saved posts yet",
                subtitle = "Posts you bookmark will appear here."
            )
        }
    }
}

@Composable
private fun EmptyStateContent(
    icon: ImageVector,
    message: String,
    subtitle: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextWhite40,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            color = TextWhite60,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = TextWhite40,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}
