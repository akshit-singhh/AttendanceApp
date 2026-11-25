package com.akshit.attendance.data.offline

import android.content.Context
import android.util.Log
import com.akshit.attendance.data.model.CourseScheduleResponse
import com.akshit.attendance.data.model.SessionHistoryResponse
import com.akshit.attendance.data.model.StudentAttendanceSummary
import com.akshit.attendance.data.model.StudentRosterResponse
import com.akshit.attendance.data.model.TeacherTimetableResponse
import com.akshit.attendance.data.model.UserProfileResponse
import com.akshit.attendance.util.AppDateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Lightweight JSON file cache for the teacher's daily schedule and student rosters.
 *
 * Strategy: Cache-First, Network-Refresh
 *   1. UI reads from disk cache instantly (0 ms latency, works fully offline).
 *   2. When online, the ViewModel fetches fresh data, updates the cache, and re-renders.
 *   3. After loading the schedule, all rosters for today's classes are pre-fetched in
 *      parallel in the background so roll-call works offline in the classroom.
 *
 * Cache files (in app's internal filesDir — private, never backed up):
 *   teacher_schedule_cache.json         — today's CourseScheduleResponse list + date stamp
 *   roster_cache_{courseOfferingId}.json — StudentRosterResponse list per offering
 */
object TeacherCacheManager {

    // Dynamic date provider: uses AppDateProvider (toggleable between live clock and demo semester)
    private val EffectiveDate: LocalDate get() = AppDateProvider.today

    private const val TAG = "TeacherCacheManager"
    private const val SCHEDULE_FILE = "teacher_schedule_cache.json"
    private const val ROSTER_FILE_PREFIX = "roster_cache_"
    private const val STUDENTS_SUMMARY_PREFIX = "students_summary_"
    private const val DATE_STAMP_FILE = "teacher_schedule_date.txt"
    private const val TIMETABLE_FILE = "teacher_timetable_cache.json"
    private const val HISTORY_FILE = "teacher_history_cache.json"
    private const val PROFILE_FILE = "teacher_profile_cache.json"
    private const val LEGACY_PROFILE_FILE = "user_profile_cache.json"
    private const val SUBMITTED_CLASSES_FILE = "teacher_submitted_classes.json"
    private const val POSTPONED_CLASSES_FILE = "teacher_postponed_classes.json"
    private const val CANCELLED_CLASSES_FILE = "teacher_cancelled_classes.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var appContext: Context? = null

    private val _submittedClasses = MutableStateFlow<Set<String>>(emptySet())
    val submittedClasses: StateFlow<Set<String>> = _submittedClasses

    private val _postponedClasses = MutableStateFlow<Set<String>>(emptySet())
    val postponedClasses: StateFlow<Set<String>> = _postponedClasses

    private val _cancelledClasses = MutableStateFlow<Set<String>>(emptySet())
    val cancelledClasses: StateFlow<Set<String>> = _cancelledClasses

    fun init(context: Context) {
        appContext = context.applicationContext
        loadInitialSubmittedClasses()
    }

    private fun loadInitialSubmittedClasses() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, SUBMITTED_CLASSES_FILE)
            if (file.exists()) {
                val list = json.decodeFromString<List<String>>(file.readText())
                _submittedClasses.value = list.toSet()
            }
        } catch (_: Exception) {}
        try {
            val pFile = File(ctx.filesDir, POSTPONED_CLASSES_FILE)
            if (pFile.exists()) {
                val pList = json.decodeFromString<List<String>>(pFile.readText())
                _postponedClasses.value = pList.toSet()
            }
        } catch (_: Exception) {}
        try {
            val cFile = File(ctx.filesDir, CANCELLED_CLASSES_FILE)
            if (cFile.exists()) {
                val cList = json.decodeFromString<List<String>>(cFile.readText())
                _cancelledClasses.value = cList.toSet()
            }
        } catch (_: Exception) {}
    }

    // ─── Schedule Cache ──────────────────────────────────────────────────────

    /**
     * Returns the cached schedule list from disk.
     * Returns empty list if no cache exists yet.
     */
    fun loadSchedule(): List<CourseScheduleResponse> {
        val ctx = appContext ?: return emptyList()
        return try {
            val file = File(ctx.filesDir, SCHEDULE_FILE)
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<CourseScheduleResponse>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading schedule cache", e)
            emptyList()
        }
    }

    /**
     * Returns true if the cached schedule was saved on today's date.
     * Teachers' schedules can change day-to-day (e.g., labs vs. lectures),
     * so stale cache from yesterday must be refreshed.
     */
    fun isScheduleCacheForToday(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val stampFile = File(ctx.filesDir, DATE_STAMP_FILE)
            if (!stampFile.exists()) return false
            val cachedDate = stampFile.readText().trim()
            cachedDate == EffectiveDate.toString()
        } catch (e: Exception) {
            false
        }
    }

    /** Saves the fresh schedule list to disk with today's date stamp. */
    fun saveSchedule(classes: List<CourseScheduleResponse>) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, SCHEDULE_FILE).writeText(json.encodeToString(classes))
                File(ctx.filesDir, DATE_STAMP_FILE).writeText(EffectiveDate.toString())
                Log.d(TAG, "Schedule cache saved: ${classes.size} classes")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving schedule cache", e)
            }
        }
    }

    // ─── Roster Cache ────────────────────────────────────────────────────────

    /**
     * Returns the cached student roster for a specific course offering.
     * Returns null if no roster has been cached yet for this offering.
     */
    fun loadRoster(courseOfferingId: Int): List<StudentRosterResponse>? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, "$ROSTER_FILE_PREFIX$courseOfferingId.json")
            if (!file.exists()) return null
            json.decodeFromString<List<StudentRosterResponse>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading roster cache for offering $courseOfferingId", e)
            null
        }
    }

    /** Saves a fresh student roster for a course offering to disk. */
    fun saveRoster(courseOfferingId: Int, roster: List<StudentRosterResponse>) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val file = File(ctx.filesDir, "$ROSTER_FILE_PREFIX$courseOfferingId.json")
                file.writeText(json.encodeToString(roster))
                Log.d(TAG, "Roster cache saved for offering $courseOfferingId: ${roster.size} students")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving roster cache for offering $courseOfferingId", e)
            }
        }
    }

    /**
     * Pre-fetches rosters for all given course offering IDs in background.
     * Called right after the schedule is refreshed when online.
     * This ensures teachers can open any class and start roll call even after losing signal.
     */
    fun prefetchRosters(
        offeringIds: List<Int>,
        fetchFn: suspend (Int) -> List<StudentRosterResponse>
    ) {
        if (offeringIds.isEmpty()) return
        scope.launch {
            for (offeringId in offeringIds) {
                try {
                    val roster = fetchFn(offeringId)
                    saveRoster(offeringId, roster)
                } catch (e: Exception) {
                    // Pre-fetch is best-effort: if one fails, continue with the rest
                    Log.w(TAG, "Pre-fetch failed for offering $offeringId: ${e.message}")
                }
            }
            Log.d(TAG, "Roster pre-fetch complete for ${offeringIds.size} course(s)")
        }
    }

    // ─── Students Summary Cache (For Analytics & Defaulters) ──────────────────

    fun loadStudentsSummary(courseOfferingId: Int): List<StudentAttendanceSummary> {
        val ctx = appContext ?: return emptyList()
        return try {
            val file = File(ctx.filesDir, "$STUDENTS_SUMMARY_PREFIX$courseOfferingId.json")
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<StudentAttendanceSummary>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading students summary cache for $courseOfferingId", e)
            emptyList()
        }
    }

    fun saveStudentsSummary(courseOfferingId: Int, list: List<StudentAttendanceSummary>) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val file = File(ctx.filesDir, "$STUDENTS_SUMMARY_PREFIX$courseOfferingId.json")
                file.writeText(json.encodeToString(list))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving students summary cache for $courseOfferingId", e)
            }
        }
    }

    // ─── Timetable Cache ─────────────────────────────────────────────────────

    fun loadTimetable(): TeacherTimetableResponse? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, TIMETABLE_FILE)
            if (!file.exists()) return null
            json.decodeFromString<TeacherTimetableResponse>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading timetable cache", e)
            null
        }
    }

    fun saveTimetable(timetable: TeacherTimetableResponse) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, TIMETABLE_FILE).writeText(json.encodeToString(timetable))
                Log.d(TAG, "Timetable cache saved")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving timetable cache", e)
            }
        }
    }

    // ─── Attendance History Cache ────────────────────────────────────────────

    fun loadHistory(): List<SessionHistoryResponse>? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, HISTORY_FILE)
            if (!file.exists()) return null
            json.decodeFromString<List<SessionHistoryResponse>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading history cache", e)
            null
        }
    }

    fun saveHistory(history: List<SessionHistoryResponse>) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, HISTORY_FILE).writeText(json.encodeToString(history))
                Log.d(TAG, "History cache saved: ${history.size} session(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving history cache", e)
            }
        }
    }

    // ─── User Profile Cache ──────────────────────────────────────────────────

    fun loadProfile(): UserProfileResponse? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, PROFILE_FILE)
            val candidate = if (file.exists()) {
                json.decodeFromString<UserProfileResponse>(file.readText())
            } else {
                val legacyFile = File(ctx.filesDir, LEGACY_PROFILE_FILE)
                if (legacyFile.exists()) {
                    val legacy = json.decodeFromString<UserProfileResponse>(legacyFile.readText())
                    legacyFile.delete()
                    legacy
                } else null
            }
            if (candidate != null && (candidate.roles.any { it.equals("TEACHER", ignoreCase = true) || it.uppercase() in listOf("COORDINATOR", "HOD", "DEAN", "ADMIN") } || !candidate.employee_code.isNullOrBlank())) {
                candidate
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading profile cache", e)
            null
        }
    }

    fun saveProfile(profile: UserProfileResponse) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val legacyFile = File(ctx.filesDir, LEGACY_PROFILE_FILE)
                if (legacyFile.exists()) legacyFile.delete()
                File(ctx.filesDir, PROFILE_FILE).writeText(json.encodeToString(profile))
                Log.d(TAG, "Teacher profile cache saved")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile cache", e)
            }
        }
    }

    fun clearAll() {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, SCHEDULE_FILE).delete()
            File(ctx.filesDir, DATE_STAMP_FILE).delete()
            File(ctx.filesDir, TIMETABLE_FILE).delete()
            File(ctx.filesDir, HISTORY_FILE).delete()
            File(ctx.filesDir, PROFILE_FILE).delete()
            File(ctx.filesDir, LEGACY_PROFILE_FILE).delete()
            ctx.filesDir.listFiles { _, name ->
                name.startsWith(ROSTER_FILE_PREFIX) || name.startsWith(STUDENTS_SUMMARY_PREFIX)
            }?.forEach { it.delete() }
            Log.d(TAG, "Teacher offline cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing teacher cache", e)
        }
    }

    // ─── Submitted Classes Cache ─────────────────────────────────────────────

    /**
     * Records that a class session has been submitted (either online or offline),
     * persistently saving its key so that even if the network is lost or the app
     * is restarted, the class card remains in SUBMITTED state (Attendance Recorded).
     */
    fun recordSubmittedClass(courseOfferingId: Int, date: String) {
        val ctx = appContext ?: return
        val key = "$courseOfferingId:$date"
        try {
            val file = File(ctx.filesDir, SUBMITTED_CLASSES_FILE)
            val current = if (file.exists()) {
                try {
                    json.decodeFromString<List<String>>(file.readText()).toMutableSet()
                } catch (_: Exception) {
                    mutableSetOf()
                }
            } else {
                mutableSetOf()
            }
            if (current.add(key)) {
                file.writeText(json.encodeToString(current.toList()))
                Log.d(TAG, "Recorded submitted class key: $key")
            }
            _submittedClasses.value = current.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error recording submitted class: $key", e)
        }
    }

    /**
     * Checks if a specific course offering on a given date has already had attendance submitted.
     */
    fun isClassSubmitted(courseOfferingId: Int, date: String): Boolean {
        val key = "$courseOfferingId:$date"
        if (_submittedClasses.value.contains(key)) return true
        val ctx = appContext ?: return false
        return try {
            val file = File(ctx.filesDir, SUBMITTED_CLASSES_FILE)
            if (file.exists()) {
                val list = json.decodeFromString<List<String>>(file.readText())
                list.contains(key)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun recordPostponedClass(courseOfferingId: Int, date: String) {
        val ctx = appContext ?: return
        val key = "$courseOfferingId:$date"
        try {
            val file = File(ctx.filesDir, POSTPONED_CLASSES_FILE)
            val current = if (file.exists()) {
                try {
                    json.decodeFromString<List<String>>(file.readText()).toMutableSet()
                } catch (_: Exception) {
                    mutableSetOf()
                }
            } else {
                mutableSetOf()
            }
            if (current.add(key)) {
                file.writeText(json.encodeToString(current.toList()))
            }
            _postponedClasses.value = current.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error recording postponed class: $key", e)
        }
    }

    fun removePostponedClass(courseOfferingId: Int, date: String) {
        val ctx = appContext ?: return
        val key = "$courseOfferingId:$date"
        try {
            val file = File(ctx.filesDir, POSTPONED_CLASSES_FILE)
            val current = if (file.exists()) {
                try {
                    json.decodeFromString<List<String>>(file.readText()).toMutableSet()
                } catch (_: Exception) {
                    mutableSetOf()
                }
            } else {
                mutableSetOf()
            }
            if (current.remove(key)) {
                file.writeText(json.encodeToString(current.toList()))
            }
            _postponedClasses.value = current.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing postponed class: $key", e)
        }
    }

    fun isClassPostponed(courseOfferingId: Int, date: String): Boolean {
        val key = "$courseOfferingId:$date"
        return _postponedClasses.value.contains(key)
    }

    fun recordCancelledClass(courseOfferingId: Int, date: String) {
        val ctx = appContext ?: return
        val key = "$courseOfferingId:$date"
        try {
            val file = File(ctx.filesDir, CANCELLED_CLASSES_FILE)
            val current = if (file.exists()) {
                try {
                    json.decodeFromString<List<String>>(file.readText()).toMutableSet()
                } catch (_: Exception) {
                    mutableSetOf()
                }
            } else {
                mutableSetOf()
            }
            if (current.add(key)) {
                file.writeText(json.encodeToString(current.toList()))
            }
            _cancelledClasses.value = current.toSet()
            // If it was previously postponed, remove from postponed
            removePostponedClass(courseOfferingId, date)
            // Cancelled classes should be excluded from pending
            recordSubmittedClass(courseOfferingId, date)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording cancelled class: $key", e)
        }
    }

    fun isClassCancelled(courseOfferingId: Int, date: String): Boolean {
        val key = "$courseOfferingId:$date"
        return _cancelledClasses.value.contains(key)
    }

    /**
     * Retrieves all courseOfferingIds recorded as submitted for the specified date.
     * Integrates:
     * 1. The persistent submitted classes file.
     * 2. Any sessions matching the date in the cached attendance history.
     */
    fun getSubmittedOfferingIdsForDate(date: String): Set<Int> {
        val ctx = appContext ?: return emptySet()
        val result = mutableSetOf<Int>()

        // 1. From persistent submitted file
        try {
            val file = File(ctx.filesDir, SUBMITTED_CLASSES_FILE)
            if (file.exists()) {
                val list = json.decodeFromString<List<String>>(file.readText())
                val suffix = ":$date"
                list.filter { it.endsWith(suffix) }.forEach { entry ->
                    entry.substringBefore(":").toIntOrNull()?.let { result.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading submitted classes cache", e)
        }

        // 2. From cached history
        try {
            val history = loadHistory()
            history?.filter { it.date == date }?.forEach {
                result.add(it.course_offering_id)
            }
        } catch (_: Exception) {}

        return result
    }

    /**
     * Attempts to resolve human-readable subject & section info for a course offering ID
     * from the cached schedule or cached weekly timetable.
     */
    fun resolveCourseInfo(courseOfferingId: Int): CourseScheduleResponse? {
        val fromSchedule = loadSchedule().firstOrNull { it.course_offering_id == courseOfferingId }
        if (fromSchedule != null) return fromSchedule
        val fromHistory = loadHistory()?.firstOrNull { it.course_offering_id == courseOfferingId }
        if (fromHistory != null) {
            return CourseScheduleResponse(
                course_offering_id = fromHistory.course_offering_id,
                subject_name = fromHistory.subject_name,
                subject_code = "",
                section_name = fromHistory.section_name,
                start_time = "",
                end_time = "",
                room = null,
                day_of_week = "MON",
                duration_periods = 1
            )
        }
        return null
    }

    /**
     * Determines whether an error string is a network/connectivity issue rather than an app defect.
     */
    fun isNetworkError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return lower.contains("unable to resolve host") ||
               lower.contains("no address associated with hostname") ||
               lower.contains("failed to connect") ||
               lower.contains("connectexception") ||
               lower.contains("sockettimeoutexception") ||
               lower.contains("unknownhostexception") ||
               lower.contains("network") ||
               lower.contains("timeout") ||
               lower.contains("offline") ||
               lower.contains("connection refused")
    }
}
