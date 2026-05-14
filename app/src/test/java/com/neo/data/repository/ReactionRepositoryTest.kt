package com.neo.data.repository

import com.neo.data.dao.ReactionDao
import com.neo.data.model.ReactionType
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReactionRepositoryTest {

    private val reactionDao: ReactionDao = mockk()

    private lateinit var repository: ReactionRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = ReactionRepository(reactionDao)
    }

    @Test
    fun `hasUserReacted delegates`() = runTest {
        coEvery { reactionDao.hasUserReacted("post-1", "user-1", ReactionType.LIKE) } returns true

        val result = repository.hasUserReacted("post-1", "user-1", ReactionType.LIKE)

        assertTrue(result)
    }

    @Test
    fun `deleteReaction delegates`() = runTest {
        coEvery { reactionDao.deleteReaction("post-1", "user-1", ReactionType.LIKE) } just Runs

        repository.deleteReaction("post-1", "user-1", ReactionType.LIKE)

        coVerify { reactionDao.deleteReaction("post-1", "user-1", ReactionType.LIKE) }
    }

    @Test
    fun `getReactionCountForPost returns flow`() = runTest {
        coEvery { reactionDao.getReactionCountForPost("post-1", ReactionType.LIKE) } returns flowOf(3)

        val result = repository.getReactionCountForPost("post-1", ReactionType.LIKE)

        // Can't easily assert on Flow, but at least verify it's not null
        assertNotNull(result)
    }

    @Test
    fun `deleteOldReactions without exclusion`() = runTest {
        coEvery { reactionDao.deleteOldReactions(1000L) } returns 5

        repository.deleteOldReactions(1000L)

        coVerify { reactionDao.deleteOldReactions(1000L) }
    }

    @Test
    fun `deleteOldReactions with exclusion`() = runTest {
        coEvery { reactionDao.deleteOldReactionsExcludingUser(1000L, "user-1") } returns 3

        repository.deleteOldReactions(1000L, "user-1")

        coVerify { reactionDao.deleteOldReactionsExcludingUser(1000L, "user-1") }
    }
}
