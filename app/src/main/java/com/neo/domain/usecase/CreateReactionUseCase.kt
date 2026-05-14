package com.neo.domain.usecase

import com.neo.data.model.Reaction
import com.neo.data.model.ReactionType
import com.neo.data.repository.ReactionRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateReactionUseCase @Inject constructor(
    private val reactionRepository: ReactionRepository,
    private val cryptoManager: CryptoManager,
    private val syncPort: ISyncPort
) {
    
    companion object {
        private const val TAG = "CreateReactionUseCase"
        private const val REACTION_TTL = 5
    }
    
    suspend operator fun invoke(
        postId: String,
        userName: String,
        type: ReactionType = ReactionType.LIKE
    ): Result<Reaction> {
        return try {
            val userId = cryptoManager.getDeviceId()
            
            val alreadyReacted = reactionRepository.hasUserReacted(postId, userId, type)
            if (alreadyReacted) {
                return Result.failure(
                    IllegalStateException("You have already reacted to this post")
                )
            }
            
            val reactionId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val message = createReactionMessage(
                id = reactionId,
                postId = postId,
                userId = userId,
                type = type,
                timestamp = timestamp
            )
            
            val signature = cryptoManager.sign(message)
            val publicKey = cryptoManager.getPublicKey()
            
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
            
            reactionRepository.insertReaction(reaction)

            syncPort.broadcastReaction(reaction)

            Result.success(reaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
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