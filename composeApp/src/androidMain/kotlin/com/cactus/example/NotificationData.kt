package com.cactus.example

data class NotificationData(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
