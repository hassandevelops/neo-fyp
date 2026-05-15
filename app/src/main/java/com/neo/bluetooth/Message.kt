package com.neo.bluetooth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents different types of messages exchanged between peers.
 */
@Serializable
sealed class Message {

    /**
     * Initial handshake when connecting to a peer.
     */
    @Serializable
    @SerialName("handshake")
    data class Handshake(
        val deviceId: String,
        val deviceName: String,
        val publicKey: String
    ) : Message()

    /**
     * Broadcast a single post to a peer.
     */
    @Serializable
    @SerialName("post_broadcast")
    data class PostBroadcast(
        val id: String,
        val authorId: String,
        val authorName: String,
        val content: String,
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val ttl: Int,
        val imageHash: String? = null,
        val imageSize: Int? = null,
        val imageWidth: Int? = null,
        val imageHeight: Int? = null
    ) : Message()

    /**
     * Request posts newer than a specific timestamp.
     */
    @Serializable
    @SerialName("sync_request")
    data class SyncRequest(
        val lastTimestamp: Long
    ) : Message()

    /**
     * Response to sync request with list of posts.
     */
    @Serializable
    @SerialName("sync_response")
    data class SyncResponse(
        val posts: List<PostBroadcast>
    ) : Message()

    /**
     * Acknowledgment message.
     */
    @Serializable
    @SerialName("ack")
    data class Ack(
        val messageId: String,
        val messageType: String = "unknown",
        val success: Boolean = true
    ) : Message()

    /**
     * Metadata about an image being transmitted.
     * Sent before image chunks to prepare receiver.
     */
    @Serializable
    @SerialName("image_metadata")
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
    @Serializable
    @SerialName("image_chunk")
    data class ImageChunk(
        val postId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val data: String,
        val checksum: String
    ) : Message()

    /**
     * Comment broadcast message.
     * Propagates comments through the network.
     */
    @Serializable
    @SerialName("comment_broadcast")
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

    @Serializable
    @SerialName("reaction_broadcast")
    data class ReactionBroadcast(
        val id: String,
        val postId: String,
        val userId: String,
        val userName: String,
        val type: String,
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val ttl: Int
    ) : Message()
}
