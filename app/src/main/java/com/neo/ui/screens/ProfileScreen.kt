package com.neo.ui.screens

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
import androidx.compose.ui.graphics.Brush
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
import com.neo.ui.components.GradientBackground
import com.neo.ui.theme.*
import com.neo.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    isCurrentUser: Boolean = true,
    modifier: Modifier = Modifier
) {
    val profileName by viewModel.profileName.collectAsState()
    val profileBio by viewModel.profileBio.collectAsState()
    val handle by viewModel.handle.collectAsState()
    val postCount by viewModel.postCount.collectAsState()
    val nodeCount by viewModel.connectedPeersCount.collectAsState()
    val userPosts by viewModel.userPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

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
                        IconButton(onClick = { /* More options */ }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = TextWhite
                            )
                        }
                    }

                    // Profile card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite5)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            NeoLime.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    NeoLime.copy(alpha = 0.2f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, NeoLime, CircleShape)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(NeoGray800, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.splash),
                                        contentDescription = "Profile",
                                        modifier = Modifier.size(60.dp)
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
                    }

                    // Stats row
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeoGray900)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatItem(label = "PULSES", value = postCount.toString())
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(BorderWhite20))
                            StatItem(label = "NODES", value = nodeCount.toString())
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(BorderWhite20))
                            StatItem(label = "FOLLOWERS", value = "0")
                        }
                    }

                    // Establish Link button
                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoLime)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = NeoBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Establish Link",
                            color = NeoBlack,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
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
                val chunked = contentPosts.chunked(2)
                items(
                    count = chunked.size,
                    key = { index -> "${selectedTab}_row_$index" }
                ) { index ->
                    val row = chunked[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { post ->
                            PostGridItem(
                                post = post,
                                onClick = { onPostClick(post.id) },
                                onRemoveSaved = if (selectedTab == 2) {
                                    { viewModel.toggleSave(post.id) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Bottom spacer for tab bar clearance
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostGridItem(
    post: Post,
    onClick: () -> Unit = {},
    onRemoveSaved: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imagePath = post.imageHash?.let {
        java.io.File(context.filesDir, "images/$it.jpg").takeIf { f -> f.exists() }
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onRemoveSaved != null) {{ showMenu = true }} else null
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeoGray800)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imagePath != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = imagePath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.content.take(80),
                        color = TextWhite60,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onRemoveSaved != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(NeoBlack.copy(alpha = 0.6f))
                        .combinedClickable(
                            onClick = { showMenu = true },
                            onLongClick = null
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved",
                        tint = NeoLime,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            showMenu = false
                            onRemoveSaved()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextWhite40, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
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
