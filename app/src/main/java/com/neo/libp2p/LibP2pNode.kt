package com.neo.libp2p

import android.content.Context
import android.util.Log
import com.neo.bluetooth.Message
import com.neo.bluetooth.MessageProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Delegates to GoLibP2pNode (go-libp2p binary via local TCP).
 * This class exists so SyncManager/GossipProtocol continue working
 * without changes — they reference LibP2pNode directly.
 */
class LibP2pNode(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceId: String,
    private val publicKeyBase64: String
) {
    companion object {
        private const val TAG = "LibP2pNode"
        const val NEO_PROTOCOL_ID = "/neo/gossip/1.0.0"
        const val NEO_RENDEZVOUS = "neo-social-network-v1"
        const val LISTEN_PORT = 9876
    }

    private val impl = GoLibP2pNode(context, scope, deviceId, publicKeyBase64)

    val connectedPeers: StateFlow<List<String>> get() = impl.connectedPeers
    val incomingMessages: Flow<Pair<String, Message>> get() = impl.incomingMessages

    fun start() = impl.start()
    fun stop() = impl.stop()

    suspend fun sendToPeer(peerId: String, message: Message): Boolean =
        impl.sendToPeer(peerId, message)

    suspend fun broadcastToPeers(message: Message, excludePeer: String? = null) {
        impl.broadcastToPeers(message, excludePeer)
    }

    suspend fun publishToTopic(message: Message): Boolean =
        impl.publishToTopic(message)

    val listenAddresses: List<String>
        get() = impl.listenAddresses

    fun addPeerByMultiaddr(multiaddrStr: String) =
        impl.addPeerByMultiaddr(multiaddrStr)

    fun savePeer(multiaddrStr: String) = impl.savePeer(multiaddrStr)
    fun getSavedPeers(): List<String> = impl.getSavedPeers()
}
