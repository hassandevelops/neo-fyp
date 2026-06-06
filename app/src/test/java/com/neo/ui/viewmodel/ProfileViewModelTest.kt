package com.neo.ui.viewmodel

import com.neo.data.preferences.UserPreferences
import com.neo.data.repository.PostRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.security.CryptoManager
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
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    private val userPreferences = UserPreferences(RuntimeEnvironment.getApplication())
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val postRepository: PostRepository = mockk()
    private val savedPostRepository: SavedPostRepository = mockk()
    private val syncPort: ISyncPort = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        userPreferences.resetToDefaults()
        every { cryptoManager.getDeviceId() } returns "device-123"
        every { postRepository.getPostCountForAuthor("device-123") } returns flowOf(0)
        every { postRepository.getPostsByAuthor("device-123") } returns flowOf(emptyList())
        every { postRepository.getAllPosts() } returns flowOf(emptyList())
        every { syncPort.connectedPeersCount } returns MutableStateFlow(0)
        every { savedPostRepository.observeSavedPostIds() } returns MutableStateFlow(emptySet())

        viewModel = ProfileViewModel(RuntimeEnvironment.getApplication(), userPreferences, cryptoManager, postRepository, savedPostRepository, syncPort)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial profile name from preferences`() {
        assertEquals("Neo User", viewModel.profileName.value)
    }

    @Test
    fun `initial profile bio from preferences`() {
        assertEquals("Decentralized social media enthusiast", viewModel.profileBio.value)
    }

    @Test
    fun `device ID from crypto manager`() {
        assertEquals("device-123", viewModel.deviceId)
    }

    @Test
    fun `update profile saves and updates state`() = runTest(testDispatcher) {
        viewModel.updateProfile("New Name", "New bio")
        advanceUntilIdle()

        assertEquals("New Name", viewModel.profileName.value)
        assertEquals("New bio", viewModel.profileBio.value)
    }

    @Test
    fun `update profile sets saved state`() = runTest(testDispatcher) {
        viewModel.updateProfile("Name", "Bio")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProfileViewModel.UiState.Saved)
    }

    @Test
    fun `reset ui state returns to Idle`() {
        viewModel.updateProfile("New Name", "New Bio")
        viewModel.resetUiState()
        assertEquals(ProfileViewModel.UiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `handle is derived from profile name and device id`() = runTest(testDispatcher) {
        backgroundScope.launch(testDispatcher) { viewModel.handle.collect { } }
        advanceUntilIdle()
        val handle = viewModel.handle.value
        assertTrue(handle.startsWith("@"))
        assertTrue(handle.contains("neo"))
        assertTrue(handle.contains("device-123".takeLast(4)))
    }

    @Test
    fun `handle includes last 4 chars of device id`() = runTest(testDispatcher) {
        backgroundScope.launch(testDispatcher) { viewModel.handle.collect { } }
        advanceUntilIdle()
        val handle = viewModel.handle.value
        assertEquals("device-123".takeLast(4), handle.takeLast(4))
    }

    @Test
    fun `postCount reflects repository`() = runTest(testDispatcher) {
        backgroundScope.launch(testDispatcher) { viewModel.postCount.collect { } }
        advanceUntilIdle()
        assertEquals(0, viewModel.postCount.value)
    }

    @Test
    fun `userPosts reflects repository`() = runTest(testDispatcher) {
        backgroundScope.launch(testDispatcher) { viewModel.userPosts.collect { } }
        advanceUntilIdle()
        assertTrue(viewModel.userPosts.value.isEmpty())
    }

    @Test
    fun `connectedPeersCount reflects sync port`() = runTest(testDispatcher) {
        backgroundScope.launch(testDispatcher) { viewModel.connectedPeersCount.collect { } }
        advanceUntilIdle()
        assertEquals(0, viewModel.connectedPeersCount.value)
    }

    @Test
    fun `updateProfile error sets Error state`() = runTest(testDispatcher) {
        val failingPrefs = mockk<UserPreferences>(relaxed = true)
        every { failingPrefs.userName } returns "Test"
        every { failingPrefs.userBio } returns "Bio"
        every { failingPrefs.userName = any() } throws RuntimeException("Save failed")

        val vm = ProfileViewModel(RuntimeEnvironment.getApplication(), failingPrefs, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        vm.updateProfile("Name", "Bio")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProfileViewModel.UiState.Error)
        assertEquals("Save failed", (vm.uiState.value as ProfileViewModel.UiState.Error).message)
    }
}
