package com.neo.domain.usecase

import com.neo.data.model.ReactionType
import com.neo.data.repository.ReactionRepository
import com.neo.security.CryptoManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeleteReactionUseCaseTest {

    private val reactionRepository: ReactionRepository = mockk()
    private val cryptoManager: CryptoManager = mockk(relaxed = true)

    private lateinit var useCase: DeleteReactionUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = DeleteReactionUseCase(
            reactionRepository = reactionRepository,
            cryptoManager = cryptoManager
        )
    }

    @Test
    fun `unlike a post that was liked succeeds`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns true
        coEvery { reactionRepository.deleteReaction("post-1", "device-1", ReactionType.LIKE) } just Runs

        val result = useCase(postId = "post-1")

        assertTrue(result.isSuccess)
        coVerify { reactionRepository.deleteReaction("post-1", "device-1", ReactionType.LIKE) }
    }

    @Test
    fun `unlike a post that was not liked fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns false

        val result = useCase(postId = "post-1")

        assertTrue(result.isFailure)
        assertEquals("You have not reacted to this post", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { reactionRepository.deleteReaction(any(), any(), any()) }
    }

    @Test
    fun `unlike does not broadcast`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns true
        coEvery { reactionRepository.deleteReaction(any(), any(), any()) } just Runs

        useCase(postId = "post-1")

        // No sync port calls should happen - TTL-based expiry
        coVerify(exactly = 0) { reactionRepository.insertReaction(any()) }
    }

    @Test
    fun `unlike calls delete with correct parameters`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns true
        coEvery { reactionRepository.deleteReaction("post-1", "device-1", ReactionType.LIKE) } just Runs

        useCase(postId = "post-1", type = ReactionType.LIKE)

        coVerify { reactionRepository.deleteReaction("post-1", "device-1", ReactionType.LIKE) }
    }

    @Test
    fun `double unlike first succeeds then fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returnsMany listOf(true, false)
        coEvery { reactionRepository.deleteReaction(any(), any(), any()) } just Runs

        val first = useCase(postId = "post-1")
        assertTrue(first.isSuccess)

        val second = useCase(postId = "post-1")
        assertTrue(second.isFailure)
        assertEquals("You have not reacted to this post", second.exceptionOrNull()?.message)
    }

    @Test
    fun `unlike with repository failure returns error`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns true
        coEvery { reactionRepository.deleteReaction(any(), any(), any()) } throws RuntimeException("DB error")

        val result = useCase(postId = "post-1")

        assertTrue(result.isFailure)
    }
}
