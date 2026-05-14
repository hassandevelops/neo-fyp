package com.neo.ui.viewmodel

import com.neo.data.preferences.UserPreferences
import com.neo.security.CryptoManager
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
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    private val userPreferences = UserPreferences(RuntimeEnvironment.getApplication())
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        every { cryptoManager.getDeviceId() } returns "device-123"

        viewModel = ProfileViewModel(userPreferences, cryptoManager)
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
        viewModel.resetUiState()

        assertTrue(viewModel.uiState.value is ProfileViewModel.UiState.Idle)
    }
}
