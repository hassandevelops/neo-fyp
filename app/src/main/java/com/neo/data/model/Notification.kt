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
    val isRead: Boolean = false
)
