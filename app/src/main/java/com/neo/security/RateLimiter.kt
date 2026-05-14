package com.neo.security

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate limiter to prevent spam by limiting post creation rate.
 * Uses sliding window algorithm for accurate rate limiting.
 */
@Singleton
class RateLimiter @Inject constructor() {

    companion object {
        private const val TAG = "RateLimiter"
        private const val DEFAULT_POST_LIMIT = 10 // posts per hour
        private const val DEFAULT_COMMENT_LIMIT = 20 // comments per hour
        private const val DEFAULT_WINDOW_MS = 3600000L // 1 hour
        private const val CLEANUP_THRESHOLD = 100 // Cleanup when map exceeds this size

        // Inbound rate limits (from TRD.md)
        private const val INBOUND_POST_LIMIT = 30 // posts per hour from single peer
        private const val INBOUND_COMMENT_LIMIT = 20 // comments per hour from single peer
    }
    
    // Map of deviceId to list of timestamps
    // Separate tracking for posts and comments
    private val postTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val commentTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    // Inbound rate limiting - track received messages from peers
    private val inboundPostTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val inboundCommentTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    
    private var postLimit = DEFAULT_POST_LIMIT
    private var commentLimit = DEFAULT_COMMENT_LIMIT
    private var windowMs = DEFAULT_WINDOW_MS
    
    /**
     * Check if a device can create a post.
     * 
     * @param deviceId The device ID to check
     * @return true if allowed, false if rate limit exceeded
     */
    fun canCreatePost(deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = postTimestamps.getOrPut(deviceId) { mutableListOf() }
        
        // Remove timestamps outside the window
        timestamps.removeAll { it < now - windowMs }
        
        // Check if under limit
        val allowed = timestamps.size < postLimit
        
        if (allowed) {
            timestamps.add(now)
            Log.d(TAG, "Post allowed for $deviceId (${timestamps.size}/$postLimit)")
        } else {
            Log.w(TAG, "Post rate limit exceeded for $deviceId (${timestamps.size}/$postLimit)")
        }
        
        // Periodic cleanup
        if (postTimestamps.size + commentTimestamps.size > CLEANUP_THRESHOLD) {
            cleanup()
        }
        
        return allowed
    }
    
    /**
     * Check if a device can create a comment.
     */
    fun canCreateComment(deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = commentTimestamps.getOrPut(deviceId) { mutableListOf() }
        
        // Remove timestamps outside the window
        timestamps.removeAll { it < now - windowMs }
        
        // Check if under limit
        val allowed = timestamps.size < commentLimit
        
        if (allowed) {
            timestamps.add(now)
            Log.d(TAG, "Comment allowed for $deviceId (${timestamps.size}/$commentLimit)")
        } else {
            Log.w(TAG, "Comment rate limit exceeded for $deviceId (${timestamps.size}/$commentLimit)")
        }
        
        // Periodic cleanup
        if (postTimestamps.size + commentTimestamps.size > CLEANUP_THRESHOLD) {
            cleanup()
        }
        
        return allowed
    }
    
    /**
     * Get remaining posts allowed for a device.
     */
    fun getRemainingPosts(deviceId: String): Int {
        val now = System.currentTimeMillis()
        val timestamps = postTimestamps[deviceId] ?: return postLimit
        
        // Remove old timestamps
        timestamps.removeAll { it < now - windowMs }
        
        return maxOf(0, postLimit - timestamps.size)
    }
    
    /**
     * Get remaining comments allowed for a device.
     */
    fun getRemainingComments(deviceId: String): Int {
        val now = System.currentTimeMillis()
        val timestamps = commentTimestamps[deviceId] ?: return commentLimit
        
        // Remove old timestamps
        timestamps.removeAll { it < now - windowMs }
        
        return maxOf(0, commentLimit - timestamps.size)
    }
    
    /**
     * Get time until next post is allowed (in milliseconds).
     * Returns 0 if post is allowed now.
     */
    fun getTimeUntilNextPost(deviceId: String): Long {
        val now = System.currentTimeMillis()
        val timestamps = postTimestamps[deviceId] ?: return 0
        
        // Remove old timestamps
        timestamps.removeAll { it < now - windowMs }
        
        if (timestamps.size < postLimit) {
            return 0 // Can post now
        }
        
        // Find oldest timestamp in window
        val oldest = timestamps.minOrNull() ?: return 0
        val timeUntilExpiry = (oldest + windowMs) - now
        
        return maxOf(0, timeUntilExpiry)
    }
    
