package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.neo.data.model.Post
import com.neo.data.model.PeerProfile
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.PeerProfileRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.domain.usecase.GetFeedUseCase
import com.neo.sync.SyncManager
import com.neo.security.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private val identityManager: IdentityManager,
    private val peerProfileRepository: PeerProfileRepository,
    private val createPostUseCase: CreatePostUseCase
) : AndroidViewModel(application) {

    private val _profileImageUri = MutableStateFlow(userPreferences.profileImageUri)
    val profileImageUri: StateFlow<String?> = _profileImageUri.asStateFlow()

    val currentUserId: String = cryptoManager.getDeviceId()

    /** The local user's identity DID (posts are authored under this). */
    val myDid: String = runCatching { identityManager.getDid() }.getOrDefault("")

    /** All ids that represent "me" (DID for posts, deviceId for comments/reactions). */
    val selfIds: Set<String> = setOf(myDid, currentUserId).filter { it.isNotEmpty() }.toSet()

    /** Peer profiles keyed by DID, for resolving avatars/names of other users. */
    val profilesByDid: StateFlow<Map<String, PeerProfile>> =
        peerProfileRepository.observeProfilesByDid()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _postCreationState = MutableStateFlow<CreatePostViewModel.UiState>(CreatePostViewModel.UiState.Idle)
    val postCreationState: StateFlow<CreatePostViewModel.UiState> = _postCreationState.asStateFlow()

    fun refreshProfileImage() {
        _profileImageUri.value = userPreferences.profileImageUri
    }

    fun createPost(content: String, authorName: String, imageUri: String? = null, locationName: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _postCreationState.value = CreatePostViewModel.UiState.Creating("Creating post…")
            onComplete() // Dismiss immediately

            // The post is persisted locally by the use case before it broadcasts,
            // so it appears in the feed immediately regardless of network state.
            // Cap the whole call so a slow/stuck transport can never wedge the
            // "Publishing…" indicator — the post still syncs in the background.
            val result = try {
                kotlinx.coroutines.withTimeout(8000) {
                    createPostUseCase(content, authorName, imageUri, locationName)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Treat a slow broadcast as success: the post is saved and will
                // propagate when a peer is reachable.
                Result.success(Unit)
            }

            if (result.isSuccess) {
                _postCreationState.value = CreatePostViewModel.UiState.Broadcasting("Broadcasting to mesh…")
                kotlinx.coroutines.delay(1500)
                _postCreationState.value = CreatePostViewModel.UiState.Success("Post shared with mesh")
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

    /** Newest notification (or null) — drives the transient in-app banner. */
    val latestNotification: StateFlow<com.neo.data.model.Notification?> =
        notificationRepository.getAllNotifications()
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
