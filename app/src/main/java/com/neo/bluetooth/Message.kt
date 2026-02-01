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
        val messageId: String,
        val messageType: String = "unknown",  // Type of message being acked
        val success: Boolean = true            // Whether processing was successful
    ) : Message()
    
    /**
     * Metadata about an image being transmitted.
     * Sent before image chunks to prepare receiver.
     */
    data class ImageMetadata(
        val postId: String,
        val imageHash: String,
        val totalSize: Int,
        val totalChunks: Int,
        val width: Int,
        val height: Int
    ) : Message()
    
    /**
     * Image chunk data.
     */
    data class ImageChunk(
        val postId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val data: String,  // Base64 encoded chunk
        val checksum: String
    ) : Message()
    
    /**
     * Comment broadcast message.
     * Propagates comments through the network.
     */
    data class CommentBroadcast(
        val id: String,
        val postId: String,
        val parentCommentId: String?,
        val authorId: String,
        val authorName: String,
        val content: String,
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val ttl: Int
    ) : Message()
    
    data class ReactionBroadcast(
        val id: String,
        val postId: String,
        val userId: String,
        val userName: String,
        val type: String,  // ReactionType.name
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val ttl: Int
    ) : Message()
}
