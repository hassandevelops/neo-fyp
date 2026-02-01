package com.neo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a blocked user in the Neo network.
 * Posts from blocked users are filtered and not displayed.
 */
@Entity(tableName = "blocked_users")
data class BlockedUser(
    @PrimaryKey
    val blockedUserId: String,      // Device/User ID that is blocked
    val blockedAt: Long,             // Timestamp when user was blocked
    val reason: String? = null       // Optional reason for blocking
)
