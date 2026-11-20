package com.akshit.attendance.notification

import android.content.Context
import android.util.Log
import com.akshit.attendance.data.network.RetrofitClient
import com.akshit.attendance.data.network.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AttendanceNotificationManager {

    private const val TAG = "AttendanceNotification"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Synced")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Start / bootstrap notification tracking for a student.
     * With FCM, notifications arrive via push; this method simply bootstraps the inbox history.
     */
    fun startListening(context: Context, rollNumber: String) {
        appContext = context.applicationContext
        _connectionStatus.value = "FCM Active"
        fetchHistory()
    }

    fun stopListening() {
        // No persistent socket connection needed with Firebase FCM
    }

    /**
     * Fetches notification history directly from the backend API (suspend version for UI callers).
     */
    suspend fun refreshHistory() {
        val ctx = appContext ?: return
        _isLoading.value = true
        try {
            _connectionStatus.value = "Syncing..."
            val tokenManager = TokenManager(ctx)
            val token = tokenManager.accessToken()
            if (token.isNullOrBlank()) {
                _connectionStatus.value = "Not logged in"
                return
            }

            val api = RetrofitClient.get(tokenManager)
            val response = api.getMyNotifications(page = 1, size = 50)

            val items = response.items.map { rec ->
                val category = when (rec.notification_type.uppercase()) {
                    "ATTENDANCE_ALERT" -> "ATTENDANCE"
                    "TIMETABLE" -> "TIMETABLE"
                    "ACADEMIC_ORDER", "CIRCULAR" -> "NOTICE"
                    "ANNOUNCEMENT", "GENERAL" -> "NOTICE"
                    else -> "NOTICE"
                }

                val formattedTime = formatTimestamp(rec.created_at)

                NotificationItem(
                    id = rec.id.toString(),
                    title = rec.title,
                    message = rec.message,
                    timestamp = formattedTime,
                    category = category,
                    isRead = rec.is_read
                )
            }

            _notifications.value = items
            _connectionStatus.value = "Synced"
            Log.i(TAG, "Successfully synced ${items.size} notifications from backend API.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load notification history from backend: ${e.message}")
            _connectionStatus.value = "Sync failed"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Non-blocking background fetch helper.
     */
    fun fetchHistory(topic: String = "") {
        scope.launch {
            refreshHistory()
        }
    }

    /**
     * Mark a single notification recipient item as read.
     */
    fun markAsRead(id: String) {
        val ctx = appContext ?: return
        val recipientId = id.toIntOrNull() ?: return

        // Optimistically mark as read in UI
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }

        scope.launch {
            try {
                val tokenManager = TokenManager(ctx)
                val api = RetrofitClient.get(tokenManager)
                api.markNotificationRead(recipientId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark notification $id as read: ${e.message}")
            }
        }
    }

    /**
     * Mark all notifications as read.
     */
    fun markAllAsRead() {
        val ctx = appContext ?: return

        // Optimistically mark all as read in UI
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }

        scope.launch {
            try {
                val tokenManager = TokenManager(ctx)
                val api = RetrofitClient.get(tokenManager)
                api.markAllNotificationsRead()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark all notifications as read: ${e.message}")
            }
        }
    }

    private fun formatTimestamp(isoString: String): String {
        return try {
            val cleaned = if (!isoString.endsWith("Z") && !isoString.contains("+")) isoString + "Z" else isoString
            val instant = try {
                Instant.parse(cleaned)
            } catch (_: Exception) {
                java.time.LocalDateTime.parse(isoString.substringBefore("Z").substringBefore("+")).atZone(ZoneId.systemDefault()).toInstant()
            }
            val formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            isoString
        }
    }
}
