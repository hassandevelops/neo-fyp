package com.neo.domain.usecase

import android.util.Log
import com.neo.data.model.Comment
import com.neo.data.repository.CommentRepository
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import com.neo.sync.GossipProtocol
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for creating comments on posts.
 * Handles validation, signing, rate limiting, and propagation.
 */
@Singleton
class CreateCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository,
    private val cryptoManager: CryptoManager,
    private val gossipProtocol: GossipProtocol,
    private val rateLimiter: RateLimiter
) {
    
    companion object {
        private const val TAG = "CreateCommentUseCase"
        private const val MAX_COMMENT_LENGTH = 500
        private const val MIN_COMMENT_LENGTH = 1
        private const val COMMENT_TTL = 5 // Lower TTL than posts
    }
    
    /**
     * Create a new comment.
     * 
     * @param postId ID of the post to comment on
     * @param content Comment text content
     * @param authorName Display name of the author
     * @param parentCommentId Optional ID of parent comment for replies
     * @return Result containing the created Comment or error
     */
    suspend operator fun invoke(
        postId: String,
        content: String,
        authorName: String,
        parentCommentId: String? = null
    ): Result<Comment> {
        return try {
            // Get device ID for rate limiting
            val deviceId = cryptoManager.getDeviceId()
            
            // Check rate limit (20 comments per hour)
            if (!rateLimiter.canCreateComment(deviceId)) {
                val timeUntil = rateLimiter.getTimeUntilNextComment(deviceId)
                val minutesUntil = (timeUntil / 60000).toInt()
                return Result.failure(
                    IllegalStateException(
                        "Rate limit exceeded. Please wait $minutesUntil minutes before commenting again."
                    )
                )
            }
            
            // Validate content
            val trimmedContent = content.trim()
            if (trimmedContent.length < MIN_COMMENT_LENGTH) {
                return Result.failure(IllegalArgumentException("Comment cannot be empty"))
            }
            if (trimmedContent.length > MAX_COMMENT_LENGTH) {
                return Result.failure(
                    IllegalArgumentException("Comment exceeds maximum length of $MAX_COMMENT_LENGTH characters")
                )
            }
            
            // Generate unique ID
            val commentId = UUID.randomUUID().toString()
            
            // Get author ID and public key
            val authorId = cryptoManager.getDeviceId()
            val publicKey = cryptoManager.getPublicKey()
            
            // Create message to sign
            val timestamp = System.currentTimeMillis()
            val message = createCommentMessage(
                id = commentId,
                postId = postId,
                parentCommentId = parentCommentId,
                authorId = authorId,
                content = trimmedContent,
                timestamp = timestamp
            )
            
            // Sign the comment
            val signature = cryptoManager.sign(message)
            
            // Create comment object
            val comment = Comment(
                id = commentId,
                postId = postId,
                parentCommentId = parentCommentId,
                authorId = authorId,
                authorName = authorName,
                content = trimmedContent,
                timestamp = timestamp,
                signature = signature,
                publicKey = publicKey,
                ttl = COMMENT_TTL,
                firstSeenTimestamp = timestamp
            )
            
            // Save to local database
            commentRepository.insertComment(comment)
            
            // Broadcast to peers
            gossipProtocol.broadcastComment(comment)
            
            Log.i(TAG, "Comment created successfully: $commentId on post $postId")
            Result.success(comment)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create comment", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create the message string for signing.
     */
    private fun createCommentMessage(
        id: String,
        postId: String,
        parentCommentId: String?,
        authorId: String,
        content: String,
        timestamp: Long
    ): String {
        return "$id|$postId|${parentCommentId ?: ""}|$authorId|$content|$timestamp"
    }
}
