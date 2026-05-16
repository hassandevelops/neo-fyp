package com.neo.ui.viewmodel

import com.neo.data.model.ReactionType
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.ReactionRepository
import com.neo.domain.usecase.CreateCommentUseCase
import com.neo.domain.usecase.CreateReactionUseCase
import com.neo.domain.usecase.DeleteReactionUseCase
import com.neo.security.CryptoManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {

    private lateinit var commentRepository: CommentRepository
    private lateinit var createCommentUseCase: CreateCommentUseCase
    private lateinit var reactionRepository: ReactionRepository
    private lateinit var createReactionUseCase: CreateReactionUseCase
    private lateinit var deleteReactionUseCase: DeleteReactionUseCase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var notificationRepository: NotificationRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        commentRepository = mockk()
        createCommentUseCase = mockk()
        reactionRepository = mockk()
        createReactionUseCase = mockk()
        deleteReactionUseCase = mockk()
        cryptoManager = mockk(relaxed = true)
        notificationRepository = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PostDetailViewModel {
        return PostDetailViewModel(
            application = mockk(relaxed = true),
            commentRepository = commentRepository,
            createCommentUseCase = createCommentUseCase,
            reactionRepository = reactionRepository,
            createReactionUseCase = createReactionUseCase,
            deleteReactionUseCase = deleteReactionUseCase,
            cryptoManager = cryptoManager,
            notificationRepository = notificationRepository
        )
    }

    @Test
    fun `create comment success updates ui state`() = runTest {
        coEvery { createCommentUseCase(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = createViewModel()

        vm.createComment(postId = "post-1", content = "Nice!", authorName = "Alice")

        assertTrue(vm.uiState.value is PostDetailViewModel.UiState.Success)
    }

    @Test
    fun `create comment failure updates ui state`() = runTest {
        coEvery { createCommentUseCase(any(), any(), any(), any()) } returns Result.failure(IllegalArgumentException("Too long"))
        val vm = createViewModel()

        vm.createComment(postId = "post-1", content = "A".repeat(600), authorName = "Alice")

        assertTrue(vm.uiState.value is PostDetailViewModel.UiState.Error)
    }

    @Test
    fun `toggle like when not liked creates reaction`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns false
        coEvery { createReactionUseCase("post-1", "Alice", ReactionType.LIKE) } returns Result.success(mockk(relaxed = true))
        val vm = createViewModel()

        vm.toggleLike(postId = "post-1", userName = "Alice")

        coVerify { createReactionUseCase("post-1", "Alice", ReactionType.LIKE) }
    }

    @Test
    fun `toggle like when liked deletes reaction`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns true
        coEvery { deleteReactionUseCase("post-1", ReactionType.LIKE) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.toggleLike(postId = "post-1", userName = "Alice")

        coVerify { deleteReactionUseCase("post-1", ReactionType.LIKE) }
    }

    @Test
    fun `toggle like failure calls error callback`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted(any(), any(), any()) } returns false
        coEvery { createReactionUseCase(any(), any(), any()) } returns Result.failure(Exception("Network error"))
        val vm = createViewModel()

        var errorMessage = ""
        vm.toggleLike(postId = "post-1", userName = "Alice", onError = { errorMessage = it })

        assertEquals("Network error", errorMessage)
    }

    @Test
    fun `get top level comments delegates to repository`() {
        val vm = createViewModel()
        val flow = MutableStateFlow(emptyList<com.neo.data.model.Comment>())
        every { commentRepository.getTopLevelCommentsForPost("post-1") } returns flow

        val result = vm.getTopLevelCommentsForPost("post-1")

        assertSame(flow, result)
    }

    @Test
    fun `get replies delegates to repository`() {
        val vm = createViewModel()
        val flow = MutableStateFlow(emptyList<com.neo.data.model.Comment>())
        every { commentRepository.getRepliesForComment("comment-1") } returns flow

        val result = vm.getRepliesForComment("comment-1")

        assertSame(flow, result)
    }

    @Test
    fun `reset ui state returns to Idle`() {
        val vm = createViewModel()
        vm.resetUiState()

        assertTrue(vm.uiState.value is PostDetailViewModel.UiState.Idle)
    }

    @Test
    fun `hasUserLikedPostFlow delegates to repository`() {
        val vm = createViewModel()
        every { cryptoManager.getDeviceId() } returns "device-1"
        val flow = MutableStateFlow(false)
        every { reactionRepository.hasUserReactedFlow("post-1", "device-1", ReactionType.LIKE) } returns flow

        val result = vm.hasUserLikedPostFlow("post-1")

        assertSame(flow, result)
    }

    @Test
    fun `create comment inserts notification on success`() = runTest {
        coEvery { createCommentUseCase(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { notificationRepository.insert(any()) } just Runs
        val vm = createViewModel()

        vm.createComment(postId = "post-1", content = "Nice!", authorName = "Alice")

        coVerify { notificationRepository.insert(match { it.type == "comment" }) }
    }

    @Test
    fun `toggle like inserts notification for new like`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns false
        coEvery { createReactionUseCase("post-1", "Alice", ReactionType.LIKE) } returns Result.success(mockk(relaxed = true))
        coEvery { notificationRepository.insert(any()) } just Runs
        val vm = createViewModel()

        vm.toggleLike(postId = "post-1", userName = "Alice")

        coVerify { notificationRepository.insert(match { it.type == "like" }) }
    }

    @Test
    fun `toggle like does not insert notification for unlike`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { reactionRepository.hasUserReacted("post-1", "device-1", ReactionType.LIKE) } returns true
        coEvery { deleteReactionUseCase("post-1", ReactionType.LIKE) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.toggleLike(postId = "post-1", userName = "Alice")

        coVerify(exactly = 0) { notificationRepository.insert(any()) }
    }
}
