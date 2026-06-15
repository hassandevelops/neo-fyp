package com.neo.ui.viewmodel

import android.app.Application
import com.neo.domain.usecase.CreatePostUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreatePostViewModelTest {

    private lateinit var createPostUseCase: CreatePostUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createPostUseCase = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create post success updates ui state to Success`() = runTest(testDispatcher) {
        coEvery { createPostUseCase(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Content", "Author")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CreatePostViewModel.UiState.Success)
        assertEquals("Post shared with mesh", (vm.uiState.value as CreatePostViewModel.UiState.Success).message)
    }

    @Test
    fun `create post failure updates ui state to Error`() = runTest(testDispatcher) {
        coEvery { createPostUseCase(any(), any(), any(), any()) } returns Result.failure(IllegalArgumentException("Content too long"))
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Very long content", "Author")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CreatePostViewModel.UiState.Error)
        assertEquals("Content too long", (vm.uiState.value as CreatePostViewModel.UiState.Error).message)
    }

    @Test
    fun `create post with null image uri`() = runTest(testDispatcher) {
        coEvery { createPostUseCase("Content", "Author", null, null) } returns Result.success(mockk(relaxed = true))
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Content", "Author", null)
        advanceUntilIdle()

        coVerify { createPostUseCase("Content", "Author", null, null) }
    }

    @Test
    fun `create post with image uri`() = runTest(testDispatcher) {
        coEvery { createPostUseCase("Content", "Author", "content://image.jpg", null) } returns Result.success(mockk(relaxed = true))
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Content", "Author", "content://image.jpg")
        advanceUntilIdle()

        coVerify { createPostUseCase("Content", "Author", "content://image.jpg", null) }
    }

    @Test
    fun `reset ui state returns to Idle`() = runTest(testDispatcher) {
        coEvery { createPostUseCase(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Content", "Author")
        advanceUntilIdle()
        vm.resetUiState()

        assertTrue(vm.uiState.value is CreatePostViewModel.UiState.Idle)
    }

    @Test
    fun `failed post falls back to default error message`() = runTest(testDispatcher) {
        coEvery { createPostUseCase(any(), any(), any(), any()) } returns Result.failure(Exception())
        val vm = CreatePostViewModel(mockk(relaxed = true), createPostUseCase)

        vm.createPost("Content", "Author")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CreatePostViewModel.UiState.Error)
        assertEquals("Failed to create post", (vm.uiState.value as CreatePostViewModel.UiState.Error).message)
    }
}
