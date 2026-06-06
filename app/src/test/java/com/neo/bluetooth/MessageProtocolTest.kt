package com.neo.bluetooth

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MessageProtocolTest {

    private lateinit var protocol: MessageProtocol

    @Before
    fun setup() {
        protocol = MessageProtocol
    }

    @Test
    fun `serialize Handshake produces valid JSON`() {
        val msg = Message.Handshake("device-1", "Alice", "pubkey123")
        val json = protocol.serialize(msg)
        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("device-1"))
    }

    @Test
    fun `deserialize Handshake recovers object`() {
        val json = """{"kind":"handshake","deviceId":"dev1","deviceName":"Alice","publicKey":"pk1"}"""
        val msg = protocol.deserialize(json)
        assertTrue(msg is Message.Handshake)
        msg as Message.Handshake
        assertEquals("dev1", msg.deviceId)
        assertEquals("Alice", msg.deviceName)
    }

    @Test
    fun `serialize PostBroadcast produces complete JSON`() {
        val msg = Message.PostBroadcast(
            id = "post-1", authorId = "a1", authorName = "Alice",
            content = "Hello", timestamp = 1000L, signature = "sig",
            publicKey = "pk", ttl = 7
        )
        val json = protocol.serialize(msg)
        assertNotNull(json)
        assertTrue(json.contains("post-1"))
        assertTrue(json.contains("Hello"))
        assertTrue(json.contains("sig"))
    }

    @Test
    fun `round-trip serialize deserialize`() {
        val original = Message.PostBroadcast(
            id = "p1", authorId = "a1", authorName = "Alice",
            content = "Hello World", timestamp = 1000L,
            signature = "sig", publicKey = "pk", ttl = 7
        )
        val json = protocol.serialize(original)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.PostBroadcast)
        deserialized as Message.PostBroadcast
        assertEquals(original.id, deserialized.id)
        assertEquals(original.content, deserialized.content)
        assertEquals(original.authorId, deserialized.authorId)
    }

    @Test
    fun `serialize EventSyncRequest`() {
        val msg = Message.EventSyncRequest(requesterDid = "did:key:abc", lastKnownTimestamp = 500L)
        val json = protocol.serialize(msg)
        assertNotNull(json)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.EventSyncRequest)
    }

    @Test
    fun `serialize EventSyncResponse`() {
        val events = emptyList<EventLogDto>()
        val msg = Message.EventSyncResponse(authorDid = "did:key:abc", events = events)
        val json = protocol.serialize(msg)
        assertNotNull(json)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.EventSyncResponse)
    }

    @Test
    fun `serialize Ack`() {
        val msg = Message.Ack(messageId = "m1", messageType = "PostBroadcast", success = true)
        val json = protocol.serialize(msg)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.Ack)
        val ack = deserialized as Message.Ack
        assertEquals("m1", ack.messageId)
        assertTrue(ack.success)
    }

    @Test
    fun `serialize CommentBroadcast`() {
        val msg = Message.CommentBroadcast(
            id = "c1", postId = "p1", parentCommentId = null,
            authorId = "a1", authorName = "Alice", content = "Nice!",
            timestamp = 1000L, signature = "sig", publicKey = "pk", ttl = 5
        )
        val json = protocol.serialize(msg)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.CommentBroadcast)
    }

    @Test
    fun `serialize ReactionBroadcast`() {
        val msg = Message.ReactionBroadcast(
            id = "r1", postId = "p1", userId = "u1", userName = "Alice",
            type = "LIKE", timestamp = 1000L, signature = "sig",
            publicKey = "pk", ttl = 5
        )
        val json = protocol.serialize(msg)
        val deserialized = protocol.deserialize(json)
        assertTrue(deserialized is Message.ReactionBroadcast)
    }

    @Test
    fun `malformed JSON returns null`() {
        val msg = protocol.deserialize("{{invalid json}}")
        assertNull(msg)
    }

    @Test
    fun `empty JSON returns null`() {
        val msg = protocol.deserialize("")
        assertNull(msg)
    }
}
