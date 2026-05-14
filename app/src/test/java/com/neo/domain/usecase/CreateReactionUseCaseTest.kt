package com.neo.domain.usecase

import com.neo.data.model.Reaction
import com.neo.data.model.ReactionType
import com.neo.data.repository.ReactionRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreateReactionUseCaseTest {

    private val reactionRepository: ReactionRepository = mockk()
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val syncPort: ISyncPort = mockk()

    private lateinit var useCase: CreateReactionUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = CreateReactionUseCase(
            reactionRepository = reactionRepository,
            cryptoManager = cryptoManager,
            syncPort = syncPort
        )
    }

    @Test
    fun `like a post for the first time succeeds`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns false
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { reactionRepository.insertReaction(any()) } just Runs
        coEvery { syncPort.broadcastReaction(any()) } just Runs

        val result = useCase(postId = "post-1", userName = "Alice", type = ReactionType.LIKE)

        assertTrue(result.isSuccess)
        val reaction = result.getOrNull()
        assertEquals("post-1", reaction?.postId)
        assertEquals("Alice", reaction?.userName)
        assertEquals(ReactionType.LIKE, reaction?.type)
        assertEquals("device-1", reaction?.userId)
        coVerify { reactionRepository.insertReaction(any()) }
        coVerify { syncPort.broadcastReaction(any()) }
    }

    @Test
    fun `liking an already liked post fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns true

        val result = useCase(postId = "post-1", userName = "Alice", type = ReactionType.LIKE)

        assertTrue(result.isFailure)
        assertEquals("You have already reacted to this post", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { reactionRepository.insertReaction(any()) }
        coVerify(exactly = 0) { syncPort.broadcastReaction(any()) }
    }

    @Test
    fun `reaction inserts with correct TTL`() = runTest {
        val capturedSlot = slot<Reaction>()
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns false
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { reactionRepository.insertReaction(capture(capturedSlot)) } just Runs
        coEvery { syncPort.broadcastReaction(any()) } just Runs

        useCase(postId = "post-1", userName = "Alice")

        assertEquals(5, capturedSlot.captured.ttl)
    }

    @Test
    fun `reaction calls broadcast after insert`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns false
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { reactionRepository.insertReaction(any()) } just Runs
        coEvery { syncPort.broadcastReaction(any()) } just Runs

        useCase(postId = "post-1", userName = "Alice")

        coVerifyOrder {
            reactionRepository.insertReaction(any())
            syncPort.broadcastReaction(any())
        }
    }

    @Test
    fun `reaction uses crypto signing chain`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns false
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { reactionRepository.insertReaction(any()) } just Runs
        coEvery { syncPort.broadcastReaction(any()) } just Runs

        useCase(postId = "post-1", userName = "Alice")

        coVerify { cryptoManager.getDeviceId() }
        coVerify { cryptoManager.getPublicKey() }
        coVerify { cryptoManager.sign(any()) }
    }

    @Test
    fun `reaction with repository failure returns error`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns false
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { reactionRepository.insertReaction(any()) } throws RuntimeException("DB error")

        val result = useCase(postId = "post-1", userName = "Alice")

        assertTrue(result.isFailure)
    }
}
