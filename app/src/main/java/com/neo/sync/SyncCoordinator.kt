package com.neo.sync

import android.util.Base64
import android.util.Log
import com.neo.data.dao.EventLogDao
import com.neo.data.dao.PostDao
import com.neo.data.model.EventLog
import com.neo.data.model.Post
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes incoming P2P events, verifies cryptographic signatures,
 * and safely updates the materialized local database views.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val identityManager: IdentityManager,
    private val cryptoManager: CryptoManager,
    private val eventLogDao: EventLogDao,
    private val postDao: PostDao,
    private val imageFileStore: ImageFileStore
) {
    companion object {
        private const val TAG = "SyncCoordinator"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Processes a batch of incoming events from the P2P network.
     * Guarantees idempotency and cryptographic safety.
     */
    suspend fun processIncomingEvents(events: List<EventLog>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Processing ${events.size} incoming events")
        for (event in events) {
            // 1. SECURITY: Verify the RSA signature
            val extractedKey = extractPublicKeyFromDid(event.authorDid)
            Log.d(TAG, "Verifying event ${event.eventId}: authorDid=${event.authorDid}, extractedKey(first 40)=${extractedKey.take(40)}...")
            Log.d(TAG, "Verification inputs: eventId=${event.eventId.take(40)}..., signature=${event.signature.take(40)}..., keyAlgorithm=RSA")

            val isValid = cryptoManager.verify(
                message = event.eventId,
                signatureBase64 = event.signature,
                publicKeyBase64 = extractedKey,
                keyAlgorithm = "RSA"
            )

            if (!isValid) {
                Log.w(TAG, "Dropping tampered or invalid event: ${event.eventId}")
                Log.w(TAG, "Signature verification FAILED for event ${event.eventId}. DID: ${event.authorDid}, key prefix: ${extractedKey.take(40)}")
                continue
            }

            Log.d(TAG, "Event ${event.eventId} signature VERIFIED OK")

            // A single malformed event (bad JSON payload, unexpected type) must not
            // abort processing of the rest of the batch — isolate failures per event.
            try {
                // 2. PERSISTENCE: Insert into append-only log (OnConflictStrategy.IGNORE handles duplicates)
                val insertedRowId = eventLogDao.insertEvent(event)
                Log.d(TAG, "Event ${event.eventId} inserted into event_log (rowId=$insertedRowId)")

                // 3. MATERIALIZATION: Update the fast-read Post table
                applyEventToMaterializedView(event)
            } catch (e: Exception) {
                Log.e(TAG, "Skipping event ${event.eventId}: failed to apply (${e.message})", e)
            }
        }
    }

    private suspend fun applyEventToMaterializedView(event: EventLog) {
        when (event.eventType) {
            "CREATE_POST" -> {
                val payload = json.decodeFromString<PostPayload>(event.payload)
                // Use the original UUID from the gossip path, not the eventId,
                // so posts arriving via gossip and event sync share the same ID
                val postSignature = payload.signature ?: event.signature
                val postPublicKey = payload.publicKey ?: extractPublicKeyFromDid(event.authorDid)
                postDao.insertOrUpdatePost(
                    Post(
                        id = payload.postId,
                        authorId = event.authorDid,
                        authorName = payload.authorName,
                        content = payload.content,
                        imageHash = payload.imageHash,
                        imageSize = payload.imageSize,
                        imageWidth = payload.imageWidth,
                        imageHeight = payload.imageHeight,
                        locationName = payload.locationName,
                        timestamp = event.timestamp,
                        signature = postSignature,
                        publicKey = postPublicKey,
                        keyAlgorithm = "RSA",
                        ttl = payload.ttl,
                        firstSeenTimestamp = event.timestamp
                    )
                )

                // Save embedded image data if present (newer post path).
                // Old posts without imageData will fall through to chunked assembly (if any chunks arrive).
                if (payload.imageHash != null && payload.imageData != null) {
                    try {
                        val imageBytes = Base64.decode(payload.imageData, Base64.NO_WRAP)
                        imageFileStore.save(payload.imageHash, imageBytes)
                        Log.d(TAG, "Saved embedded image for post ${payload.postId} (${imageBytes.size} bytes)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save embedded image for post ${payload.postId}", e)
                    }
                }
            }
            "DELETE_POST" -> {
                val payload = json.decodeFromString<DeletePayload>(event.payload)
                postDao.markPostAsDeleted(payload.targetPostId)
            }
            // Add "LIKE_POST", "COMMENT" etc. here in the future
        }
    }

    /**
     * Helper to extract the Base64 public key from a "did:key:base64url" string.
     */
    private fun extractPublicKeyFromDid(did: String): String {
        return did.removePrefix("did:key:")
            .replace('-', '+')
            .replace('_', '/')
    }

    // -----------------------------------------------------------------------------
    // Payload Data Classes
    // -----------------------------------------------------------------------------
    @Serializable
    data class PostPayload(
        val postId: String,
        val authorName: String,
        val content: String,
        val imageHash: String? = null,
        val imageSize: Int? = null,
        val imageWidth: Int? = null,
        val imageHeight: Int? = null,
        val imageData: String? = null,
        val locationName: String? = null,
        val ttl: Int = 7,
        val signature: String? = null,
        val publicKey: String? = null
    )

    @Serializable
    data class DeletePayload(val targetPostId: String)
}