package com.akshit.attendance.notification

import android.util.Log
import com.akshit.attendance.data.model.DeviceTokenRegisterRequest
import com.akshit.attendance.data.network.RetrofitClient
import com.akshit.attendance.data.network.TokenManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AttendanceFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New Firebase FCM registration token generated: $token")

        // Sync new token with backend if user is logged in
        serviceScope.launch {
            try {
                val tokenManager = TokenManager(applicationContext)
                val accessToken = tokenManager.accessToken()
                if (!accessToken.isNullOrBlank()) {
                    val api = RetrofitClient.get(tokenManager)
                    api.registerDeviceToken(DeviceTokenRegisterRequest(fcm_token = token, device_type = "android"))
                    Log.d(TAG, "FCM token synced to backend successfully.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync refreshed FCM token to backend: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Extract Title and Body from notification payload or custom data dictionary
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "University Notice"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["message"]
            ?: remoteMessage.data["body"]
            ?: "New announcement received."

        val priority = remoteMessage.data["priority"] ?: "NORMAL"
        val isUrgent = priority.equals("URGENT", ignoreCase = true) || priority.equals("HIGH", ignoreCase = true)

        // 1. Trigger Heads-Up Notification via existing NotificationHelper
        NotificationHelper.showAttendanceNotification(
            context = applicationContext,
            title = title,
            message = body,
            isAbsent = isUrgent
        )

        // 2. Automatically refresh in-app notification inbox
        AttendanceNotificationManager.init(applicationContext)
        AttendanceNotificationManager.fetchHistory()
    }

    companion object {
        private const val TAG = "AttendanceFCM"
    }
}
