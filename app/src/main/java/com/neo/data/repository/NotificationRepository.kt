package com.neo.data.repository

import com.neo.data.dao.NotificationDao
import com.neo.data.model.Notification
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao
) {

    fun getAllNotifications(): Flow<List<Notification>> = notificationDao.getAllNotifications()

    fun getUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    suspend fun insert(notification: Notification) = notificationDao.insert(notification)

    suspend fun markAsRead(notificationId: String) = notificationDao.markAsRead(notificationId)

    suspend fun markAllAsRead() = notificationDao.markAllAsRead()
}
