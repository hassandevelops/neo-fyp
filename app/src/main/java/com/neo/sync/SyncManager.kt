package com.neo.sync

import android.util.Log
import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.dao.EventLogDao
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.di.ApplicationScope
import com.neo.libp2p.LibP2pNode
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
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
    private val syncCoordinator: SyncCoordinator,
    private val identityManager: IdentityManager,
    private val cryptoManager: CryptoManager,
    private val eventLogDao: EventLogDao,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_INTERVAL_MS = 30000L
    }

    private var bluetoothService: BluetoothService? = null
    private var libP2pService: com.neo.transport.TransportPort? = null
    private var libP2pNode: LibP2pNode? = null
    private var syncJob: Job? = null
    private var peerMonitorJob: Job? = null
    private var libP2pRoutingJob: Job? = null
    private var libP2pPeerMonitorJob: Job? = null

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
        @Suppress("TooGenericExceptionCaught")
        scope.launch {
            try {
                service.allIncomingMessages.collect { (peerAddress, message) ->
                    when (message) {
                        is Message.Handshake -> {
                            gossipProtocol.handleHandshake(message, peerAddress, "ble")
                            scope.launch { syncWithPeer(service, peerAddress) }
                        }
                        is Message.PostBroadcast -> {
                            gossipProtocol.handleReceivedPost(message, peerAddress)
                            sendAck(service, peerAddress, message.id, "PostBroadcast")
                        }
                        is Message.CommentBroadcast -> {
                            gossipProtocol.handleReceivedComment(message, peerAddress)
                            sendAck(service, peerAddress, message.id, "CommentBroadcast")
                        }
                        is Message.ReactionBroadcast -> {
                            gossipProtocol.handleReceivedReaction(message, peerAddress)
                            sendAck(service, peerAddress, message.id, "ReactionBroadcast")
                        }
                        is Message.ImageMetadata -> {
                            gossipProtocol.handleImageMetadata(message, peerAddress)
                            sendAck(service, peerAddress, "${message.postId}:img:meta", "ImageMetadata")
                        }
                        is Message.ImageChunk -> {
                            gossipProtocol.handleImageChunk(message, peerAddress)
                            // Ack every chunk, including duplicates — the lost
                            // message may have been a previous ack, not the chunk.
                            sendAck(service, peerAddress, "${message.postId}:img:${message.chunkIndex}", "ImageChunk")
                        }
                        is Message.Ack -> gossipProtocol.handleAck(message)
                        is Message.EventSyncRequest -> handleEventSyncRequest(message, peerAddress)
                        is Message.EventSyncResponse -> handleEventSyncResponse(message, peerAddress)
                        is Message.PeerExchange -> handlePeerExchange(message, peerAddress)
                        is Message.ProfileBroadcast -> {
                            gossipProtocol.handleReceivedProfile(message, peerAddress)
                            sendAck(service, peerAddress, "${message.did}:profile:${message.updatedAt}", "ProfileBroadcast")
                        }
                        is Message.FollowBroadcast -> {
                            gossipProtocol.handleReceivedFollow(message, peerAddress)
                            sendAck(service, peerAddress, "${message.id}:${message.timestamp}", "FollowBroadcast")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Message routing crashed, restarting in 3s", e)
                delay(3000)
                setupMessageRouting(service)
            }
        }
    }

    private suspend fun sendAck(
        service: BluetoothService,
        peerAddress: String,
        messageId: String,
        messageType: String
    ) {
        service.sendMessage(peerAddress, Message.Ack(messageId, messageType, true))
    }

    fun setBluetoothService(service: BluetoothService) {
        this.bluetoothService = service
        gossipProtocol.setBluetoothService(service)
        setupMessageRouting(service)

        peerMonitorJob?.cancel()
        peerMonitorJob = scope.launch {
            var knownPeers = emptySet<String>()
            service.connectedPeers.collect { peers ->
                val currentPeers = peers.toSet()
                val inetCount = libP2pService?.getConnectedPeerAddresses()?.size ?: 0
                _connectedPeersCount.value = currentPeers.size + inetCount
                currentPeers.minus(knownPeers).forEach { peerAddress ->
                    gossipProtocol.sendHandshake(peerAddress)
                    scope.launch { sendPeerExchange(peerAddress) }
                    scope.launch { broadcastAllLocalPostsTo(peerAddress) }
                    scope.launch { gossipProtocol.broadcastProfileToPeer(peerAddress) }
                    scope.launch { gossipProtocol.broadcastFollowsToPeer(peerAddress) }
                }
                knownPeers = currentPeers
            }
        }

        startPeriodicSync()
    }

    fun setLibP2pService(service: com.neo.transport.TransportPort) {
        this.libP2pService = service
        // Snag LibP2pNode reference for PEX
        if (service is com.neo.libp2p.LibP2pService) {
            this.libP2pNode = service.getNode()
        }
        gossipProtocol.setLibP2pService(service)
        startLibP2pMessageRouting(service)
        startLibP2pPeerMonitor(service)
    }

    fun forceSyncNow() {
        scope.launch {
            Log.d(TAG, "Force sync triggered")
            bluetoothService?.let { syncWithPeers(it) }
            libP2pService?.let { service ->
                service.getConnectedPeerAddresses().forEach { peerId ->
                    syncWithLibP2pPeer(service, peerId)
                }
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        peerMonitorJob?.cancel()
        libP2pRoutingJob?.cancel()
        libP2pPeerMonitorJob?.cancel()
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                bluetoothService?.let { syncWithPeers(it) }
                // Also sync with internet peers
                libP2pService?.let { service ->
                    service.getConnectedPeerAddresses().forEach { peerId ->
                        scope.launch { syncWithLibP2pPeer(service, peerId) }
                    }
                }
            }
        }
    }

    private suspend fun syncWithPeer(service: BluetoothService, peerAddress: String) {
        val myDid = identityManager.getOrCreateIdentity().did
        val lastSyncTimestamp = eventLogDao.getMaxTimestamp() ?: 0L
        Log.d(TAG, "Requesting event sync from $peerAddress (since ts=$lastSyncTimestamp)")
        val request = Message.EventSyncRequest(
            requesterDid = myDid,
            lastKnownTimestamp = lastSyncTimestamp
        )
        service.sendMessage(peerAddress, request)
    }

    private suspend fun syncWithPeers(service: BluetoothService) {
        val peers = service.getConnectedPeerAddresses()
        if (peers.isEmpty()) {
            Log.d(TAG, "No peers connected, skipping sync")
            return
        }
        Log.d(TAG, "Syncing with ${peers.size} peers (periodic sync)")
        peers.forEach { peerAddress -> syncWithPeer(service, peerAddress) }
    }

    suspend fun handleEventSyncRequest(request: Message.EventSyncRequest, peerAddress: String) {
        val missingEvents = eventLogDao.getAllEventsSince(request.lastKnownTimestamp)
        if (missingEvents.isEmpty()) {
            Log.d(TAG, "No events since ${request.lastKnownTimestamp} for $peerAddress")
            return
        }

        val dtos = missingEvents.map { com.neo.bluetooth.EventLogDto.fromEntity(it) }
        val response = Message.EventSyncResponse(authorDid = request.requesterDid, events = dtos)

        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        if (internetPeers.contains(peerAddress)) {
            libP2pService?.sendMessage(peerAddress, response)
        } else {
            bluetoothService?.sendMessage(peerAddress, response)
        }
        Log.d(TAG, "Sent ${missingEvents.size} events to $peerAddress")
    }

    suspend fun handleEventSyncResponse(response: Message.EventSyncResponse, peerAddress: String) {
        Log.d(TAG, "Received ${response.events.size} events for author ${response.authorDid} from $peerAddress")
        if (response.events.isEmpty()) {
            Log.d(TAG, "No events in sync response from $peerAddress")
            return
        }
        response.events.forEachIndexed { i, dto ->
            Log.d(TAG, "Sync response event[$i]: eventId=${dto.eventId.take(40)}..., authorDid=${dto.authorDid}, type=${dto.eventType}, seq=${dto.sequenceNum}, signature(first 40)=${dto.signature.take(40)}...")
        }
        val entities = response.events.map { it.toEntity() }
        Log.d(TAG, "Forwarding ${entities.size} events to SyncCoordinator.processIncomingEvents")
        syncCoordinator.processIncomingEvents(entities)
    }

    /**
     * Handle PeerExchange message — save new peer addresses and attempt connections.
     * This enables multi-hop WAN discovery: if A knows B and B knows C,
     * A discovers C via B's PEX.
     */
    fun handlePeerExchange(pex: Message.PeerExchange, peerAddress: String) {
        Log.d(TAG, "Received PEX from $peerAddress with ${pex.addresses.size} addresses")
        val myPeerId = libP2pNode?.let { node ->
            try {
                node.listenAddresses.firstOrNull()?.let { addr ->
                    val parts = addr.split("/")
                    val idx = parts.indexOf("p2p")
                    if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
                }
            } catch (_: Exception) { null }
        }
        for (addrStr in pex.addresses) {
            if (addrStr.contains("/p2p/")) {
                // Skip self-addresses
                if (myPeerId != null && addrStr.contains("/p2p/$myPeerId")) continue
                libP2pNode?.savePeer(addrStr)
            }
        }
    }

    /**
     * Send our known peers' addresses (PEX) to a newly connected peer.
     */
    private suspend fun sendPeerExchange(peerAddress: String) {
        val node = libP2pNode ?: return
        val myPeerId = try {
            node.listenAddresses.firstOrNull()?.let { addr ->
                val parts = addr.split("/")
                val idx = parts.indexOf("p2p")
                if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
            } ?: return
        } catch (_: Exception) { return }

        val addresses = node.listenAddresses.filter { !it.contains("/p2p/$myPeerId") }
        val savedPeers = node.getSavedPeers().filter { !it.contains("/p2p/$myPeerId") }
        val allAddresses = (addresses + savedPeers).distinct()
        if (allAddresses.isEmpty()) return

        val pex = Message.PeerExchange(
            senderPeerId = identityManager.getOrCreateIdentity().did,
            addresses = allAddresses
        )
        val internetPeers = libP2pService?.getConnectedPeerAddresses() ?: emptyList()
        if (internetPeers.contains(peerAddress)) {
            libP2pService?.sendMessage(peerAddress, pex)
        } else {
            bluetoothService?.sendMessage(peerAddress, pex)
        }
        Log.d(TAG, "Sent PEX with ${allAddresses.size} addresses to $peerAddress")
    }

    /**
     * Re-broadcast all locally-created posts to a newly connected peer.
     * This is critical because at post creation time there may have been
     * zero connected peers, leaving posts orphaned in the local DB.
     */
    private suspend fun broadcastAllLocalPostsTo(peerAddress: String) {
        val myDid = identityManager.getOrCreateIdentity().did
        val myDeviceId = cryptoManager.getDeviceId()
        val myPosts = postRepository.getPostsByAuthorList(myDid, myDeviceId)
        if (myPosts.isEmpty()) return
        Log.d(TAG, "Rebroadcasting ${myPosts.size} local posts to new peer $peerAddress")
        for (post in myPosts) {
            gossipProtocol.broadcastPostToPeer(post, peerAddress)
        }
    }

    private fun startLibP2pMessageRouting(service: com.neo.transport.TransportPort) {
        libP2pRoutingJob?.cancel()
        libP2pRoutingJob = scope.launch {
            try {
                service.allIncomingMessages.collect { (peerAddress, message) ->
                    when (message) {
                        is Message.Handshake -> {
                            gossipProtocol.handleHandshake(message, peerAddress, "libp2p")
                            scope.launch { syncWithLibP2pPeer(service, peerAddress) }
                        }
                        is Message.PostBroadcast -> {
                            gossipProtocol.handleReceivedPost(message, peerAddress)
                            sendAckViaTp(service, peerAddress, message.id, "PostBroadcast")
                        }
                        is Message.CommentBroadcast -> {
                            gossipProtocol.handleReceivedComment(message, peerAddress)
                            sendAckViaTp(service, peerAddress, message.id, "CommentBroadcast")
                        }
                        is Message.ReactionBroadcast -> {
                            gossipProtocol.handleReceivedReaction(message, peerAddress)
                            sendAckViaTp(service, peerAddress, message.id, "ReactionBroadcast")
                        }
                        is Message.ImageMetadata -> {
                            gossipProtocol.handleImageMetadata(message, peerAddress)
                            sendAckViaTp(service, peerAddress, "${message.postId}:img:meta", "ImageMetadata")
                        }
                        is Message.ImageChunk -> {
                            gossipProtocol.handleImageChunk(message, peerAddress)
                            sendAckViaTp(service, peerAddress, "${message.postId}:img:${message.chunkIndex}", "ImageChunk")
                        }
                        is Message.Ack -> gossipProtocol.handleAck(message)
                        is Message.EventSyncRequest -> handleEventSyncRequest(message, peerAddress)
                        is Message.EventSyncResponse -> handleEventSyncResponse(message, peerAddress)
                        is Message.PeerExchange -> handlePeerExchange(message, peerAddress)
                        is Message.ProfileBroadcast -> {
                            gossipProtocol.handleReceivedProfile(message, peerAddress)
                            sendAckViaTp(service, peerAddress, "${message.did}:profile:${message.updatedAt}", "ProfileBroadcast")
                        }
                        is Message.FollowBroadcast -> {
                            gossipProtocol.handleReceivedFollow(message, peerAddress)
                            sendAckViaTp(service, peerAddress, "${message.id}:${message.timestamp}", "FollowBroadcast")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LibP2p message routing crashed, restarting in 3s", e)
                delay(3000)
                startLibP2pMessageRouting(service)
            }
        }
    }

    private suspend fun sendAckViaTp(
        service: com.neo.transport.TransportPort,
        peerAddress: String,
        messageId: String,
        messageType: String
    ) {
        service.sendMessage(peerAddress, Message.Ack(messageId, messageType, true))
    }

    private fun startLibP2pPeerMonitor(service: com.neo.transport.TransportPort) {
        libP2pPeerMonitorJob?.cancel()
        libP2pPeerMonitorJob = scope.launch {
            var knownPeers = emptySet<String>()
            service.connectedPeers.collect { peers ->
                val currentPeers = peers.toSet()
                val btCount = bluetoothService?.getConnectedPeerAddresses()?.size ?: 0
                _connectedPeersCount.value = btCount + currentPeers.size
                currentPeers.minus(knownPeers).forEach { peerId ->
                    gossipProtocol.sendHandshake(peerId)
                    scope.launch { sendPeerExchange(peerId) }
                    scope.launch { broadcastAllLocalPostsTo(peerId) }
                    scope.launch { gossipProtocol.broadcastProfileToPeer(peerId) }
                    scope.launch { gossipProtocol.broadcastFollowsToPeer(peerId) }
                }
                knownPeers = currentPeers
            }
        }
    }

    private suspend fun syncWithLibP2pPeer(
        service: com.neo.transport.TransportPort,
        peerId: String
    ) {
        val myDid = identityManager.getOrCreateIdentity().did
        val lastSyncTimestamp = eventLogDao.getMaxTimestamp() ?: 0L
        Log.d(TAG, "Requesting event sync via libp2p from $peerId (since ts=$lastSyncTimestamp)")
        val request = Message.EventSyncRequest(
            requesterDid = myDid,
            lastKnownTimestamp = lastSyncTimestamp
        )
        service.sendMessage(peerId, request)
    }
}
