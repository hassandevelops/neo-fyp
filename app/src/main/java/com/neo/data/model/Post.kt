package com.neo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a post in the decentralized social network.
 * Posts are cryptographically signed to ensure authenticity.
 */
@Entity(tableName = "posts")
data class Post(
    @PrimaryKey
    val id: String,                    // UUID - globally unique identifier
    val authorId: String,              // Device/User UUID who created this post
    val authorName: String,            // Display name of the author
    val content: String,               // Post text content
    val imageUri: String? = null,      // Optional image attachment
    val timestamp: Long,               // Unix timestamp (milliseconds) when post was created
    val signature: String,             // Ed25519 signature of the post
    val publicKey: String,             // Author's public key for signature verification
    val ttl: Int = 7,                 // Time-to-live in hops (decrements on each forward)
    val firstSeenTimestamp: Long       // When this device first received the post
)
