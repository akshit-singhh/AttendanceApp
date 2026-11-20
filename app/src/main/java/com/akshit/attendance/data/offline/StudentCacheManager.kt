package com.akshit.attendance.data.offline

import android.content.Context
import android.util.Log
import com.akshit.attendance.data.model.StudentCalendarResponse
import com.akshit.attendance.data.model.StudentDashboardResponse
import com.akshit.attendance.data.model.StudentScheduleSlot
import com.akshit.attendance.data.model.StudentSubjectHistoryResponse
import com.akshit.attendance.data.model.TeacherTimetableResponse
import com.akshit.attendance.data.model.UserProfileResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Robust JSON disk cache manager for Student data.
 * Enables 100% offline viewing for:
 *   - Overall attendance dashboard & subject standings
 *   - Today's and weekly scheduled classes
 *   - Full official timetable grid
 *   - Student user profile
 *   - Attendance calendar monthly data
 *   - Detailed subject attendance history
 *
 * Implements Cache-First, Network-Refresh pattern:
 * UI reads from disk instantaneously (0ms, offline-resilient), then updates transparently when online.
 */
object StudentCacheManager {

    private const val TAG = "StudentCacheManager"
    private const val DASHBOARD_FILE = "student_dashboard_cache.json"
    private const val SCHEDULE_FILE = "student_schedule_cache.json"
    private const val TIMETABLE_FILE = "student_timetable_cache.json"
    private const val PROFILE_FILE = "student_profile_cache.json"
    private const val CALENDAR_PREFIX = "student_calendar_cache_"
    private const val SUBJECT_PREFIX = "student_subject_cache_"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ─── Dashboard Cache ──────────────────────────────────────────────────

    fun loadDashboard(): StudentDashboardResponse? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, DASHBOARD_FILE)
            if (!file.exists()) return null
            json.decodeFromString<StudentDashboardResponse>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading dashboard cache", e)
            null
        }
    }

    fun saveDashboard(data: StudentDashboardResponse) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, DASHBOARD_FILE).writeText(json.encodeToString(data))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving dashboard cache", e)
            }
        }
    }

    // ─── Schedule Cache (Today's Classes & Week Slots) ────────────────────

    fun loadSchedule(): List<StudentScheduleSlot> {
        val ctx = appContext ?: return emptyList()
        return try {
            val file = File(ctx.filesDir, SCHEDULE_FILE)
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<StudentScheduleSlot>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading schedule cache", e)
            emptyList()
        }
    }

    fun saveSchedule(slots: List<StudentScheduleSlot>) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, SCHEDULE_FILE).writeText(json.encodeToString(slots))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving schedule cache", e)
            }
        }
    }

    // ─── Timetable Grid Cache ─────────────────────────────────────────────

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

    fun saveTimetable(data: TeacherTimetableResponse) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, TIMETABLE_FILE).writeText(json.encodeToString(data))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving timetable cache", e)
            }
        }
    }

    // ─── Profile Cache ────────────────────────────────────────────────────

    fun loadProfile(): UserProfileResponse? {
        val ctx = appContext ?: return null
        return try {
            val file = File(ctx.filesDir, PROFILE_FILE)
            if (!file.exists()) return null
            val prof = json.decodeFromString<UserProfileResponse>(file.readText())
            if (prof.roles.any { it.equals("STUDENT", ignoreCase = true) } || !prof.roll_number.isNullOrBlank()) {
                prof
            } else {
                Log.w(TAG, "Cached profile is not a student profile, ignoring")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading profile cache", e)
            null
        }
    }

    fun saveProfile(data: UserProfileResponse) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                File(ctx.filesDir, PROFILE_FILE).writeText(json.encodeToString(data))
                Log.d(TAG, "Student profile cache saved")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile cache", e)
            }
        }
    }

    // ─── Calendar Monthly Cache ───────────────────────────────────────────

    fun loadCalendar(month: String?): StudentCalendarResponse? {
        val ctx = appContext ?: return null
        val key = month ?: "current"
        return try {
            val file = File(ctx.filesDir, "$CALENDAR_PREFIX$key.json")
            if (!file.exists()) return null
            json.decodeFromString<StudentCalendarResponse>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calendar cache for $month", e)
            null
        }
    }

    fun saveCalendar(month: String?, data: StudentCalendarResponse) {
        val ctx = appContext ?: return
        val key = month ?: "current"
        scope.launch {
            try {
                File(ctx.filesDir, "$CALENDAR_PREFIX$key.json").writeText(json.encodeToString(data))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving calendar cache", e)
            }
        }
    }

    // ─── Subject Attendance History Cache ─────────────────────────────────

    fun loadSubjectHistory(courseCode: String): StudentSubjectHistoryResponse? {
        val ctx = appContext ?: return null
        val cleanCode = courseCode.trim().uppercase()
        return try {
            val file = File(ctx.filesDir, "$SUBJECT_PREFIX$cleanCode.json")
            if (!file.exists()) return null
            json.decodeFromString<StudentSubjectHistoryResponse>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading subject history cache for $courseCode", e)
            null
        }
    }

    fun saveSubjectHistory(courseCode: String, data: StudentSubjectHistoryResponse) {
        val ctx = appContext ?: return
        val cleanCode = courseCode.trim().uppercase()
        scope.launch {
            try {
                File(ctx.filesDir, "$SUBJECT_PREFIX$cleanCode.json").writeText(json.encodeToString(data))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving subject history cache", e)
            }
        }
    }

    fun clearAll() {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, DASHBOARD_FILE).delete()
            File(ctx.filesDir, SCHEDULE_FILE).delete()
            File(ctx.filesDir, TIMETABLE_FILE).delete()
            File(ctx.filesDir, PROFILE_FILE).delete()
            ctx.filesDir.listFiles { _, name ->
                name.startsWith(CALENDAR_PREFIX) || name.startsWith(SUBJECT_PREFIX)
            }?.forEach { it.delete() }
            Log.d(TAG, "Student offline cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing student cache", e)
        }
    }

    // ─── Network Error Detection Helper ───────────────────────────────────

    fun isNetworkError(e: Throwable?): Boolean {
        if (e is IOException) return true
        return isNetworkError(e?.message)
    }

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
                lower.contains("connection refused") ||
                lower.contains("stream was reset") ||
                lower.contains("software caused connection abort")
    }
}
