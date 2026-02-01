package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.Reaction
import com.neo.data.model.ReactionType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Reaction entities.
 * Provides methods for CRUD operations and queries.
 */
@Dao
interface ReactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reaction: Reaction)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reactions: List<Reaction>)
    
    /**
     * Get all reactions of a specific type for a post.
     */
    @Query("SELECT * FROM reactions WHERE postId = :postId AND type = :type ORDER BY timestamp DESC")
    fun getReactionsForPost(postId: String, type: ReactionType): Flow<List<Reaction>>
    
    /**
     * Get count of reactions of a specific type for a post.
     */
    @Query("SELECT COUNT(*) FROM reactions WHERE postId = :postId AND type = :type")
    fun getReactionCountForPost(postId: String, type: ReactionType): Flow<Int>
    
    /**
     * Check if a user has reacted to a post with a specific reaction type.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM reactions WHERE postId = :postId AND userId = :userId AND type = :type)")
    suspend fun hasUserReacted(postId: String, userId: String, type: ReactionType): Boolean
    
    /**
     * Check if a user has reacted to a post with a specific reaction type (Flow version).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM reactions WHERE postId = :postId AND userId = :userId AND type = :type)")
    fun hasUserReactedFlow(postId: String, userId: String, type: ReactionType): Flow<Boolean>
    
    /**
     * Get a specific reaction by ID.
     */
    @Query("SELECT * FROM reactions WHERE id = :reactionId")
    suspend fun getReactionById(reactionId: String): Reaction?
    
    /**
     * Check if a reaction exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM reactions WHERE id = :reactionId)")
    suspend fun reactionExists(reactionId: String): Boolean
    
    /**
     * Delete a specific reaction.
     */
    @Query("DELETE FROM reactions WHERE postId = :postId AND userId = :userId AND type = :type")
    suspend fun deleteReaction(postId: String, userId: String, type: ReactionType)
    
    /**
     * Delete all reactions for a post.
     */
    @Query("DELETE FROM reactions WHERE postId = :postId")
    suspend fun deleteReactionsForPost(postId: String)
    
    /**
     * Delete old reactions before a certain timestamp.
     */
    @Query("DELETE FROM reactions WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldReactions(beforeTimestamp: Long): Int
    
    /**
     * Delete old reactions excluding a specific user.
     */
    @Query("DELETE FROM reactions WHERE timestamp < :beforeTimestamp AND userId != :excludeUserId")
    suspend fun deleteOldReactionsExcludingUser(beforeTimestamp: Long, excludeUserId: String): Int
    
    /**
     * Get total reaction count.
     */
    @Query("SELECT COUNT(*) FROM reactions")
    suspend fun getTotalReactionCount(): Int
    
    /**
     * Delete all reactions.
     */
    @Query("DELETE FROM reactions")
    suspend fun deleteAll()
}
