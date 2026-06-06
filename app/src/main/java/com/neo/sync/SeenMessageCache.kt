package com.neo.sync

import android.util.Log
import com.neo.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache for tracking seen messages to prevent duplicate processing.
 * Uses a 24-hour window for eviction.
 */
@Singleton
class SeenMessageCache @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SeenMessageCache"
        private const val CLEANUP_INTERVAL_MS = 3600000L // 1 hour
        private const val MESSAGE_TTL_MS = 86400000L // 24 hours
    }

    private val seenMessages = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Long>): Boolean {
                return size > 10_000
            }
        }
    )
    private var cleanupJob: Job? = null

    init {
        startCleanupJob()
    }

    /**
     * Start periodic cleanup job.
     */
    private fun startCleanupJob() {
        cleanupJob = scope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupExpiredMessages()
            }
        }
    }

    /**
     * Check if a message has been seen.
     * @param messageId The message ID to check
     * @return true if the message has been seen, false otherwise
     */
    fun contains(messageId: String): Boolean {
        return seenMessages.containsKey(messageId)
    }

    /**
     * Mark a message as seen.
     * @param messageId The message ID to add
     */
    fun add(messageId: String) {
        seenMessages[messageId] = System.currentTimeMillis()
    }

    /**
     * Check if not seen and add if not present.
     * @param messageId The message ID to check and add
     * @return true if the message was not seen before (is new), false otherwise
     */
    fun checkAndAdd(messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = seenMessages.putIfAbsent(messageId, now)
        val isNew = previous == null
        if (!isNew) Log.w(TAG, "Dropping duplicate message: $messageId")
        return isNew
    }

    /**
     * Clean up expired messages older than 24 hours.
     */
    private fun cleanupExpiredMessages() {
        val now = System.currentTimeMillis()
        val iterator = seenMessages.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > MESSAGE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    /**
     * Get the count of cached message IDs.
     */
    fun size(): Int = seenMessages.size

    /**
     * Clear all cached messages.
     */
    fun clear() {
        seenMessages.clear()
    }

    /**
     * Shutdown the cleanup job.
     */
    fun shutdown() {
        cleanupJob?.cancel()
    }
}