package com.neo.sync

import android.util.Log
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val postRepository: PostRepository,
    private val deviceRepository: DeviceRepository,
    private val gossipProtocol: GossipProtocol,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_INTERVAL_MS = 30000L // 30 seconds
    }

    private var bluetoothService: BluetoothService? = null
    private var syncJob: Job? = null
    private var lastSyncTimestamp = 0L

    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    init {
        scope.launch {
            bluetoothService?.let { service ->
                setupMessageRouting(service)
            }
        }
    }

    private fun setupMessageRouting(service: BluetoothService) {
        service.onMessageReceived = { peerAddress, message ->
            scope.launch {
                when (message) {
                    is Message.SyncRequest -> {
                        handleSyncRequest(message, peerAddress)
                    }
                    is Message.SyncResponse -> {
                        handleSyncResponse(message, peerAddress)
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Set the Bluetooth service and start periodic sync.
     */
    fun setBluetoothService(service: BluetoothService) {
        this.bluetoothService = service

        setupMessageRouting(service)
        
        scope.launch {
            service.connectedPeers.collect { peers ->
                _connectedPeersCount.value = peers.size
            }
        }

        startPeriodicSync()
    }
    
    /**
     * Stop the sync manager.
     */
    fun stop() {
        syncJob?.cancel()
    }
    
    /**
     * Start periodic synchronization with peers.
     */
    private fun startPeriodicSync() {
        syncJob?.cancel()
        
        syncJob = scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                
                val service = bluetoothService
                if (service != null) {
                    syncWithPeers(service)
                }
            }
        }
    }
    
    /**
     * Sync with all connected peers.
     */
    private suspend fun syncWithPeers(service: BluetoothService) {
        val peers = service.getConnectedPeerAddresses()
        if (peers.isEmpty()) {
            Log.d(TAG, "No peers connected, skipping sync")
            return
        }
        
        Log.d(TAG, "Syncing with ${peers.size} peers")
        
        // Request posts from each peer that are newer than our last sync
        val syncRequest = Message.SyncRequest(lastTimestamp = lastSyncTimestamp)
        
        peers.forEach { peerAddress ->
            service.sendMessage(peerAddress, syncRequest)
        }
        
        lastSyncTimestamp = System.currentTimeMillis()
    }
    
    /**
     * Handle sync request from a peer.
     * Send posts newer than the requested timestamp.
     */
    suspend fun handleSyncRequest(request: Message.SyncRequest, peerAddress: String) {
        val service = bluetoothService ?: return
        
        // Get posts newer than requested timestamp
        val posts = postRepository.getPostsAfter(request.lastTimestamp)
        
        if (posts.isEmpty()) {
            Log.d(TAG, "No new posts to send to $peerAddress")
            return
        }
        
        // Convert to PostBroadcast messages
        val postBroadcasts = posts.map { post ->
            Message.PostBroadcast(
                id = post.id,
                authorId = post.authorId,
                authorName = post.authorName,
                content = post.content,
                timestamp = post.timestamp,
                signature = post.signature,
                publicKey = post.publicKey,
                ttl = post.ttl
            )
        }
        
        val response = Message.SyncResponse(posts = postBroadcasts)
        service.sendMessage(peerAddress, response)
        
        Log.d(TAG, "Sent ${posts.size} posts to $peerAddress in sync response")
    }
    
    /**
     * Handle sync response from a peer.
     * Process all received posts.
     */
    suspend fun handleSyncResponse(response: Message.SyncResponse, peerAddress: String) {
        Log.d(TAG, "Received ${response.posts.size} posts from $peerAddress")
        
        response.posts.forEach { postBroadcast ->
            gossipProtocol.handleReceivedPost(postBroadcast, peerAddress)
        }
    }
}
