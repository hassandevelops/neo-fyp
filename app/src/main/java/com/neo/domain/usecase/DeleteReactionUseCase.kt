package com.neo.domain.usecase

import android.util.Log
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
    
    companion object {
        private const val TAG = "DeleteReactionUseCase"
    }
    
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
            Log.i(TAG, "Deleted reaction for post $postId")
            
            // Note: We don't broadcast deletion messages
            // Reactions will expire via TTL mechanism
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete reaction", e)
            Result.failure(e)
        }
    }
}
