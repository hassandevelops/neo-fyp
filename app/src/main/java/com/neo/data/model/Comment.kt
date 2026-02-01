package com.neo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a comment on a post in the Neo network.
 * Comments can be top-level or replies to other comments (nested).
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = Post::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["postId"], name = "index_comments_postId"),
        Index(value = ["parentCommentId"], name = "index_comments_parentCommentId"),
        Index(value = ["timestamp"], name = "index_comments_timestamp")
    ]
)
data class Comment(
    @PrimaryKey
    val id: String,                    // UUID - globally unique identifier
    val postId: String,                // ID of the post this comment belongs to
    val parentCommentId: String? = null, // ID of parent comment (null for top-level)
    val authorId: String,              // Device/User ID of comment author
    val authorName: String,            // Display name of author
    val content: String,               // Comment text content
    val timestamp: Long,               // Unix timestamp when comment was created
    val signature: String,             // Ed25519 signature for authenticity
    val publicKey: String,             // Author's public key for verification
    val ttl: Int,                      // Time-to-live for gossip propagation
    val firstSeenTimestamp: Long       // When this device first saw the comment
)
