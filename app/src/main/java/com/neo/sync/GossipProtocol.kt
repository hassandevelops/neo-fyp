package com.neo.sync

import android.util.Log
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.model.Device
import com.neo.data.model.Post
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the epidemic-style gossip protocol for post propagation.
 * Handles post broadcasting, TTL management, and deduplication.
 */
@Singleton
class GossipProtocol @Inject constructor(
    private val postRepository: PostRepository,
    private val deviceRepository: DeviceRepository,
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val TAG = "GossipProtocol"
        private const val INITIAL_TTL = 7
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bluetoothService: BluetoothService? = null
    
    /**
     * Set the Bluetooth service for message broadcasting.
     */
    fun setBluetoothService(service: BluetoothService) {
        this.bluetoothService = service
    }
    
    /**
     * Broadcast a newly created post to all connected peers.
     */
    suspend fun broadcastPost(post: Post) {
        val service = bluetoothService
        if (service == null) {
            Log.w(TAG, "Bluetooth service not available")
            return
        }
        
        val message = Message.PostBroadcast(
            id = post.id,
            authorId = post.authorId,
            authorName = post.authorName,
            content = post.content,
            timestamp = post.timestamp,
            signature = post.signature,
            publicKey = post.publicKey,
            ttl = post.ttl
        )
        
        service.broadcastMessage(message)
        Log.d(TAG, "Broadcasted post ${post.id} to all peers")
    }
    
    /**
     * Handle a post received from a peer.
     * Verifies signature, checks for duplicates, stores, and forwards if TTL > 0.
     */
    suspend fun handleReceivedPost(
        postBroadcast: Message.PostBroadcast,
        fromPeerAddress: String
    ) {
        // Check if we already have this post
        if (postRepository.postExists(postBroadcast.id)) {
            Log.d(TAG, "Post ${postBroadcast.id} already exists, ignoring")
            return
        }
        
        // Verify signature
        val message = cryptoManager.createPostMessage(
            id = postBroadcast.id,
            authorId = postBroadcast.authorId,
            content = postBroadcast.content,
            timestamp = postBroadcast.timestamp
        )
        
        val isValid = cryptoManager.verify(
            message = message,
            signatureBase64 = postBroadcast.signature,
            publicKeyBase64 = postBroadcast.publicKey
        )
        
        if (!isValid) {
            Log.w(TAG, "Invalid signature for post ${postBroadcast.id}, rejecting")
            return
        }
        
        // Store the post
        val post = Post(
            id = postBroadcast.id,
            authorId = postBroadcast.authorId,
            authorName = postBroadcast.authorName,
            content = postBroadcast.content,
            timestamp = postBroadcast.timestamp,
            signature = postBroadcast.signature,
            publicKey = postBroadcast.publicKey,
            ttl = postBroadcast.ttl,
            firstSeenTimestamp = System.currentTimeMillis()
        )
        
        val inserted = postRepository.insertPost(post)
        if (!inserted) {
            Log.d(TAG, "Post ${post.id} was already inserted by another thread")
            return
        }
        
        Log.d(TAG, "Stored post ${post.id} from ${post.authorName}")
        
        // Forward to other peers if TTL > 0
        if (post.ttl > 0) {
            forwardPost(post, fromPeerAddress)
        } else {
            Log.d(TAG, "Post ${post.id} reached TTL limit, not forwarding")
        }
    }
    
    /**
     * Forward a post to all connected peers except the sender.
     */
    private suspend fun forwardPost(post: Post, excludePeerAddress: String) {
        val service = bluetoothService
        if (service == null) {
            Log.w(TAG, "Bluetooth service not available for forwarding")
            return
        }
        
        // Decrement TTL
        val newTtl = post.ttl - 1
        if (newTtl < 0) return
        
        val message = Message.PostBroadcast(
            id = post.id,
            authorId = post.authorId,
            authorName = post.authorName,
            content = post.content,
            timestamp = post.timestamp,
            signature = post.signature,
            publicKey = post.publicKey,
            ttl = newTtl
        )
        
        service.broadcastMessage(message, excludePeer = excludePeerAddress)
        Log.d(TAG, "Forwarded post ${post.id} with TTL=$newTtl")
    }
    
    /**
     * Handle handshake from a peer.
     * Store the device information.
     */
    suspend fun handleHandshake(handshake: Message.Handshake, peerAddress: String) {
        val device = Device(
            deviceId = handshake.deviceId,
            deviceName = handshake.deviceName,
            publicKey = handshake.publicKey,
            lastSeenTimestamp = System.currentTimeMillis(),
            bluetoothAddress = peerAddress
        )
        
        deviceRepository.insertDevice(device)
        Log.d(TAG, "Stored device info for ${handshake.deviceName}")
    }
    
    /**
     * Send handshake to a peer.
     */
    suspend fun sendHandshake(peerAddress: String) {
        val service = bluetoothService ?: return
        
        val handshake = Message.Handshake(
            deviceId = cryptoManager.getDeviceId(),
            deviceName = android.os.Build.MODEL, // Use device model as default name
            publicKey = cryptoManager.getPublicKey()
        )
        
        service.sendMessage(peerAddress, handshake)
        Log.d(TAG, "Sent handshake to $peerAddress")
    }
}
