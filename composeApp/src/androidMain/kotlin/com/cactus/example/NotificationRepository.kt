package com.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

object NotificationRepository {
    private val _notifications = MutableStateFlow<List<NotificationData>>(emptyList())
    val notifications: StateFlow<List<NotificationData>> = _notifications.asStateFlow()

    fun addNotification(notification: NotificationData) {
        val currentList = _notifications.value.toMutableList()
        currentList.add(notification)
        _notifications.value = currentList
    }

    fun getTodayNotifications(): List<NotificationData> {
        val startOfDay = getStartOfDay()
        return _notifications.value.filter { it.timestamp >= startOfDay }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
