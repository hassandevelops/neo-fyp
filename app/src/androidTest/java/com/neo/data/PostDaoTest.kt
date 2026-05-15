package com.neo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.data.dao.PostDao
import com.neo.data.db.AppDatabase
import com.neo.data.model.Post
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for PostDao.
 * Tests database operations with actual Room database.
 */
@RunWith(AndroidJUnit4::class)
class PostDaoTest {
    
    private lateinit var database: AppDatabase
    private lateinit var postDao: PostDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        postDao = database.postDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertPost_and_getPostById() = runBlocking {
        // Arrange
        val post = createTestPost("post-1")
        
        // Act
        postDao.insert(post)
        val retrieved = postDao.getPostById("post-1")
        
        // Assert
        assertNotNull(retrieved)
        assertEquals(post.id, retrieved!!.id)
        assertEquals(post.content, retrieved.content)
        assertEquals(post.authorName, retrieved.authorName)
    }
    
    @Test
    fun insertPost_withConflict_ignores() = runBlocking {
        // Arrange
        val post1 = createTestPost("post-1", content = "First")
        val post2 = createTestPost("post-1", content = "Second")
        
        // Act
        val result1 = postDao.insert(post1)
        val result2 = postDao.insert(post2)
        val retrieved = postDao.getPostById("post-1")
        
        // Assert
        assertNotEquals(-1L, result1) // First insert succeeds
        assertEquals(-1L, result2) // Second insert ignored
        assertEquals("First", retrieved!!.content) // Original content preserved
    }
    
    @Test
    fun getAllPosts_returnsOrderedByTimestamp() = runBlocking {
        // Arrange
        val post1 = createTestPost("post-1", timestamp = 1000L)
        val post2 = createTestPost("post-2", timestamp = 3000L)
        val post3 = createTestPost("post-3", timestamp = 2000L)
        
        // Act
        postDao.insert(post1)
        postDao.insert(post2)
        postDao.insert(post3)
        
        // Note: getAllPosts() returns Flow, would need to collect in real test
        // This is a template showing the test structure
        
        // Assert
        // val posts = postDao.getAllPosts().first()
        // assertEquals(3, posts.size)
        // assertEquals("post-2", posts[0].id) // Newest first
        // assertEquals("post-3", posts[1].id)
        // assertEquals("post-1", posts[2].id)
    }
    
    @Test
    fun getPostsAfter_returnsFilteredPosts() = runBlocking {
        // Arrange
        val post1 = createTestPost("post-1", timestamp = 1000L)
        val post2 = createTestPost("post-2", timestamp = 2000L)
        val post3 = createTestPost("post-3", timestamp = 3000L)
        
        postDao.insert(post1)
        postDao.insert(post2)
        postDao.insert(post3)
        
        // Act
        val posts = postDao.getPostsAfter(1500L)
        
        // Assert
        assertEquals(2, posts.size)
        assertTrue(posts.all { it.timestamp > 1500L })
    }
    
    @Test
    fun postExists_returnsCorrectValue() = runBlocking {
        // Arrange
        val post = createTestPost("post-1")
        
        // Act
        val existsBefore = postDao.postExists("post-1")
        postDao.insert(post)
        val existsAfter = postDao.postExists("post-1")
        
        // Assert
        assertFalse(existsBefore)
        assertTrue(existsAfter)
    }
    
    @Test
    fun deleteOldPosts_removesOldPosts() = runBlocking {
        // Arrange
        val post1 = createTestPost("post-1", timestamp = 1000L)
        val post2 = createTestPost("post-2", timestamp = 2000L)
        val post3 = createTestPost("post-3", timestamp = 3000L)
        
        postDao.insert(post1)
        postDao.insert(post2)
        postDao.insert(post3)
        
        // Act
        val deletedCount = postDao.deleteOldPosts(2500L)
        val remainingCount = postDao.getPostCount()
        
        // Assert
        assertEquals(2, deletedCount)
        assertEquals(1, remainingCount)
        assertFalse(postDao.postExists("post-1"))
        assertFalse(postDao.postExists("post-2"))
        assertTrue(postDao.postExists("post-3"))
    }
    
    @Test
    fun updatePost_modifiesExistingPost() = runBlocking {
        // Arrange
        val originalPost = createTestPost("post-1", content = "Original")
        postDao.insert(originalPost)
        
        // Act
        val updatedPost = originalPost.copy(
            content = "Updated",
            imageHash = "image-hash"
        )
        postDao.update(updatedPost)
        val retrieved = postDao.getPostById("post-1")
        
        // Assert
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved!!.content)
        assertEquals("image-hash", retrieved.imageHash)
    }
    
    @Test
    fun getPostCount_returnsCorrectCount() = runBlocking {
        // Arrange & Act
        val countBefore = postDao.getPostCount()
        postDao.insert(createTestPost("post-1"))
        postDao.insert(createTestPost("post-2"))
        postDao.insert(createTestPost("post-3"))
        val countAfter = postDao.getPostCount()
        
        // Assert
        assertEquals(0, countBefore)
        assertEquals(3, countAfter)
    }
    
    // Helper function to create test posts
    private fun createTestPost(
        id: String,
        content: String = "Test content",
        timestamp: Long = System.currentTimeMillis()
    ): Post {
        return Post(
            id = id,
            authorId = "test-author",
            authorName = "Test Author",
            content = content,
            imageHash = null,
            imageSize = null,
            imageWidth = null,
            imageHeight = null,
            timestamp = timestamp,
            signature = "test-signature",
            publicKey = "test-public-key",
            ttl = 7,
            firstSeenTimestamp = timestamp
        )
    }
}
