package com.neo.domain.usecase

import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.domain.port.IImageCompressor
import com.neo.domain.port.ISyncPort
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import java.util.UUID
import javax.inject.Inject

class CreatePostUseCase @Inject constructor(
    private val postRepository: PostRepository,
    private val cryptoManager: CryptoManager,
    private val rateLimiter: RateLimiter,
    private val imageFileStore: ImageFileStore,
    private val imageCompressor: IImageCompressor,
    private val syncPort: ISyncPort
) {
    
    companion object {
        const val MAX_CONTENT_LENGTH = 500
    }
    
    suspend operator fun invoke(
        content: String,
        authorName: String,
        imageUri: String? = null
    ): Result<Post> {
        return try {
            val deviceId = cryptoManager.getDeviceId()
            
            if (!rateLimiter.canCreatePost(deviceId)) {
                val timeUntil = rateLimiter.getTimeUntilNextPost(deviceId)
                val minutesUntil = (timeUntil / 60000).toInt()
                return Result.failure(
                    IllegalStateException(
                        "Rate limit exceeded. Please wait $minutesUntil minutes before posting again."
                    )
                )
            }
            
            if (content.isBlank()) {
                return Result.failure(IllegalArgumentException("Content cannot be empty"))
            }
            if (authorName.isBlank()) {
                return Result.failure(IllegalArgumentException("Author name cannot be empty"))
            }
            if (content.length > MAX_CONTENT_LENGTH) {
                return Result.failure(IllegalArgumentException("Content exceeds maximum length"))
            }
            
            var compressedImage: com.neo.domain.port.CompressedImageResult? = null
            if (imageUri != null) {
                compressedImage = imageCompressor.compress(imageUri)

                if (compressedImage == null) {
                    return Result.failure(IllegalArgumentException("Failed to compress image"))
                }

                imageFileStore.save(compressedImage.hash, compressedImage.data)
            }

            val publicKey = cryptoManager.getPublicKey()

            val postId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val message = cryptoManager.createPostMessage(
                id = postId,
                authorId = deviceId,
                content = content,
                timestamp = timestamp
            )

            val signature = cryptoManager.sign(message)

            val post = Post(
                id = postId,
                authorId = deviceId,
                authorName = authorName,
                content = content,
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
            
            postRepository.insertPost(post)

            syncPort.broadcastPost(post)

            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}