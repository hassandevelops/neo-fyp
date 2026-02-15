package com.neo.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neo.ui.components.EnhancedBottomNavigation
import com.neo.ui.screens.*
import com.neo.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

/**
 * Enhanced navigation setup for Neo app with all screens and bottom navigation
 */
@Composable
fun EnhancedNeoNavigation(
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Track current route for bottom navigation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "feed"
    
    // Collect state from ViewModel
    val posts by viewModel.posts.collectAsState()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is FeedViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
                // Don't auto-navigate away - let user dismiss manually
            }
            is FeedViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }
    
    Scaffold(
        bottomBar = {
            // Show bottom nav only on main screens, not on create post or post detail
            if (currentRoute in listOf("feed", "search", "ble_status", "notifications", "profile")) {
                EnhancedBottomNavigation(
                    selectedRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Pop up to the start destination to avoid building up a large stack
                            popUpTo("feed") { saveState = true }
                            // Avoid multiple copies of the same destination
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "feed",
            modifier = modifier.padding(paddingValues)
        ) {
        // Main Feed
        composable("feed") {
            EnhancedFeedScreen(
                viewModel = viewModel,
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToNotifications = { navController.navigate("notifications") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToBLEStatus = { navController.navigate("ble_status") },
                onNavigateToCreatePost = { navController.navigate("create_post") },
                onNavigateToPostDetail = { post ->
                    navController.navigate("post_detail/${post.id}")
                }
            )
        }
        
        // Create Post
        composable("create_post") {
            EnhancedCreatePostScreen(
                onPostClick = { content, authorName, imageUri ->
                    viewModel.createPost(content, authorName, imageUri)
                    navController.navigate("feed") {
                        popUpTo("feed") { inclusive = true }
                    }
                },
                onDismiss = {
                    navController.navigate("feed") {
                        popUpTo("feed") { inclusive = true }
                    }
                }
            )
        }
        
        // Profile
        composable("profile") {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("edit_profile") }
            )
        }
        
        // Edit Profile
        composable("edit_profile") {
            val currentName by viewModel.profileName.collectAsState()
            val currentBio by viewModel.profileBio.collectAsState()
            
            EditProfileScreen(
                currentName = currentName,
                currentBio = currentBio,
                onSave = { name, bio ->
                    viewModel.updateProfile(name, bio)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
        
        // Search
        composable("search") {
            SearchScreen(
                posts = posts,
                onPostClick = { post ->
                    navController.navigate("post_detail/${post.id}")
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Notifications
        composable("notifications") {
            NotificationsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // Settings
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAccount = { navController.navigate("settings/account") },
                onNavigateToPrivacy = { navController.navigate("settings/privacy") },
                onNavigateToNotifications = { navController.navigate("settings/notifications") },
                onNavigateToAbout = { navController.navigate("settings/about") }
            )
        }
        
        // Settings Detail Screens
        composable("settings/account") {
            AccountSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("settings/privacy") {
            PrivacySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("settings/notifications") {
            NotificationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("settings/about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // BLE Mesh Status
        composable("ble_status") {
            BLEMeshStatusScreen(
                connectedPeers = List(connectedPeersCount) { "Peer $it" },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Post Detail
        composable(
            route = "post_detail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId")
            val post = posts.find { it.id == postId }
            
            if (post != null) {
                PostDetailScreen(
                    post = post,
                    onBack = { navController.popBackStack() }
                )
            }
            }
        }
    }
}
