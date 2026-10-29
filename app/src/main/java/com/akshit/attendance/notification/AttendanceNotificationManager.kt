package com.akshit.attendance.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.akshit.attendance.data.network.ApiConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.TimeUnit

@Serializable
data class NtfyEvent(
    val id: String = "",
    val time: Long = 0L,
    val event: String = "",
    val topic: String = "",
    val title: String? = null,
    val message: String? = null,
    val priority: Int = 3,
    val tags: List<String> = emptyList()
)

object AttendanceNotificationManager {

    private const val TAG = "AttendanceNotification"
    private const val PREFS_NAME = "attendance_notification_prefs"
    private const val KEY_READ_IDS = "read_notification_ids"
    private const val KEY_DELIVERED_IDS = "delivered_notification_ids"

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE connections must have infinite read timeout
        .connectTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var currentRoll: String? = null

    fun normalizeTopic(rollNumber: String): String {
        val clean = rollNumber.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return "student_$clean"
    }

    private fun getReadIds(): Set<String> {
        return prefs?.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadId(id: String) {
        val current = getReadIds().toMutableSet()
        current.add(id)
        prefs?.edit()?.putStringSet(KEY_READ_IDS, current)?.apply()
    }

    private fun saveAllReadIds(ids: Set<String>) {
        val current = getReadIds().toMutableSet()
        current.addAll(ids)
        prefs?.edit()?.putStringSet(KEY_READ_IDS, current)?.apply()
    }

    private fun getDeliveredIds(): Set<String> {
        return prefs?.getStringSet(KEY_DELIVERED_IDS, emptySet()) ?: emptySet()
    }

    private fun markAsDelivered(id: String) {
        if (id.isBlank()) return
        val current = getDeliveredIds().toMutableSet()
        current.add(id)
        prefs?.edit()?.putStringSet(KEY_DELIVERED_IDS, current)?.apply()
    }

    private fun markAllAsDelivered(ids: Set<String>) {
        if (ids.isEmpty()) return
        val current = getDeliveredIds().toMutableSet()
        current.addAll(ids)
        prefs?.edit()?.putStringSet(KEY_DELIVERED_IDS, current)?.apply()
    }

    @Synchronized
    fun startListening(context: Context, rollNumber: String) {
        if (rollNumber.isBlank()) return
        appContext = context.applicationContext
        prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (currentRoll == rollNumber && job?.isActive == true) {
            return
        }

        stopListening()
        currentRoll = rollNumber
        val topic = normalizeTopic(rollNumber)

        // 1. Immediately fetch full notification history from ntfy server cache for the inbox list
        fetchHistory(topic)

        // 2. Start persistent SSE stream for NEW real-time live events only
        // Do NOT use ?since=... here so old historical messages aren't replayed as heads-up popups
        val url = "${ApiConfig.NTFY_BASE_URL.trimEnd('/')}/$topic/sse"
        Log.i(TAG, "Starting live SSE listener for topic: $topic ($url)")
        _connectionStatus.value = "Connecting to $topic..."

        job = scope.launch {
            while (isActive) {
                var response: Response? = null
                var reader: BufferedReader? = null
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .build()

                    response = client.newCall(request).execute()
                    val body = response.body
                    if (response.isSuccessful && body != null) {
                        Log.i(TAG, "Successfully connected to SSE stream for $topic")
                        _connectionStatus.value = "Connected ($topic)"
                        reader = BufferedReader(InputStreamReader(body.byteStream()))
                        var line: String? = null

                        while (isActive) {
                            line = reader.readLine() ?: break
                            val currentLine = line
                            if (currentLine.startsWith("data:")) {
                                val dataJson = currentLine.removePrefix("data:").trim()
                                if (dataJson.isNotBlank()) {
                                    handleNtfyEvent(context.applicationContext, dataJson)
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "SSE response HTTP error: ${response.code}")
                        _connectionStatus.value = "HTTP ${response.code} error"
                    }
                } catch (e: CancellationException) {
                    _connectionStatus.value = "Stopped"
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "SSE connection failed (${e.javaClass.simpleName}): ${e.message}")
                    _connectionStatus.value = "Error: ${e.message ?: "Connection failed"}"
                } finally {
                    try { reader?.close() } catch (_: Exception) {}
                    try { response?.close() } catch (_: Exception) {}
                }

                if (isActive) {
                    delay(5000L) // Wait 5 seconds before retrying
                }
            }
        }
    }

    /**
     * Poll historical notifications from ntfy server cache
     */
    fun fetchHistory(topic: String = currentRoll?.let { normalizeTopic(it) } ?: "") {
        if (topic.isBlank()) return
        scope.launch {
            try {
                val pollUrl = "${ApiConfig.NTFY_BASE_URL.trimEnd('/')}/$topic/json?poll=1"
                val request = Request.Builder().url(pollUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val readIds = getReadIds()
                        val historyItems = mutableListOf<NotificationItem>()
                        val historyIds = mutableSetOf<String>()

                        body.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotBlank()) {
                                try {
                                    val event = json.decodeFromString<NtfyEvent>(trimmed)
                                    if (event.event == "message" && !event.message.isNullOrBlank()) {
                                        if (event.id.isNotBlank()) {
                                            historyIds.add(event.id)
                                        }
                                        historyItems.add(convertToNotificationItem(event, readIds))
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Mark all existing historical messages as delivered so they never buzz as popups
                        markAllAsDelivered(historyIds)

                        // Merge with current notifications, avoiding duplicates, sorted newest first
                        val currentMap = _notifications.value.associateBy { it.id }.toMutableMap()
                        historyItems.forEach { item ->
                            if (!currentMap.containsKey(item.id)) {
                                currentMap[item.id] = item
                            }
                        }

                        val sorted = currentMap.values.sortedByDescending { it.rawTime }
                        _notifications.value = sorted
                        Log.i(TAG, "Loaded ${sorted.size} notifications for $topic")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load notification history for $topic: ${e.message}")
            }
        }
    }

    private fun handleNtfyEvent(context: Context, jsonStr: String) {
        try {
            val event = json.decodeFromString<NtfyEvent>(jsonStr)
            if (event.event == "message" && !event.message.isNullOrBlank()) {
                val readIds = getReadIds()
                val newItem = convertToNotificationItem(event, readIds)

                // Update notification list in real time
                val currentList = _notifications.value.filter { it.id != newItem.id }.toMutableList()
                currentList.add(0, newItem)
                _notifications.value = currentList

                // Check persistent delivered IDs: only trigger system heads-up notification once per event ID!
                val deliveredIds = getDeliveredIds()
                val isAlreadyDelivered = event.id.isNotBlank() && deliveredIds.contains(event.id)

                if (!isAlreadyDelivered) {
                    if (event.id.isNotBlank()) {
                        markAsDelivered(event.id)
                    }

                    val title = newItem.title
                    val message = newItem.message
                    val isAbsent = event.tags.contains("x") || event.tags.contains("warning") || title.contains("Absent", ignoreCase = true)

                    Log.i(TAG, "Triggering fresh native Android notification: $title - $message")
                    NotificationHelper.showAttendanceNotification(
                        context = context,
                        title = title,
                        message = message,
                        isAbsent = isAbsent
                    )
                } else {
                    Log.d(TAG, "Ignoring already-delivered notification: ${event.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse notification payload: $jsonStr", e)
        }
    }

    private fun convertToNotificationItem(event: NtfyEvent, readIds: Set<String>): NotificationItem {
        val title = event.title ?: "Attendance Notification"
        val message = event.message ?: ""
        val category = when {
            event.tags.contains("x") || event.tags.contains("warning") || title.contains("Absent", ignoreCase = true) || title.contains("Present", ignoreCase = true) || title.contains("Attendance", ignoreCase = true) -> "ATTENDANCE"
            title.contains("Timetable", ignoreCase = true) || title.contains("Schedule", ignoreCase = true) -> "TIMETABLE"
            else -> "ACADEMIC"
        }
        val isRead = readIds.contains(event.id)
        val timestamp = formatTimestamp(event.time)

        return NotificationItem(
            id = event.id.ifBlank { System.currentTimeMillis().toString() },
            title = title,
            message = message,
            timestamp = timestamp,
            category = category,
            isRead = isRead,
            rawTime = event.time
        )
    }

    private fun formatTimestamp(epochSeconds: Long): String {
        if (epochSeconds <= 0) return "Just now"
        val now = System.currentTimeMillis() / 1000
        val diff = now - epochSeconds
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            diff < 172800 -> "Yesterday"
            else -> {
                try {
                    val dt = Instant.ofEpochSecond(epochSeconds)
                        .atZone(ZoneId.systemDefault())
                    dt.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
                } catch (_: Exception) {
                    "${diff / 86400}d ago"
                }
            }
        }
    }

    fun markAsRead(id: String) {
        saveReadId(id)
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun markAllAsRead() {
        val allIds = _notifications.value.map { it.id }.toSet()
        saveAllReadIds(allIds)
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }

    @Synchronized
    fun stopListening() {
        job?.cancel()
        job = null
        currentRoll = null
        _connectionStatus.value = "Disconnected"
    }
}
