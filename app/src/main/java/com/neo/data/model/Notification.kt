package com.neo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["timestamp"], name = "index_notifications_timestamp"),
        Index(value = ["isRead"], name = "index_notifications_isRead")
    ]
)
data class Notification(
    @PrimaryKey
    val id: String,
    val type: String,
    val message: String,
    val postId: String? = null,
    val authorName: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
    // DID of the actor/target for navigation: for "follow" it's the follower's
    // DID (→ their profile); reserved for mentions. Null for like/comment (use postId).
    val targetUserId: String? = null,
    // Actor DID, used to resolve the actor's avatar in the notification list/banner.
    val actorId: String? = null
)
