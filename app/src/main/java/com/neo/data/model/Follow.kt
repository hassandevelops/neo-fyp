package com.neo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A directed follow edge: [followerDid] follows [followeeDid]. Signed by the
 * follower and gossiped peer-to-peer (mirrors the reaction propagation flow).
 *
 * [active] toggles follow/unfollow so the edge can be revoked without deletion
 * (latest [timestamp] wins). The unique index on (follower, followee) keeps a
 * single canonical edge per pair.
 */
@Entity(
    tableName = "follows",
    indices = [
        Index(value = ["followerDid", "followeeDid"], name = "index_follows_unique", unique = true),
        Index(value = ["followeeDid"], name = "index_follows_followeeDid"),
        Index(value = ["followerDid"], name = "index_follows_followerDid")
    ]
)
data class Follow(
    @PrimaryKey
    val id: String,                  // Stable edge id = "<followerDid>:<followeeDid>"
    val followerDid: String,
    val followeeDid: String,
    val active: Boolean = true,      // true = following, false = unfollowed (tombstone)
    val timestamp: Long,             // Author's timestamp of this edge change (latest wins)
    val signature: String,
    val publicKey: String
)
