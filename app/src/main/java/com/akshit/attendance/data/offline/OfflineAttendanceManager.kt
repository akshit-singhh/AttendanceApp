package com.akshit.attendance.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.akshit.attendance.data.model.OfflineSyncPayload
import com.akshit.attendance.data.network.ApiConfig
import com.akshit.attendance.data.repository.TeacherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages the offline attendance submission queue and monitors network connectivity.
 * Automatically saves failed/offline session submissions, probes server health to
 * auto-reconnect as soon as the backend comes online, and drains the queue.
 */
object OfflineAttendanceManager {

    private const val TAG = "OfflineAttendanceMgr"
    private const val QUEUE_FILENAME = "offline_attendance_queue.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    // Fast-timeout client dedicated to probing backend server availability
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var appContext: Context? = null
    private var repositoryProvider: (() -> TeacherRepository?)? = null

    private val _queuedSessions = MutableStateFlow<List<OfflineSyncPayload>>(emptyList())
    val queuedSessions: StateFlow<List<OfflineSyncPayload>> = _queuedSessions.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    private val _submittedOfferingIds = MutableStateFlow<Set<Int>>(emptySet())
    val submittedOfferingIds: StateFlow<Set<Int>> = _submittedOfferingIds.asStateFlow()

    fun init(context: Context, repoProvider: () -> TeacherRepository?) {
        appContext = context.applicationContext
        repositoryProvider = repoProvider

        scope.launch {
            loadQueueFromDisk()
            setupConnectivityMonitoring(context.applicationContext)
        }
    }

    private suspend fun loadQueueFromDisk() = mutex.withLock {
        val ctx = appContext ?: return@withLock
        try {
            val file = File(ctx.filesDir, QUEUE_FILENAME)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<OfflineSyncPayload>>(content)
                    _queuedSessions.value = list
                    _queuedCount.value = list.size
                    _submittedOfferingIds.value = list.map { it.course_offering_id }.toSet()
                    Log.d(TAG, "Loaded ${list.size} queued offline sessions from disk")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline queue from disk", e)
        }
    }

    private suspend fun persistQueueToDisk(list: List<OfflineSyncPayload>) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, QUEUE_FILENAME)
            val content = json.encodeToString(list)
            file.writeText(content)
            _queuedSessions.value = list
            _queuedCount.value = list.size
            _submittedOfferingIds.value = list.map { it.course_offering_id }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting offline queue to disk", e)
        }
    }

    fun enqueue(payload: OfflineSyncPayload) {
        scope.launch {
            mutex.withLock {
                val current = _queuedSessions.value.toMutableList()
                val existingIndex = current.indexOfFirst {
                    it.session_uuid == payload.session_uuid ||
                    (it.course_offering_id == payload.course_offering_id && it.date == payload.date)
                }
                if (existingIndex >= 0) {
                    current[existingIndex] = payload
                } else {
                    current.add(payload)
                }
                persistQueueToDisk(current)
                Log.d(TAG, "Enqueued offline session for course ${payload.course_offering_id}. Total pending: ${current.size}")
            }

            if (_isOnline.value) {
                triggerAutoSync()
            }
        }
    }

    fun triggerAutoSync() {
        val repo = repositoryProvider?.invoke() ?: return
        if (_queuedCount.value == 0 || _isSyncing.value) return

        scope.launch {
            syncQueue(repo)
        }
    }

    suspend fun syncQueue(repo: TeacherRepository): Int {
        if (_isSyncing.value) return 0
        return mutex.withLock {
            val pending = _queuedSessions.value.toMutableList()
            if (pending.isEmpty()) return@withLock 0

            _isSyncing.value = true
            var successCount = 0
            val iterator = pending.iterator()

            try {
                while (iterator.hasNext()) {
                    val payload = iterator.next()
                    try {
                        Log.d(TAG, "Syncing offline session ${payload.session_uuid} for course ${payload.course_offering_id}...")
                        repo.submitAttendance(payload)
                        iterator.remove()
                        successCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed syncing session ${payload.session_uuid}: ${e.message}")
                        // Stop draining on network or server error to retry later
                        break
                    }
                }
                persistQueueToDisk(pending)
            } finally {
                _isSyncing.value = false
            }

            if (successCount > 0) {
                val msg = if (pending.isEmpty()) {
                    "Synced all $successCount offline session(s) successfully"
                } else {
                    "Synced $successCount session(s), ${pending.size} still queued"
                }
                _lastSyncMessage.value = msg
                scope.launch {
                    delay(4000)
                    if (_lastSyncMessage.value == msg) {
                        _lastSyncMessage.value = null
                    }
                }
            }
            successCount
        }
    }

    private var autoReconnectJob: Job? = null

    private fun setupConnectivityMonitoring(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        // Initial connection check
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val initialConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOnline.value = initialConnected

        if (initialConnected && _queuedCount.value > 0) {
            triggerAutoSync()
        }

        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network interface available")
                    onNetworkRestored()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network interface lost")
                    autoReconnectJob?.cancel()
                    _isOnline.value = false
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    if (!hasInternet) {
                        autoReconnectJob?.cancel()
                        _isOnline.value = false
                    } else if (!_isOnline.value) {
                        onNetworkRestored()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not register default network callback", e)
        }
    }

    /**
     * Debounces Android OS network change callbacks so multiple rapid capability events
     * collapse into a single reachability check when internet is restored.
     */
    private fun onNetworkRestored() {
        if (_isOnline.value) return
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            delay(800L)
            if (!_isOnline.value) {
                val isHealthy = try {
                    probeServer()
                } catch (_: Exception) {
                    false
                }
                if (isHealthy) {
                    markOnline()
                }
            }
        }
    }

    /**
     * Performs an on-demand fast HTTP GET request to check whether the backend is up.
     * Triggered strictly on user interaction (e.g. tapping "Tap to reconnect" on the banner).
     * Enforces a minimum smooth duration (~700ms) so users get clear, non-flickering visual feedback.
     */
    fun checkServerHealth(onResult: ((Boolean) -> Unit)? = null) {
        if (_isConnecting.value) return
        scope.launch {
            _isConnecting.value = true
            val startTime = System.currentTimeMillis()
            val isHealthy = try {
                probeServer()
            } catch (_: Exception) {
                false
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 700L) {
                delay(700L - elapsed)
            }
            if (isHealthy) {
                markOnline()
            }
            _isConnecting.value = false
            withContext(Dispatchers.Main) {
                onResult?.invoke(isHealthy)
            }
        }
    }

    private fun probeServer(): Boolean {
        // Try /api/v1/health first
        try {
            val url = ApiConfig.BASE_URL + "health"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            healthClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..499) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // Fallback to root /health
        try {
            val fallbackUrl = ApiConfig.BASE_URL.replace("/api/v1/", "/health")
            val request = Request.Builder()
                .url(fallbackUrl)
                .get()
                .build()
            healthClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..499) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    fun markOnline() {
        if (!_isOnline.value) {
            Log.d(TAG, "Transitioned to ONLINE")
            _isOnline.value = true
            if (_queuedCount.value > 0) {
                triggerAutoSync()
            }
        }
    }

    fun markOffline() {
        if (_isOnline.value) {
            Log.d(TAG, "Transitioned to OFFLINE")
            _isOnline.value = false
        }
    }
}
