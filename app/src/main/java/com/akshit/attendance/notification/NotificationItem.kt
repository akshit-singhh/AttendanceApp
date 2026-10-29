package com.akshit.attendance.notification

import kotlinx.serialization.Serializable

@Serializable
data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val category: String = "ATTENDANCE", // "ATTENDANCE", "ACADEMIC", "TIMETABLE"
    val isRead: Boolean = false,
    val rawTime: Long = 0L
)
