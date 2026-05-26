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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val userPosts by viewModel.userPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()

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
                                    surface = NeoDarkGray.copy(alpha = 0.85f)
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .border(BorderStroke(0.5.dp, GlassBorderMid), RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Profile", color = TextWhite) },
                                        onClick = {
                                            showMenu = false
                                            onEditProfile()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, null, tint = TextWhite60)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Profile", color = TextWhite) },
                                        onClick = {
                                            showMenu = false
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Check out $profileName on Neo! ${handle}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Share, null, tint = TextWhite60)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copy Handle", color = TextWhite) },
                                        onClick = {
                                            showMenu = false
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("handle", handle))
                                            Toast.makeText(context, "Handle copied", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ContentCopy, null, tint = TextWhite60)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Profile card — premium liquid glass panel
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        fillColor = GlassWhite16,
                        shadowElevation = 20.dp,
                        cornerRadius = 72f,
                        accentGlow = Color.Transparent  // No green tint — pure glass
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = CircleShape,
                                        ambientColor = NeoLimeGlow20,
                                        spotColor = NeoLimeGlow20
                                    )
                                    .clip(CircleShape)
                                    .border(2.5.dp, NeoLime, CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(NeoGray800, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileImageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = profileImageUri),
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.splash),
                                        contentDescription = "Profile",
                                        modifier = Modifier.size(60.dp)
                                    )
                                }
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

                    // Stats row — liquid glass
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        fillColor = GlassWhite12,
                        shadowElevation = 8.dp,
                        cornerRadius = 54f
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatItem(label = "PULSES", value = postCount.toString())
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(GlassBorderMid))
                            StatItem(label = "NODES", value = nodeCount.toString())
                            Box(modifier = Modifier.width(1.dp).height(32.dp).background(GlassBorderMid))
                            StatItem(label = "FOLLOWERS", value = "0")
                        }
                    }

                    // Establish Link button
                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(50.dp),
                                ambientColor = NeoLimeGlow20,
                                spotColor = NeoLimeGlow20
                            ),
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

                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = NeoDarkGray.copy(alpha = 0.85f)
                    )
                ) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .border(BorderStroke(0.5.dp, GlassBorderMid), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                onRemoveSaved()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = TextWhite60)
                            }
                        )
                    }
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
