package com.neo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.data.model.Notification
import com.neo.data.model.PeerProfile
import com.neo.data.repository.NotificationRepository
import com.neo.data.repository.PeerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val peerProfileRepository: PeerProfileRepository
) : ViewModel() {

    val notifications: StateFlow<List<Notification>> = notificationRepository.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Peer profiles keyed by DID, for resolving the actor's avatar in each row. */
    val profilesByDid: StateFlow<Map<String, PeerProfile>> =
        peerProfileRepository.observeProfilesByDid()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val unreadCount: StateFlow<Int> = notificationRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }
}
