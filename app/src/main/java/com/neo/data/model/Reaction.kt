package com.neo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a user's reaction to a post.
 * Supports LIKE, SHARE, and REPOST reactions.
 */
@Entity(
    tableName = "reactions",
    foreignKeys = [
        ForeignKey(
            entity = Post::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["postId", "userId", "type"], unique = true, name = "index_reactions_unique"),
        Index(value = ["postId"], name = "index_reactions_postId"),
        Index(value = ["timestamp"], name = "index_reactions_timestamp")
    ]
)
data class Reaction(
    @PrimaryKey
    val id: String,                    // UUID - globally unique identifier
    val postId: String,                // ID of the post being reacted to
    val userId: String,                // Device/User ID who reacted
    val userName: String,              // Display name of user
    val type: ReactionType,            // Type of reaction
    val timestamp: Long,               // Unix timestamp when reaction was created
    val signature: String,             // Ed25519 signature for authenticity
    val publicKey: String,             // User's public key for verification
    val ttl: Int,                      // Time-to-live for gossip propagation
    val firstSeenTimestamp: Long       // When this device first saw the reaction
)

/**
 * Types of reactions supported.
 */
enum class ReactionType {
    LIKE,      // Like/heart a post
    SHARE,     // Share post to own feed (future)
    REPOST     // Repost with comment (future)
}
