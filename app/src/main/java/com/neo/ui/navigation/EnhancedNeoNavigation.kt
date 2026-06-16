package com.neo.ui.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import android.util.Log
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
    libP2pService: com.neo.libp2p.LibP2pService? = null,
    modifier: Modifier = Modifier
) {
    val TAG = "EnhancedNeoNavigation"
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
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val currentImageUri by viewModel.profileImageUri.collectAsStateWithLifecycle()

    // ── Transient in-app notification banner ───────────────────────────────────
    val latestNotification by viewModel.latestNotification.collectAsStateWithLifecycle()
    val bannerProfiles by viewModel.profilesByDid.collectAsStateWithLifecycle()
    var bannerNotification by remember { mutableStateOf<com.neo.data.model.Notification?>(null) }
    var lastSeenNotifId by remember { mutableStateOf<String?>(null) }
    var bannerInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(latestNotification?.id) {
        val n = latestNotification
        if (n != null) {
            if (!bannerInitialized) {
                // Don't banner whatever already exists on app open.
                lastSeenNotifId = n.id
                bannerInitialized = true
            } else if (n.id != lastSeenNotifId) {
                lastSeenNotifId = n.id
                // Only surface fresh, unread notifications while not already viewing the list.
                if (!n.isRead && currentRoute != "notifications") {
                    bannerNotification = n
                    kotlinx.coroutines.delay(3000)
                    bannerNotification = null
                }
            }
        }
    }

    val postCreationState by viewModel.postCreationState.collectAsStateWithLifecycle()
    LaunchedEffect(postCreationState) {
        when (val state = postCreationState) {
            is CreatePostViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetPostCreationState()
            }
            is CreatePostViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetPostCreationState()
            }
            is CreatePostViewModel.UiState.Broadcasting -> {
                Log.d(TAG, "Broadcasting: ${state.message}")
            }
            is CreatePostViewModel.UiState.Creating -> {
                Log.d(TAG, "Creating: ${state.message}")
            }
            else -> {}
        }
    }
    
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
      Box(modifier = Modifier.fillMaxSize()) {
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
                    onComplete = { username ->
                        userPreferences.userName = username
                        userPreferences.profileUpdatedAt = System.currentTimeMillis()
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
                currentUserImageUri = currentImageUri,
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToNotifications = { navController.navigate("notifications") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToBLEStatus = { navController.navigate("ble_status") },
                onNavigateToUserProfile = { post ->
                    if (post.authorId == viewModel.currentUserId) {
                        navController.navigate("profile")
                    } else {
                        navController.navigate("profile/${post.authorId}")
                    }
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
            EnhancedCreatePostScreen(
                onPostClick = { content, authorName, imageUri, locationName ->
                    viewModel.createPost(content, authorName, imageUri, locationName) {
                        navController.popBackStack()
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                },
                currentUserName = userPreferences.userName,
                currentUserImageUri = currentImageUri
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
            val profilePostDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel = hiltViewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                postDetailViewModel = profilePostDetailViewModel,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("edit_profile") },
                onPostClick = { postId -> navController.navigate("post_detail/$postId") }
            )
        }

        // Another user's profile (read-only)
        composable(
                "profile/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val otherProfilePostDetailViewModel: com.neo.ui.viewmodel.PostDetailViewModel = hiltViewModel()
            ProfileScreen(
                viewModel = profileViewModel,
                postDetailViewModel = otherProfilePostDetailViewModel,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("edit_profile") },
                onPostClick = { postId -> navController.navigate("post_detail/$postId") },
                isCurrentUser = false
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
            val currentName by profileViewModel.profileName.collectAsStateWithLifecycle()
            val currentBio by profileViewModel.profileBio.collectAsStateWithLifecycle()
            val currentProfilePhoto by profileViewModel.profileImageUri.collectAsStateWithLifecycle()

            EditProfileScreen(
                currentName = currentName,
                currentBio = currentBio,
                currentImageUri = currentProfilePhoto,
                onSave = { name, bio, imageUri ->
                    profileViewModel.updateProfile(name, bio, imageUri)
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
            val searchPosts by viewModel.getAllPosts().collectAsStateWithLifecycle(initialValue = emptyList())
            val searchProfiles by viewModel.profilesByDid.collectAsStateWithLifecycle()
            SearchScreen(
                posts = searchPosts,
                onPostClick = { post ->
                    navController.navigate("post_detail/${post.id}")
                },
                onBack = { navController.popBackStack() },
                profiles = searchProfiles,
                selfIds = viewModel.selfIds,
                selfImageUri = currentImageUri
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
                onBack = { navController.popBackStack() },
                onNotificationClick = { notification ->
                    navigateForNotification(navController, notification, viewModel.currentUserId)
                }
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
                onNavigateToAbout = { navController.navigate("settings/about") },
                onNavigateToNetwork = { navController.navigate("settings/network") }
            )
        }

        composable(
                "settings/network",
                enterTransition = { fadeIn(animationSpec = tween(0)) },
                exitTransition = { fadeOut(animationSpec = tween(0)) },
                popEnterTransition = { fadeIn(animationSpec = tween(0)) },
                popExitTransition = { fadeOut(animationSpec = tween(0)) }
            ) {
            NetworkSettingsScreen(
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() }
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
            // Live WAN status from the bound libp2p service (null until bound).
            // Fallback flows are remembered unconditionally (Rules of Compose);
            // collectAsStateWithLifecycle re-keys when the service binds.
            val fbDht = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
            val fbBootstrap = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
            val fbError = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
            val fbReach = remember { kotlinx.coroutines.flow.MutableStateFlow("unknown") }
            val wanDhtCount by (libP2pService?.dhtPeerCount ?: fbDht).collectAsStateWithLifecycle()
            val wanBootstrap by (libP2pService?.bootstrapConnected ?: fbBootstrap).collectAsStateWithLifecycle()
            val wanError by (libP2pService?.lastError ?: fbError).collectAsStateWithLifecycle()
            val wanReachability by (libP2pService?.reachability ?: fbReach).collectAsStateWithLifecycle()
            BLEMeshStatusScreen(
                connectedPeers = connectedPeers.ifEmpty { List(connectedPeersCount) { "Peer $it" } },
                dhtPeerCount = wanDhtCount,
                bootstrapConnected = wanBootstrap,
                lastError = wanError,
                reachability = wanReachability,
                onForceSync = { viewModel.forceSyncNow() },
                onShowConnectQr = { navController.navigate("connect_me") },
                onBack = { navController.popBackStack() }
            )
        }

        // Connect to me — show this device's QR so a peer can scan & connect
        composable(
            "connect_me",
            enterTransition = { fadeIn(animationSpec = tween(0)) },
            exitTransition = { fadeOut(animationSpec = tween(0)) },
            popEnterTransition = { fadeIn(animationSpec = tween(0)) },
            popExitTransition = { fadeOut(animationSpec = tween(0)) }
        ) {
            val fbConnect = remember { kotlinx.coroutines.flow.MutableStateFlow<com.neo.libp2p.ConnectInfo?>(null) }
            val fbReach2 = remember { kotlinx.coroutines.flow.MutableStateFlow("unknown") }
            val connectInfo by (libP2pService?.connectInfo ?: fbConnect).collectAsStateWithLifecycle()
            val reachability by (libP2pService?.reachability ?: fbReach2).collectAsStateWithLifecycle()
            LaunchedEffect(libP2pService) { libP2pService?.requestConnectInfo() }
            ConnectMeScreen(
                connectInfo = connectInfo,
                reachability = reachability,
                onRefresh = { libP2pService?.requestConnectInfo() },
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
            val post by postDetailViewModel.post.collectAsStateWithLifecycle()
            
            when {
                post != null -> PostDetailScreen(
                    post = post!!,
                    viewModel = postDetailViewModel,
                    currentUserName = userPreferences.userName,
                    currentUserId = viewModel.currentUserId,
                    profileImageUri = currentImageUri,
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

        // Transient in-app banner overlay (above content, below system bars).
        com.neo.ui.components.NeoNotificationBanner(
            notification = bannerNotification,
            avatarUri = bannerNotification?.actorId?.let { bannerProfiles[it]?.avatarPath },
            visible = bannerNotification != null,
            onClick = {
                val n = bannerNotification
                bannerNotification = null
                if (n != null) navigateForNotification(navController, n, viewModel.currentUserId)
            },
            onDismiss = { bannerNotification = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
      }
    }
}

/**
 * Routes a tapped notification to the content it references:
 *  - like / comment / reply / repost → the related post detail (via [Notification.postId])
 *  - follow / mention                → the actor's profile  (via [Notification.targetUserId])
 * Missing ids are no-ops; the post-detail screen itself handles a deleted post.
 */
private fun navigateForNotification(
    navController: androidx.navigation.NavController,
    notification: com.neo.data.model.Notification,
    currentUserId: String
) {
    when (notification.type) {
        "follow", "mention" -> {
            val userId = notification.targetUserId ?: return
            if (userId == currentUserId) {
                navController.navigate("profile")
            } else {
                navController.navigate("profile/$userId")
            }
        }
        else -> {
            val postId = notification.postId ?: return
            navController.navigate("post_detail/$postId")
        }
    }
}
