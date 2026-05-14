package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neo.domain.usecase.CreatePostUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Success(val message: String) : UiState()
    }

    fun createPost(content: String, authorName: String, imageUri: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val result = createPostUseCase(content, authorName, imageUri)

            if (result.isSuccess) {
                _uiState.value = UiState.Success("Post created successfully!")
            } else {
                _uiState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to create post")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
}