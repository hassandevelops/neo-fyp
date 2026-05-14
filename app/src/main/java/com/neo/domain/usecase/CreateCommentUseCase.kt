package com.neo.domain.usecase

import com.neo.data.model.Comment
import com.neo.data.repository.CommentRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository,
    private val cryptoManager: CryptoManager,
    private val rateLimiter: RateLimiter,
    private val syncPort: ISyncPort
) {
    
    companion object {
        private const val TAG = "CreateCommentUseCase"
        private const val MAX_COMMENT_LENGTH = 500
        private const val MIN_COMMENT_LENGTH = 1
        private const val COMMENT_TTL = 5
    }
    
    suspend operator fun invoke(
        postId: String,
        content: String,
        authorName: String,
        parentCommentId: String? = null
    ): Result<Comment> {
        return try {
            val deviceId = cryptoManager.getDeviceId()
            
            if (!rateLimiter.canCreateComment(deviceId)) {
                val timeUntil = rateLimiter.getTimeUntilNextComment(deviceId)
                val minutesUntil = (timeUntil / 60000).toInt()
                return Result.failure(
                    IllegalStateException(
                        "Rate limit exceeded. Please wait $minutesUntil minutes before commenting again."
                    )
                )
            }
            
            val trimmedContent = content.trim()
            if (trimmedContent.length < MIN_COMMENT_LENGTH) {
                return Result.failure(IllegalArgumentException("Comment cannot be empty"))
            }
            if (trimmedContent.length > MAX_COMMENT_LENGTH) {
                return Result.failure(
                    IllegalArgumentException("Comment exceeds maximum length of $MAX_COMMENT_LENGTH characters")
                )
            }
            
            val commentId = UUID.randomUUID().toString()
            val authorId = cryptoManager.getDeviceId()
            val publicKey = cryptoManager.getPublicKey()
            
            val timestamp = System.currentTimeMillis()
            val message = createCommentMessage(
                id = commentId,
                postId = postId,
                parentCommentId = parentCommentId,
                authorId = authorId,
                content = trimmedContent,
                timestamp = timestamp
            )
            
            val signature = cryptoManager.sign(message)
            
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
            
            commentRepository.insertComment(comment)

            syncPort.broadcastComment(comment)

            Result.success(comment)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
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