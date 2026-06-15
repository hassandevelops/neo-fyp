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
        val keyAlgorithm: String = "RSA",
        val ttl: Int,
        val imageHash: String? = null,
        val imageSize: Int? = null,
        val imageWidth: Int? = null,
        val imageHeight: Int? = null,
        val imageData: String? = null,
        val locationName: String? = null,
        val eventId: String = "",
        val authorDid: String = "",
        val sequenceNum: Long = 0L,
        val eventType: String = "CREATE_POST",
        val eventSignature: String = "",
        val eventPayload: String = "{}"
    ) : Message()

    /**
     * Request all events newer than a specific timestamp.
     */
    @Serializable
    @SerialName("event_sync_request")
    data class EventSyncRequest(
        val requesterDid: String,
        val lastKnownTimestamp: Long
    ) : Message()

    /**
     * Response to event sync request with list of event logs.
     */
    @Serializable
    @SerialName("event_sync_response")
    data class EventSyncResponse(
        val authorDid: String,
        val events: List<EventLogDto>
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
        val keyAlgorithm: String = "RSA",
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
        val keyAlgorithm: String = "RSA",
        val ttl: Int
    ) : Message()

    /**
     * Peer exchange message — shares known peer multiaddresses for WAN discovery.
     * Sent automatically after handshake to propagate the address book through the mesh.
     */
    @Serializable
    @SerialName("peer_exchange")
    data class PeerExchange(
        val senderPeerId: String,
        val addresses: List<String>     // Multiaddr strings
    ) : Message()

    /**
     * Profile metadata broadcast — propagates a peer's display name, bio, and a
     * small avatar thumbnail keyed by their DID. Mutable "latest-wins" state
     * (newest [updatedAt] supersedes), signed by the author's identity key.
     */
    @Serializable
    @SerialName("profile_broadcast")
    data class ProfileBroadcast(
        val did: String,
        val displayName: String,
        val bio: String = "",
        val avatarThumbBase64: String? = null,   // small JPEG thumbnail, base64 (NO_WRAP)
        val updatedAt: Long,
        val signature: String,
        val publicKey: String,
        val keyAlgorithm: String = "RSA",
        val ttl: Int
    ) : Message()

    /**
     * Follow edge broadcast — propagates a directed (un)follow, signed by the
     * follower's identity key. [active] = false revokes a prior follow.
     */
    @Serializable
    @SerialName("follow_broadcast")
    data class FollowBroadcast(
        val id: String,
        val followerDid: String,
        val followeeDid: String,
        val followerName: String,
        val active: Boolean,
        val timestamp: Long,
        val signature: String,
        val publicKey: String,
        val keyAlgorithm: String = "RSA",
        val ttl: Int
    ) : Message()
}
