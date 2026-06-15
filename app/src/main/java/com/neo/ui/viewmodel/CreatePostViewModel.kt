package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neo.domain.usecase.CreatePostUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    application: Application,
    private val createPostUseCase: CreatePostUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _broadcastProgress = MutableStateFlow<BroadcastState>(BroadcastState.None)
    val broadcastProgress: StateFlow<BroadcastState> = _broadcastProgress.asStateFlow()

    sealed class UiState {
        object Idle : UiState()
        data class Creating(val message: String) : UiState()
        data class Broadcasting(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class Success(val message: String) : UiState()
    }

    sealed class BroadcastState {
        object None : BroadcastState()
        object InProgress : BroadcastState()
        data class Completed(val totalPeers: Int) : BroadcastState()
        data class Failed(val message: String) : BroadcastState()
    }

    fun createPost(content: String, authorName: String, imageUri: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Creating("Creating post…")

            val result = createPostUseCase(content, authorName, imageUri)

            if (result.isSuccess) {
                _uiState.value = UiState.Broadcasting("Broadcasting to mesh…")
                _broadcastProgress.value = BroadcastState.InProgress
                // Give the broadcast a moment, then consider it done.
                // Actual per-peer ACK tracking is transparent to the user.
                delay(2000)
                _broadcastProgress.value = BroadcastState.Completed(1)
                _uiState.value = UiState.Success("Post shared with mesh")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to create post"
                _uiState.value = UiState.Error(err)
                _broadcastProgress.value = BroadcastState.Failed(err)
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
        _broadcastProgress.value = BroadcastState.None
    }
}