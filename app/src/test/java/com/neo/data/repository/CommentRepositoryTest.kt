package com.neo.data.repository

import com.neo.data.dao.CommentDao
import com.neo.data.model.Comment
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CommentRepositoryTest {

    private val commentDao: CommentDao = mockk()

    private lateinit var repository: CommentRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = CommentRepository(commentDao)
    }

    @Test
    fun `getTopLevelComments delegates`() = runTest {
        coEvery { commentDao.getTopLevelCommentsForPost("post-1") } returns flowOf(emptyList())

        val result = repository.getTopLevelCommentsForPost("post-1").first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRepliesForComment delegates`() = runTest {
        coEvery { commentDao.getRepliesForComment("comment-1") } returns flowOf(emptyList())

        val result = repository.getRepliesForComment("comment-1").first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `insertComment delegates`() = runTest {
        val comment = mockk<Comment>(relaxed = true)
        coEvery { commentDao.insert(comment) } just Runs

        repository.insertComment(comment)

        coVerify { commentDao.insert(comment) }
    }

    @Test
    fun `getCommentCountForPost delegates`() = runTest {
        coEvery { commentDao.getCommentCountForPost("post-1") } returns 5

        val result = repository.getCommentCountForPost("post-1")

        assertEquals(5, result)
    }

    @Test
    fun `deleteComment delegates`() = runTest {
        coEvery { commentDao.deleteById("comment-1") } just Runs

        repository.deleteComment("comment-1")

        coVerify { commentDao.deleteById("comment-1") }
    }

    @Test
    fun `deleteCommentsForPost delegates`() = runTest {
        coEvery { commentDao.deleteCommentsForPost("post-1") } just Runs

        repository.deleteCommentsForPost("post-1")

        coVerify { commentDao.deleteCommentsForPost("post-1") }
    }

    @Test
    fun `deleteOldComments without author exclusion`() = runTest {
        coEvery { commentDao.deleteOldComments(1000L) } returns 3

        repository.deleteOldComments(1000L)

        coVerify { commentDao.deleteOldComments(1000L) }
    }

    @Test
    fun `deleteOldComments with author exclusion`() = runTest {
        coEvery { commentDao.deleteOldCommentsExcludingAuthor(1000L, "my-device") } returns 2

        repository.deleteOldComments(1000L, "my-device")

        coVerify { commentDao.deleteOldCommentsExcludingAuthor(1000L, "my-device") }
    }
}
