package com.neo.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "neo_user_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_BIO = "user_bio"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

        private const val DEFAULT_USER_NAME = "Neo User"
        private const val DEFAULT_USER_BIO = "Decentralized social media enthusiast"
    }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
        set(value) = prefs.edit { putString(KEY_USER_NAME, value) }

    var userBio: String
        get() = prefs.getString(KEY_USER_BIO, DEFAULT_USER_BIO) ?: DEFAULT_USER_BIO
        set(value) = prefs.edit { putString(KEY_USER_BIO, value) }

    var profileImageUri: String?
        get() = prefs.getString(KEY_PROFILE_IMAGE_URI, null)
        set(value) = prefs.edit { putString(KEY_PROFILE_IMAGE_URI, value) }

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    fun resetToDefaults() {
        prefs.edit {
            putString(KEY_USER_NAME, DEFAULT_USER_NAME)
            putString(KEY_USER_BIO, DEFAULT_USER_BIO)
            putString(KEY_PROFILE_IMAGE_URI, null)
            putBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
    }
}