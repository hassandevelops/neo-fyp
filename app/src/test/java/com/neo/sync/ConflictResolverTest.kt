package com.neo.sync

import com.neo.data.model.Post
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ConflictResolver.
 */
class ConflictResolverTest {
    
    private lateinit var conflictResolver: ConflictResolver
    
    @Before
    fun setup() {
        conflictResolver = ConflictResolver()
    }
    
    @Test
    fun `test resolve with newer timestamp replaces existing`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 1000L, content = "Old content")
        val new = createPost(id = "post-1", timestamp = 2000L, content = "New content")
        
        // Act
        val resolution = conflictResolver.resolve(existing, new)
        
        // Assert
        assertTrue(resolution is ConflictResolver.Resolution.ReplaceWithNew)
        assertEquals(new, (resolution as ConflictResolver.Resolution.ReplaceWithNew).post)
    }
    
    @Test
    fun `test resolve with older timestamp keeps existing`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 2000L, content = "Newer content")
        val new = createPost(id = "post-1", timestamp = 1000L, content = "Older content")
        
        // Act
        val resolution = conflictResolver.resolve(existing, new)
        
        // Assert
        assertTrue(resolution is ConflictResolver.Resolution.KeepExisting)
        assertEquals(existing, (resolution as ConflictResolver.Resolution.KeepExisting).post)
    }
    
    @Test
    fun `test resolve with same timestamp uses authorId tiebreaker`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 1000L, authorId = "author-A", content = "Content A")
        val new = createPost(id = "post-1", timestamp = 1000L, authorId = "author-B", content = "Content B")
        
        // Act
        val resolution = conflictResolver.resolve(existing, new)
        
        // Assert - author-B > author-A lexicographically
        assertTrue(resolution is ConflictResolver.Resolution.ReplaceWithNew)
    }
    
    @Test
    fun `test resolve with different IDs returns invalid`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 1000L)
        val new = createPost(id = "post-2", timestamp = 2000L)
        
        // Act
        val resolution = conflictResolver.resolve(existing, new)
        
        // Assert
        assertTrue(resolution is ConflictResolver.Resolution.Invalid)
        assertTrue((resolution as ConflictResolver.Resolution.Invalid).reason.contains("don't match"))
    }
    
    @Test
    fun `test resolve with identical content keeps existing`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 1000L, content = "Same content")
        val new = createPost(id = "post-1", timestamp = 1000L, content = "Same content")
        
        // Act
        val resolution = conflictResolver.resolve(existing, new)
        
        // Assert
        assertTrue(resolution is ConflictResolver.Resolution.KeepExisting)
    }
    
    @Test
    fun `test isConflict returns true for different content`() {
        // Arrange
        val post1 = createPost(id = "post-1", content = "Content A")
        val post2 = createPost(id = "post-1", content = "Content B")
        
        // Act
        val isConflict = conflictResolver.isConflict(post1, post2)
        
        // Assert
        assertTrue(isConflict)
    }
    
    @Test
    fun `test isConflict returns false for same content`() {
        // Arrange
        val post1 = createPost(id = "post-1", content = "Same content")
        val post2 = createPost(id = "post-1", content = "Same content")
        
        // Act
        val isConflict = conflictResolver.isConflict(post1, post2)
        
        // Assert
        assertFalse(isConflict)
    }
    
    @Test
    fun `test isConflict returns false for different IDs`() {
        // Arrange
        val post1 = createPost(id = "post-1", content = "Content A")
        val post2 = createPost(id = "post-2", content = "Content B")
        
        // Act
        val isConflict = conflictResolver.isConflict(post1, post2)
        
        // Assert
        assertFalse(isConflict)
    }
    
    @Test
    fun `test getConflictDetails returns formatted string`() {
        // Arrange
        val existing = createPost(id = "post-1", timestamp = 1000L, authorId = "author-1", authorName = "Alice", content = "Old")
        val new = createPost(id = "post-1", timestamp = 2000L, authorId = "author-2", authorName = "Bob", content = "New")
        
        // Act
        val details = conflictResolver.getConflictDetails(existing, new)
        
        // Assert
        assertTrue(details.contains("post-1"))
        assertTrue(details.contains("Alice"))
        assertTrue(details.contains("Bob"))
        assertTrue(details.contains("Old"))
        assertTrue(details.contains("New"))
    }
    
    // Helper function to create test posts
    private fun createPost(
        id: String = "test-post",
        timestamp: Long = System.currentTimeMillis(),
        authorId: String = "test-author",
        authorName: String = "Test Author",
        content: String = "Test content"
    ): Post {
        return Post(
            id = id,
            authorId = authorId,
            authorName = authorName,
            content = content,
            timestamp = timestamp,
            signature = "test-signature",
            publicKey = "test-public-key",
            ttl = 7,
            firstSeenTimestamp = timestamp
        )
    }
}
