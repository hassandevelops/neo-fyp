package com.neo.ui.navigation

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neo.ui.screens.CreatePostScreen
import com.neo.ui.screens.FeedScreen
import com.neo.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

/**
 * Navigation setup for Neo app.
 */
@Composable
fun NeoNavigation(
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    
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
                navController.popBackStack()
            }
            is FeedViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "feed",
        modifier = modifier
    ) {
        composable("feed") {
            FeedScreen(
                posts = posts,
                connectedPeersCount = connectedPeersCount,
                onCreatePostClick = {
                    navController.navigate("create_post")
                },
                onRefreshClick = {
                    // Refresh is automatic via Flow
                }
            )
        }
        
        composable("create_post") {
            CreatePostScreen(
                onPostClick = { content, authorName ->
                    viewModel.createPost(content, authorName)
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
    }
    
    SnackbarHost(hostState = snackbarHostState)
}
