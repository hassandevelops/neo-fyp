package com.neo.ui.viewmodel

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.neo.data.model.Post
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.domain.usecase.GetFeedUseCase
import com.neo.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.neo.data.preferences.UserPreferences
import com.neo.security.CryptoManager
import com.neo.domain.usecase.CreatePostUseCase

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val getFeedUseCase: GetFeedUseCase,
    private val syncPort: ISyncPort,
    private val syncManager: SyncManager,
    private val notificationRepository: NotificationRepository,
    private val savedPostRepository: SavedPostRepository,
    private val userPreferences: UserPreferences,
    private val cryptoManager: CryptoManager,
    private val createPostUseCase: CreatePostUseCase
) : AndroidViewModel(application) {

    private val _profileImageUri = MutableStateFlow(userPreferences.profileImageUri)
    val profileImageUri: StateFlow<String?> = _profileImageUri.asStateFlow()

    val currentUserId: String = cryptoManager.getDeviceId()

    private val _postCreationState = MutableStateFlow<CreatePostViewModel.UiState>(CreatePostViewModel.UiState.Idle)
    val postCreationState: StateFlow<CreatePostViewModel.UiState> = _postCreationState.asStateFlow()

    fun refreshProfileImage() {
        _profileImageUri.value = userPreferences.profileImageUri
    }

    fun createPost(content: String, authorName: String, imageUri: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _postCreationState.value = CreatePostViewModel.UiState.Loading
            onComplete() // Dismiss immediately

            val result = createPostUseCase(content, authorName, imageUri)
            if (result.isSuccess) {
                _postCreationState.value = CreatePostViewModel.UiState.Success("Post created successfully!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create post"
                _postCreationState.value = CreatePostViewModel.UiState.Error(errorMsg)
            }
        }
    }

    fun resetPostCreationState() {
        _postCreationState.value = CreatePostViewModel.UiState.Idle
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val pagedPosts: Flow<PagingData<Post>> = getFeedUseCase.executePaged()
        .cachedIn(viewModelScope)

    val connectedPeersCount: StateFlow<Int> = syncPort.connectedPeersCount
    val connectedPeers: StateFlow<List<String>> = syncPort.connectedPeers

    fun forceSyncNow() {
        syncManager.forceSyncNow()
    }

    val notificationCount: StateFlow<Int> = notificationRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedPostIds: StateFlow<Set<String>> = savedPostRepository.observeSavedPostIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun getAllPosts(): Flow<List<Post>> = getFeedUseCase.execute()

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            savedPostRepository.toggle(postId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(300)
            _isRefreshing.value = false
        }
    }
}
