package com.neo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Latest-known profile metadata for a peer, identified by their DID.
 *
 * Unlike posts (append-only signed events), a profile is mutable current-state:
 * we keep only the newest version per DID, gossiped peer-to-peer with a
 * "latest-wins" rule based on [updatedAt]. The current device's own profile
 * lives in UserPreferences, not here.
 */
@Entity(tableName = "peer_profiles")
data class PeerProfile(
    @PrimaryKey
    val did: String,                  // Author DID (did:key) this profile belongs to
    val displayName: String,          // Display name
    val bio: String = "",             // Profile bio
    val avatarPath: String? = null,   // file:// path to the locally-cached avatar thumbnail
    val updatedAt: Long               // Author's own timestamp of last profile change (latest wins)
)
