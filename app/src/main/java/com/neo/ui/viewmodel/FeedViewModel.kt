package com.neo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.neo.data.model.Post
import com.neo.domain.port.ISyncPort
import com.neo.domain.usecase.GetFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    application: Application,
    private val getFeedUseCase: GetFeedUseCase,
    private val syncPort: ISyncPort
) : AndroidViewModel(application) {

    val posts: StateFlow<List<Post>> = getFeedUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pagedPosts: Flow<PagingData<Post>> = getFeedUseCase.executePaged()
        .cachedIn(viewModelScope)

    val connectedPeersCount: StateFlow<Int> = syncPort.connectedPeersCount
}