package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.BlockedUser
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for BlockedUser operations.
 */
@Dao
interface BlockedUserDao {
    
    /**
     * Insert a blocked user.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedUser: BlockedUser)
    
    /**
     * Delete a blocked user (unblock).
     */
    @Query("DELETE FROM blocked_users WHERE blockedUserId = :userId")
    suspend fun delete(userId: String)
    
    /**
     * Check if a user is blocked.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE blockedUserId = :userId)")
    suspend fun isBlocked(userId: String): Boolean
    
    /**
     * Get all blocked users as a Flow.
     */
    @Query("SELECT * FROM blocked_users ORDER BY blockedAt DESC")
    fun getAllBlocked(): Flow<List<BlockedUser>>
    
    /**
     * Get all blocked users (non-reactive).
     */
    @Query("SELECT * FROM blocked_users")
    suspend fun getAllBlockedList(): List<BlockedUser>
    
    /**
     * Get count of blocked users.
     */
    @Query("SELECT COUNT(*) FROM blocked_users")
    suspend fun getBlockedCount(): Int
    
    /**
     * Delete all blocked users.
     */
    @Query("DELETE FROM blocked_users")
    suspend fun deleteAll()
}
