package com.neo.sync

import com.neo.bluetooth.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AckManager.
 */
class AckManagerTest {
    
    private lateinit var ackManager: AckManager
    private var sendCallCount = 0
    private var successCallCount = 0
    private var failureCallCount = 0
    
    @Before
    fun setup() {
        ackManager = AckManager()
        sendCallCount = 0
        successCallCount = 0
        failureCallCount = 0
    }
    
    @After
    fun teardown() {
        ackManager.shutdown()
    }
    
    @Test
    fun `test sendWithAck tracks message`() {
        // Arrange
        val messageId = "msg-1"
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        // Act
        ackManager.sendWithAck(messageId, message, "peer-1", { _, _ -> sendCallCount++ })
        
        // Assert
        assertEquals(1, ackManager.getPendingCount())
        assertTrue(ackManager.getPendingMessageIds().contains(messageId))
    }
    
    @Test
    fun `test handleAck removes message and calls success`() = runBlocking {
        // Arrange
        val messageId = "msg-2"
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck(
            messageId, 
            message, 
            "peer-1", 
            { _, _ -> sendCallCount++ },
            onSuccess = { successCallCount++ }
        )
        
        // Act
        val handled = ackManager.handleAck(messageId)
        delay(100) // Give time for callback
        
        // Assert
        assertTrue(handled)
        assertEquals(0, ackManager.getPendingCount())
        assertEquals(1, successCallCount)
    }
    
    @Test
    fun `test handleAck for unknown message returns false`() {
        // Act
        val handled = ackManager.handleAck("unknown-msg")
        
        // Assert
        assertFalse(handled)
    }
    
    @Test
    fun `test retry mechanism with exponential backoff`() = runBlocking {
        // Arrange
        val messageId = "msg-3"
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck(
            messageId,
            message,
            "peer-1",
            { _, _ -> sendCallCount++ }
        )
        
        // Act - wait for first retry (1 second timeout)
        delay(1200)
        
        // Assert - should have retried once
        assertTrue(sendCallCount >= 2) // Initial send + 1 retry
        assertEquals(1, ackManager.getPendingCount())
    }
    
    @Test
    fun `test max retries calls failure callback`() = runBlocking {
        // Arrange
        val messageId = "msg-4"
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck(
            messageId,
            message,
            "peer-1",
            { _, _ -> sendCallCount++ },
            onFailure = { failureCallCount++ }
        )
        
        // Act - wait for all retries (1s + 2s + 4s + processing time)
        delay(8000)
        
        // Assert
        assertEquals(0, ackManager.getPendingCount())
        assertEquals(1, failureCallCount)
        assertTrue(sendCallCount >= 4) // Initial + 3 retries
    }
    
    @Test
    fun `test cancel stops tracking`() {
        // Arrange
        val messageId = "msg-5"
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck(messageId, message, "peer-1", { _, _ -> sendCallCount++ })
        
        // Act
        ackManager.cancel(messageId)
        
        // Assert
        assertEquals(0, ackManager.getPendingCount())
    }
    
    @Test
    fun `test clear removes all pending messages`() {
        // Arrange
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck("msg-1", message, "peer-1", { _, _ -> })
        ackManager.sendWithAck("msg-2", message, "peer-2", { _, _ -> })
        ackManager.sendWithAck("msg-3", message, "peer-3", { _, _ -> })
        
        // Act
        ackManager.clear()
        
        // Assert
        assertEquals(0, ackManager.getPendingCount())
    }
    
    @Test
    fun `test getPendingMessageIds returns correct IDs`() {
        // Arrange
        val message = Message.PostBroadcast("post-1", "author-1", "Author", "Content", 1000L, "sig", "key", 7)
        
        ackManager.sendWithAck("msg-1", message, "peer-1", { _, _ -> })
        ackManager.sendWithAck("msg-2", message, "peer-2", { _, _ -> })
        
        // Act
        val ids = ackManager.getPendingMessageIds()
        
        // Assert
        assertEquals(2, ids.size)
        assertTrue(ids.contains("msg-1"))
        assertTrue(ids.contains("msg-2"))
    }
}
