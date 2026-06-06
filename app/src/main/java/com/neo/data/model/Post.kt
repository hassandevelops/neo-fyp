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
    val imageHash: String? = null,     // SHA-256 hash of image data for verification
    val imageSize: Int? = null,        // Size of compressed image in bytes
    val imageWidth: Int? = null,       // Image width in pixels
    val imageHeight: Int? = null,      // Image height in pixels
    val timestamp: Long,               // Unix timestamp (milliseconds) when post was created
    val signature: String,             // Cryptographic signature of the post
    val publicKey: String,             // Author's public key for signature verification
    val keyAlgorithm: String = "RSA", // Algorithm used to sign (preserved across sync)
    val ttl: Int = 7,                 // Time-to-live in hops (decrements on each forward)
    val firstSeenTimestamp: Long,      // When this device first received the post
    val isDeleted: Boolean = false,    // Tombstone flag for CRDT deletion
    val eventId: String? = null,       // SHA-256 event ID for event-sourced relay
    val authorDid: String? = null,     // Decentralized identifier (did:key) for event log relay
    val sequenceNum: Long? = null,     // Monotonically increasing per-author event counter
    val eventType: String? = null,     // "CREATE_POST" etc. for event log reconstruction
    val eventSignature: String? = null,// RSA signature of eventId for verification
    val eventPayload: String? = null   // JSON payload for event log reconstruction
)
