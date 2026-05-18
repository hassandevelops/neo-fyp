package com.neo.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navDeepLink
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neo.data.preferences.UserPreferences
import com.neo.ui.components.EnhancedBottomNavigation
import com.neo.ui.screens.*
import com.neo.ui.theme.TextWhite60
import com.neo.ui.viewmodel.CreatePostViewModel
import com.neo.ui.viewmodel.FeedViewModel
import com.neo.ui.viewmodel.NotificationsViewModel
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
    deepLinkIntent: Intent? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    var startDestination by remember { mutableStateOf(if (userPreferences.isOnboardingComplete) "feed" else "onboarding") }

    // Handle deep link intents from cold start or onNewIntent
    var lastHandledDeepLink by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLinkIntent) {
        val uri = deepLinkIntent?.dataString
        if (deepLinkIntent?.action == Intent.ACTION_VIEW && uri != null && uri != lastHandledDeepLink) {
            lastHandledDeepLink = uri
            navController.handleDeepLink(deepLinkIntent)
        }
    }

    // Track current route for bottom navigation
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    // Collect state from ViewModel
    val posts by viewModel.posts.collectAsState()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Show bottom nav only on main screens, not on create post or post detail
            if (currentRoute in listOf("feed", "search", "ble_status", "notifications", "profile")) {
                EnhancedBottomNavigation(
                    selectedRoute = currentRoute,
                    onFabClick = { navController.navigate("create_post") },
                    onNavigate = { route ->
                        if (!navController.popBackStack(route, inclusive = false)) {
                            navController.navigate(route)
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
            modifier = modifier.padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Onboarding
            composable(
                "onboarding",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
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
            composable(
                "feed",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            val postDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel = hiltViewModel()
            EnhancedFeedScreen(
                viewModel = viewModel,
                postDetailViewModel = postDetailViewModel,
                currentUserName = userPreferences.userName,
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToNotifications = { navController.navigate("notifications") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToBLEStatus = { navController.navigate("ble_status") },
                onNavigateToPostDetail = { post ->
                    navController.navigate("post_detail/${post.id}")
                }
            )
        }
        
        // Create Post
        composable(
            "create_post",
            enterTransition = {
                slideInVertically(initialOffsetY = { it }, animationSpec = tween(350))
            },
            exitTransition = {
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(250))
            }
        ) {
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
        composable(
                "profile",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("edit_profile") },
                onPostClick = { postId -> navController.navigate("post_detail/$postId") }
            )
        }

        // Edit Profile
        composable(
                "edit_profile",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
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
        composable(
                "search",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            SearchScreen(
                posts = posts,
                onPostClick = { post ->
                    navController.navigate("post_detail/${post.id}")
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Notifications
        composable(
                "notifications",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            val notificationsViewModel: NotificationsViewModel = hiltViewModel()
            NotificationsScreen(
                viewModel = notificationsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        // Settings
        composable(
                "settings",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAccount = { navController.navigate("settings/account") },
                onNavigateToPrivacy = { navController.navigate("settings/privacy") },
                onNavigateToNotifications = { navController.navigate("settings/notifications") },
                onNavigateToAbout = { navController.navigate("settings/about") }
            )
        }
        
        // Settings Detail Screens
        composable(
                "settings/account",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            AccountSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
                "settings/privacy",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            PrivacySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
                "settings/notifications",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            NotificationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
                "settings/about",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        // BLE Mesh Status
        composable(
                "ble_status",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            BLEMeshStatusScreen(
                connectedPeers = connectedPeers.ifEmpty { List(connectedPeersCount) { "Peer $it" } },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Post Detail
        composable(
            route = "post_detail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "neo://posts/{postId}" }),
            enterTransition = { fadeIn(animationSpec = tween(0)) },
            exitTransition = { fadeOut(animationSpec = tween(0)) },
            popEnterTransition = { fadeIn(animationSpec = tween(0)) },
            popExitTransition = { fadeOut(animationSpec = tween(0)) }
        ) {
            val postDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel = hiltViewModel()
            val post by postDetailViewModel.post.collectAsState()
            
            when {
                post != null -> PostDetailScreen(
                    post = post!!,
                    viewModel = postDetailViewModel,
                    currentUserName = userPreferences.userName,
                    onBack = { navController.popBackStack() }
                )
                postDetailViewModel.loadAttempted -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Post not found",
                            color = TextWhite60,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("Go back")
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
}
