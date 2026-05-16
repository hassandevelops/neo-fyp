package com.neo.ui.viewmodel

import com.neo.data.model.Notification
import com.neo.data.repository.NotificationRepository
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
class NotificationsViewModelTest {

    private val notificationRepository: NotificationRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(0)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `notifications list reflects repository`() = runTest(testDispatcher) {
        every { notificationRepository.getAllNotifications() } returns MutableStateFlow(
            listOf(
                Notification(id = "n1", type = "like", message = "Liked", timestamp = 1000L),
                Notification(id = "n2", type = "comment", message = "Commented", timestamp = 2000L)
            )
        )

        viewModel = NotificationsViewModel(notificationRepository)
        backgroundScope.launch(testDispatcher) { viewModel.notifications.collect { } }
        advanceUntilIdle()

        assertEquals(2, viewModel.notifications.value.size)
    }

    @Test
    fun `empty notifications returns empty list`() = runTest(testDispatcher) {
        every { notificationRepository.getAllNotifications() } returns flowOf(emptyList())

        viewModel = NotificationsViewModel(notificationRepository)
        backgroundScope.launch(testDispatcher) { viewModel.notifications.collect { } }
        advanceUntilIdle()

        assertTrue(viewModel.notifications.value.isEmpty())
    }

    @Test
    fun `unread count reflects repository`() = runTest(testDispatcher) {
        every { notificationRepository.getAllNotifications() } returns flowOf(emptyList())
        every { notificationRepository.getUnreadCount() } returns MutableStateFlow(3)

        viewModel = NotificationsViewModel(notificationRepository)
        backgroundScope.launch(testDispatcher) { viewModel.unreadCount.collect { } }
        advanceUntilIdle()

        assertEquals(3, viewModel.unreadCount.value)
    }

    @Test
    fun `markAsRead delegates to repository`() = runTest(testDispatcher) {
        every { notificationRepository.getAllNotifications() } returns flowOf(emptyList())
        coEvery { notificationRepository.markAsRead("n1") } just Runs

        viewModel = NotificationsViewModel(notificationRepository)
        backgroundScope.launch(testDispatcher) { viewModel.notifications.collect { } }
        advanceUntilIdle()
        viewModel.markAsRead("n1")
        advanceUntilIdle()

        coVerify { notificationRepository.markAsRead("n1") }
    }

    @Test
    fun `markAllAsRead delegates to repository`() = runTest(testDispatcher) {
        every { notificationRepository.getAllNotifications() } returns flowOf(emptyList())
        coEvery { notificationRepository.markAllAsRead() } just Runs

        viewModel = NotificationsViewModel(notificationRepository)
        backgroundScope.launch(testDispatcher) { viewModel.notifications.collect { } }
        advanceUntilIdle()
        viewModel.markAllAsRead()
        advanceUntilIdle()

        coVerify { notificationRepository.markAllAsRead() }
    }
}
