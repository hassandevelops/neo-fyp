package com.neo.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RateLimiter.
 */
class RateLimiterTest {
    
    private lateinit var rateLimiter: RateLimiter
    
    @Before
    fun setup() {
        rateLimiter = RateLimiter()
        rateLimiter.resetAll()
    }
    
    @Test
    fun `test canCreatePost allows posts under limit`() {
        // Arrange
        val deviceId = "device-1"
        rateLimiter.configure(5, 60000) // 5 posts per minute
        
        // Act & Assert
        repeat(5) { i ->
            assertTrue("Post $i should be allowed", rateLimiter.canCreatePost(deviceId))
        }
    }
    
    @Test
    fun `test canCreatePost blocks posts over limit`() {
        // Arrange
        val deviceId = "device-2"
        rateLimiter.configure(3, 60000) // 3 posts per minute
        
        // Act
        repeat(3) { rateLimiter.canCreatePost(deviceId) }
        val fourthPost = rateLimiter.canCreatePost(deviceId)
        
        // Assert
        assertFalse("Fourth post should be blocked", fourthPost)
    }
    
    @Test
    fun `test getRemainingPosts returns correct count`() {
        // Arrange
        val deviceId = "device-3"
        rateLimiter.configure(10, 60000)
        
        // Act
        assertEquals(10, rateLimiter.getRemainingPosts(deviceId))
        
        rateLimiter.canCreatePost(deviceId)
        assertEquals(9, rateLimiter.getRemainingPosts(deviceId))
        
        repeat(4) { rateLimiter.canCreatePost(deviceId) }
        assertEquals(5, rateLimiter.getRemainingPosts(deviceId))
    }
    
    @Test
    fun `test sliding window removes old timestamps`() {
        // Arrange
        val deviceId = "device-4"
        rateLimiter.configure(2, 100) // 2 posts per 100ms
        
        // Act
        rateLimiter.canCreatePost(deviceId)
        rateLimiter.canCreatePost(deviceId)
        assertFalse("Third post should be blocked", rateLimiter.canCreatePost(deviceId))
        
        // Wait for window to expire
        Thread.sleep(150)
        
        // Assert
        assertTrue("Post should be allowed after window expires", rateLimiter.canCreatePost(deviceId))
    }
    
    @Test
    fun `test getTimeUntilNextPost returns zero when posts allowed`() {
        // Arrange
        val deviceId = "device-5"
        rateLimiter.configure(5, 60000)
        
        // Act & Assert
        assertEquals(0, rateLimiter.getTimeUntilNextPost(deviceId))
        
        rateLimiter.canCreatePost(deviceId)
        assertEquals(0, rateLimiter.getTimeUntilNextPost(deviceId))
    }
    
    @Test
    fun `test getTimeUntilNextPost returns time when limit exceeded`() {
        // Arrange
        val deviceId = "device-6"
        rateLimiter.configure(2, 1000) // 2 posts per second
        
        // Act
        rateLimiter.canCreatePost(deviceId)
        rateLimiter.canCreatePost(deviceId)
        
        val timeUntil = rateLimiter.getTimeUntilNextPost(deviceId)
        
        // Assert
        assertTrue("Time until next post should be > 0", timeUntil > 0)
        assertTrue("Time until next post should be <= 1000ms", timeUntil <= 1000)
    }
    
    @Test
    fun `test reset clears device rate limit`() {
        // Arrange
        val deviceId = "device-7"
        rateLimiter.configure(2, 60000)
        
        // Act
        rateLimiter.canCreatePost(deviceId)
        rateLimiter.canCreatePost(deviceId)
        assertFalse("Third post should be blocked", rateLimiter.canCreatePost(deviceId))
        
        rateLimiter.reset(deviceId)
        
        // Assert
        assertTrue("Post should be allowed after reset", rateLimiter.canCreatePost(deviceId))
    }
    
    @Test
    fun `test resetAll clears all rate limits`() {
        // Arrange
        rateLimiter.configure(1, 60000)
        rateLimiter.canCreatePost("device-1")
        rateLimiter.canCreatePost("device-2")
        
        // Act
        rateLimiter.resetAll()
        
        // Assert
        assertTrue(rateLimiter.canCreatePost("device-1"))
        assertTrue(rateLimiter.canCreatePost("device-2"))
    }
    
    @Test
    fun `test multiple devices tracked independently`() {
        // Arrange
        rateLimiter.configure(2, 60000)
        
        // Act
        rateLimiter.canCreatePost("device-A")
        rateLimiter.canCreatePost("device-A")
        rateLimiter.canCreatePost("device-B")
        
        // Assert
        assertFalse("Device A should be blocked", rateLimiter.canCreatePost("device-A"))
        assertTrue("Device B should be allowed", rateLimiter.canCreatePost("device-B"))
    }
    
    @Test
    fun `test configure updates limits`() {
        // Arrange
        val deviceId = "device-8"
        rateLimiter.configure(2, 60000)
        
        // Act
        rateLimiter.canCreatePost(deviceId)
        rateLimiter.canCreatePost(deviceId)
        assertFalse("Should be blocked with limit 2", rateLimiter.canCreatePost(deviceId))
        
        rateLimiter.configure(5, 60000) // Increase limit
        
        // Assert
        assertTrue("Should be allowed with limit 5", rateLimiter.canCreatePost(deviceId))
    }
    
    @Test
    fun `test getConfig returns current configuration`() {
        // Arrange
        rateLimiter.configure(15, 120000)
        
        // Act
        val (limit, window) = rateLimiter.getConfig()
        
        // Assert
        assertEquals(15, limit)
        assertEquals(120000L, window)
    }
}
