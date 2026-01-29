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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.model.Post
import com.neo.ui.components.*
import com.neo.ui.theme.*
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
    
    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                EnhancedHeader(
                    onProfileClick = onNavigateToProfile,
                    onSearchClick = onNavigateToSearch,
                    onNotificationsClick = onNavigateToNotifications,
                    onSettingsClick = onNavigateToSettings,
                    notificationCount = 3
                )
                
                // Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
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
                    items(posts) { post ->
                        EnhancedPostCard(
                            post = post,
                            onPostClick = { onNavigateToPostDetail(post) },
                            onLikeClick = { /* Handle like */ },
                            onCommentClick = { onNavigateToPostDetail(post) }
                        )
                    }
                }
            }
        }
        
        // Floating Action Button
        FloatingActionButton(
            onClick = onNavigateToCreatePost,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = NeoPurple,
            contentColor = TextWhite
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Post",
                modifier = Modifier.size(24.dp)
            )
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
                            NeoCyan.copy(alpha = 0.1f),
                            NeoPurple.copy(alpha = 0.1f),
                            NeoPink.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = NeoCyan.copy(alpha = 0.3f),
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
                            .background(NeoCyan.copy(alpha = 0.2f))
                            .border(
                                1.dp,
                                NeoCyan.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "BLE Mesh",
                            tint = NeoCyan,
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
                    tint = NeoCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
