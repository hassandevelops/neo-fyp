package com.neo.sync

import android.util.Log
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.model.Device
import com.neo.data.model.Post
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.BlockedUserRepository
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.ReactionRepository
import com.neo.media.ImageChunker
import com.neo.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the epidemic-style gossip protocol for post propagation.
 * Handles post broadcasting, TTL management, deduplication, and image transmission.
 */
@Singleton
class GossipProtocol @Inject constructor(
    private val postRepository: PostRepository,
    private val deviceRepository: DeviceRepository,
    private val cryptoManager: CryptoManager,
    private val blockedUserRepository: BlockedUserRepository,
    private val commentRepository: CommentRepository,
    private val reactionRepository: ReactionRepository,
    private val ackManager: AckManager,
    private val conflictResolver: ConflictResolver
) {
    companion object {
        private const val TAG = "GossipProtocol"
        private const val INITIAL_TTL = 7
        private const val CHUNK_DELAY_MS = 50L // Delay between chunks to avoid congestion
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bluetoothService: BluetoothService? = null
    private val chunkAssemblyManager = ImageChunker.ChunkAssemblyManager()
    
    /**
     * Set the Bluetooth service for message broadcasting.
     */
    fun setBluetoothService(service: BluetoothService) {
        this.bluetoothService = service
    }
    
    /**
     * Broadcast a newly created post to all connected peers.
     * If post has image data, broadcasts metadata and chunks.
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
        
        // Broadcast image if present
        if (post.imageData != null && post.imageHash != null) {
            broadcastImage(post)
        }
    }
    
    /**
     * Broadcast a comment to all connected peers.
     */
    suspend fun broadcastComment(comment: com.neo.data.model.Comment) {
        val service = bluetoothService
        if (service == null) {
            Log.w(TAG, "Bluetooth service not available")
            return
        }
        
        val message = Message.CommentBroadcast(
            id = comment.id,
            postId = comment.postId,
            parentCommentId = comment.parentCommentId,
            authorId = comment.authorId,
            authorName = comment.authorName,
            content = comment.content,
            timestamp = comment.timestamp,
            signature = comment.signature,
            publicKey = comment.publicKey,
            ttl = comment.ttl
        )
        
        service.broadcastMessage(message)
        Log.d(TAG, "Broadcasted comment ${comment.id} to all peers")
    }
    
    /**
     * Broadcast image data for a post.
     */
    private suspend fun broadcastImage(post: Post) {
        val service = bluetoothService ?: return
        val imageData = post.imageData ?: return
        
        // Send metadata first
        val metadata = Message.ImageMetadata(
            postId = post.id,
            imageHash = post.imageHash!!, 
            totalSize = post.imageSize ?: 0,
            totalChunks = (imageData.length + ImageChunker.CHUNK_SIZE - 1) / ImageChunker.CHUNK_SIZE,
            width = post.imageWidth ?: 0,
            height = post.imageHeight ?: 0
        )
        service.broadcastMessage(metadata)
        Log.d(TAG, "Sent image metadata for post ${post.id}")
        
        // Chunk and send image data
        val chunker = ImageChunker()
        val chunks = chunker.chunkImage(imageData, post.id)
        
        for (chunk in chunks) {
            val chunkMessage = Message.ImageChunk(
                postId = chunk.postId,
                chunkIndex = chunk.chunkIndex,
                totalChunks = chunk.totalChunks,
                data = chunk.data,
                checksum = chunk.checksum
            )
            service.broadcastMessage(chunkMessage)
            delay(CHUNK_DELAY_MS) // Small delay to avoid congestion
        }
        
        Log.d(TAG, "Sent ${chunks.size} image chunks for post ${post.id}")
    }
    
    /**
     * Handle a post received from a peer.
     * Verifies signature, checks for duplicates, stores, and forwards if TTL > 0.
     */
    suspend fun handleReceivedPost(
        postBroadcast: Message.PostBroadcast,
        fromPeerAddress: String
    ) {
        // Check if author is blocked
        if (blockedUserRepository.isBlocked(postBroadcast.authorId)) {
            Log.w(TAG, "Post from blocked user ${postBroadcast.authorId}, ignoring")
            return
        }
        
        // Check if we already have this post
        val existingPost = postRepository.getPostById(postBroadcast.id)
        
        if (existingPost != null) {
            // Post exists - check for conflict
            val newPost = Post(
                id = postBroadcast.id,
                authorId = postBroadcast.authorId,
                authorName = postBroadcast.authorName,
                content = postBroadcast.content,
                timestamp = postBroadcast.timestamp,
                signature = postBroadcast.signature,
                publicKey = postBroadcast.publicKey,
                ttl = postBroadcast.ttl,
                firstSeenTimestamp = System.currentTimeMillis(),
                imageData = null,
                imageHash = null,
                imageSize = null,
                imageWidth = null,
                imageHeight = null
            )
            
            if (conflictResolver.isConflict(existingPost, newPost)) {
                Log.w(TAG, "Conflict detected for post ${postBroadcast.id}")
                
                when (val resolution = conflictResolver.resolve(existingPost, newPost)) {
                    is ConflictResolver.Resolution.ReplaceWithNew -> {
                        Log.i(TAG, "Replacing post ${postBroadcast.id} with newer version")
                        postRepository.updatePost(resolution.post)
                    }
                    is ConflictResolver.Resolution.KeepExisting -> {
                        Log.d(TAG, "Keeping existing post ${postBroadcast.id}")
                    }
                    is ConflictResolver.Resolution.Invalid -> {
                        Log.e(TAG, "Invalid conflict resolution: ${resolution.reason}")
                    }
                }
            } else {
                Log.d(TAG, "Post ${postBroadcast.id} already exists with identical content, ignoring")
            }
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
    
    /**
     * Handle received image metadata.
     */
    suspend fun handleImageMetadata(metadata: Message.ImageMetadata, peerAddress: String) {
        Log.d(TAG, "Received image metadata for post ${metadata.postId}: ${metadata.totalChunks} chunks")
        // Metadata is informational, actual assembly happens when chunks arrive
    }
    
    /**
     * Handle received image chunk.
     * Assembles chunks and updates post when complete.
     */
    suspend fun handleImageChunk(chunk: Message.ImageChunk, peerAddress: String) {
        val imageChunk = ImageChunker.ImageChunk(
            postId = chunk.postId,
            chunkIndex = chunk.chunkIndex,
            totalChunks = chunk.totalChunks,
            data = chunk.data,
            checksum = chunk.checksum
        )
        
        val completeImageData = chunkAssemblyManager.addChunk(imageChunk)
        
        if (completeImageData != null) {
            // All chunks received, update the post
            Log.d(TAG, "Image assembly complete for post ${chunk.postId}")
            
            // Get the existing post
            val existingPost = postRepository.getPostById(chunk.postId)
            if (existingPost != null) {
                // Update post with image data
                val updatedPost = existingPost.copy(
                    imageData = completeImageData
                )
                postRepository.updatePost(updatedPost)
                Log.d(TAG, "Updated post ${chunk.postId} with image data")
            }
        }
        
        Log.d(TAG, "Image chunk ${chunk.chunkIndex + 1}/${chunk.totalChunks} received for post ${chunk.postId}")
    }
    
    /**
     * Handle acknowledgment message.
     */
    fun handleAck(ack: Message.Ack) {
        val handled = ackManager.handleAck(ack.messageId)
        
        if (handled) {
            Log.d(TAG, "Processed ACK for message ${ack.messageId} (type: ${ack.messageType}, success: ${ack.success})")
        } else {
            Log.w(TAG, "Received ACK for unknown message ${ack.messageId}")
        }
    }
    
    /**
     * Handle received comment from a peer.
     * Verifies signature and stores if valid.
     */
    suspend fun handleReceivedComment(
        commentBroadcast: Message.CommentBroadcast,
        fromPeerAddress: String
    ) {
        // Check if author is blocked
        if (blockedUserRepository.isBlocked(commentBroadcast.authorId)) {
            Log.w(TAG, "Comment from blocked user ${commentBroadcast.authorId}, ignoring")
            return
        }
        
        // Check if we already have this comment
        if (commentRepository.commentExists(commentBroadcast.id)) {
            Log.d(TAG, "Comment ${commentBroadcast.id} already exists, ignoring")
            return
        }
        
        // Verify the post exists
        val post = postRepository.getPostById(commentBroadcast.postId)
        if (post == null) {
            Log.w(TAG, "Post ${commentBroadcast.postId} not found for comment ${commentBroadcast.id}")
            return
        }
        
        // If this is a reply, verify parent comment exists
        if (commentBroadcast.parentCommentId != null) {
            val parentComment = commentRepository.getCommentById(commentBroadcast.parentCommentId)
            if (parentComment == null) {
                Log.w(TAG, "Parent comment ${commentBroadcast.parentCommentId} not found for reply ${commentBroadcast.id}")
                return
            }
        }
        
        // Verify signature
        val message = createCommentMessage(
            id = commentBroadcast.id,
            postId = commentBroadcast.postId,
            parentCommentId = commentBroadcast.parentCommentId,
            authorId = commentBroadcast.authorId,
            content = commentBroadcast.content,
            timestamp = commentBroadcast.timestamp
        )
        
        val isValid = cryptoManager.verify(
            message = message,
            signatureBase64 = commentBroadcast.signature,
            publicKeyBase64 = commentBroadcast.publicKey
        )
        
        if (!isValid) {
            Log.w(TAG, "Invalid signature for comment ${commentBroadcast.id}, rejecting")
            return
        }
        
        // Create comment object
        val comment = com.neo.data.model.Comment(
            id = commentBroadcast.id,
            postId = commentBroadcast.postId,
            parentCommentId = commentBroadcast.parentCommentId,
            authorId = commentBroadcast.authorId,
            authorName = commentBroadcast.authorName,
            content = commentBroadcast.content,
            timestamp = commentBroadcast.timestamp,
            signature = commentBroadcast.signature,
            publicKey = commentBroadcast.publicKey,
            ttl = commentBroadcast.ttl,
            firstSeenTimestamp = System.currentTimeMillis()
        )
        
        // Store comment
        commentRepository.insertComment(comment)
        Log.i(TAG, "Stored comment ${comment.id} on post ${comment.postId}")
        
        // Propagate to other peers if TTL > 0
        if (comment.ttl > 0) {
            val decrementedComment = comment.copy(ttl = comment.ttl - 1)
            broadcastComment(decrementedComment)
            Log.d(TAG, "Propagated comment ${comment.id} with TTL ${decrementedComment.ttl}")
        }
    }
    
    /**
     * Create the message string for comment signing/verification.
     */
    private fun createCommentMessage(
        id: String,
        postId: String,
        parentCommentId: String?,
        authorId: String,
        content: String,
        timestamp: Long
    ): String {
        return "$id|$postId|${parentCommentId ?: ""}|$authorId|$content|$timestamp"
    }
    /**
     * Broadcast a reaction to all connected peers.
     */
    suspend fun broadcastReaction(reaction: com.neo.data.model.Reaction) {
        val message = Message.ReactionBroadcast(
            id = reaction.id,
            postId = reaction.postId,
            userId = reaction.userId,
            userName = reaction.userName,
            type = reaction.type.name,
            timestamp = reaction.timestamp,
            signature = reaction.signature,
            publicKey = reaction.publicKey,
            ttl = reaction.ttl
        )
        
        bluetoothService?.broadcastMessage(message)
        Log.d(TAG, "Broadcasted reaction ${reaction.id} for post ${reaction.postId}")
    }
    
    /**
     * Handle a received reaction from a peer.
     */
    suspend fun handleReceivedReaction(
        reactionBroadcast: Message.ReactionBroadcast,
        fromPeerAddress: String
    ) {
        // Check if author is blocked
        if (blockedUserRepository.isBlocked(reactionBroadcast.userId)) {
            Log.w(TAG, "Reaction from blocked user ${reactionBroadcast.userId}, ignoring")
            return
        }
        
        // Check if we already have this reaction
        if (reactionRepository.reactionExists(reactionBroadcast.id)) {
            Log.d(TAG, "Reaction ${reactionBroadcast.id} already exists, ignoring")
            return
        }
        
        // Verify the post exists
        val post = postRepository.getPostById(reactionBroadcast.postId)
        if (post == null) {
            Log.w(TAG, "Post ${reactionBroadcast.postId} not found for reaction ${reactionBroadcast.id}")
            return
        }
        
        // Verify signature
        val message = createReactionMessage(
            id = reactionBroadcast.id,
            postId = reactionBroadcast.postId,
            userId = reactionBroadcast.userId,
            type = reactionBroadcast.type,
            timestamp = reactionBroadcast.timestamp
        )
        
        val isValid = cryptoManager.verify(
            message = message,
            signatureBase64 = reactionBroadcast.signature,
            publicKeyBase64 = reactionBroadcast.publicKey
        )
        
        if (!isValid) {
            Log.w(TAG, "Invalid signature for reaction ${reactionBroadcast.id}, rejecting")
            return
        }
        
        // Parse reaction type
        val reactionType = try {
            com.neo.data.model.ReactionType.valueOf(reactionBroadcast.type)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown reaction type: ${reactionBroadcast.type}")
            return
        }
        
        // Create reaction object
        val reaction = com.neo.data.model.Reaction(
            id = reactionBroadcast.id,
            postId = reactionBroadcast.postId,
            userId = reactionBroadcast.userId,
            userName = reactionBroadcast.userName,
            type = reactionType,
            timestamp = reactionBroadcast.timestamp,
            signature = reactionBroadcast.signature,
            publicKey = reactionBroadcast.publicKey,
            ttl = reactionBroadcast.ttl,
            firstSeenTimestamp = System.currentTimeMillis()
        )
        
        // Store reaction
        reactionRepository.insertReaction(reaction)
        Log.i(TAG, "Stored reaction ${reaction.id} on post ${reaction.postId}")
        
        // Propagate to other peers if TTL > 0
        if (reaction.ttl > 0) {
            val decrementedReaction = reaction.copy(ttl = reaction.ttl - 1)
            broadcastReaction(decrementedReaction)
            Log.d(TAG, "Propagated reaction ${reaction.id} with TTL ${decrementedReaction.ttl}")
        }
    }
    
    /**
     * Create the message string for reaction signing/verification.
     */
    private fun createReactionMessage(
        id: String,
        postId: String,
        userId: String,
        type: String,
        timestamp: Long
    ): String {
        return "$id|$postId|$userId|$type|$timestamp"
    }
}
