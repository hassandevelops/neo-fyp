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
        private const val KEY_PROFILE_UPDATED_AT = "profile_updated_at"
        private const val KEY_BOOTSTRAP_MULTIADDR = "bootstrap_multiaddr"
        private const val KEY_BOOTSTRAP_ENABLED = "bootstrap_enabled"

        private const val DEFAULT_USER_NAME = "Neo User"
        private const val DEFAULT_USER_BIO = "Decentralized social media enthusiast"
    }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
        set(value) = prefs.edit { putString(KEY_USER_NAME, value) }

    /** True once the user has explicitly chosen a username (e.g. during onboarding). */
    val isUserNameSet: Boolean
        get() = prefs.contains(KEY_USER_NAME)

    var userBio: String
        get() = prefs.getString(KEY_USER_BIO, DEFAULT_USER_BIO) ?: DEFAULT_USER_BIO
        set(value) = prefs.edit { putString(KEY_USER_BIO, value) }

    var profileImageUri: String?
        get() = prefs.getString(KEY_PROFILE_IMAGE_URI, null)
        set(value) = prefs.edit { putString(KEY_PROFILE_IMAGE_URI, value) }

    /** Local timestamp of the last profile edit — drives "latest-wins" profile sync. */
    var profileUpdatedAt: Long
        get() = prefs.getLong(KEY_PROFILE_UPDATED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_PROFILE_UPDATED_AT, value) }

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    var bootstrapMultiaddr: String?
        get() = prefs.getString(KEY_BOOTSTRAP_MULTIADDR, null)
        set(value) = prefs.edit { putString(KEY_BOOTSTRAP_MULTIADDR, value) }

    var bootstrapEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOTSTRAP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_BOOTSTRAP_ENABLED, value) }

    /**
     * Observe changes to the bootstrap override. Invokes [onChange] with the new
     * multiaddr whenever it is set/changed (and enabled), so the running libp2p
     * node can apply it live — e.g. right after a QR scan — without a restart.
     * Returns the listener so the caller can unregister it.
     */
    fun observeBootstrap(onChange: (String) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOOTSTRAP_MULTIADDR || key == KEY_BOOTSTRAP_ENABLED) {
                val addr = bootstrapMultiaddr
                if (bootstrapEnabled && !addr.isNullOrBlank()) onChange(addr)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun resetToDefaults() {
        prefs.edit {
            putString(KEY_USER_NAME, DEFAULT_USER_NAME)
            putString(KEY_USER_BIO, DEFAULT_USER_BIO)
            putString(KEY_PROFILE_IMAGE_URI, null)
            putBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
    }
}