    /**
     * Get time until next comment is allowed (in milliseconds).
     * Returns 0 if comment is allowed now.
     */
    fun getTimeUntilNextComment(deviceId: String): Long {
        val now = System.currentTimeMillis()
        val timestamps = commentTimestamps[deviceId] ?: return 0
        
        // Remove old timestamps
        timestamps.removeAll { it < now - windowMs }
        
        if (timestamps.size < commentLimit) {
            return 0 // Can comment now
        }
        
        // Find oldest timestamp in window
        val oldest = timestamps.minOrNull() ?: return 0
        val timeUntilExpiry = (oldest + windowMs) - now
        
        return maxOf(0, timeUntilExpiry)
    }
    
    /**
     * Configure rate limits.
     * 
     * @param postsPerWindow Number of posts allowed per window
     * @param commentsPerWindow Number of comments allowed per window
     * @param windowMs Window size in milliseconds
     */
    fun configure(postsPerWindow: Int, commentsPerWindow: Int = DEFAULT_COMMENT_LIMIT, windowMs: Long = DEFAULT_WINDOW_MS) {
        this.postLimit = postsPerWindow
        this.commentLimit = commentsPerWindow
        this.windowMs = windowMs
        Log.d(TAG, "Rate limit configured: $postsPerWindow posts, $commentsPerWindow comments per ${windowMs}ms")
    }
    
    /**
     * Reset rate limit for a specific device.
     */
    fun reset(deviceId: String) {
        postTimestamps.remove(deviceId)
        commentTimestamps.remove(deviceId)
        Log.d(TAG, "Rate limit reset for $deviceId")
    }
    
    /**
     * Reset all rate limits.
     */
    fun resetAll() {
        postTimestamps.clear()
        commentTimestamps.clear()
        Log.d(TAG, "All rate limits reset")
    }
    
    /**
     * Clean up old entries to prevent memory leak.
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        
        // Clean up posts
        val postIterator = postTimestamps.entries.iterator()
        while (postIterator.hasNext()) {
            val entry = postIterator.next()
            entry.value.removeAll { it < now - windowMs }
            if (entry.value.isEmpty()) {
                postIterator.remove()
            }
        }
        
        // Clean up comments
        val commentIterator = commentTimestamps.entries.iterator()
        while (commentIterator.hasNext()) {
            val entry = commentIterator.next()
            entry.value.removeAll { it < now - windowMs }
            if (entry.value.isEmpty()) {
                commentIterator.remove()
            }
        }
        
        Log.d(TAG, "Cleanup complete. Active devices: ${postTimestamps.size} posts, ${commentTimestamps.size} comments")
    }
    
    /**
     * Get current configuration.
     */
    fun getConfig(): Triple<Int, Int, Long> {
        return Triple(postLimit, commentLimit, windowMs)
    }

    /**
     * Check if an inbound post from an author should be accepted.
     * Returns true if under limit, false if over limit (should be dropped).
     */
    fun canAcceptInboundPost(authorId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = inboundPostTimestamps.getOrPut(authorId) { mutableListOf() }

        timestamps.removeAll { it < now - windowMs }

        val allowed = timestamps.size < INBOUND_POST_LIMIT
        if (allowed) {
            timestamps.add(now)
        } else {
            Log.w(TAG, "Inbound post rate limit exceeded for $authorId (${timestamps.size}/$INBOUND_POST_LIMIT)")
        }

        return allowed
    }

    /**
     * Check if an inbound comment from an author should be accepted.
     * Returns true if under limit, false if over limit (should be dropped).
     */
    fun canAcceptInboundComment(authorId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = inboundCommentTimestamps.getOrPut(authorId) { mutableListOf() }

        timestamps.removeAll { it < now - windowMs }

        val allowed = timestamps.size < INBOUND_COMMENT_LIMIT
        if (allowed) {
            timestamps.add(now)
        } else {
            Log.w(TAG, "Inbound comment rate limit exceeded for $authorId (${timestamps.size}/$INBOUND_COMMENT_LIMIT)")
        }

        return allowed
    }

    /**
     * Check if an inbound reaction from an author should be accepted.
     * Uses the same limit as comments since they're similar in nature.
     */
    fun canAcceptInboundReaction(authorId: String): Boolean {
        return canAcceptInboundComment(authorId)
    }
}
