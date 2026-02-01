package com.neo.data.repository

import com.neo.data.dao.BlockedUserDao
import com.neo.data.model.BlockedUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for BlockedUser data operations.
 */
@Singleton
class BlockedUserRepository @Inject constructor(
    private val blockedUserDao: BlockedUserDao
) {
    
    /**
     * Block a user.
     */
    suspend fun blockUser(userId: String, reason: String? = null) {
        val blockedUser = BlockedUser(
            blockedUserId = userId,
            blockedAt = System.currentTimeMillis(),
            reason = reason
        )
        blockedUserDao.insert(blockedUser)
    }
    
    /**
     * Unblock a user.
     */
    suspend fun unblockUser(userId: String) {
        blockedUserDao.delete(userId)
    }
    
    /**
     * Check if a user is blocked.
     */
    suspend fun isBlocked(userId: String): Boolean {
        return blockedUserDao.isBlocked(userId)
    }
    
    /**
     * Get all blocked users as a Flow.
     */
    fun getAllBlocked(): Flow<List<BlockedUser>> {
        return blockedUserDao.getAllBlocked()
    }
    
    /**
     * Get all blocked users (non-reactive).
     */
    suspend fun getAllBlockedList(): List<BlockedUser> {
        return blockedUserDao.getAllBlockedList()
    }
    
    /**
     * Get count of blocked users.
     */
    suspend fun getBlockedCount(): Int {
        return blockedUserDao.getBlockedCount()
    }
}
