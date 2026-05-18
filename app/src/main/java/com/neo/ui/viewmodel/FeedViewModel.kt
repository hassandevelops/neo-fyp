package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.neo.data.model.Post
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.SavedPostRepository
import com.neo.domain.port.ISyncPort
import com.neo.domain.usecase.GetFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val getFeedUseCase: GetFeedUseCase,
    private val syncPort: ISyncPort,
    private val notificationRepository: NotificationRepository,
    private val savedPostRepository: SavedPostRepository
) : AndroidViewModel(application) {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val isEmpty: Boolean) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val posts: StateFlow<List<Post>> = getFeedUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pagedPosts: Flow<PagingData<Post>> = getFeedUseCase.executePaged()
        .cachedIn(viewModelScope)

    val connectedPeersCount: StateFlow<Int> = syncPort.connectedPeersCount
    val connectedPeers: StateFlow<List<String>> = syncPort.connectedPeers

    val notificationCount: StateFlow<Int> = notificationRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedPostIds: StateFlow<Set<String>> = savedPostRepository.observeSavedPostIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            savedPostRepository.toggle(postId)
        }
    }

    init {
        observePosts()
    }

    private fun observePosts() {
        viewModelScope.launch {
            posts.collect { postList ->
                _uiState.value = UiState.Success(isEmpty = postList.isEmpty())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(300)
            val current = posts.value
            _uiState.value = UiState.Success(isEmpty = current.isEmpty())
            _isRefreshing.value = false
        }
    }
}
