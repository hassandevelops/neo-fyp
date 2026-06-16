package com.neo.sync

import android.util.Log
import com.neo.bluetooth.Message
import com.neo.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages message acknowledgments and retries for reliable delivery.
 * Implements exponential backoff for retries.
 */
@Singleton
class AckManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "AckManager"
        private const val MAX_RETRIES = 10
        private const val INITIAL_TIMEOUT_MS = 500L // 0.5 seconds
        private const val MAX_TIMEOUT_MS = 15000L // 15 seconds
    }

    /**
     * Represents a pending message awaiting acknowledgment.
     */
    private data class PendingMessage(
        val messageId: String,
        val message: Message,
        val peerAddress: String,
        val sendFunction: suspend (Message, String) -> Boolean,
        val onSuccess: (() -> Unit)? = null,
        val onFailure: ((String) -> Unit)? = null,
        var retryCount: Int = 0,
        var timeoutMs: Long = INITIAL_TIMEOUT_MS,
        var job: Job? = null
    )

    // Map of messageId to pending message
    private val pendingMessages = ConcurrentHashMap<String, PendingMessage>()
    
    /**
     * Send a message and track it for acknowledgment.
     * 
     * @param messageId Unique ID for this message
     * @param message The message to send
     * @param peerAddress Address of the peer to send to
     * @param sendFunction Function to actually send the message
     * @param onSuccess Callback when ACK received
     * @param onFailure Callback when max retries exceeded
     */
    fun sendWithAck(
        messageId: String,
        message: Message,
        peerAddress: String,
        sendFunction: suspend (Message, String) -> Boolean,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        val pending = PendingMessage(
            messageId = messageId,
            message = message,
            peerAddress = peerAddress,
            sendFunction = sendFunction,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
        
        val previous = pendingMessages.put(messageId, pending)
        if (previous != null) {
            Log.w(TAG, "Overwriting pending messageId=$messageId (prev peer=${previous.peerAddress}, new peer=$peerAddress)")
        }

        scope.launch {
            try {
                val sent = pending.sendFunction(pending.message, pending.peerAddress)
                Log.d(TAG, "sendWithAck: $messageId initial send result=$sent")
                if (!sent) {
                    Log.w(TAG, "sendWithAck: $messageId initial send FAILED, peer=${pending.peerAddress}, scheduling retry in ${pending.timeoutMs}ms")
                }
                // Schedule a retry regardless: a successful socket write is not an
                // ACK. The retry job is cancelled in handleAck() once the peer
                // confirms receipt, so this only re-sends if no ACK arrives.
                scheduleRetry(pending)
            } catch (e: Exception) {
                pendingMessages.remove(messageId)
                pending.onFailure?.invoke("Initial send failed: ${e.message}")
                Log.e(TAG, "sendWithAck: $messageId initial send exception", e)
            }
        }
        
        Log.d(TAG, "Tracking message $messageId for ACK")
    }
    
    /**
     * Handle received acknowledgment.
     * 
     * @param messageId ID of the acknowledged message
     * @return true if message was pending, false otherwise
     */
    fun handleAck(messageId: String): Boolean {
        val pending = pendingMessages.remove(messageId)
        
        if (pending != null) {
            // Cancel retry job
            pending.job?.cancel()
            
            // Call success callback
            pending.onSuccess?.invoke()
            
            Log.d(TAG, "ACK received for message $messageId")
            return true
        }
        
        Log.w(TAG, "Received ACK for unknown message $messageId")
        return false
    }
    
    /**
     * Schedule a retry for a pending message.
     */
    private fun scheduleRetry(pending: PendingMessage) {
        pending.job = scope.launch {
            delay(pending.timeoutMs)
            
            // Check if still pending (not acked)
            if (pendingMessages.containsKey(pending.messageId)) {
                retry(pending)
            }
        }
    }
    
    /**
     * Retry sending a message.
     */
    private suspend fun retry(pending: PendingMessage) {
        pending.retryCount++
        
        if (pending.retryCount > MAX_RETRIES) {
            // Max retries exceeded
            pendingMessages.remove(pending.messageId)
            
            val errorMsg = "Max retries ($MAX_RETRIES) exceeded for message ${pending.messageId}"
            Log.e(TAG, errorMsg)
            
            pending.onFailure?.invoke(errorMsg)
            return
        }
        
        // Exponential backoff
        pending.timeoutMs = minOf(pending.timeoutMs * 2, MAX_TIMEOUT_MS)
        
        Log.w(TAG, "Retrying message ${pending.messageId} (attempt ${pending.retryCount}/$MAX_RETRIES)")
        
        try {
            // Resend message
            pending.sendFunction(pending.message, pending.peerAddress)
            
            // Schedule next retry
            scheduleRetry(pending)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retry message ${pending.messageId}", e)
            
            // Remove from pending and call failure callback
            pendingMessages.remove(pending.messageId)
            pending.onFailure?.invoke("Retry failed: ${e.message}")
        }
    }
    
    /**
     * Cancel tracking for a message.
     */
    fun cancel(messageId: String) {
        val pending = pendingMessages.remove(messageId)
        pending?.job?.cancel()
        
        Log.d(TAG, "Cancelled tracking for message $messageId")
    }
    
    /**
     * Get count of pending messages.
     */
    fun getPendingCount(): Int {
        return pendingMessages.size
    }
    
    /**
     * Get pending message IDs.
     */
    fun getPendingMessageIds(): List<String> {
        return pendingMessages.keys.toList()
    }
    
    /**
     * Clear all pending messages.
     */
    fun clear() {
        pendingMessages.values.forEach { it.job?.cancel() }
        pendingMessages.clear()
        
        Log.d(TAG, "Cleared all pending messages")
    }
    
    /**
     * Clean up resources.
     */
    fun shutdown() {
        clear()
        
        Log.d(TAG, "AckManager shutdown")
    }
}
