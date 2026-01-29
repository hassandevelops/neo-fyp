package com.neo.bluetooth

/**
 * Represents different types of messages exchanged between peers.
 */
sealed class Message {
    
    /**
     * Initial handshake when connecting to a peer.
     */
    data class Handshake(
        val deviceId: String,
        val deviceName: String,
        val publicKey: String
    ) : Message()
    
    /**
     * Broadcast a single post to a peer.
     */
    data class PostBroadcast(
        val id: String,
        val authorId: String,
        val authorName: String,
        val content: String,
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val ttl: Int
    ) : Message()
    
    /**
     * Request posts newer than a specific timestamp.
     */
    data class SyncRequest(
        val lastTimestamp: Long
    ) : Message()
    
    /**
     * Response to sync request with list of posts.
     */
    data class SyncResponse(
        val posts: List<PostBroadcast>
    ) : Message()
    
    /**
     * Acknowledgment message.
     */
    data class Ack(
        val messageId: String
    ) : Message()
}
