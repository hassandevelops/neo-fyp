package com.neo.ui.util

import com.neo.data.model.PeerProfile

/**
 * Resolves the avatar image URI for a content author across the whole app.
 *
 * Posts are authored under the user's DID; the current user's own image lives in
 * UserPreferences while every other user's avatar arrives via profile-sync gossip
 * (keyed by DID in [profiles]). Comments/reactions are authored under deviceId, so
 * for those only the current user (whose id is in [selfIds]) resolves; others fall
 * back to the default avatar.
 *
 * @param authorId        the content author's id (DID for posts; deviceId for comments)
 * @param profiles        peer profiles keyed by DID (from profile sync)
 * @param selfIds         the current user's ids (DID + deviceId)
 * @param selfImageUri    the current user's local profile image
 * @return the image URI to display, or null to show the default avatar.
 */
fun resolveAvatar(
    authorId: String?,
    profiles: Map<String, PeerProfile>,
    selfIds: Set<String>,
    selfImageUri: String?,
): String? {
    if (authorId == null) return null
    if (authorId in selfIds) return selfImageUri
    return profiles[authorId]?.avatarPath
}
