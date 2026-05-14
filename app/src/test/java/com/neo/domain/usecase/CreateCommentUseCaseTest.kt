package com.neo.domain.usecase

import com.neo.data.model.Comment
import com.neo.data.repository.CommentRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreateCommentUseCaseTest {

    private val commentRepository: CommentRepository = mockk()
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val rateLimiter: RateLimiter = mockk()
    private val syncPort: ISyncPort = mockk()

    private lateinit var useCase: CreateCommentUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = CreateCommentUseCase(
            commentRepository = commentRepository,
            cryptoManager = cryptoManager,
            rateLimiter = rateLimiter,
            syncPort = syncPort
        )
    }

    @Test
    fun `create top-level comment succeeds`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { commentRepository.insertComment(any()) } just Runs
        coEvery { syncPort.broadcastComment(any()) } just Runs

        val result = useCase(postId = "post-1", content = "Nice post!", authorName = "Alice")

        assertTrue(result.isSuccess)
        val comment = result.getOrNull()
        assertEquals("Nice post!", comment?.content)
        assertEquals("Alice", comment?.authorName)
        assertEquals("post-1", comment?.postId)
        assertNull(comment?.parentCommentId)
        coVerify { commentRepository.insertComment(any()) }
        coVerify { syncPort.broadcastComment(any()) }
    }

    @Test
    fun `create reply comment sets parentCommentId`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { commentRepository.insertComment(any()) } just Runs
        coEvery { syncPort.broadcastComment(any()) } just Runs

        val result = useCase(postId = "post-1", content = "Reply!", authorName = "Bob", parentCommentId = "comment-1")

        assertTrue(result.isSuccess)
        assertEquals("comment-1", result.getOrNull()?.parentCommentId)
    }

    @Test
    fun `create comment with blank content fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true

        val result = useCase(postId = "post-1", content = "   ", authorName = "Alice")

        assertTrue(result.isFailure)
        assertEquals("Comment cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create comment exceeding max length fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true

        val longContent = "A".repeat(501)
        val result = useCase(postId = "post-1", content = longContent, authorName = "Alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("exceeds maximum length") == true)
    }

    @Test
    fun `create comment when rate limited fails`() = runTest {
        val deviceId = "device-1"
        coEvery { cryptoManager.getDeviceId() } returns deviceId
        coEvery { rateLimiter.canCreateComment(deviceId) } returns false
        coEvery { rateLimiter.getTimeUntilNextComment(deviceId) } returns 60000L

        val result = useCase(postId = "post-1", content = "Comment", authorName = "Alice")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Rate limit") == true)
        coVerify(exactly = 0) { commentRepository.insertComment(any()) }
        coVerify(exactly = 0) { syncPort.broadcastComment(any()) }
    }

    @Test
    fun `create comment calls broadcast after insert`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { commentRepository.insertComment(any()) } just Runs
        coEvery { syncPort.broadcastComment(any()) } just Runs

        useCase(postId = "post-1", content = "Comment", authorName = "Alice")

        coVerifyOrder {
            commentRepository.insertComment(any())
            syncPort.broadcastComment(any())
        }
    }

    @Test
    fun `create comment populates correct TTL`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { commentRepository.insertComment(any()) } just Runs
        coEvery { syncPort.broadcastComment(any()) } just Runs

        val result = useCase(postId = "post-1", content = "Comment", authorName = "Alice")

        assertEquals(5, result.getOrNull()?.ttl)
    }

    @Test
    fun `create comment uses crypto for signing`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreateComment(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "signed"
        coEvery { commentRepository.insertComment(any()) } just Runs
        coEvery { syncPort.broadcastComment(any()) } just Runs

        useCase(postId = "post-1", content = "Comment", authorName = "Alice")

        coVerify { cryptoManager.getDeviceId() }
        coVerify { cryptoManager.getPublicKey() }
        coVerify { cryptoManager.sign(any()) }
    }
}
