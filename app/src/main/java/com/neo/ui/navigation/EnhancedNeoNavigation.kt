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
import com.neo.data.preferences.UserPreferences
import com.neo.ui.components.EnhancedBottomNavigation
import com.neo.ui.screens.*
import com.neo.ui.viewmodel.CreatePostViewModel
import com.neo.ui.viewmodel.FeedViewModel
import com.neo.ui.viewmodel.ProfileViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Enhanced navigation setup for Neo app with all screens and bottom navigation
 */
@Composable
fun EnhancedNeoNavigation(
    viewModel: FeedViewModel,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    var startDestination by remember { mutableStateOf(if (userPreferences.isOnboardingComplete) "feed" else "onboarding") }

    // Track current route for bottom navigation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    // Collect state from ViewModel
    val posts by viewModel.posts.collectAsState()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    
    Scaffold(
        bottomBar = {
            // Show bottom nav only on main screens, not on create post or post detail
            if (currentRoute in listOf("feed", "search", "ble_status", "notifications", "profile")) {
                EnhancedBottomNavigation(
                    selectedRoute = currentRoute,
                    onFabClick = { navController.navigate("create_post") },
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("feed") { saveState = true }
                            launchSingleTop = true
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
            startDestination = startDestination,
            modifier = modifier.padding(paddingValues)
        ) {
            // Onboarding
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        userPreferences.isOnboardingComplete = true
                        navController.navigate("feed") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

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
            val createPostViewModel: CreatePostViewModel = hiltViewModel()
            EnhancedCreatePostScreen(
                viewModel = createPostViewModel,
                snackbarHostState = snackbarHostState,
                onPostClick = { content, authorName, imageUri ->
                    createPostViewModel.createPost(content, authorName, imageUri)
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
            val profileViewModel: ProfileViewModel = hiltViewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("edit_profile") }
            )
        }

        // Edit Profile
        composable("edit_profile") {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val currentName by profileViewModel.profileName.collectAsState()
            val currentBio by profileViewModel.profileBio.collectAsState()

            EditProfileScreen(
                currentName = currentName,
                currentBio = currentBio,
                onSave = { name, bio ->
                    profileViewModel.updateProfile(name, bio)
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
                val postDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel = hiltViewModel()
                PostDetailScreen(
                    post = post,
                    viewModel = postDetailViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            }
        }
    }
}
