package com.akshit.attendance.data.offline

import android.content.Context
import android.util.Log
import com.akshit.attendance.data.model.CourseScheduleResponse
import com.akshit.attendance.data.model.StudentRosterResponse
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

    private const val TAG = "TeacherCacheManager"
    private const val SCHEDULE_FILE = "teacher_schedule_cache.json"
    private const val ROSTER_FILE_PREFIX = "roster_cache_"
    private const val DATE_STAMP_FILE = "teacher_schedule_date.txt"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
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
            cachedDate == LocalDate.now().toString()
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
                File(ctx.filesDir, DATE_STAMP_FILE).writeText(LocalDate.now().toString())
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
}
