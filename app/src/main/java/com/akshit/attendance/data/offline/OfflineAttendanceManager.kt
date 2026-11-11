package com.akshit.attendance.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.akshit.attendance.data.model.OfflineSyncPayload
import com.akshit.attendance.data.repository.TeacherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages the offline attendance submission queue and monitors network connectivity.
 * Automatically saves failed/offline session submissions and drains the queue when connectivity is restored.
 */
object OfflineAttendanceManager {

    private const val TAG = "OfflineAttendanceMgr"
    private const val QUEUE_FILENAME = "offline_attendance_queue.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var appContext: Context? = null
    private var repositoryProvider: (() -> TeacherRepository?)? = null

    private val _queuedSessions = MutableStateFlow<List<OfflineSyncPayload>>(emptyList())
    val queuedSessions: StateFlow<List<OfflineSyncPayload>> = _queuedSessions.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

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
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Connectivity restored: network available")
                    _isOnline.value = true
                    triggerAutoSync()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Connectivity lost: network unavailable")
                    val currentCaps = cm.getNetworkCapabilities(cm.activeNetwork)
                    val stillConnected = currentCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    _isOnline.value = stillConnected
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isOnline.value = hasInternet
                    if (hasInternet && _queuedCount.value > 0) {
                        triggerAutoSync()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not register NetworkCallback", e)
        }
    }
}
