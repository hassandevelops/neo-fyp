package com.neo.sync

import android.util.Log
import com.neo.data.model.Post
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves conflicts when duplicate posts with different content are received.
 * Uses timestamp-based resolution (newest wins).
 */
@Singleton
class ConflictResolver @Inject constructor() {
    
    companion object {
        private const val TAG = "ConflictResolver"
    }
    
    /**
     * Conflict resolution result.
     */
    sealed class Resolution {
        data class KeepExisting(val post: Post) : Resolution()
        data class ReplaceWithNew(val post: Post) : Resolution()
        data class Invalid(val reason: String) : Resolution()
    }
    
    /**
     * Resolve conflict between existing and new post with same ID.
     * 
     * @param existing The existing post in database
     * @param new The newly received post
     * @return Resolution indicating which post to keep
     */
    fun resolve(existing: Post, new: Post): Resolution {
        // Sanity check: IDs must match
        if (existing.id != new.id) {
            return Resolution.Invalid("Post IDs don't match")
        }
        
        // If content is identical, no conflict
        if (existing.content == new.content &&
            existing.imageData == new.imageData) {
            Log.d(TAG, "No conflict for post ${existing.id} - content identical")
            return Resolution.KeepExisting(existing)
        }
        
        // Conflict detected - log it
        Log.w(TAG, "Conflict detected for post ${existing.id}")
        Log.w(TAG, "  Existing: timestamp=${existing.timestamp}, content=${existing.content.take(50)}")
        Log.w(TAG, "  New: timestamp=${new.timestamp}, content=${new.content.take(50)}")
        
        // Resolution strategy: Newest timestamp wins
        return if (new.timestamp > existing.timestamp) {
            Log.i(TAG, "Resolving conflict: Replacing with newer post")
            Resolution.ReplaceWithNew(new)
        } else if (new.timestamp < existing.timestamp) {
            Log.i(TAG, "Resolving conflict: Keeping existing newer post")
            Resolution.KeepExisting(existing)
        } else {
            // Same timestamp - use authorId as tiebreaker (lexicographic order)
            if (new.authorId > existing.authorId) {
                Log.i(TAG, "Resolving conflict: Same timestamp, using authorId tiebreaker (new wins)")
                Resolution.ReplaceWithNew(new)
            } else {
                Log.i(TAG, "Resolving conflict: Same timestamp, using authorId tiebreaker (existing wins)")
                Resolution.KeepExisting(existing)
            }
        }
    }
    
    /**
     * Check if two posts are in conflict (same ID, different content).
     */
    fun isConflict(post1: Post, post2: Post): Boolean {
        if (post1.id != post2.id) return false
        
        return post1.content != post2.content || 
               post1.imageData != post2.imageData
    }
    
    /**
     * Get conflict details for logging/debugging.
     */
    fun getConflictDetails(existing: Post, new: Post): String {
        return buildString {
            appendLine("Conflict Details for Post ID: ${existing.id}")
            appendLine("Existing Post:")
            appendLine("  Timestamp: ${existing.timestamp}")
            appendLine("  Author: ${existing.authorName} (${existing.authorId})")
            appendLine("  Content: ${existing.content}")
            appendLine("  Has Image: ${existing.imageData != null}")
            appendLine("New Post:")
            appendLine("  Timestamp: ${new.timestamp}")
            appendLine("  Author: ${new.authorName} (${new.authorId})")
            appendLine("  Content: ${new.content}")
            appendLine("  Has Image: ${new.imageData != null}")
        }
    }
}
