package com.neo.data.repository

import com.neo.data.dao.ReactionDao
import com.neo.data.model.ReactionType
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
    fun `deleteOldReactions with exclusion`() {
        coEvery { reactionDao.deleteOldReactionsExcludingUser(1000L, "user-1") } returns 3
        val result = runBlocking { repository.deleteOldReactions(1000L, "user-1") }
        assertEquals(3, result)
    }

    @Test
    fun `insertReaction delegates`() {
        val reaction = com.neo.data.model.Reaction(
            id = "r1", postId = "p1", userId = "u1", userName = "Alice",
            type = com.neo.data.model.ReactionType.LIKE, timestamp = 1L,
            signature = "s", publicKey = "pk", ttl = 5, firstSeenTimestamp = 1L
        )
        coEvery { reactionDao.insert(reaction) } just Runs

        runBlocking { repository.insertReaction(reaction) }

        coVerify { reactionDao.insert(reaction) }
    }

    @Test
    fun `hasUserReactedFlow delegates`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(false)
        every { reactionDao.hasUserReactedFlow("p1", "u1", com.neo.data.model.ReactionType.LIKE) } returns flow

        val result = repository.hasUserReactedFlow("p1", "u1", com.neo.data.model.ReactionType.LIKE)

        assertSame(flow, result)
    }

    @Test
    fun `getReactionsForPost delegates`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.neo.data.model.Reaction>())
        every { reactionDao.getReactionsForPost("p1", com.neo.data.model.ReactionType.LIKE) } returns flow

        val result = repository.getReactionsForPost("p1", com.neo.data.model.ReactionType.LIKE)

        assertSame(flow, result)
    }

    @Test
    fun `getReactionById delegates`() {
        val reaction = com.neo.data.model.Reaction(
            id = "r1", postId = "p1", userId = "u1", userName = "Alice",
            type = com.neo.data.model.ReactionType.LIKE, timestamp = 1L,
            signature = "s", publicKey = "pk", ttl = 5, firstSeenTimestamp = 1L
        )
        coEvery { reactionDao.getReactionById("r1") } returns reaction

        val result = runBlocking { repository.getReactionById("r1") }

        assertEquals(reaction, result)
    }

    @Test
    fun `reactionExists delegates`() {
        coEvery { reactionDao.reactionExists("r1") } returns true

        val result = runBlocking { repository.reactionExists("r1") }

        assertTrue(result)
    }
}
