package com.akshit.attendance.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.akshit.attendance.MainActivity
import com.akshit.attendance.R

object NotificationHelper {

    const val CHANNEL_ID = "university_broadcasts"
    const val CHANNEL_NAME = "University Broadcasts"
    const val CHANNEL_ATTENDANCE_ALERTS = "attendance_alerts"
    const val CHANNEL_ATTENDANCE_UPDATES = "attendance_updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val highImportance = NotificationManager.IMPORTANCE_HIGH
            val lowImportance = NotificationManager.IMPORTANCE_LOW

            val broadcastChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, highImportance).apply {
                description = "Official university circulars and notices"
                enableVibration(true)
                enableLights(true)
            }
            val alertsChannel = NotificationChannel(
                CHANNEL_ATTENDANCE_ALERTS,
                "Attendance Alerts & Corrections",
                highImportance
            ).apply {
                description = "High-priority alerts when marked Absent or when attendance is corrected"
                enableVibration(true)
                enableLights(true)
            }
            val updatesChannel = NotificationChannel(
                CHANNEL_ATTENDANCE_UPDATES,
                "Attendance Confirmations",
                lowImportance
            ).apply {
                description = "Silent confirmations when marked Present"
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(broadcastChannel)
            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(updatesChannel)
        }
    }

    fun showAttendanceNotification(
        context: Context,
        title: String,
        message: String,
        isAbsent: Boolean = false,
        isSilent: Boolean = false,
        channelId: String = if (isSilent) CHANNEL_ATTENDANCE_UPDATES else CHANNEL_ATTENDANCE_ALERTS,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isSilent) {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
        } else {
            notificationBuilder
                .setSound(defaultSoundUri)
                .setPriority(if (isAbsent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
