package com.neo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.data.preferences.UserPreferences
import com.neo.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _profileName = MutableStateFlow(userPreferences.userName)
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileBio = MutableStateFlow(userPreferences.userBio)
    val profileBio: StateFlow<String> = _profileBio.asStateFlow()

    val deviceId: String
        get() = cryptoManager.getDeviceId()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Idle : UiState()
        object Saving : UiState()
        object Saved : UiState()
        data class Error(val message: String) : UiState()
    }

    fun updateProfile(name: String, bio: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Saving
            try {
                userPreferences.userName = name
                userPreferences.userBio = bio
                _profileName.value = name
                _profileBio.value = bio
                _uiState.value = UiState.Saved
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}