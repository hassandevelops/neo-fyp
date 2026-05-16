package com.neo.sync

import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private lateinit var gossipProtocol: GossipProtocol

    @Before
    fun setup() {
        gossipProtocol = mockk(relaxed = true)
        syncManager = SyncManager(
            postRepository = mockk(),
            deviceRepository = mockk(),
            gossipProtocol = gossipProtocol,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @After
    fun teardown() {
        syncManager.stop()
    }

    @Test
    fun `setBluetoothService wires message routing`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        every { service.onMessageReceived = any() } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())

        syncManager.setBluetoothService(service)

        verify { service.onMessageReceived = any() }
    }

    @Test
    fun `stop cancels periodic sync`() {
        syncManager.stop()
    }

    @Test
    fun `connectedPeersCount starts at zero`() {
        assertEquals(0, syncManager.connectedPeersCount.value)
    }

    @Test
    fun `setBluetoothService starts message routing`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        every { service.onMessageReceived = any() } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())

        syncManager.setBluetoothService(service)

        verify(exactly = 1) { service.onMessageReceived = any() }
    }

    @Test
    fun `post broadcast route sends ack`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        val callback = slot<(String, Message) -> Unit>()
        every { service.onMessageReceived = capture(callback) } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())
        coEvery { service.sendMessage("peer-1", any()) } returns true

        val message = Message.PostBroadcast(
            id = "post-1",
            authorId = "author-1",
            authorName = "Alice",
            content = "Hello",
            timestamp = 1000L,
            signature = "sig",
            publicKey = "pk",
            ttl = 7
        )

        syncManager.setBluetoothService(service)
        callback.captured.invoke("peer-1", message)

        coVerify { gossipProtocol.handleReceivedPost(message, "peer-1") }
        coVerify {
            service.sendMessage(
                "peer-1",
                match { it is Message.Ack && it.messageId == "post-1" && it.messageType == "PostBroadcast" }
            )
        }
    }

    @Test
    fun `comment broadcast route sends ack`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        val callback = slot<(String, Message) -> Unit>()
        every { service.onMessageReceived = capture(callback) } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())
        coEvery { service.sendMessage("peer-1", any()) } returns true

        val message = Message.CommentBroadcast(
            id = "comment-1",
            postId = "post-1",
            parentCommentId = null,
            authorId = "author-1",
            authorName = "Alice",
            content = "Nice",
            timestamp = 1000L,
            signature = "sig",
            publicKey = "pk",
            ttl = 5
        )

        syncManager.setBluetoothService(service)
        callback.captured.invoke("peer-1", message)

        coVerify { gossipProtocol.handleReceivedComment(message, "peer-1") }
        coVerify {
            service.sendMessage(
                "peer-1",
                match { it is Message.Ack && it.messageId == "comment-1" && it.messageType == "CommentBroadcast" }
            )
        }
    }

    @Test
    fun `setBluetoothService updates connectedPeersCount`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        val peers = MutableStateFlow(listOf("p1", "p2"))
        every { service.onMessageReceived = any() } just Runs
        every { service.connectedPeers } returns peers

        syncManager.setBluetoothService(service)
        delay(100)

        assertEquals(2, syncManager.connectedPeersCount.value)
    }

    @Test
    fun `reaction broadcast route sends ack`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        val callback = slot<(String, Message) -> Unit>()
        every { service.onMessageReceived = capture(callback) } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())
        coEvery { service.sendMessage("peer-1", any()) } returns true

        val message = Message.ReactionBroadcast(
            id = "reaction-1",
            postId = "post-1",
            userId = "user-1",
            userName = "Alice",
            type = "LIKE",
            timestamp = 1000L,
            signature = "sig",
            publicKey = "pk",
            ttl = 5
        )

        syncManager.setBluetoothService(service)
        callback.captured.invoke("peer-1", message)

        coVerify { gossipProtocol.handleReceivedReaction(message, "peer-1") }
        coVerify {
            service.sendMessage(
                "peer-1",
                match { it is Message.Ack && it.messageId == "reaction-1" && it.messageType == "ReactionBroadcast" }
            )
        }
    }
}
