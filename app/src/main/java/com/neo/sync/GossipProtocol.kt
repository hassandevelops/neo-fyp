package com.neo.sync

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import android.util.Base64
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.dao.EventLogDao
import com.neo.data.model.Device
import com.neo.data.model.EventLog
import com.neo.data.model.Post
import com.neo.data.repository.BlockedUserRepository
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.ReactionRepository
import com.neo.di.ApplicationScope
import com.neo.domain.port.ISyncPort
import com.neo.media.ImageChunker
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
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
    private val conflictResolver: ConflictResolver,
    private val seenMessageCache: SeenMessageCache,
    private val rateLimiter: RateLimiter,
    private val imageFileStore: ImageFileStore,
    private val eventLogDao: EventLogDao,
    @ApplicationScope private val scope: CoroutineScope
) : ISyncPort {
    companion object {
        private const val TAG = "GossipProtocol"
        private const val INITIAL_TTL = 7
        private const val CHUNK_DELAY_MS = 100L // Delay between chunks to avoid congestion
    }

    private var bluetoothService: BluetoothService? = null
    private var libP2pService: com.neo.transport.TransportPort? = null
    private var libP2pNode: com.neo.libp2p.LibP2pNode? = null
    private val chunkAssemblyManager = ImageChunker.ChunkAssemblyManager(scope)
    private val _btPeers = MutableStateFlow<List<String>>(emptyList())
    private val _internetPeers = MutableStateFlow<List<String>>(emptyList())
    private var blockedUserIds: Set<String> = emptySet()

    override val connectedPeersCount: StateFlow<Int> = combine(_btPeers, _internetPeers) { bt, inet -> (bt + inet).distinct() }
        .map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)
    override val connectedPeers: StateFlow<List<String>> = combine(_btPeers, _internetPeers) { bt, inet -> (bt + inet).distinct() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun forceSyncNow() {
        // Force sync is handled at the SyncManager level via FeedViewModel
    }

    /**
     * Set the Bluetooth service for message broadcasting.
     */
    fun setBluetoothService(service: BluetoothService) {
        this.bluetoothService = service
        scope.launch {
            service.connectedPeers.collect { peers ->
                _btPeers.value = peers
            }
        }

        // Cache blocked users in memory to avoid DB queries on hot gossip path
        scope.launch {
            blockedUserRepository.getAllBlocked().collect { blocked ->
                blockedUserIds = blocked.map { it.blockedUserId }.toHashSet()
            }
        }
    }

    /**
     * Set the libp2p service for internet message broadcasting.
     */
    fun setLibP2pService(service: com.neo.transport.TransportPort) {
        this.libP2pService = service
        if (service is com.neo.libp2p.LibP2pService) {
            this.libP2pNode = service.getNode()
        }
        scope.launch {
            service.connectedPeers.collect { peers ->
                _internetPeers.value = peers
            }
        }
    }
    
    /**
     * Broadcast a newly created post to all connected peers.
     * If post has image data, broadcasts metadata and chunks.
     */
    override suspend fun broadcastPost(post: Post) {
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val peerAddresses = (btPeers + internetPeers).distinct()

        Log.d(TAG, "broadcastPost called. Connected peers: ${peerAddresses.size} (bt=${btPeers.size}, internet=${internetPeers.size})")
        if (peerAddresses.isEmpty()) {
            Log.w(TAG, "No peers available for broadcasting — post ${post.id} saved locally, will sync on next peer connect")
        }

        val message = buildPostBroadcast(post)
        Log.d(TAG, "broadcastPost: sending post ${post.id} (authorId=${post.authorId.take(20)}...) to ${peerAddresses.size} peers: ${peerAddresses.joinToString { it.take(8) }}")
        for (peerAddress in peerAddresses) {
            ackManager.sendWithAck(
                messageId = post.id,
                message = message,
                peerAddress = peerAddress,
                sendFunction = { msg, addr ->
                    if (internetPeers.contains(addr)) {
                        libP2pService?.sendMessage(addr, msg) ?: false
                    } else {
                        bluetoothService?.sendMessage(addr, msg) ?: false
                    }
                }
            )
        }
        Log.d(TAG, "Broadcasted post ${post.id} to ${peerAddresses.size} peers")

        // Also publish to GossipSub for WAN mesh distribution
        scope.launch {
            libP2pNode?.publishToTopic(message)
        }
        // Image is now embedded inside the PostBroadcast message (imageData field).
        // No separate chunked broadcast needed.
    }

    /**
     * Broadcast a post to a single specific peer — used when re-broadcasting
     * local posts to a newly connected peer.
     */
    suspend fun broadcastPostToPeer(post: Post, peerAddress: String) {
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val message = buildPostBroadcast(post)
        ackManager.sendWithAck(
            messageId = post.id,
            message = message,
            peerAddress = peerAddress,
            sendFunction = { msg, addr ->
                if (internetPeers.contains(addr)) {
                    libP2pService?.sendMessage(addr, msg) ?: false
                } else {
                    bluetoothService?.sendMessage(addr, msg) ?: false
                }
            },
            onSuccess = {
                Log.d(TAG, "Rebroadcasted post ${post.id} to new peer $peerAddress")
            },
            onFailure = { error ->
                Log.w(TAG, "Failed to rebroadcast post ${post.id} to $peerAddress: $error")
            }
        )
        // Image is embedded in PostBroadcast; no separate chunked send required.
    }

    /**
     * Broadcast a comment to all connected peers.
     */
    override suspend fun broadcastComment(comment: com.neo.data.model.Comment) {
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val peerAddresses = (btPeers + internetPeers).distinct()

        if (peerAddresses.isEmpty()) {
            Log.w(TAG, "No peers available for broadcasting comment")
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
            keyAlgorithm = cryptoManager.getKeyAlgorithmPublic(),
            ttl = comment.ttl
        )

        for (peerAddress in peerAddresses) {
            ackManager.sendWithAck(
                messageId = comment.id,
                message = message,
                peerAddress = peerAddress,
                sendFunction = { msg, addr ->
                    if (internetPeers.contains(addr)) {
                        libP2pService?.sendMessage(addr, msg) ?: false
                    } else {
                        bluetoothService?.sendMessage(addr, msg) ?: false
                    }
                }
            )
        }
        Log.d(TAG, "Broadcasted comment ${comment.id} to ${peerAddresses.size} peers")
    }
    
    /**
     * Broadcast image data for a post.
     */
    private suspend fun broadcastImage(post: Post) {
        val imageHash = post.imageHash ?: return

        Log.d(TAG, "broadcastImage: starting for post ${post.id}, hash=$imageHash")

        // Load image from file store
        val imageData = imageFileStore.load(imageHash) ?: run {
            Log.w(TAG, "Image not found in file store: $imageHash")
            return
        }

        Log.d(TAG, "broadcastImage: loaded ${imageData.size} bytes from file store")

        // Encode to Base64 first so we know the actual chunk count
        val encodedImageData = Base64.encodeToString(imageData, Base64.NO_WRAP)
        val chunker = ImageChunker()
        val actualTotalChunks = (encodedImageData.length + ImageChunker.CHUNK_SIZE - 1) / ImageChunker.CHUNK_SIZE

        // Send metadata first
        val metadata = Message.ImageMetadata(
            postId = post.id,
            imageHash = imageHash,
            totalSize = post.imageSize ?: 0,
            totalChunks = actualTotalChunks,
            width = post.imageWidth ?: 0,
            height = post.imageHeight ?: 0
        )
        bluetoothService?.broadcastMessage(metadata)
        libP2pService?.broadcastMessage(metadata)
        Log.d(TAG, "Sent image metadata for post ${post.id}: $actualTotalChunks chunks, ${post.imageSize ?: 0} bytes")

        // Chunk and send image data
        val chunks = chunker.chunkImage(encodedImageData, post.id)

        for (chunk in chunks) {
            val chunkMessage = Message.ImageChunk(
                postId = chunk.postId,
                chunkIndex = chunk.chunkIndex,
                totalChunks = chunk.totalChunks,
                data = chunk.data,
                checksum = chunk.checksum
            )
            bluetoothService?.broadcastMessage(chunkMessage)
            libP2pService?.broadcastMessage(chunkMessage)
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
        // Check if we've already seen this message
        if (!seenMessageCache.checkAndAdd(postBroadcast.id)) {
            Log.d(TAG, "Already seen post ${postBroadcast.id}, ignoring")
            return
        }

        // Check if author is blocked
        if (postBroadcast.authorId in blockedUserIds) {
            Log.w(TAG, "Post from blocked user ${postBroadcast.authorId}, ignoring")
            return
        }

        // Check inbound rate limit
        if (!rateLimiter.canAcceptInboundPost(postBroadcast.authorId)) {
            Log.w(TAG, "Inbound rate limit exceeded for author ${postBroadcast.authorId}, dropping post from peer $fromPeerAddress")
            return
        }

        // Check if we already have this post
        val existingPost = postRepository.getPostById(postBroadcast.id)
        
        if (existingPost != null) {
            // Post exists - check for conflict, but also fill in event log if missing
            val newPost = Post(
                id = postBroadcast.id,
                authorId = postBroadcast.authorId,
                authorName = postBroadcast.authorName,
                content = postBroadcast.content,
                imageHash = postBroadcast.imageHash,
                imageSize = postBroadcast.imageSize,
                imageWidth = postBroadcast.imageWidth,
                imageHeight = postBroadcast.imageHeight,
                timestamp = postBroadcast.timestamp,
                signature = postBroadcast.signature,
                publicKey = postBroadcast.publicKey,
                keyAlgorithm = postBroadcast.keyAlgorithm,
                ttl = postBroadcast.ttl,
                firstSeenTimestamp = System.currentTimeMillis(),
                eventId = postBroadcast.eventId.ifBlank { null },
                authorDid = postBroadcast.authorDid.ifBlank { null },
                sequenceNum = postBroadcast.sequenceNum.takeIf { it > 0L },
                eventType = postBroadcast.eventType.ifBlank { null },
                eventSignature = postBroadcast.eventSignature.ifBlank { null },
                eventPayload = postBroadcast.eventPayload.ifBlank { null }
            )

            // Fill event_log if the existing post doesn't have event log data
            maybeFillEventLogFromBroadcast(postBroadcast, existingPost)
            
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
            publicKeyBase64 = postBroadcast.publicKey,
            keyAlgorithm = postBroadcast.keyAlgorithm
        )
        
        if (!isValid) {
            Log.w(TAG, "Invalid signature for post ${postBroadcast.id} (algorithm=${postBroadcast.keyAlgorithm}, authorId=${postBroadcast.authorId.take(20)}...), rejecting")
            return
        }
        
        // Store the post
        val post = Post(
            id = postBroadcast.id,
            authorId = postBroadcast.authorId,
            authorName = postBroadcast.authorName,
            content = postBroadcast.content,
            imageHash = postBroadcast.imageHash,
            imageSize = postBroadcast.imageSize,
            imageWidth = postBroadcast.imageWidth,
            imageHeight = postBroadcast.imageHeight,
            timestamp = postBroadcast.timestamp,
            signature = postBroadcast.signature,
            publicKey = postBroadcast.publicKey,
            keyAlgorithm = postBroadcast.keyAlgorithm,
            ttl = postBroadcast.ttl,
            firstSeenTimestamp = System.currentTimeMillis(),
            eventId = postBroadcast.eventId.ifBlank { null },
            authorDid = postBroadcast.authorDid.ifBlank { null },
            sequenceNum = postBroadcast.sequenceNum.takeIf { it > 0L },
            eventType = postBroadcast.eventType.ifBlank { null },
            eventSignature = postBroadcast.eventSignature.ifBlank { null },
            eventPayload = postBroadcast.eventPayload.ifBlank { null }
        )
        
        val inserted = postRepository.insertPost(post)
        if (!inserted) {
            Log.d(TAG, "Post ${post.id} was already inserted by another thread")
            return
        }

        // Save image data if embedded in the broadcast (newer path: image embedded in post).
        // Falls through to chunked assembly if not present (older sender).
        if (post.imageHash != null && postBroadcast.imageData != null) {
            try {
                val imageBytes = Base64.decode(postBroadcast.imageData, Base64.NO_WRAP)
                if (imageFileStore.save(post.imageHash, imageBytes) != null) {
                    Log.d(TAG, "Saved embedded image for post ${post.id} (${imageBytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save embedded image for post ${post.id}", e)
            }
        }

        Log.d(TAG, "Inserted post ${post.id} from ${post.authorName} into Room DB")

        // Reconstruct EventLog entry so this post is relayable via event sync
        insertEventLogFromBroadcast(postBroadcast)

        
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
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val allPeers = (btPeers + internetPeers).distinct()
        val peerAddresses = allPeers.filter { it != excludePeerAddress }

        if (peerAddresses.isEmpty()) {
            Log.w(TAG, "No peers available for forwarding post")
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
            keyAlgorithm = post.keyAlgorithm,
            ttl = newTtl,
            imageHash = post.imageHash,
            imageSize = post.imageSize,
            imageWidth = post.imageWidth,
            imageHeight = post.imageHeight,
            eventId = post.eventId ?: "",
            authorDid = post.authorDid ?: "",
            sequenceNum = post.sequenceNum ?: 0L,
            eventType = post.eventType ?: "CREATE_POST",
            eventSignature = post.eventSignature ?: "",
            eventPayload = post.eventPayload ?: "{}"
        )

        for (peerAddress in peerAddresses) {
            ackManager.sendWithAck(
                messageId = post.id,
                message = message,
                peerAddress = peerAddress,
                sendFunction = { msg, addr ->
                    if (internetPeers.contains(addr)) {
                        libP2pService?.sendMessage(addr, msg) ?: false
                    } else {
                        bluetoothService?.sendMessage(addr, msg) ?: false
                    }
                }
            )
        }
        Log.d(TAG, "Forwarded post ${post.id} with TTL=$newTtl to ${peerAddresses.size} peers")
    }

    /**
     * Reconstruct an EventLog entry from the PostBroadcast's event log fields
     * so gossip-received posts become relayable via EventSyncRequest/Response.
     */
    private suspend fun insertEventLogFromBroadcast(postBroadcast: Message.PostBroadcast) {
        if (postBroadcast.eventId.isBlank() || postBroadcast.eventSignature.isBlank()) {
            Log.d(TAG, "Post ${postBroadcast.id} has no event log data in broadcast, skipping event_log insertion")
            return
        }
        val eventLog = EventLog(
            eventId = postBroadcast.eventId,
            authorDid = postBroadcast.authorDid.ifBlank { postBroadcast.authorId },
            sequenceNum = postBroadcast.sequenceNum.takeIf { it > 0L } ?: 0L,
            eventType = postBroadcast.eventType.ifBlank { "CREATE_POST" },
            payload = postBroadcast.eventPayload.ifBlank { "{}" },
            signature = postBroadcast.eventSignature,
            timestamp = postBroadcast.timestamp
        )
        eventLogDao.insertEvent(eventLog)
        Log.d(TAG, "Inserted event_log entry for post ${postBroadcast.id} from gossip")
    }

    /**
     * Fill in event_log for an already-existing post that was received before
     * the event log relay fix was deployed. Only inserts if the post doesn't
     * already have event log data stored.
     */
    private suspend fun maybeFillEventLogFromBroadcast(
        postBroadcast: Message.PostBroadcast,
        existingPost: Post
    ) {
        if (existingPost.eventId != null) {
            // Already has event log data — nothing to do
            return
        }
        if (postBroadcast.eventId.isBlank() || postBroadcast.eventSignature.isBlank()) {
            // Sender doesn't have event log data either (legacy or old peer)
            return
        }
        insertEventLogFromBroadcast(postBroadcast)
        // Also update the existing post's event log fields so forwardPost carries them
        postRepository.updatePost(existingPost.copy(
            eventId = postBroadcast.eventId.ifBlank { null },
            authorDid = postBroadcast.authorDid.ifBlank { null },
            sequenceNum = postBroadcast.sequenceNum.takeIf { it > 0L },
            eventType = postBroadcast.eventType.ifBlank { null },
            eventSignature = postBroadcast.eventSignature.ifBlank { null },
            eventPayload = postBroadcast.eventPayload.ifBlank { null }
        ))
    }
    
    /**
     * Handle handshake from a peer.
     * Store the device information.
     */
    suspend fun handleHandshake(handshake: Message.Handshake, peerAddress: String, transport: String) {
        // Validate public key is parseable
        try {
            val keyBytes = android.util.Base64.decode(handshake.publicKey, android.util.Base64.NO_WRAP)
            java.security.spec.X509EncodedKeySpec(keyBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid public key in handshake from $peerAddress (transport=$transport): ${e.message}")
            return
        }

        val device = Device(
            deviceId = handshake.deviceId,
            deviceName = handshake.deviceName,
            publicKey = handshake.publicKey,
            lastSeenTimestamp = System.currentTimeMillis(),
            peerAddress = peerAddress,
            transport = transport
        )
        
        deviceRepository.insertDevice(device)
        Log.d(TAG, "Stored device info for ${handshake.deviceName}")
    }
    
    private suspend fun buildPostBroadcast(post: Post): Message.PostBroadcast {
        // Embed image data directly if available (sender has the file).
        // The receiver saves it as part of post processing — no separate chunk stream needed.
        val imageData: String? = if (post.imageHash != null) {
            imageFileStore.load(post.imageHash)?.let { bytes ->
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } else null

        return Message.PostBroadcast(
            id = post.id,
            authorId = post.authorId,
            authorName = post.authorName,
            content = post.content,
            timestamp = post.timestamp,
            signature = post.signature,
            publicKey = post.publicKey,
            keyAlgorithm = post.keyAlgorithm,
            ttl = post.ttl,
            imageHash = post.imageHash,
            imageSize = post.imageSize,
            imageWidth = post.imageWidth,
            imageHeight = post.imageHeight,
            imageData = imageData,
            eventId = post.eventId ?: "",
            authorDid = post.authorDid ?: "",
            sequenceNum = post.sequenceNum ?: 0L,
            eventType = post.eventType ?: "CREATE_POST",
            eventSignature = post.eventSignature ?: "",
            eventPayload = post.eventPayload ?: "{}"
        )
    }

    /**
     * Send handshake to a peer.
     */
    suspend fun sendHandshake(peerAddress: String) {
        val handshake = Message.Handshake(
            deviceId = cryptoManager.getDeviceId(),
            deviceName = android.os.Build.MODEL,
            publicKey = cryptoManager.getPublicKey()
        )
        
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        if (internetPeers.contains(peerAddress)) {
            libP2pService?.sendMessage(peerAddress, handshake)
            Log.d(TAG, "Sent handshake via libp2p to $peerAddress")
        } else {
            bluetoothService?.sendMessage(peerAddress, handshake)
            Log.d(TAG, "Sent handshake via Bluetooth to $peerAddress")
        }
    }
    
    /**
     * Handle received image metadata.
     */
    suspend fun handleImageMetadata(metadata: Message.ImageMetadata, peerAddress: String) {
        Log.d(TAG, "Received image_metadata for post ${metadata.postId}: " +
            "${metadata.totalChunks} chunks, ${metadata.totalSize} bytes, hash=${metadata.imageHash}")
    }
    
    /**
     * Handle received image chunk.
     * Assembles chunks and saves to ImageFileStore when complete.
     */
    suspend fun handleImageChunk(chunk: Message.ImageChunk, peerAddress: String) {
        Log.d(TAG, "handleImageChunk: post=${chunk.postId} idx=${chunk.chunkIndex}/${chunk.totalChunks} dataLen=${chunk.data.length}")
        val imageChunk = ImageChunker.ImageChunk(
            postId = chunk.postId,
            chunkIndex = chunk.chunkIndex,
            totalChunks = chunk.totalChunks,
            data = chunk.data,
            checksum = chunk.checksum
        )

        val completeImageData = chunkAssemblyManager.addChunk(imageChunk)

        if (completeImageData != null) {
            // All chunks received, save to file store
            Log.d(TAG, "Image assembly COMPLETE for post ${chunk.postId}")

            // Get the existing post to get the imageHash
            val existingPost = postRepository.getPostById(chunk.postId)
            if (existingPost != null && existingPost.imageHash != null) {
                // Save image to file store
                val imageBytes = Base64.decode(completeImageData, Base64.NO_WRAP)
                val actualHash = calculateImageHash(imageBytes)
                if (actualHash != existingPost.imageHash) {
                    Log.e(
                        TAG,
                        "Image assembly FAILED for post ${chunk.postId}: hash mismatch! " +
                            "Expected: ${existingPost.imageHash}, got: $actualHash"
                    )
                    return
                }
                val savedPath = imageFileStore.save(existingPost.imageHash, imageBytes)
                if (savedPath != null) {
                    Log.d(TAG, "Saved image to file store: ${existingPost.imageHash}")
                    // Bump firstSeenTimestamp so Room invalidates the PagingSource and UI recomposes
                    postRepository.updatePost(existingPost.copy(firstSeenTimestamp = System.currentTimeMillis()))
                } else {
                    Log.w(TAG, "Failed to save image to file store")
                }
            } else {
                Log.w(TAG, "Post not found or missing imageHash for post ${chunk.postId}")
            }
        }

        Log.d(TAG, "Image chunk ${chunk.chunkIndex + 1}/${chunk.totalChunks} received for post ${chunk.postId}")
    }

    private fun calculateImageHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
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
        // Check if we've already seen this message
        if (!seenMessageCache.checkAndAdd(commentBroadcast.id)) {
            Log.d(TAG, "Already seen comment ${commentBroadcast.id}, ignoring")
            return
        }

        // Check if author is blocked
        if (commentBroadcast.authorId in blockedUserIds) {
            Log.w(TAG, "Comment from blocked user ${commentBroadcast.authorId}, ignoring")
            return
        }

        // Check inbound rate limit
        if (!rateLimiter.canAcceptInboundComment(commentBroadcast.authorId)) {
            Log.w(TAG, "Inbound rate limit exceeded for author ${commentBroadcast.authorId}, dropping comment from peer $fromPeerAddress")
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
            publicKeyBase64 = commentBroadcast.publicKey,
            keyAlgorithm = commentBroadcast.keyAlgorithm
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
    override suspend fun broadcastReaction(reaction: com.neo.data.model.Reaction) {
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val peerAddresses = (btPeers + internetPeers).distinct()

        if (peerAddresses.isEmpty()) {
            Log.w(TAG, "No peers available for broadcasting reaction")
            return
        }

        val message = Message.ReactionBroadcast(
            id = reaction.id,
            postId = reaction.postId,
            userId = reaction.userId,
            userName = reaction.userName,
            type = reaction.type.name,
            timestamp = reaction.timestamp,
            signature = reaction.signature,
            publicKey = reaction.publicKey,
            keyAlgorithm = cryptoManager.getKeyAlgorithmPublic(),
            ttl = reaction.ttl
        )

        for (peerAddress in peerAddresses) {
            ackManager.sendWithAck(
                messageId = reaction.id,
                message = message,
                peerAddress = peerAddress,
                sendFunction = { msg, addr ->
                    if (internetPeers.contains(addr)) {
                        libP2pService?.sendMessage(addr, msg) ?: false
                    } else {
                        bluetoothService?.sendMessage(addr, msg) ?: false
                    }
                }
            )
        }
        Log.d(TAG, "Broadcasted reaction ${reaction.id} for post ${reaction.postId} to ${peerAddresses.size} peers")
    }
    
    /**
     * Handle a received reaction from a peer.
     */
    suspend fun handleReceivedReaction(
        reactionBroadcast: Message.ReactionBroadcast,
        fromPeerAddress: String
    ) {
        // Check if we've already seen this message
        if (!seenMessageCache.checkAndAdd(reactionBroadcast.id)) {
            Log.d(TAG, "Already seen reaction ${reactionBroadcast.id}, ignoring")
            return
        }

        // Check if author is blocked
        if (reactionBroadcast.userId in blockedUserIds) {
            Log.w(TAG, "Reaction from blocked user ${reactionBroadcast.userId}, ignoring")
            return
        }

        // Check inbound rate limit
        if (!rateLimiter.canAcceptInboundReaction(reactionBroadcast.userId)) {
            Log.w(TAG, "Inbound rate limit exceeded for user ${reactionBroadcast.userId}, dropping reaction from peer $fromPeerAddress")
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
            publicKeyBase64 = reactionBroadcast.publicKey,
            keyAlgorithm = reactionBroadcast.keyAlgorithm
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
