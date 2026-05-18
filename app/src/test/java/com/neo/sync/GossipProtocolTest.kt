package com.neo.sync

import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.model.BlockedUser
import com.neo.data.model.Post
import com.neo.data.model.Comment
import com.neo.data.model.Reaction
import com.neo.data.repository.BlockedUserRepository
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.ReactionRepository
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GossipProtocolTest {

    private lateinit var postRepository: PostRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var cryptoManager: CryptoManager
    private lateinit var blockedUserRepository: BlockedUserRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var reactionRepository: ReactionRepository
    private lateinit var ackManager: AckManager
    private lateinit var conflictResolver: ConflictResolver
    private lateinit var seenMessageCache: SeenMessageCache
    private lateinit var rateLimiter: RateLimiter
    private lateinit var imageFileStore: ImageFileStore
    private lateinit var gossipProtocol: GossipProtocol

    @Before
    fun setup() {
        postRepository = mockk(relaxed = true)
        deviceRepository = mockk(relaxed = true)
        cryptoManager = mockk(relaxed = true)
        blockedUserRepository = mockk()
        commentRepository = mockk(relaxed = true)
        reactionRepository = mockk(relaxed = true)
        ackManager = mockk(relaxed = true)
        conflictResolver = mockk()
        seenMessageCache = mockk()
        rateLimiter = mockk()
        imageFileStore = mockk(relaxed = true)

        every { conflictResolver.isConflict(any(), any()) } returns false

        gossipProtocol = GossipProtocol(
            postRepository = postRepository,
            deviceRepository = deviceRepository,
            cryptoManager = cryptoManager,
            blockedUserRepository = blockedUserRepository,
            commentRepository = commentRepository,
            reactionRepository = reactionRepository,
            ackManager = ackManager,
            conflictResolver = conflictResolver,
            seenMessageCache = seenMessageCache,
            rateLimiter = rateLimiter,
            imageFileStore = imageFileStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private fun mockStorePath() {
        every { seenMessageCache.checkAndAdd(any()) } returns true
        coEvery { blockedUserRepository.isBlocked(any()) } returns false
        coEvery { rateLimiter.canAcceptInboundPost(any()) } returns true
        coEvery { postRepository.getPostById(any()) } returns null
        coEvery { postRepository.insertPost(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { cryptoManager.verify(any(), any(), any()) } returns true
    }

    @Test
    fun `mock getPostById returns null`() = runTest {
        coEvery { postRepository.getPostById(any()) } returns null

        val result = postRepository.getPostById("test")

        assertNull(result)
    }

    // ── Post handling ──────────────────────────────────────────────────────

    @Test
    fun `handleReceivedPost stores new post`() = runTest {
        mockStorePath()

        gossipProtocol.handleReceivedPost(postBroadcast("post-1"), "peer-1")

        coVerify(exactly = 1) { postRepository.insertPost(any()) }
    }

    @Test
    fun `handleReceivedPost ignores duplicate`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns false

        gossipProtocol.handleReceivedPost(postBroadcast("post-1"), "peer-1")

        coVerify(exactly = 0) { postRepository.insertPost(any()) }
    }

    @Test
    fun `handleReceivedPost ignores blocked author`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns true
        // Populate the in-memory blocked cache
        val service = mockk<BluetoothService>(relaxed = true)
        every { service.allIncomingMessages } returns emptyFlow()
        every { service.connectedPeers } returns MutableStateFlow(emptyList())
        val blockedFlow = MutableStateFlow(
            listOf(BlockedUser(blockedUserId = "author-1", blockedAt = System.currentTimeMillis()))
        )
        every { blockedUserRepository.getAllBlocked() } returns blockedFlow
        gossipProtocol.setBluetoothService(service)
        advanceUntilIdle()

        gossipProtocol.handleReceivedPost(postBroadcast("post-1"), "peer-1")

        coVerify(exactly = 0) { postRepository.insertPost(any()) }
    }

    @Test
    fun `handleReceivedPost drops when rate limited`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns true
        coEvery { blockedUserRepository.isBlocked(any()) } returns false
        coEvery { rateLimiter.canAcceptInboundPost(any()) } returns false

        gossipProtocol.handleReceivedPost(postBroadcast("post-1"), "peer-1")

        coVerify(exactly = 0) { postRepository.insertPost(any()) }
    }

    @Test
    fun `handleReceivedPost decrements TTL`() = runTest {
        mockStorePath()

        gossipProtocol.handleReceivedPost(postBroadcast("post-1", ttl = 2), "peer-1")

        coVerify { postRepository.insertPost(match { it.ttl == 2 }) }
    }

    @Test
    fun `handleReceivedPost does not forward when TTL is zero`() = runTest {
        mockStorePath()

        gossipProtocol.handleReceivedPost(postBroadcast("post-1", ttl = 0), "peer-1")

        coVerify { postRepository.insertPost(match { it.ttl == 0 }) }
    }

    // ── Comment handling ───────────────────────────────────────────────────

    @Test
    fun `handleReceivedComment stores new comment`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns true
        coEvery { blockedUserRepository.isBlocked(any()) } returns false
        coEvery { rateLimiter.canAcceptInboundComment(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { cryptoManager.verify(any(), any(), any()) } returns true
        coEvery { commentRepository.insertComment(any()) } just Runs

        gossipProtocol.handleReceivedComment(commentBroadcast("comment-1"), "peer-1")

        coVerify(exactly = 1) { commentRepository.insertComment(any()) }
    }

    @Test
    fun `handleReceivedComment ignores duplicate`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns false

        gossipProtocol.handleReceivedComment(commentBroadcast("comment-1"), "peer-1")

        coVerify(exactly = 0) { commentRepository.insertComment(any()) }
    }

    // ── Reaction handling ──────────────────────────────────────────────────

    @Test
    fun `handleReceivedReaction stores new reaction`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns true
        coEvery { blockedUserRepository.isBlocked(any()) } returns false
        coEvery { cryptoManager.verify(any(), any(), any()) } returns true
        coEvery { rateLimiter.canAcceptInboundReaction(any()) } returns true
        coEvery { reactionRepository.insertReaction(any()) } just Runs

        gossipProtocol.handleReceivedReaction(reactionBroadcast("reaction-1"), "peer-1")

        coVerify(exactly = 1) { reactionRepository.insertReaction(any()) }
    }

    @Test
    fun `handleReceivedReaction ignores duplicate`() = runTest {
        every { seenMessageCache.checkAndAdd(any()) } returns false

        gossipProtocol.handleReceivedReaction(reactionBroadcast("reaction-1"), "peer-1")

        coVerify(exactly = 0) { reactionRepository.insertReaction(any()) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun postBroadcast(
        id: String,
        content: String = "Content",
        ttl: Int = 7
    ) = Message.PostBroadcast(
        id = id, authorId = "author-1", authorName = "Alice", content = content,
        timestamp = 1000L, signature = "sig", publicKey = "pk", ttl = ttl
    )

    private fun commentBroadcast(
        id: String
    ) = Message.CommentBroadcast(
        id = id, postId = "post-1", parentCommentId = null,
        authorId = "author-1", authorName = "Alice", content = "Nice",
        timestamp = 1000L, signature = "sig", publicKey = "pk", ttl = 5
    )

    private fun reactionBroadcast(
        id: String
    ) = Message.ReactionBroadcast(
        id = id, postId = "post-1", userId = "user-1", userName = "Alice",
        type = "LIKE", timestamp = 1000L, signature = "sig", publicKey = "pk", ttl = 5
    )
}
