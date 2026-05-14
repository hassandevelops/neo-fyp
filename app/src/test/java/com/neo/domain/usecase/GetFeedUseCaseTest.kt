package com.neo.domain.usecase

import androidx.paging.PagingData
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetFeedUseCaseTest {

    private val postRepository: PostRepository = mockk()

    private lateinit var useCase: GetFeedUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = GetFeedUseCase(postRepository = postRepository)
    }

    @Test
    fun `execute returns all posts from repository`() = runTest {
        val posts = listOf(
            Post(id = "1", authorId = "a1", authorName = "Alice", content = "Hello", timestamp = 1000L, signature = "sig1", publicKey = "pk1", firstSeenTimestamp = 1000L),
            Post(id = "2", authorId = "a2", authorName = "Bob", content = "World", timestamp = 2000L, signature = "sig2", publicKey = "pk2", firstSeenTimestamp = 2000L)
        )
        every { postRepository.getAllPosts() } returns flowOf(posts)

        val result = useCase.execute().first()

        assertEquals(2, result.size)
        assertEquals("Hello", result[0].content)
        assertEquals("World", result[1].content)
    }

    @Test
    fun `execute returns empty list when no posts`() = runTest {
        every { postRepository.getAllPosts() } returns flowOf(emptyList())

        val result = useCase.execute().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `executePaged delegates to repository`() = runTest {
        val pagingFlow = mockk<Flow<PagingData<Post>>>()
        every { postRepository.getPostsPaged() } returns pagingFlow

        val result = useCase.executePaged()

        assertSame(pagingFlow, result)
        verify { postRepository.getPostsPaged() }
    }
}
