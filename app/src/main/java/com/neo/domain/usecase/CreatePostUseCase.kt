package com.neo.domain.usecase

import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.security.CryptoManager
import com.neo.sync.GossipProtocol
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a new post.
 */
class CreatePostUseCase @Inject constructor(
    private val postRepository: PostRepository,
    private val cryptoManager: CryptoManager,
    private val gossipProtocol: GossipProtocol
) {
    
    companion object {
        const val MAX_CONTENT_LENGTH = 500
    }
    
    /**
     * Create and publish a new post.
     *
     * @param content The post content
     * @param authorName The author's display name
     * @param imageUri Optional image URI
     * @return Result with the created post or error
     */
    suspend operator fun invoke(
        content: String,
        authorName: String,
        imageUri: String? = null
    ): Result<Post> {
        return try {
            // Validate input
            if (content.isBlank()) {
                return Result.failure(IllegalArgumentException("Content cannot be empty"))
            }
            if (authorName.isBlank()) {
                return Result.failure(IllegalArgumentException("Author name cannot be empty"))
            }
            if (content.length > MAX_CONTENT_LENGTH) {
                return Result.failure(IllegalArgumentException("Content exceeds maximum length"))
            }
            
            // Get device ID and keys
            val deviceId = cryptoManager.getDeviceId()
            val publicKey = cryptoManager.getPublicKey()
            
            // Create message for signing
            val postId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val message = cryptoManager.createPostMessage(
                id = postId,
                authorId = deviceId,
                content = content,
                timestamp = timestamp
            )
            
            // Sign the post
            val signature = cryptoManager.sign(message)
            
            // Create post object
            val post = Post(
                id = postId,
                authorId = deviceId,
                authorName = authorName,
                content = content,
                imageUri = imageUri,
                timestamp = timestamp,
                signature = signature,
                publicKey = publicKey,
                ttl = 7,
                firstSeenTimestamp = timestamp
            )
            
            // Store locally
            postRepository.insertPost(post)
            
            // Broadcast to peers
            gossipProtocol.broadcastPost(post)
            
            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
