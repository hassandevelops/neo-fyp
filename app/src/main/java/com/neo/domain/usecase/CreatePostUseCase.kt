package com.neo.domain.usecase

import android.content.Context
import android.net.Uri
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.media.ImageCompressor
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import com.neo.sync.GossipProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a new post.
 */
class CreatePostUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
    private val cryptoManager: CryptoManager,
    private val gossipProtocol: GossipProtocol,
    private val rateLimiter: RateLimiter
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
            // Get device ID first for rate limiting
            val deviceId = cryptoManager.getDeviceId()
            
            // Check rate limit
            if (!rateLimiter.canCreatePost(deviceId)) {
                val timeUntil = rateLimiter.getTimeUntilNextPost(deviceId)
                val minutesUntil = (timeUntil / 60000).toInt()
                return Result.failure(
                    IllegalStateException(
                        "Rate limit exceeded. Please wait $minutesUntil minutes before posting again."
                    )
                )
            }
            
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
            
            // Compress image if provided
            var compressedImage: ImageCompressor.CompressedImage? = null
            if (imageUri != null) {
                val imageCompressor = ImageCompressor(context)
                compressedImage = imageCompressor.compressImage(Uri.parse(imageUri))
                
                if (compressedImage == null) {
                    return Result.failure(IllegalArgumentException("Failed to compress image"))
                }
            }
            
            // Get public key
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
                imageData = compressedImage?.data,
                imageHash = compressedImage?.hash,
                imageSize = compressedImage?.sizeBytes,
                imageWidth = compressedImage?.width,
                imageHeight = compressedImage?.height,
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
