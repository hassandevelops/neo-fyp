package com.neo.transport

import com.neo.bluetooth.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over any Neo peer transport (BLE, WiFi Direct, libp2p, etc.)
 * All transports must expose this contract to plug into SyncManager and GossipProtocol.
 */
interface TransportPort {
    /** StateFlow of currently connected peer addresses/IDs */
    val connectedPeers: StateFlow<List<String>>

    /** Hot flow of all incoming (peerAddress, Message) pairs */
    val allIncomingMessages: Flow<Pair<String, Message>>

    /** Send a message to a specific peer. Returns true on success. */
    suspend fun sendMessage(peerId: String, message: Message): Boolean

    /** Broadcast to all connected peers, optionally excluding one. */
    suspend fun broadcastMessage(message: Message, excludePeer: String? = null)

    /** Returns current list of connected peer addresses/IDs. */
    fun getConnectedPeerAddresses(): List<String>
}