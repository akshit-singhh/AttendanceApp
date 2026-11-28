package com.akshit.attendance.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.akshit.attendance.data.model.OfflineSyncPayload
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * Manages the offline attendance submission queue and monitors network connectivity.
 * Automatically saves failed/offline session submissions, probes server health to
 * auto-reconnect as soon as the backend comes online, and drains the queue.
 */
object OfflineAttendanceManager {

    private const val TAG = "OfflineAttendanceMgr"
    private const val QUEUE_FILENAME = "offline_attendance_queue.json"

    @Serializable
    data class QueuedAttendanceEntry(
        val ownerEmail: String?,
        val payload: OfflineSyncPayload,
        val failureMessage: String? = null,
        val subjectName: String? = null,
        val subjectCode: String? = null,
        val sectionName: String? = null,
        val createdAt: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var appContext: Context? = null
    private var repositoryProvider: (() -> TeacherRepository?)? = null
    private var accountEmailProvider: (suspend () -> String?)? = null
    private var activeOwnerEmail: String? = null
    private var queueEntries: List<QueuedAttendanceEntry> = emptyList()

    private val _queuedEntries = MutableStateFlow<List<QueuedAttendanceEntry>>(emptyList())
    val queuedEntries: StateFlow<List<QueuedAttendanceEntry>> = _queuedEntries.asStateFlow()
    private val _queuedSessions = MutableStateFlow<List<OfflineSyncPayload>>(emptyList())
    val queuedSessions: StateFlow<List<OfflineSyncPayload>> = _queuedSessions.asStateFlow()
    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()
    private val _needsAttentionSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val needsAttentionSessionIds: StateFlow<Set<String>> = _needsAttentionSessionIds.asStateFlow()
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

    fun init(context: Context, repoProvider: () -> TeacherRepository?, currentAccountEmail: suspend () -> String?) {
        appContext = context.applicationContext
        repositoryProvider = repoProvider
        accountEmailProvider = currentAccountEmail
        scope.launch {
            activeOwnerEmail = currentAccountEmail()?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            loadQueueFromDisk()
            setupConnectivityMonitoring(context.applicationContext)
        }
    }

    suspend fun onAccountChanged() {
        val owner = accountEmailProvider?.invoke()?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        mutex.withLock {
            activeOwnerEmail = owner
            publishQueueState()
        }
        if (owner != null && _isOnline.value) triggerAutoSync()
    }

    private suspend fun loadQueueFromDisk() = mutex.withLock {
        val ctx = appContext ?: return@withLock
        val file = File(ctx.filesDir, QUEUE_FILENAME)
        if (!file.exists()) return@withLock
        try {
            val content = file.readText()
            if (content.isBlank()) return@withLock
            queueEntries = try {
                json.decodeFromString<List<QueuedAttendanceEntry>>(content)
            } catch (_: Exception) {
                // Preserve legacy records but never send them under an unverified account.
                json.decodeFromString<List<OfflineSyncPayload>>(content).map {
                    QueuedAttendanceEntry(null, it, "Older queued session has no verified account.")
                }
            }
            persistQueueToDisk(queueEntries)
            publishQueueState()
            val unassigned = queueEntries.count { it.ownerEmail == null }
            if (unassigned > 0) _lastSyncMessage.value =
                "$unassigned older attendance session(s) were held back because their original account is unknown."
            Log.d(TAG, "Loaded ${queueEntries.size} queued offline sessions from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline queue from disk", e)
        }
    }

    private fun publishQueueState() {
        val owner = activeOwnerEmail
        val owned = if (owner == null) emptyList() else queueEntries.filter {
            it.ownerEmail.equals(owner, ignoreCase = true)
        }
        _queuedEntries.value = owned
        _queuedSessions.value = owned.map { it.payload }
        _queuedCount.value = owned.size
        _needsAttentionSessionIds.value = owned
            .filter { it.failureMessage != null }
            .map { it.payload.session_uuid }
            .toSet()
        _submittedOfferingIds.value = owned.map { it.payload.course_offering_id }.toSet()
    }

    private suspend fun persistQueueToDisk(entries: List<QueuedAttendanceEntry>) {
        val ctx = appContext ?: throw IOException("Offline attendance storage is unavailable.")
        File(ctx.filesDir, QUEUE_FILENAME).writeText(json.encodeToString(entries))
    }

    private fun normalizeBatch(label: String?): String? = label?.replace("-", "")?.trim()?.uppercase()

    suspend fun enqueue(
        payload: OfflineSyncPayload,
        ownerEmail: String,
        subjectName: String? = null,
        subjectCode: String? = null,
        sectionName: String? = null,
    ) {
        val owner = ownerEmail.trim().lowercase()
        require(owner.isNotBlank()) { "A signed-in teacher account is required to queue attendance." }
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = queueEntries.toMutableList()
                val index = current.indexOfFirst { entry ->
                    entry.ownerEmail.equals(owner, ignoreCase = true) &&
                        entry.payload.course_offering_id == payload.course_offering_id &&
                        entry.payload.date == payload.date &&
                        normalizeBatch(entry.payload.batch_label) == normalizeBatch(payload.batch_label)
                }
                val nowStr = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
                if (index >= 0) {
                    val previous = current[index]
                    if (previous.payload.session_uuid != payload.session_uuid) {
                        val mergedRecords = (previous.payload.records + payload.records)
                            .associateBy { it.student_id }.values.toList()
                        current[index] = QueuedAttendanceEntry(
                            ownerEmail = owner,
                            payload = payload.copy(session_uuid = UUID.randomUUID().toString(), records = mergedRecords),
                            failureMessage = null,
                            subjectName = subjectName ?: previous.subjectName,
                            subjectCode = subjectCode ?: previous.subjectCode,
                            sectionName = sectionName ?: previous.sectionName,
                            createdAt = previous.createdAt ?: nowStr
                        )
                    } else {
                        current[index] = previous.copy(
                            failureMessage = null,
                            subjectName = subjectName ?: previous.subjectName,
                            subjectCode = subjectCode ?: previous.subjectCode,
                            sectionName = sectionName ?: previous.sectionName
                        )
                    }
                } else {
                    current.add(
                        QueuedAttendanceEntry(
                            ownerEmail = owner,
                            payload = payload,
                            failureMessage = null,
                            subjectName = subjectName,
                            subjectCode = subjectCode,
                            sectionName = sectionName,
                            createdAt = nowStr
                        )
                    )
                }

                persistQueueToDisk(current)
                queueEntries = current
                publishQueueState()
            }
        }
        if (_isOnline.value) triggerAutoSync()
    }

    suspend fun removeEntry(sessionUuid: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = queueEntries.filterNot { it.payload.session_uuid == sessionUuid }
                if (current.size != queueEntries.size) {
                    queueEntries = current
                    persistQueueToDisk(queueEntries)
                    publishQueueState()
                    Log.d(TAG, "Removed queued session $sessionUuid. Remaining: ${queueEntries.size}")
                }
            }
        }
    }

    suspend fun retryEntry(sessionUuid: String) {
        val repo = repositoryProvider?.invoke()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = queueEntries.toMutableList()
                val index = current.indexOfFirst { it.payload.session_uuid == sessionUuid }
                if (index >= 0) {
                    current[index] = current[index].copy(failureMessage = null)
                    queueEntries = current
                    persistQueueToDisk(queueEntries)
                    publishQueueState()
                }
            }
        }
        if (_isOnline.value && repo != null) {
            scope.launch { syncQueue(repo, retryRejected = true) }
        }
    }

    fun syncAllNow(onComplete: ((Int) -> Unit)? = null) {
        val repo = repositoryProvider?.invoke() ?: run {
            onComplete?.invoke(0)
            return
        }
        if (_isSyncing.value) return
        scope.launch {
            val count = syncQueue(repo, retryRejected = true)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    fun syncSingleEntry(sessionUuid: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val repo = repositoryProvider?.invoke() ?: run {
            onResult?.invoke(false, "Repository unavailable.")
            return
        }
        if (_isSyncing.value) {
            onResult?.invoke(false, "Sync already in progress.")
            return
        }
        scope.launch {
            var success = false
            var errorMsg: String? = null
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val index = queueEntries.indexOfFirst { it.payload.session_uuid == sessionUuid }
                    if (index < 0) {
                        errorMsg = "Session not found in queue."
                    } else {
                        val entry = queueEntries[index]
                        try {
                            _isSyncing.value = true
                            repo.submitAttendance(entry.payload)
                            queueEntries = queueEntries.filterIndexed { i, _ -> i != index }
                            persistQueueToDisk(queueEntries)
                            publishQueueState()
                            success = true
                        } catch (e: HttpException) {
                            val current = queueEntries.toMutableList()
                            val msg = "Server rejected: HTTP ${e.code()}"
                            current[index] = entry.copy(failureMessage = msg)
                            queueEntries = current
                            persistQueueToDisk(queueEntries)
                            publishQueueState()
                            errorMsg = msg
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to sync session."
                        } finally {
                            _isSyncing.value = false
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onResult?.invoke(success, errorMsg)
            }
        }
    }

    fun triggerAutoSync() {
        val repo = repositoryProvider?.invoke() ?: return
        if (activeOwnerEmail == null || _queuedCount.value == 0 || _isSyncing.value) return
        scope.launch { syncQueue(repo, retryRejected = false) }
    }

    suspend fun syncQueue(repo: TeacherRepository, retryRejected: Boolean = false): Int {
        if (_isSyncing.value) return 0
        val owner = accountEmailProvider?.invoke()?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: return 0
        return mutex.withLock {
            activeOwnerEmail = owner
            publishQueueState()
            if (_queuedCount.value == 0) return@withLock 0

            _isSyncing.value = true
            var succeeded = 0
            var rejected = 0
            var transientFailure: String? = null
            val current = queueEntries.toMutableList()
            val removed = mutableSetOf<Int>()
            try {
                for (index in current.indices) {
                    val entry = current[index]
                    if (!entry.ownerEmail.equals(owner, ignoreCase = true)) continue
                    if (entry.failureMessage != null && !retryRejected) continue
                    try {
                        repo.submitAttendance(entry.payload)
                        removed += index
                        succeeded++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: HttpException) {
                        if (e.code() in 400..499 && e.code() != 429) {
                            current[index] = entry.copy(failureMessage = "Server rejected this session (HTTP ${e.code()}).")
                            rejected++
                            continue
                        }
                        transientFailure = "Temporary server failure while syncing queued attendance."
                        break
                    } catch (e: IOException) {
                        transientFailure = "Network unavailable while syncing queued attendance."
                        break
                    } catch (e: Exception) {
                        transientFailure = e.message ?: "Could not sync queued attendance."
                        break
                    }
                }
                queueEntries = current.filterIndexed { index, _ -> index !in removed }
                persistQueueToDisk(queueEntries)
                publishQueueState()
            } finally {
                _isSyncing.value = false
            }
            when {
                rejected > 0 -> _lastSyncMessage.value =
                    "$rejected attendance session(s) were rejected by the server. Correct the issue, then tap retry."
                transientFailure != null -> _lastSyncMessage.value = transientFailure
                succeeded > 0 -> {
                    val message = if (_queuedCount.value == 0) "Synced all $succeeded offline session(s) successfully"
                    else "Synced $succeeded session(s); ${_queuedCount.value} remain queued"
                    _lastSyncMessage.value = message
                    scope.launch {
                        delay(4000)
                        if (_lastSyncMessage.value == message) _lastSyncMessage.value = null
                    }
                }
            }
            succeeded
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

        if (initialConnected && _queuedCount.value > 0 && activeOwnerEmail != null) {
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
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!hasInternet) {
            if (_isOnline.value) {
                Log.d(TAG, "Transitioned to OFFLINE")
                _isOnline.value = false
            }
        }
    }
}
