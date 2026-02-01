package com.neo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a post in the decentralized social network.
 * Posts are cryptographically signed to ensure authenticity.
 */
@Entity(
    tableName = "posts",
    indices = [
        Index(value = ["timestamp"], name = "index_posts_timestamp"),
        Index(value = ["authorId"], name = "index_posts_authorId"),
        Index(value = ["firstSeenTimestamp"], name = "index_posts_firstSeenTimestamp")
    ]
)
data class Post(
    @PrimaryKey
    val id: String,                    // UUID - globally unique identifier
    val authorId: String,              // Device/User UUID who created this post
    val authorName: String,            // Display name of the author
    val content: String,               // Post text content
    val imageUri: String? = null,      // Optional local image URI (for own posts)
    val imageData: String? = null,     // Base64 encoded compressed image data (for transmission)
    val imageHash: String? = null,     // SHA-256 hash of image data for verification
    val imageSize: Int? = null,        // Size of compressed image in bytes
    val imageWidth: Int? = null,       // Image width in pixels
    val imageHeight: Int? = null,      // Image height in pixels
    val timestamp: Long,               // Unix timestamp (milliseconds) when post was created
    val signature: String,             // Ed25519 signature of the post
    val publicKey: String,             // Author's public key for signature verification
    val ttl: Int = 7,                 // Time-to-live in hops (decrements on each forward)
    val firstSeenTimestamp: Long       // When this device first received the post
)
