package com.neo.sync

/**
 * Snapshot of the WAN (libp2p) networking state. Surfaced in the BLE Status
 * screen so the demonstrator and the FYP panel can see in real time which
 * connection paths are active.
 */
data class NetworkStatus(
    val bootstrapConnected: Boolean = false,
    val bootstrapPeerId: String? = null,
    val customBootstrapConnected: Boolean = false,
    val customBootstrapPeerId: String? = null,
    val dhtPeerCount: Int = 0,
    val connectedLibP2pPeers: List<String> = emptyList(),
    val gossipsubMeshPeers: List<String> = emptyList(),
    val autoRelayCircuitAddress: String? = null,
    val lastError: String? = null
)
