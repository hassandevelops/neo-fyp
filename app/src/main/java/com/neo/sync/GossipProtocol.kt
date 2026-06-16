package com.neo.sync

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
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.ReactionRepository
import com.neo.data.repository.PeerProfileRepository
import com.neo.data.repository.FollowRepository
import com.neo.data.preferences.UserPreferences
import com.neo.di.ApplicationScope
import com.neo.domain.port.ISyncPort
import com.neo.media.AvatarStore
import com.neo.media.ImageChunker
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
import com.neo.security.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
    private val notificationRepository: NotificationRepository,
    private val peerProfileRepository: PeerProfileRepository,
    private val followRepository: FollowRepository,
    private val userPreferences: UserPreferences,
    private val avatarStore: AvatarStore,
    private val identityManager: IdentityManager,
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
        // Max image chunks pending-acked at once per peer. Caps concurrent
        // AckManager retry coroutines and BLE sendMutex contention while still
        // keeping the pipe full; layered on top of frame-level back-pressure.
        private const val IMAGE_SEND_WINDOW = 4
    }

    private var bluetoothService: BluetoothService? = null
    private var libP2pService: com.neo.transport.TransportPort? = null
    private var libP2pNode: com.neo.libp2p.LibP2pNode? = null
    private val chunkAssemblyManager = ImageChunker.ChunkAssemblyManager(scope)
    private val _btPeers = MutableStateFlow<List<String>>(emptyList())
    private val _internetPeers = MutableStateFlow<List<String>>(emptyList())
    private var blockedUserIds: Set<String> = emptySet()
    // Cache completed image data whose post hasn't arrived yet (race condition fix).
    // Key: postId, Value: (imageHash, imageBytes)
    private val pendingImages = mutableMapOf<String, Pair<String, ByteArray>>()

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

        Log.d(TAG, "broadcastPost: post=${post.id} peers=${peerAddresses.size} bt=${btPeers.size} inet=${internetPeers.size} btSvc=${bluetoothService != null} libp2pSvc=${libP2pService != null}")
        Log.d(TAG, "broadcastPost: btPeers=$btPeers inetPeers=$internetPeers")
        if (peerAddresses.isEmpty()) {
            Log.i(TAG, "broadcastPost: NO PEERS — post ${post.id} saved locally, will sync on next peer connect")
        }

        val message = buildPostBroadcast(post)
        Log.d(TAG, "broadcastPost: sending post ${post.id} to ${peerAddresses.size} peers")
        for (peerAddress in peerAddresses) {
            ackManager.sendWithAck(
                messageId = post.id,
                message = message,
                peerAddress = peerAddress,
                sendFunction = { msg, addr ->
                    if (internetPeers.contains(addr)) {
                        val sent = libP2pService?.sendMessage(addr, msg) ?: false
                        Log.d(TAG, "broadcastPost: send via libp2p to $addr result=$sent")
                        sent
                    } else {
                        val svcAvail = bluetoothService != null
                        val sent = bluetoothService?.sendMessage(addr, msg) ?: false
                        Log.d(TAG, "broadcastPost: send via BLE to $addr result=$sent svcAvail=$svcAvail")
                        sent
                    }
                },
                onSuccess = {
                    Log.d(TAG, "broadcastPost: ACK received for ${post.id} from $peerAddress")
                    // Start the heavy image stream only AFTER the post is
                    // confirmed delivered to this peer — so the text lands first
                    // and the stream can't starve the post's own delivery.
                    if (post.imageHash != null) {
                        scope.launch { sendImageChunksToPeer(post, peerAddress) }
                    }
                },
                onFailure = { err -> Log.w(TAG, "broadcastPost: FAILED for ${post.id} to $peerAddress: $err") }
            )
        }
        Log.d(TAG, "Broadcasted post ${post.id} to ${peerAddresses.size} peers")

        // Also publish to GossipSub for WAN mesh distribution. The Go binary's
        // GossipSub layer handles fanout to all subscribed peers, including
        // those reached through IPFS AutoRelay for NAT'd devices.
        scope.launch {
            libP2pNode?.publishToGossipSub(message)
        }
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
                // Send image chunks only after the post itself is acked.
                if (post.imageHash != null) {
                    scope.launch { sendImageChunksToPeer(post, peerAddress) }
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Failed to rebroadcast post ${post.id} to $peerAddress: $error")
            }
        )
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
     * Reliably send a post's image to a single peer as chunked messages.
     *
     * Unlike the old fire-and-forget broadcast, every ImageMetadata/ImageChunk
     * goes through [ackManager] (retried until the peer acks it), so a transient
     * frame loss or brief disconnect no longer silently loses the whole image.
     * A bounded send window keeps at most [IMAGE_SEND_WINDOW] chunks in flight at
     * once, capping retry-coroutine fan-out and BLE sendMutex contention while
     * still keeping the pipe full. Called only after the post itself was acked.
     */
    private suspend fun sendImageChunksToPeer(post: Post, peerAddress: String) {
        val imageHash = post.imageHash ?: return
        val imageData = imageFileStore.load(imageHash) ?: run {
            Log.w(TAG, "Image not found in file store: $imageHash")
            return
        }

        val encodedImageData = Base64.encodeToString(imageData, Base64.NO_WRAP)
        val chunks = ImageChunker().chunkImage(encodedImageData, post.id)
        val totalChunks = chunks.size

        val isInternetPeer = libP2pService?.getConnectedPeerAddresses()?.contains(peerAddress) == true
        val sendVia: suspend (Message, String) -> Boolean = { msg, addr ->
            if (isInternetPeer) {
                libP2pService?.sendMessage(addr, msg) ?: false
            } else {
                bluetoothService?.sendMessage(addr, msg) ?: false
            }
        }

        // Metadata: sent reliably for symmetry/logging, but assembly does not
        // depend on it (chunks carry their own postId/totalChunks), so we don't
        // gate the chunk stream on its ack.
        ackManager.sendWithAck(
            messageId = "${post.id}:img:meta",
            message = Message.ImageMetadata(
                postId = post.id,
                imageHash = imageHash,
                totalSize = post.imageSize ?: 0,
                totalChunks = totalChunks,
                width = post.imageWidth ?: 0,
                height = post.imageHeight ?: 0
            ),
            peerAddress = peerAddress,
            sendFunction = sendVia
        )
        Log.d(TAG, "sendImageChunksToPeer: post=${post.id} to $peerAddress, $totalChunks chunks")

        // Windowed reliable chunk send.
        val window = Semaphore(IMAGE_SEND_WINDOW)
        for (chunk in chunks) {
            window.acquire()
            ackManager.sendWithAck(
                messageId = "${post.id}:img:${chunk.chunkIndex}",
                message = Message.ImageChunk(
                    postId = chunk.postId,
                    chunkIndex = chunk.chunkIndex,
                    totalChunks = chunk.totalChunks,
                    data = chunk.data,
                    checksum = chunk.checksum
                ),
                peerAddress = peerAddress,
                sendFunction = sendVia,
                onSuccess = { window.release() },
                onFailure = { err ->
                    window.release()
                    Log.w(TAG, "Image chunk ${chunk.chunkIndex}/$totalChunks failed for post ${post.id} to $peerAddress: $err")
                }
            )
        }
        // Drain: block until every in-flight chunk has resolved (ack or give-up).
        repeat(IMAGE_SEND_WINDOW) { window.acquire() }
        Log.d(TAG, "sendImageChunksToPeer: finished $totalChunks chunks for post ${post.id} to $peerAddress")
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
                locationName = postBroadcast.locationName,
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
            locationName = postBroadcast.locationName,
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

        // If image chunks completed before the post arrived, the completed
        // image data is cached in pendingImages. Save it now.
        val cachedImage = pendingImages.remove(post.id)
        if (cachedImage != null) {
            val (cachedHash, cachedBytes) = cachedImage
            if (post.imageHash == null || cachedHash != post.imageHash) {
                Log.e(TAG, "Pending image hash mismatch for post ${post.id}")
            } else {
                Log.d(TAG, "Applying cached image for post ${post.id}")
                saveImageAndUpdatePost(post, cachedBytes)
            }
        }

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
     *
     * If the post was received via GossipSub, do NOT republish it to the
     * GossipSub topic — that would create an infinite loop. Direct
     * forwarding to BLE peers is still done.
     */
    private suspend fun forwardPost(post: Post, excludePeerAddress: String) {
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val allPeers = (btPeers + internetPeers).distinct()
        val peerAddresses = allPeers.filter { it != excludePeerAddress }

        if (peerAddresses.isEmpty() && !excludePeerAddress.startsWith("gossipsub:")) {
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
            imageData = null, // image sent separately via ImageChunks
            locationName = post.locationName,
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
        Log.d(TAG, "Forwarded post ${post.id} with TTL=$newTtl to ${peerAddresses.size} peers (via ${if (excludePeerAddress.startsWith("gossipsub:")) "gossipsub origin" else "direct"})")
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
        // Image data is NEVER embedded in PostBroadcast. It is sent separately
        // as small ImageChunk messages, each fitting in a single GATT frame.
        // Embedding Base64 image data (~135KB) causes the GATT write buffer
        // to overflow and all subsequent messages to be silently dropped.
        val imageData: String? = null

        Log.d(TAG, "buildPostBroadcast: post=${post.id} imageHash=${post.imageHash} hasLocalFile=${post.imageHash != null && imageFileStore.load(post.imageHash) != null}")

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
            locationName = post.locationName,
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
            Log.d(TAG, "Image assembly COMPLETE for post ${chunk.postId}")

            val imageBytes = Base64.decode(completeImageData, Base64.NO_WRAP)
            val actualHash = calculateImageHash(imageBytes)

            val existingPost = postRepository.getPostById(chunk.postId)
            if (existingPost != null && existingPost.imageHash != null) {
                if (actualHash != existingPost.imageHash) {
                    Log.e(TAG, "Image assembly FAILED for post ${chunk.postId}: hash mismatch! Expected: ${existingPost.imageHash}, got: $actualHash")
                    return
                }
                saveImageAndUpdatePost(existingPost, imageBytes)
            } else {
                // Post hasn't arrived yet — cache the image data.
                // handleReceivedPost will check this cache when the post arrives.
                Log.w(TAG, "Post ${chunk.postId} not in DB yet, caching completed image (hash=${actualHash.take(16)}...)")
                pendingImages[chunk.postId] = actualHash to imageBytes
            }
        }

        Log.d(TAG, "Image chunk ${chunk.chunkIndex + 1}/${chunk.totalChunks} received for post ${chunk.postId}")
    }

    private suspend fun saveImageAndUpdatePost(post: com.neo.data.model.Post, imageBytes: ByteArray) {
        val savedPath = imageFileStore.save(post.imageHash!!, imageBytes)
        if (savedPath != null) {
            Log.d(TAG, "Saved image to file store: ${post.imageHash}")
            postRepository.updatePost(post.copy(firstSeenTimestamp = System.currentTimeMillis()))
        } else {
            Log.w(TAG, "Failed to save image to file store")
        }
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
    @Volatile private var cachedLocalDid: String? = null

    /** The local user's DID (used to detect posts they authored). Cached after first lookup. */
    private suspend fun localDid(): String {
        cachedLocalDid?.let { return it }
        return try {
            identityManager.getOrCreateIdentity().did.also { cachedLocalDid = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve local DID for notifications: ${e.message}")
            ""
        }
    }

    private suspend fun insertNotification(
        type: String,
        message: String,
        postId: String?,
        actorName: String,
        targetUserId: String? = null,
        actorId: String? = null
    ) {
        notificationRepository.insert(
            com.neo.data.model.Notification(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                message = message,
                postId = postId,
                authorName = actorName,
                timestamp = System.currentTimeMillis(),
                targetUserId = targetUserId,
                actorId = actorId
            )
        )
    }

    /**
     * Emit a notification when an incoming comment targets the local user's content:
     * a reply to one of their comments, or a top-level comment on one of their posts.
     * Comments use the device id as authorId; posts are authored under the local DID.
     */
    private suspend fun notifyForIncomingComment(comment: com.neo.data.model.Comment, post: com.neo.data.model.Post) {
        try {
            val myDeviceId = cryptoManager.getDeviceId()
            if (comment.authorId == myDeviceId) return // never notify for our own echoes
            val actor = comment.authorName

            if (comment.parentCommentId != null) {
                val parent = commentRepository.getCommentById(comment.parentCommentId)
                if (parent != null && parent.authorId == myDeviceId) {
                    insertNotification("reply", "$actor replied to your comment", comment.postId, actor)
                }
            } else if (post.authorId == localDid()) {
                insertNotification("comment", "$actor commented on your post", comment.postId, actor)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create comment notification: ${e.message}")
        }
    }

    /** Emit a notification when someone likes a post the local user authored. */
    private suspend fun notifyForIncomingReaction(reaction: com.neo.data.model.Reaction, post: com.neo.data.model.Post) {
        try {
            val myDeviceId = cryptoManager.getDeviceId()
            if (reaction.userId == myDeviceId) return
            if (reaction.type == com.neo.data.model.ReactionType.LIKE && post.authorId == localDid()) {
                insertNotification("like", "${reaction.userName} liked your post", reaction.postId, reaction.userName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create reaction notification: ${e.message}")
        }
    }

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

        // Notify the local user when an incoming comment targets their content.
        notifyForIncomingComment(comment, post)

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

        // Notify the local user when someone likes a post they authored.
        notifyForIncomingReaction(reaction, post)

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

    // ─── Profile sync ────────────────────────────────────────────────────────

    /** Build the signed ProfileBroadcast for the local user's current profile. */
    private suspend fun buildProfileBroadcast(ttl: Int = INITIAL_TTL): Message.ProfileBroadcast? {
        val identity = runCatching { identityManager.getOrCreateIdentity() }.getOrNull() ?: return null
        val updatedAt = userPreferences.profileUpdatedAt
        val displayName = userPreferences.userName
        val bio = userPreferences.userBio
        val thumb = avatarStore.makeThumbnailBase64(userPreferences.profileImageUri)
        val message = createProfileMessage(identity.did, displayName, bio, updatedAt)
        val signature = signWithIdentity(message) ?: return null
        val publicKey = Base64.encodeToString(identity.publicKey.encoded, Base64.NO_WRAP)
        return Message.ProfileBroadcast(
            did = identity.did,
            displayName = displayName,
            bio = bio,
            avatarThumbBase64 = thumb,
            updatedAt = updatedAt,
            signature = signature,
            publicKey = publicKey,
            keyAlgorithm = cryptoManager.getKeyAlgorithmPublic(),
            ttl = ttl
        )
    }

    /** Broadcast the local user's profile to all connected peers (+ GossipSub). */
    override suspend fun broadcastProfile() {
        val message = buildProfileBroadcast() ?: return
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val peerAddresses = (btPeers + internetPeers).distinct()
        for (peerAddress in peerAddresses) {
            sendProfileTo(message, peerAddress, internetPeers)
        }
        scope.launch { libP2pNode?.publishToGossipSub(message) }
        Log.d(TAG, "Broadcasted profile for ${message.did} to ${peerAddresses.size} peers")
    }

    /** Push the local user's profile to a single newly-connected peer. */
    suspend fun broadcastProfileToPeer(peerAddress: String) {
        val message = buildProfileBroadcast() ?: return
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        sendProfileTo(message, peerAddress, internetPeers)
    }

    private suspend fun sendProfileTo(
        message: Message.ProfileBroadcast,
        peerAddress: String,
        internetPeers: List<String>
    ) {
        ackManager.sendWithAck(
            messageId = "${message.did}:profile:${message.updatedAt}",
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

    /** Handle a received profile broadcast — latest-wins, signature-verified. */
    suspend fun handleReceivedProfile(
        profile: Message.ProfileBroadcast,
        fromPeerAddress: String
    ) {
        // Dedup by did:updatedAt so repeated copies of the same version are ignored.
        if (!seenMessageCache.checkAndAdd("${profile.did}:profile:${profile.updatedAt}")) {
            return
        }

        // Verify signature against the key encoded in the DID (not the supplied key).
        val expectedKey = extractPublicKeyFromDid(profile.did)
        val message = createProfileMessage(profile.did, profile.displayName, profile.bio, profile.updatedAt)
        val isValid = cryptoManager.verify(
            message = message,
            signatureBase64 = profile.signature,
            publicKeyBase64 = expectedKey,
            keyAlgorithm = "RSA"
        )
        if (!isValid) {
            Log.w(TAG, "Invalid signature for profile ${profile.did}, rejecting")
            return
        }

        // Latest-wins: ignore if we already hold a newer/equal version.
        val existing = peerProfileRepository.getByDid(profile.did)
        if (existing != null && existing.updatedAt >= profile.updatedAt) {
            return
        }

        val avatarPath = avatarStore.saveAvatar(profile.did, profile.avatarThumbBase64)
            ?: existing?.avatarPath
        peerProfileRepository.upsert(
            com.neo.data.model.PeerProfile(
                did = profile.did,
                displayName = profile.displayName,
                bio = profile.bio,
                avatarPath = avatarPath,
                updatedAt = profile.updatedAt
            )
        )
        Log.i(TAG, "Stored profile for ${profile.did} (updatedAt=${profile.updatedAt})")

        // Propagate to other peers if TTL remains.
        if (profile.ttl > 0) {
            val decremented = profile.copy(ttl = profile.ttl - 1)
            val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
            val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
            (btPeers + internetPeers).distinct()
                .filter { it != fromPeerAddress }
                .forEach { sendProfileTo(decremented, it, internetPeers) }
        }
    }

    private fun createProfileMessage(did: String, name: String, bio: String, updatedAt: Long): String =
        "$did|$name|$bio|$updatedAt"

    // ─── Follow graph ──────────────────────────────────────────────────────────

    override suspend fun broadcastFollow(follow: com.neo.data.model.Follow, followerName: String) {
        val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val peerAddresses = (btPeers + internetPeers).distinct()

        val message = Message.FollowBroadcast(
            id = follow.id,
            followerDid = follow.followerDid,
            followeeDid = follow.followeeDid,
            followerName = followerName,
            active = follow.active,
            timestamp = follow.timestamp,
            signature = follow.signature,
            publicKey = follow.publicKey,
            keyAlgorithm = cryptoManager.getKeyAlgorithmPublic(),
            ttl = INITIAL_TTL
        )
        for (peerAddress in peerAddresses) {
            sendFollowTo(message, peerAddress, internetPeers)
        }
        scope.launch { libP2pNode?.publishToGossipSub(message) }
        Log.d(TAG, "Broadcasted follow ${follow.id} (active=${follow.active}) to ${peerAddresses.size} peers")
    }

    /** Re-send the local user's outgoing follow edges to a newly-connected peer. */
    suspend fun broadcastFollowsToPeer(peerAddress: String) {
        val myDid = localDid()
        if (myDid.isEmpty()) return
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        val myName = userPreferences.userName
        followRepository.getOutgoing(myDid).forEach { follow ->
            val message = Message.FollowBroadcast(
                id = follow.id,
                followerDid = follow.followerDid,
                followeeDid = follow.followeeDid,
                followerName = myName,
                active = follow.active,
                timestamp = follow.timestamp,
                signature = follow.signature,
                publicKey = follow.publicKey,
                keyAlgorithm = cryptoManager.getKeyAlgorithmPublic(),
                ttl = INITIAL_TTL
            )
            sendFollowTo(message, peerAddress, internetPeers)
        }
    }

    private suspend fun sendFollowTo(
        message: Message.FollowBroadcast,
        peerAddress: String,
        internetPeers: List<String>
    ) {
        ackManager.sendWithAck(
            messageId = "${message.id}:${message.timestamp}",
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

    /** Handle a received follow edge — latest-wins, signature-verified. */
    suspend fun handleReceivedFollow(
        follow: Message.FollowBroadcast,
        fromPeerAddress: String
    ) {
        if (!seenMessageCache.checkAndAdd("${follow.id}:${follow.timestamp}")) {
            return
        }
        if (follow.followerDid in blockedUserIds) {
            return
        }

        val expectedKey = extractPublicKeyFromDid(follow.followerDid)
        val message = createFollowMessage(
            follow.id, follow.followerDid, follow.followeeDid, follow.active, follow.timestamp
        )
        val isValid = cryptoManager.verify(
            message = message,
            signatureBase64 = follow.signature,
            publicKeyBase64 = expectedKey,
            keyAlgorithm = "RSA"
        )
        if (!isValid) {
            Log.w(TAG, "Invalid signature for follow ${follow.id}, rejecting")
            return
        }

        // Latest-wins on the (follower, followee) edge.
        val existing = followRepository.getById(follow.id)
        if (existing != null && existing.timestamp >= follow.timestamp) {
            return
        }

        followRepository.upsert(
            com.neo.data.model.Follow(
                id = follow.id,
                followerDid = follow.followerDid,
                followeeDid = follow.followeeDid,
                active = follow.active,
                timestamp = follow.timestamp,
                signature = follow.signature,
                publicKey = follow.publicKey
            )
        )
        Log.i(TAG, "Stored follow ${follow.id} (active=${follow.active})")

        // Notify the local user when someone newly follows them.
        if (follow.active && existing?.active != true && follow.followeeDid == localDid()) {
            insertNotification(
                type = "follow",
                message = "${follow.followerName} started following you",
                postId = null,
                actorName = follow.followerName,
                targetUserId = follow.followerDid,
                actorId = follow.followerDid
            )
        }

        if (follow.ttl > 0) {
            val decremented = follow.copy(ttl = follow.ttl - 1)
            val btPeers = bluetoothService?.getConnectedPeerAddresses() ?: emptyList()
            val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
            (btPeers + internetPeers).distinct()
                .filter { it != fromPeerAddress }
                .forEach { sendFollowTo(decremented, it, internetPeers) }
        }
    }

    private fun createFollowMessage(
        id: String, followerDid: String, followeeDid: String, active: Boolean, timestamp: Long
    ): String = "$id|$followerDid|$followeeDid|$active|$timestamp"

    // ─── Identity signing helpers (DID-keyed) ───────────────────────────────────

    /** Sign with the identity (DID) private key — RSA SHA-256, matching posts. */
    private fun signWithIdentity(message: String): String? {
        return try {
            val sig = java.security.Signature.getInstance("SHA256withRSA")
            sig.initSign(identityManager.getPrivateKey())
            sig.update(message.toByteArray())
            Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sign with identity key: ${e.message}")
            null
        }
    }

    /** Extract the Base64 public key from a "did:key:base64url" string. */
    private fun extractPublicKeyFromDid(did: String): String =
        did.removePrefix("did:key:").replace('-', '+').replace('_', '/')
}
