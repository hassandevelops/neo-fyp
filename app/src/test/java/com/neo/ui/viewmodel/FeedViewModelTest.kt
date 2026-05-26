package com.neo.ui.viewmodel

import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.domain.usecase.GetFeedUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedViewModelTest {

    private val getFeedUseCase: GetFeedUseCase = mockk()
    private val syncPort: ISyncPort = mockk()
    private val notificationRepository: NotificationRepository = mockk()
    private val savedPostRepository: SavedPostRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: FeedViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        every { syncPort.connectedPeers } returns MutableStateFlow(emptyList())
        every { savedPostRepository.observeSavedPostIds() } returns MutableStateFlow(emptySet())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connected peers count reflects sync port`() = runTest(testDispatcher) {
        val peersCount = MutableStateFlow(5)
        every { getFeedUseCase.executePaged() } returns flowOf()
        every { syncPort.connectedPeersCount } returns peersCount
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(0)

        viewModel = FeedViewModel(mockk(relaxed = true), getFeedUseCase, syncPort, notificationRepository, savedPostRepository)
        backgroundScope.launch(testDispatcher) { viewModel.connectedPeersCount.collect { } }
        advanceUntilIdle()

        assertEquals(5, viewModel.connectedPeersCount.value)
    }

    @Test
    fun `paged posts delegates to use case`() = runTest(testDispatcher) {
        every { getFeedUseCase.executePaged() } returns flowOf()
        every { syncPort.connectedPeersCount } returns MutableStateFlow(0)
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(0)

        viewModel = FeedViewModel(mockk(relaxed = true), getFeedUseCase, syncPort, notificationRepository, savedPostRepository)
        advanceUntilIdle()

        assertNotNull(viewModel.pagedPosts)
        verify { getFeedUseCase.executePaged() }
    }

    @Test
    fun `isRefreshing starts false`() = runTest(testDispatcher) {
        every { getFeedUseCase.executePaged() } returns flowOf()
        every { syncPort.connectedPeersCount } returns MutableStateFlow(0)
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(0)

        viewModel = FeedViewModel(mockk(relaxed = true), getFeedUseCase, syncPort, notificationRepository, savedPostRepository)
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh sets and clears isRefreshing`() = runTest(testDispatcher) {
        every { getFeedUseCase.executePaged() } returns flowOf()
        every { syncPort.connectedPeersCount } returns MutableStateFlow(0)
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(0)

        viewModel = FeedViewModel(mockk(relaxed = true), getFeedUseCase, syncPort, notificationRepository, savedPostRepository)
        backgroundScope.launch(testDispatcher) { viewModel.isRefreshing.collect { } }
        advanceUntilIdle()

        viewModel.refresh()
        advanceTimeBy(100)
        assertTrue(viewModel.isRefreshing.value)

        advanceTimeBy(300)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `notificationCount reflects repository`() = runTest(testDispatcher) {
        every { getFeedUseCase.executePaged() } returns flowOf()
        every { syncPort.connectedPeersCount } returns MutableStateFlow(0)
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(5)

        viewModel = FeedViewModel(mockk(relaxed = true), getFeedUseCase, syncPort, notificationRepository, savedPostRepository)
        backgroundScope.launch(testDispatcher) { viewModel.notificationCount.collect { } }
        advanceUntilIdle()

        assertEquals(5, viewModel.notificationCount.value)
    }
}
