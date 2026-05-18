package com.neo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_posts",
    indices = [
        Index(value = ["savedAt"], name = "index_saved_posts_savedAt")
    ]
)
data class SavedPost(
    @PrimaryKey
    val postId: String,
    val savedAt: Long = System.currentTimeMillis()
)
