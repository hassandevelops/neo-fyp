package com.neo.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.model.Post
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.ReactionRepository
import com.neo.domain.usecase.CreateCommentUseCase
import com.neo.domain.usecase.CreatePostUseCase
import com.neo.domain.usecase.CreateReactionUseCase
import com.neo.domain.usecase.DeleteReactionUseCase
import com.neo.domain.usecase.GetFeedUseCase
import com.neo.data.model.ReactionType
import com.neo.security.CryptoManager
import com.neo.sync.GossipProtocol
import com.neo.sync.SyncManager
import com.neo.utils.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main feed screen.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val getFeedUseCase: GetFeedUseCase,
    private val createPostUseCase: CreatePostUseCase,
    private val commentRepository: CommentRepository,
    private val createCommentUseCase: CreateCommentUseCase,
    private val reactionRepository: ReactionRepository,
    private val createReactionUseCase: CreateReactionUseCase,
    private val deleteReactionUseCase: DeleteReactionUseCase,
    private val cryptoManager: CryptoManager,
    private val gossipProtocol: GossipProtocol,
    private val syncManager: SyncManager
) : AndroidViewModel(application) {
    
    // Feed posts
    val posts: StateFlow<List<Post>> = getFeedUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Connected peers count
    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()
    
    // UI state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Success(val message: String) : UiState()
    }
    
    /**
     * Set the Bluetooth service and start listening for updates.
     */
    fun setBluetoothService(service: BluetoothService) {
        gossipProtocol.setBluetoothService(service)
        syncManager.setBluetoothService(service)
        
        // Set up message handler
        service.onMessageReceived = { peerAddress, message ->
            handleReceivedMessage(peerAddress, message)
        }
        
        // Monitor connected peers
        viewModelScope.launch {
            service.connectedPeers.collect { peers ->
                _connectedPeersCount.value = peers.size
            }
        }
    }
    
    /**
     * Handle messages received from peers.
     */
    private fun handleReceivedMessage(peerAddress: String, message: Message) {
        viewModelScope.launch {
            when (message) {
                is Message.Handshake -> {
                    gossipProtocol.handleHandshake(message, peerAddress)
                    // Send our handshake back
                    gossipProtocol.sendHandshake(peerAddress)
                }
                is Message.PostBroadcast -> {
                    gossipProtocol.handleReceivedPost(message, peerAddress)
                }
                is Message.SyncRequest -> {
                    syncManager.handleSyncRequest(message, peerAddress)
                }
                is Message.SyncResponse -> {
                    syncManager.handleSyncResponse(message, peerAddress)
                }
                is Message.ImageMetadata -> {
                    gossipProtocol.handleImageMetadata(message, peerAddress)
                }
                is Message.ImageChunk -> {
                    gossipProtocol.handleImageChunk(message, peerAddress)
                }
                is Message.Ack -> {
                    // Handle acknowledgment
                    gossipProtocol.handleAck(message)
                }
                is Message.CommentBroadcast -> {
                    gossipProtocol.handleReceivedComment(message, peerAddress)
                }
                else -> {
                    // Ignore other message types
                }
            }
        }
    }
    
    /**
     * Create a new post.
     */
    fun createPost(content: String, authorName: String, imageUri: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            // Save image to internal storage if provided
            val savedImagePath = if (imageUri != null) {
                try {
                    ImageUtils.saveImageToInternalStorage(getApplication(), Uri.parse(imageUri))
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            val result = createPostUseCase(content, authorName, savedImagePath)
            
            _uiState.value = if (result.isSuccess) {
                UiState.Success("Post created successfully!")
            } else {
                UiState.Error(result.exceptionOrNull()?.message ?: "Failed to create post")
            }
        }
    }
    
    /**
     * Reset UI state to idle.
     */
    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
    
    /**
     * Get top-level comments for a post.
     */
    fun getCommentsForPost(postId: String) = commentRepository.getTopLevelCommentsForPost(postId)
    
    /**
     * Get replies for a specific comment.
     */
    fun getRepliesForComment(commentId: String) = commentRepository.getRepliesForComment(commentId)
    
    /**
     * Get comment count for a post.
     */
    fun getCommentCountForPost(postId: String) = commentRepository.getCommentCountForPostFlow(postId)
    
    /**
     * Create a new comment on a post.
     */
    fun createComment(
        postId: String,
        content: String,
        authorName: String,
        parentCommentId: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = createCommentUseCase(
                postId = postId,
                content = content,
                authorName = authorName,
                parentCommentId = parentCommentId
            )
            
            result.fold(
                onSuccess = { 
                    android.util.Log.d("FeedViewModel", "Comment created successfully")
                    onSuccess()
                },
                onFailure = { error ->
                    android.util.Log.e("FeedViewModel", "Failed to create comment", error)
                    onError(error.message ?: "Failed to create comment")
                }
            )
        }
    }
    
    // ========== Reactions ==========
    
    /**
     * Get like count for a post.
     */
    fun getLikeCountForPost(postId: String): Flow<Int> {
        return reactionRepository.getReactionCountForPost(postId, ReactionType.LIKE)
    }
    
    /**
     * Check if current user has liked a post.
     */
    fun hasUserLikedPost(postId: String): Flow<Boolean> {
        val userId = cryptoManager.getDeviceId()
        return reactionRepository.hasUserReactedFlow(postId, userId, ReactionType.LIKE)
    }
    
    /**
     * Toggle like on a post.
     */
    fun toggleLike(
        postId: String,
        userName: String,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val userId = cryptoManager.getDeviceId()
            val hasLiked = reactionRepository.hasUserReacted(postId, userId, ReactionType.LIKE)
            
            if (hasLiked) {
                // Unlike
                val result = deleteReactionUseCase(postId, ReactionType.LIKE)
                result.fold(
                    onSuccess = {
                        android.util.Log.d("FeedViewModel", "Unliked post $postId")
                    },
                    onFailure = { error ->
                        android.util.Log.e("FeedViewModel", "Failed to unlike post", error)
                        onError(error.message ?: "Failed to unlike post")
                    }
                )
            } else {
                // Like
                val result = createReactionUseCase(postId, userName, ReactionType.LIKE)
                result.fold(
                    onSuccess = {
                        android.util.Log.d("FeedViewModel", "Liked post $postId")
                    },
                    onFailure = { error ->
                        android.util.Log.e("FeedViewModel", "Failed to like post", error)
                        onError(error.message ?: "Failed to like post")
                    }
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        syncManager.stop()
    }
}
