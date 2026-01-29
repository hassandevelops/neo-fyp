package com.neo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.model.Post
import com.neo.domain.usecase.CreatePostUseCase
import com.neo.domain.usecase.GetFeedUseCase
import com.neo.sync.GossipProtocol
import com.neo.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main feed screen.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val getFeedUseCase: GetFeedUseCase,
    private val createPostUseCase: CreatePostUseCase,
    private val gossipProtocol: GossipProtocol,
    private val syncManager: SyncManager
) : ViewModel() {
    
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
            
            val result = createPostUseCase(content, authorName, imageUri)
            
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
    
    override fun onCleared() {
        super.onCleared()
        syncManager.stop()
    }
}
