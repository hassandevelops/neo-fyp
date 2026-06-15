package com.neo.data.repository

import com.neo.data.dao.PostDao
import com.neo.data.model.Post
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PostRepositoryTest {

    private val postDao: PostDao = mockk()

    private lateinit var repository: PostRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = PostRepository(postDao)
    }

    @Test
    fun `getAllPosts returns posts from DAO`() = runTest {
        val posts = listOf(
            Post("1", "a1", "Alice", "Hello", null, null, null, null, null, 1000L, "sig", "pk", ttl = 7, firstSeenTimestamp = 1000L)
        )
        coEvery { postDao.getAllPosts() } returns flowOf(posts)

        val result = repository.getAllPosts().first()

        assertEquals(1, result.size)
    }

    @Test
    fun `insertPost returns true on success`() = runTest {
        val post = Post("1", "a1", "Alice", "Hello", null, null, null, null, null, 1000L, "sig", "pk", ttl = 7, firstSeenTimestamp = 1000L)
        coEvery { postDao.insert(post) } returns 1L

        val result = repository.insertPost(post)

        assertTrue(result)
    }

    @Test
    fun `insertPost returns false on duplicate`() = runTest {
        val post = Post("1", "a1", "Alice", "Hello", null, null, null, null, null, 1000L, "sig", "pk", ttl = 7, firstSeenTimestamp = 1000L)
        coEvery { postDao.insert(post) } returns -1L

        val result = repository.insertPost(post)

        assertFalse(result)
    }

    @Test
    fun `getPostsAfter returns filtered posts`() = runTest {
        coEvery { postDao.getPostsAfter(1000L) } returns emptyList()

        val result = repository.getPostsAfter(1000L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `postExists delegates to DAO`() = runTest {
        coEvery { postDao.postExists("1") } returns true

        assertTrue(repository.postExists("1"))
    }

    @Test
    fun `deleteOldPosts without author exclusion`() = runTest {
        coEvery { postDao.deleteOldPosts(1000L) } returns 3

        val result = repository.deleteOldPosts(1000L)

        assertEquals(3, result)
        coVerify { postDao.deleteOldPosts(1000L) }
    }

    @Test
    fun `deleteOldPosts with author exclusion`() = runTest {
        coEvery { postDao.deleteOldPostsExcludingAuthor(1000L, "my-device") } returns 2

        val result = repository.deleteOldPosts(1000L, "my-device")

        assertEquals(2, result)
        coVerify { postDao.deleteOldPostsExcludingAuthor(1000L, "my-device") }
    }

    @Test
    fun `getPostCount returns count`() = runTest {
        coEvery { postDao.getPostCount() } returns 42

        val result = repository.getPostCount()

        assertEquals(42, result)
    }

    @Test
    fun `updatePost delegates to DAO`() = runTest {
        val post = Post("1", "a1", "Alice", "Updated", null, null, null, null, null, 1000L, "sig", "pk", ttl = 7, firstSeenTimestamp = 1000L)
        coEvery { postDao.update(post) } just Runs

        repository.updatePost(post)

        coVerify { postDao.update(post) }
    }

    @Test
    fun `paging config values`() {
        assertEquals(20, PostRepository.PAGE_SIZE)
        assertEquals(5, PostRepository.PREFETCH_DISTANCE)
        assertEquals(40, PostRepository.INITIAL_LOAD_SIZE)
    }
}
