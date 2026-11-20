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
        val isLow = priority.equals("LOW", ignoreCase = true)

        val tokenManager = TokenManager(applicationContext)
        val role = kotlinx.coroutines.runBlocking {
            try { tokenManager.activeRole()?.uppercase() } catch (_: Exception) { null }
        }
        val isTeacher = role != null && role != "STUDENT"

        val shouldSkipPopup = if (isTeacher) {
            val (pending, upcoming, leave, announce) = kotlinx.coroutines.runBlocking {
                try {
                    listOf(
                        tokenManager.isNotifyTeacherPendingAttendanceEnabled(),
                        tokenManager.isNotifyTeacherUpcomingClassEnabled(),
                        tokenManager.isNotifyTeacherLeaveRequestsEnabled(),
                        tokenManager.isNotifyTeacherAnnouncementsEnabled()
                    )
                } catch (_: Exception) {
                    listOf(true, true, true, true)
                }
            }
            val isPending = title.contains("Pending", ignoreCase = true) || body.contains("Pending", ignoreCase = true)
            val isUpcoming = title.contains("Lecture", ignoreCase = true) || title.contains("Schedule", ignoreCase = true) || (title.contains("Class", ignoreCase = true) && !title.contains("Cancelled", ignoreCase = true))
            val isLeave = title.contains("Leave", ignoreCase = true) || body.contains("Leave", ignoreCase = true)

            when {
                isPending && !pending -> true
                isUpcoming && !upcoming -> true
                isLeave && !leave -> true
                !isPending && !isUpcoming && !isLeave && !announce -> true
                else -> false
            }
        } else {
            val (notifyOnPresent, notifyOnAbsent, notifyOnCorrection) = kotlinx.coroutines.runBlocking {
                try {
                    Triple(
                        tokenManager.isNotifyOnPresentEnabled(),
                        tokenManager.isNotifyOnAbsentEnabled(),
                        tokenManager.isNotifyOnCorrectionEnabled()
                    )
                } catch (_: Exception) {
                    Triple(false, true, false)
                }
            }

            val isCorrection = title.contains("Corrected", ignoreCase = true) || title.contains("Updated", ignoreCase = true)
            val isAbsentAlert = title.contains("Absent", ignoreCase = true) || body.contains("Absent", ignoreCase = true)

            when {
                isLow && !notifyOnPresent -> true
                isCorrection && !notifyOnCorrection -> true
                isAbsentAlert && !notifyOnAbsent -> true
                else -> false
            }
        }

        if (shouldSkipPopup) {
            Log.d(TAG, "Skipping system tray notification popup per user preference: title='$title'")
        } else {
            NotificationHelper.showAttendanceNotification(
                context = applicationContext,
                title = title,
                message = body,
                isAbsent = isUrgent,
                isSilent = isLow
            )
        }

        // 2. Automatically refresh in-app notification inbox
        AttendanceNotificationManager.init(applicationContext)
        AttendanceNotificationManager.fetchHistory()
    }

    companion object {
        private const val TAG = "AttendanceFCM"
    }
}
