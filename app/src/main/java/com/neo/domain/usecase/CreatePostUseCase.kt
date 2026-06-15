package com.neo.domain.usecase

import android.util.Log
import com.neo.data.dao.EventLogDao
import com.neo.data.model.EventLog
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.domain.port.IImageCompressor
import com.neo.domain.port.ISyncPort
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
import com.neo.security.RateLimiter
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CreatePostUseCase @Inject constructor(
    private val postRepository: PostRepository,
    private val cryptoManager: CryptoManager,
    private val rateLimiter: RateLimiter,
    private val imageFileStore: ImageFileStore,
    private val imageCompressor: IImageCompressor,
    private val syncPort: ISyncPort,
    private val identityManager: IdentityManager,
    private val eventLogDao: EventLogDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sequenceMutex = Mutex()

    companion object {
        private const val TAG = "CreatePostUseCase"
        const val MAX_CONTENT_LENGTH = 500
    }
    
    suspend operator fun invoke(
        content: String,
        authorName: String,
        imageUri: String? = null,
        locationName: String? = null
    ): Result<Post> {
        return try {
            val identity = identityManager.getOrCreateIdentity()
            val authorDid = identity.did
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

            val publicKeyBase64 = Base64.getEncoder().encodeToString(identity.publicKey.encoded)
            val keyAlgorithm = cryptoManager.getKeyAlgorithmPublic()
            Log.d(TAG, "IdentityManager publicKey (first 40): ${publicKeyBase64.take(40)}...")
            Log.d(TAG, "keyAlgorithm: $keyAlgorithm, authorDid: $authorDid")

            val postId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val message = cryptoManager.createPostMessage(
                id = postId,
                authorId = authorDid,
                content = content,
                timestamp = timestamp
            )

            val signature = signWithPrivateKey(message, identity.privateKey)
            Log.d(TAG, "Post signature (first 40): ${signature.take(40)}...")

            // Compute payload JSON (does not depend on sequence number)
            val payloadJson = json.encodeToString(
                JsonObject.serializer(), buildJsonObject {
                    put("postId", postId)
                    put("authorName", authorName)
                    put("content", content)
                    put("imageHash", compressedImage?.hash)
                    put("imageSize", compressedImage?.sizeBytes)
                    put("imageWidth", compressedImage?.width)
                    put("imageHeight", compressedImage?.height)
                    put("imageData", compressedImage?.let { android.util.Base64.encodeToString(it.data, android.util.Base64.NO_WRAP) })
                    put("locationName", locationName)
                    put("ttl", 7)
                    put("signature", signature)
                    put("publicKey", publicKeyBase64)
                }
            )

            // Atomically assign sequence number and insert EventLog to prevent races
            Log.d(TAG, "IdentityManager DID: $authorDid")
            Log.d(TAG, "EventLog uses same identity as Post key: true")
            val (sequenceNum, eventId, eventSignature) = insertEventLogAtomically(
                authorDid = authorDid,
                payloadJson = payloadJson,
                timestamp = timestamp,
                identity = identity
            )

            val post = Post(
                id = postId,
                authorId = authorDid,
                authorName = authorName,
                content = content,
                imageHash = compressedImage?.hash,
                imageSize = compressedImage?.sizeBytes,
                imageWidth = compressedImage?.width,
                imageHeight = compressedImage?.height,
                locationName = locationName,
                timestamp = timestamp,
                signature = signature,
                publicKey = publicKeyBase64,
                keyAlgorithm = keyAlgorithm,
                ttl = 7,
                firstSeenTimestamp = timestamp,
                eventId = eventId,
                authorDid = authorDid,
                sequenceNum = sequenceNum,
                eventType = "CREATE_POST",
                eventSignature = eventSignature,
                eventPayload = payloadJson
            )
            
            postRepository.insertPost(post)
            Log.d(TAG, "EventLog inserted into DB")

            syncPort.broadcastPost(post)
            Log.d(TAG, "Post $postId broadcast triggered")

            Result.success(post)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertEventLogAtomically(
        authorDid: String,
        payloadJson: String,
        timestamp: Long,
        identity: com.neo.security.IdentityManager.Identity
    ): Triple<Long, String, String> = sequenceMutex.withLock {
        insertEventLogWhileLocked(authorDid, payloadJson, timestamp, identity)
    }

    private suspend fun insertEventLogWhileLocked(
        authorDid: String,
        payloadJson: String,
        timestamp: Long,
        identity: com.neo.security.IdentityManager.Identity
    ): Triple<Long, String, String> {
        while (true) {
            val lastSeq = eventLogDao.getLastSequenceNum(authorDid) ?: 0L
            val seq = lastSeq + 1L
            val rawId = "$payloadJson|$authorDid|$seq|$timestamp"
            val eId = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(rawId.toByteArray())
            )
            val eSig = signWithPrivateKey(eId, identity.privateKey)
            val eventLog = EventLog(
                eventId = eId, authorDid = authorDid,
                sequenceNum = seq, eventType = "CREATE_POST",
                payload = payloadJson, signature = eSig, timestamp = timestamp
            )
            val result = eventLogDao.insertEvent(eventLog)
            if (result != -1L) {
                return Triple(seq, eId, eSig)
            }
            Log.w(TAG, "Sequence collision for $authorDid @ $seq, retrying...")
        }
    }

    private fun signWithPrivateKey(message: String, privateKey: PrivateKey): String {
        try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(message.toByteArray())
            return Base64.getEncoder().encodeToString(signature.sign())
        } catch (e: Exception) {
            throw RuntimeException("Failed to sign with identity key", e)
        }
    }
}