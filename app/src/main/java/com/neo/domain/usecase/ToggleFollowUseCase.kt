package com.neo.domain.usecase

import com.neo.data.model.Follow
import com.neo.data.preferences.UserPreferences
import com.neo.data.repository.FollowRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.IdentityManager
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follows/unfollows another user. The edge is signed with the local identity
 * (DID) key — peers verify it against the public key encoded in the follower's
 * DID — then stored locally and gossiped across the mesh.
 */
@Singleton
class ToggleFollowUseCase @Inject constructor(
    private val followRepository: FollowRepository,
    private val identityManager: IdentityManager,
    private val userPreferences: UserPreferences,
    private val syncPort: ISyncPort
) {
    suspend operator fun invoke(followeeDid: String, follow: Boolean): Result<Follow> {
        return try {
            val identity = identityManager.getOrCreateIdentity()
            val followerDid = identity.did
            if (followerDid == followeeDid) {
                return Result.failure(IllegalArgumentException("Cannot follow yourself"))
            }

            val id = followRepository.edgeId(followerDid, followeeDid)
            val timestamp = System.currentTimeMillis()
            val message = "$id|$followerDid|$followeeDid|$follow|$timestamp"
            val signature = signWithPrivateKey(message, identity.privateKey)
            val publicKey = Base64.getEncoder().encodeToString(identity.publicKey.encoded)

            val edge = Follow(
                id = id,
                followerDid = followerDid,
                followeeDid = followeeDid,
                active = follow,
                timestamp = timestamp,
                signature = signature,
                publicKey = publicKey
            )
            followRepository.upsert(edge)
            syncPort.broadcastFollow(edge, userPreferences.userName)
            Result.success(edge)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun signWithPrivateKey(message: String, privateKey: PrivateKey): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(message.toByteArray())
        return Base64.getEncoder().encodeToString(signature.sign())
    }
}
