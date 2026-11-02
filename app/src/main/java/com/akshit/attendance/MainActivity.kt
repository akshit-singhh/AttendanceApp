package com.akshit.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.akshit.attendance.navigation.AppNavGraph
import com.akshit.attendance.notification.NotificationHelper
import com.akshit.attendance.ui.theme.AttendanceAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Android notification channel for attendance alerts
        NotificationHelper.createNotificationChannel(this)

        // Request runtime permission for Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Always sync Firebase FCM device token on app launch
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { fcmToken ->
                    if (!fcmToken.isNullOrBlank()) {
                        android.util.Log.d("AttendanceFCM", "Fetched FCM token on startup: $fcmToken")
                        val app = application as AttendanceApp
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val token = app.tokenManager.accessToken()
                                if (!token.isNullOrBlank()) {
                                    val api = com.akshit.attendance.data.network.RetrofitClient.get(app.tokenManager)
                                    api.registerDeviceToken(
                                        com.akshit.attendance.data.model.DeviceTokenRegisterRequest(
                                            fcm_token = fcmToken,
                                            device_type = "android"
                                        )
                                    )
                                    android.util.Log.d("AttendanceFCM", "Successfully registered FCM token on startup!")
                                } else {
                                    android.util.Log.d("AttendanceFCM", "User not logged in yet, skipping token sync.")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("AttendanceFCM", "Failed to sync FCM token on startup: ${e.message}")
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("AttendanceFCM", "Firebase messaging token retrieval failed: ${e.message}")
        }

        setContent {
            AttendanceAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(application as AttendanceApp)
                }
            }
        }
    }
}
