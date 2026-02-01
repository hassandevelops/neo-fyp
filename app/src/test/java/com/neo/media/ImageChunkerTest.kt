package com.neo.media

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ImageChunker.
 */
class ImageChunkerTest {
    
    private lateinit var imageChunker: ImageChunker
    
    @Before
    fun setup() {
        imageChunker = ImageChunker()
    }
    
    @Test
    fun `test chunkImage splits data correctly`() {
        // Arrange
        val imageData = "A".repeat(10000) // 10KB of data
        val postId = "test-post-123"
        
        // Act
        val chunks = imageChunker.chunkImage(imageData, postId)
        
        // Assert
        assertTrue(chunks.isNotEmpty())
        val expectedChunks = (imageData.length + ImageChunker.CHUNK_SIZE - 1) / ImageChunker.CHUNK_SIZE
        assertEquals(expectedChunks, chunks.size)
        assertEquals(expectedChunks, chunks.first().totalChunks)
    }
    
    @Test
    fun `test chunks have sequential indices`() {
        // Arrange
        val imageData = "B".repeat(10000)
        val postId = "test-post-456"
        
        // Act
        val chunks = imageChunker.chunkImage(imageData, postId)
        
        // Assert
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
            assertEquals(postId, chunk.postId)
        }
    }
    
    @Test
    fun `test each chunk has valid checksum`() {
        // Arrange
        val imageData = "C".repeat(10000)
        val postId = "test-post-789"
        
        // Act
        val chunks = imageChunker.chunkImage(imageData, postId)
        
        // Assert
        chunks.forEach { chunk ->
            assertNotNull(chunk.checksum)
            assertTrue(chunk.checksum.isNotEmpty())
        }
    }
    
    @Test
    fun `test reassembleChunks reconstructs original data`() {
        // Arrange
        val originalData = "Original image data " * 500 // Repeat to make it larger
        val postId = "test-post-reassemble"
        val chunks = imageChunker.chunkImage(originalData, postId)
        
        // Act
        val reassembled = imageChunker.reassembleChunks(chunks)
        
        // Assert
        assertNotNull(reassembled)
        assertEquals(originalData, reassembled)
    }
    
    @Test
    fun `test reassembleChunks returns null for incomplete chunks`() {
        // Arrange
        val imageData = "D".repeat(10000)
        val postId = "test-post-incomplete"
        val chunks = imageChunker.chunkImage(imageData, postId).toMutableList()
        chunks.removeAt(chunks.size / 2) // Remove a middle chunk
        
        // Act
        val reassembled = imageChunker.reassembleChunks(chunks)
        
        // Assert
        assertNull(reassembled)
    }
    
    @Test
    fun `test reassembleChunks handles out-of-order chunks`() {
        // Arrange
        val originalData = "E".repeat(10000)
        val postId = "test-post-shuffle"
        val chunks = imageChunker.chunkImage(originalData, postId).shuffled()
        
        // Act
        val reassembled = imageChunker.reassembleChunks(chunks)
        
        // Assert
        assertNotNull(reassembled)
        assertEquals(originalData, reassembled)
    }
    
    @Test
    fun `test reassembleChunks detects corrupted chunk`() {
        // Arrange
        val imageData = "F".repeat(10000)
        val postId = "test-post-corrupt"
        val chunks = imageChunker.chunkImage(imageData, postId).toMutableList()
        
        // Corrupt a chunk's data but keep checksum
        val corruptedChunk = chunks[0].copy(data = "CORRUPTED")
        chunks[0] = corruptedChunk
        
        // Act
        val reassembled = imageChunker.reassembleChunks(chunks)
        
        // Assert
        assertNull(reassembled) // Should fail checksum verification
    }
    
    @Test
    fun `test ChunkAssemblyManager tracks progress`() {
        // Arrange
        val manager = ImageChunker.ChunkAssemblyManager()
        val imageData = "G".repeat(10000)
        val postId = "test-post-progress"
        val chunks = imageChunker.chunkImage(imageData, postId)
        
        // Act & Assert
        chunks.forEachIndexed { index, chunk ->
            val result = manager.addChunk(chunk)
            
            if (index < chunks.size - 1) {
                assertNull(result) // Not complete yet
                val progress = manager.getProgress(postId)
                assertNotNull(progress)
                assertEquals(index + 1, progress!!.first)
                assertEquals(chunks.size, progress.second)
            } else {
                assertNotNull(result) // Complete
                assertEquals(imageData, result)
            }
        }
    }
    
    @Test
    fun `test ChunkAssemblyManager handles duplicate chunks`() {
        // Arrange
        val manager = ImageChunker.ChunkAssemblyManager()
        val imageData = "H".repeat(5000)
        val postId = "test-post-duplicate"
        val chunks = imageChunker.chunkImage(imageData, postId)
        
        // Act
        manager.addChunk(chunks[0])
        val result = manager.addChunk(chunks[0]) // Add same chunk again
        
        // Assert
        assertNull(result) // Should ignore duplicate
    }
}
