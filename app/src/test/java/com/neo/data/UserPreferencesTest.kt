package com.neo.data

import android.content.Context
import android.content.SharedPreferences
import com.neo.data.preferences.UserPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UserPreferencesTest {

    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        userPreferences = UserPreferences(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `default user name`() {
        assertEquals("Neo User", userPreferences.userName)
    }

    @Test
    fun `default user bio`() {
        assertEquals("Decentralized social media enthusiast", userPreferences.userBio)
    }

    @Test
    fun `onboarding complete default is false`() {
        assertFalse(userPreferences.isOnboardingComplete)
    }

    @Test
    fun `set user name persists`() {
        userPreferences.userName = "New Name"
        assertEquals("New Name", userPreferences.userName)
    }

    @Test
    fun `set onboarding persists`() {
        userPreferences.isOnboardingComplete = true
        assertTrue(userPreferences.isOnboardingComplete)
    }

    @Test
    fun `reset to defaults`() {
        userPreferences.userName = "Custom"
        userPreferences.resetToDefaults()
        assertEquals("Neo User", userPreferences.userName)
    }
}
