package com.neo.domain.usecase

import android.util.Log
import com.neo.data.model.Reaction
import com.neo.data.model.ReactionType
import com.neo.data.repository.ReactionRepository
import com.neo.security.CryptoManager
import com.neo.sync.GossipProtocol
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for creating a reaction to a post.
 * Handles signature generation and propagation.
 */
@Singleton
class CreateReactionUseCase @Inject constructor(
    private val reactionRepository: ReactionRepository,
    private val cryptoManager: CryptoManager,
    private val gossipProtocol: GossipProtocol
) {
    
    companion object {
        private const val TAG = "CreateReactionUseCase"
        private const val REACTION_TTL = 5 // Lower TTL than posts
    }
    
    suspend operator fun invoke(
        postId: String,
        userName: String,
        type: ReactionType = ReactionType.LIKE
    ): Result<Reaction> {
        return try {
            val userId = cryptoManager.getDeviceId()
            
            // Check if user already reacted
            val alreadyReacted = reactionRepository.hasUserReacted(postId, userId, type)
            if (alreadyReacted) {
                return Result.failure(
                    IllegalStateException("You have already reacted to this post")
                )
            }
            
            val reactionId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            // Create message for signing
            val message = createReactionMessage(
                id = reactionId,
                postId = postId,
                userId = userId,
                type = type,
                timestamp = timestamp
            )
            
            // Sign the reaction
            val signature = cryptoManager.sign(message)
            val publicKey = cryptoManager.getPublicKey()
            
            // Create reaction object
            val reaction = Reaction(
                id = reactionId,
                postId = postId,
                userId = userId,
                userName = userName,
                type = type,
                timestamp = timestamp,
                signature = signature,
                publicKey = publicKey,
                ttl = REACTION_TTL,
                firstSeenTimestamp = System.currentTimeMillis()
            )
            
            // Store locally
            reactionRepository.insertReaction(reaction)
            Log.i(TAG, "Created reaction $reactionId for post $postId")
            
            // Broadcast to network
            gossipProtocol.broadcastReaction(reaction)
            Log.d(TAG, "Broadcasted reaction $reactionId")
            
            Result.success(reaction)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create reaction", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create the message string for signing.
     */
    private fun createReactionMessage(
        id: String,
        postId: String,
        userId: String,
        type: ReactionType,
        timestamp: Long
    ): String {
        return "$id|$postId|$userId|${type.name}|$timestamp"
    }
}
