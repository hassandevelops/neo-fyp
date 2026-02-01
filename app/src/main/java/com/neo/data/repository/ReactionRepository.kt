package com.neo.data.repository

import com.neo.data.dao.ReactionDao
import com.neo.data.model.Reaction
import com.neo.data.model.ReactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing reactions.
 * Provides clean abstraction over ReactionDao.
 */
@Singleton
class ReactionRepository @Inject constructor(
    private val reactionDao: ReactionDao
) {
    
    suspend fun insertReaction(reaction: Reaction) {
        reactionDao.insert(reaction)
    }
    
    suspend fun insertReactions(reactions: List<Reaction>) {
        reactionDao.insertAll(reactions)
    }
    
    fun getReactionsForPost(postId: String, type: ReactionType): Flow<List<Reaction>> {
        return reactionDao.getReactionsForPost(postId, type)
    }
    
    fun getReactionCountForPost(postId: String, type: ReactionType): Flow<Int> {
        return reactionDao.getReactionCountForPost(postId, type)
    }
    
    suspend fun hasUserReacted(postId: String, userId: String, type: ReactionType): Boolean {
        return reactionDao.hasUserReacted(postId, userId, type)
    }
    
    fun hasUserReactedFlow(postId: String, userId: String, type: ReactionType): Flow<Boolean> {
        return reactionDao.hasUserReactedFlow(postId, userId, type)
    }
    
    suspend fun getReactionById(reactionId: String): Reaction? {
        return reactionDao.getReactionById(reactionId)
    }
    
    suspend fun reactionExists(reactionId: String): Boolean {
        return reactionDao.reactionExists(reactionId)
    }
    
    suspend fun deleteReaction(postId: String, userId: String, type: ReactionType) {
        reactionDao.deleteReaction(postId, userId, type)
    }
    
    suspend fun deleteReactionsForPost(postId: String) {
        reactionDao.deleteReactionsForPost(postId)
    }
    
    suspend fun deleteOldReactions(beforeTimestamp: Long, excludeUserId: String? = null): Int {
        return if (excludeUserId != null) {
            reactionDao.deleteOldReactionsExcludingUser(beforeTimestamp, excludeUserId)
        } else {
            reactionDao.deleteOldReactions(beforeTimestamp)
        }
    }
    
    suspend fun getTotalReactionCount(): Int {
        return reactionDao.getTotalReactionCount()
    }
}
