package com.neo.domain.usecase

import com.neo.data.model.ReactionType
import com.neo.data.repository.ReactionRepository
import com.neo.security.CryptoManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for deleting a reaction from a post.
 * Removes the reaction locally (propagation handled by TTL expiry).
 */
@Singleton
class DeleteReactionUseCase @Inject constructor(
    private val reactionRepository: ReactionRepository,
    private val cryptoManager: CryptoManager
) {

    suspend operator fun invoke(
        postId: String,
        type: ReactionType = ReactionType.LIKE
    ): Result<Unit> {
        return try {
            val userId = cryptoManager.getDeviceId()

            // Check if user has reacted
            val hasReacted = reactionRepository.hasUserReacted(postId, userId, type)
            if (!hasReacted) {
                return Result.failure(
                    IllegalStateException("You have not reacted to this post")
                )
            }

            // Delete the reaction
            reactionRepository.deleteReaction(postId, userId, type)

            // Note: We don't broadcast deletion messages
            // Reactions will expire via TTL mechanism

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
