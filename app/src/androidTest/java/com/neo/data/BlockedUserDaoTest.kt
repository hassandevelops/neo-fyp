package com.neo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.data.dao.BlockedUserDao
import com.neo.data.db.AppDatabase
import com.neo.data.model.BlockedUser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for BlockedUserDao.
 */
@RunWith(AndroidJUnit4::class)
class BlockedUserDaoTest {
    
    private lateinit var database: AppDatabase
    private lateinit var blockedUserDao: BlockedUserDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        blockedUserDao = database.blockedUserDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertBlockedUser_and_checkIsBlocked() = runBlocking {
        // Arrange
        val blockedUser = BlockedUser(
            blockedUserId = "user-123",
            blockedAt = System.currentTimeMillis(),
            reason = "Spam"
        )
        
        // Act
        blockedUserDao.insert(blockedUser)
        val isBlocked = blockedUserDao.isBlocked("user-123")
        
        // Assert
        assertTrue(isBlocked)
    }
    
    @Test
    fun isBlocked_returnsFalse_forNonBlockedUser() = runBlocking {
        // Act
        val isBlocked = blockedUserDao.isBlocked("non-existent-user")
        
        // Assert
        assertFalse(isBlocked)
    }
    
    @Test
    fun deleteBlockedUser_removesUser() = runBlocking {
        // Arrange
        val blockedUser = BlockedUser(
            blockedUserId = "user-456",
            blockedAt = System.currentTimeMillis()
        )
        blockedUserDao.insert(blockedUser)
        
        // Act
        blockedUserDao.delete("user-456")
        val isBlocked = blockedUserDao.isBlocked("user-456")
        
        // Assert
        assertFalse(isBlocked)
    }
    
    @Test
    fun getAllBlockedList_returnsAllBlockedUsers() = runBlocking {
        // Arrange
        val user1 = BlockedUser("user-1", System.currentTimeMillis())
        val user2 = BlockedUser("user-2", System.currentTimeMillis())
        val user3 = BlockedUser("user-3", System.currentTimeMillis())
        
        blockedUserDao.insert(user1)
        blockedUserDao.insert(user2)
        blockedUserDao.insert(user3)
        
        // Act
        val blockedUsers = blockedUserDao.getAllBlockedList()
        
        // Assert
        assertEquals(3, blockedUsers.size)
        assertTrue(blockedUsers.any { it.blockedUserId == "user-1" })
        assertTrue(blockedUsers.any { it.blockedUserId == "user-2" })
        assertTrue(blockedUsers.any { it.blockedUserId == "user-3" })
    }
    
    @Test
    fun getBlockedCount_returnsCorrectCount() = runBlocking {
        // Arrange
        assertEquals(0, blockedUserDao.getBlockedCount())
        
        blockedUserDao.insert(BlockedUser("user-1", System.currentTimeMillis()))
        blockedUserDao.insert(BlockedUser("user-2", System.currentTimeMillis()))
        
        // Act
        val count = blockedUserDao.getBlockedCount()
        
        // Assert
        assertEquals(2, count)
    }
    
    @Test
    fun insert_withReplace_updatesExistingUser() = runBlocking {
        // Arrange
        val user1 = BlockedUser("user-1", 1000L, "Original reason")
        val user2 = BlockedUser("user-1", 2000L, "Updated reason")
        
        // Act
        blockedUserDao.insert(user1)
        blockedUserDao.insert(user2)
        
        val blockedUsers = blockedUserDao.getAllBlockedList()
        
        // Assert
        assertEquals(1, blockedUsers.size)
        assertEquals("Updated reason", blockedUsers[0].reason)
        assertEquals(2000L, blockedUsers[0].blockedAt)
    }
    
    @Test
    fun deleteAll_removesAllBlockedUsers() = runBlocking {
        // Arrange
        blockedUserDao.insert(BlockedUser("user-1", System.currentTimeMillis()))
        blockedUserDao.insert(BlockedUser("user-2", System.currentTimeMillis()))
        blockedUserDao.insert(BlockedUser("user-3", System.currentTimeMillis()))
        
        // Act
        blockedUserDao.deleteAll()
        val count = blockedUserDao.getBlockedCount()
        
        // Assert
        assertEquals(0, count)
    }
}
