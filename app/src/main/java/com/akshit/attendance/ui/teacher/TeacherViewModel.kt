package com.akshit.attendance.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.TeacherCacheManager
import com.akshit.attendance.data.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface ScheduleUiState {
    object Loading : ScheduleUiState
    data class Loaded(
        val classes: List<CourseScheduleResponse>,
        val submittedOfferingIds: Set<Int> = emptySet(),
        val teacherName: String? = null,
        val fromCache: Boolean = false   // true when shown from local cache (offline)
    ) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

sealed interface RosterUiState {
    object Loading : RosterUiState
    data class Loaded(
        val roster: List<StudentRosterResponse>,
        val marks: Map<Int, AttendanceStatus>,
        val courseInfo: CourseScheduleResponse? = null,
        val sessionDate: String? = null
    ) : RosterUiState
    data class Error(val message: String) : RosterUiState
}

sealed interface PendingUiState {
    object Loading : PendingUiState
    data class Loaded(val pendingClasses: List<PendingClassResponse>) : PendingUiState
    data class Error(val message: String) : PendingUiState
}

sealed interface SubmitUiState {
    object Idle : SubmitUiState
    object Submitting : SubmitUiState
    data class Done(val response: SyncResponse) : SubmitUiState
    data class Error(val message: String) : SubmitUiState
}

sealed interface TimetableUiState {
    object Loading : TimetableUiState
    data class Loaded(val data: TeacherTimetableResponse) : TimetableUiState
    data class Error(val message: String) : TimetableUiState
}

private const val CURRENT_TERM_ID = 1

class TeacherViewModel(
    private val repo: TeacherRepository,
    private val tokenManager: TokenManager? = null
) : ViewModel() {

    private val _schedule = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val schedule: StateFlow<ScheduleUiState> = _schedule

    private val _roster = MutableStateFlow<RosterUiState>(RosterUiState.Loading)
    val roster: StateFlow<RosterUiState> = _roster

    private val _submit = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
    val submit: StateFlow<SubmitUiState> = _submit

    private val _timetable = MutableStateFlow<TimetableUiState>(TimetableUiState.Loading)
    val timetable: StateFlow<TimetableUiState> = _timetable

    private val _pending = MutableStateFlow<PendingUiState>(PendingUiState.Loading)
    val pending: StateFlow<PendingUiState> = _pending

    private val _profile = MutableStateFlow<UserProfileResponse?>(null)
    val profile: StateFlow<UserProfileResponse?> = _profile

    private var activeCourseOfferingId: Int? = null
    private var activeSessionDate: String? = null

    init {
        loadProfile()
        loadSchedule()
        loadTimetable()
        loadPendingClasses()
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = repo.getProfile()
            } catch (_: Exception) {
                val name = tokenManager?.userName()
                val email = tokenManager?.userEmail()
                if (!name.isNullOrBlank()) {
                    _profile.value = UserProfileResponse(
                        user_id = 0,
                        email = email ?: "",
                        full_name = name,
                        roles = listOf("TEACHER")
                    )
                }
            }
        }
    }

    fun loadPendingClasses() {
        viewModelScope.launch {
            _pending.value = PendingUiState.Loading
            _pending.value = try {
                val list = repo.getPendingClasses()
                PendingUiState.Loaded(list)
            } catch (e: Exception) {
                PendingUiState.Error(e.message ?: "Could not load pending classes.")
            }
        }
    }

    fun loadTimetable(teacherName: String? = null, facultyId: String? = null) {
        viewModelScope.launch {
            _timetable.value = TimetableUiState.Loading
            _timetable.value = try {
                // When teacherName & facultyId are null, backend automatically and securely resolves
                // the authenticated teacher's timetable from their token and employee_code (0% collision risk)
                val response = repo.getTimetable(teacherName, facultyId)
                TimetableUiState.Loaded(response)
            } catch (e: Exception) {
                TimetableUiState.Error(e.message ?: "Could not load timetable.")
            }
        }
    }

    fun loadSchedule(day: String? = null) {
        viewModelScope.launch {
            val teacherName = tokenManager?.userName()
            val todayStr = LocalDate.now().toString()

            // ── Step 1: Show cached data instantly (zero-latency, works fully offline) ──
            val cached = TeacherCacheManager.loadSchedule()
            if (cached.isNotEmpty()) {
                val queuedIds = OfflineAttendanceManager.submittedOfferingIds.value
                _schedule.value = ScheduleUiState.Loaded(
                    classes = cached,
                    submittedOfferingIds = queuedIds,
                    teacherName = teacherName,
                    fromCache = !TeacherCacheManager.isScheduleCacheForToday()
                )
            } else {
                _schedule.value = ScheduleUiState.Loading
            }

            // ── Step 2: Fetch fresh from network in background ──
            try {
                val classes = repo.getSchedule(null, day)
                val submittedToday = try {
                    repo.getHistory()
                        .filter { it.date == todayStr }
                        .map { it.course_offering_id }
                        .toSet()
                } catch (_: Exception) { emptySet() }

                val queuedIds = OfflineAttendanceManager.submittedOfferingIds.value
                _schedule.value = ScheduleUiState.Loaded(
                    classes = classes,
                    submittedOfferingIds = submittedToday + queuedIds,
                    teacherName = teacherName,
                    fromCache = false
                )

                // Save fresh schedule to disk
                TeacherCacheManager.saveSchedule(classes)

                // ── Step 3: Pre-fetch all rosters for today's classes in background ──
                // So the teacher can open any class card and start roll call even with no signal.
                TeacherCacheManager.prefetchRosters(
                    offeringIds = classes.map { it.course_offering_id }
                ) { offeringId ->
                    repo.getRoster(offeringId, todayStr)
                }
            } catch (e: Exception) {
                // Network failed — keep whatever is currently displayed (cache or loading)
                // Only show error if we have nothing at all to show
                if (_schedule.value is ScheduleUiState.Loading) {
                    _schedule.value = ScheduleUiState.Error(
                        e.message ?: "Could not load today's classes."
                    )
                }
            }
        }
    }

    fun loadRoster(
        courseOfferingId: Int,
        sessionDate: String? = null,
        customCourseInfo: CourseScheduleResponse? = null
    ) {
        activeCourseOfferingId = courseOfferingId
        activeSessionDate = sessionDate
        _roster.value = RosterUiState.Loading
        viewModelScope.launch {
            val targetDate = sessionDate ?: LocalDate.now().toString()

            // ── Step 1: Show cached roster instantly ──
            val cachedRoster = TeacherCacheManager.loadRoster(courseOfferingId)
            if (cachedRoster != null) {
                val courseInfo = resolvecourseInfo(courseOfferingId, customCourseInfo)
                _roster.value = RosterUiState.Loaded(
                    roster = cachedRoster,
                    marks = cachedRoster.associate { it.student_id to (it.status ?: AttendanceStatus.PRESENT) },
                    courseInfo = courseInfo,
                    sessionDate = sessionDate
                )
            }

            // ── Step 2: Try to fetch fresh roster from network ──
            try {
                val freshRoster = repo.getRoster(courseOfferingId, targetDate)
                val courseInfo = resolvecourseInfo(courseOfferingId, customCourseInfo)
                _roster.value = RosterUiState.Loaded(
                    roster = freshRoster,
                    marks = freshRoster.associate { it.student_id to (it.status ?: AttendanceStatus.PRESENT) },
                    courseInfo = courseInfo,
                    sessionDate = sessionDate
                )
                // Update cache with the fresh data
                TeacherCacheManager.saveRoster(courseOfferingId, freshRoster)
            } catch (e: Exception) {
                // Network failed — only emit error if we have nothing cached to show
                if (_roster.value is RosterUiState.Loading) {
                    _roster.value = RosterUiState.Error(
                        e.message ?: "Could not load student roster."
                    )
                }
                // If we already showed cached roster above, the user can still take attendance
            }
        }
    }

    /** Resolves CourseScheduleResponse from loaded schedule or custom override. */
    private fun resolvecourseInfo(
        courseOfferingId: Int,
        customCourseInfo: CourseScheduleResponse?
    ): CourseScheduleResponse? {
        if (customCourseInfo != null) return customCourseInfo
        val sched = _schedule.value
        if (sched is ScheduleUiState.Loaded) {
            return sched.classes.find { it.course_offering_id == courseOfferingId }
        }
        // Fall back to cached schedule on disk
        return TeacherCacheManager.loadSchedule()
            .find { it.course_offering_id == courseOfferingId }
    }

    fun toggleMark(studentId: Int) {
        val current = _roster.value
        if (current is RosterUiState.Loaded) {
            val existing = current.marks[studentId] ?: AttendanceStatus.PRESENT
            val flipped = if (existing == AttendanceStatus.PRESENT) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
            _roster.value = current.copy(marks = current.marks + (studentId to flipped))
        }
    }

    fun markAll(status: AttendanceStatus) {
        val current = _roster.value
        if (current is RosterUiState.Loaded) {
            _roster.value = current.copy(marks = current.roster.associate { it.student_id to status })
        }
    }

    fun submitAttendance(durationPeriods: Int = 1) {
        val current = _roster.value
        val courseOfferingId = activeCourseOfferingId
        if (current !is RosterUiState.Loaded || courseOfferingId == null) return

        _submit.value = SubmitUiState.Submitting
        viewModelScope.launch {
            val nowUtc = Instant.now().toString()
            val localToday = LocalDate.now().toString()
            val targetDate = activeSessionDate ?: localToday
            val payload = OfflineSyncPayload(
                session_uuid = UUID.randomUUID().toString(),
                course_offering_id = courseOfferingId,
                date = targetDate,
                duration_periods = durationPeriods,
                records = current.marks.map { (studentId, status) ->
                    SyncRecordItem(record_uuid = UUID.randomUUID().toString(), student_id = studentId, status = status, captured_at = nowUtc)
                },
            )
            _submit.value = try {
                val response = repo.submitAttendance(payload)
                val currentSched = _schedule.value
                if (currentSched is ScheduleUiState.Loaded) {
                    _schedule.value = currentSched.copy(
                        submittedOfferingIds = currentSched.submittedOfferingIds + courseOfferingId
                    )
                }
                loadPendingClasses()
                SubmitUiState.Done(response)
            } catch (e: Exception) {
                // If offline or network/server submission fails, queue in OfflineAttendanceManager
                OfflineAttendanceManager.enqueue(payload)
                val currentSched = _schedule.value
                if (currentSched is ScheduleUiState.Loaded) {
                    _schedule.value = currentSched.copy(
                        submittedOfferingIds = currentSched.submittedOfferingIds + courseOfferingId
                    )
                }
                SubmitUiState.Done(SyncResponse(status = "QUEUED", message = "Attendance saved offline. Will auto-sync when online."))
            }
        }
    }

    fun syncOfflineQueue() {
        viewModelScope.launch {
            val count = OfflineAttendanceManager.syncQueue(repo)
            if (count > 0) {
                loadSchedule()
                loadPendingClasses()
            }
        }
    }

    suspend fun getAllCourses(): List<CourseScheduleResponse> {
        return try {
            repo.getSchedule(null, "ALL").distinctBy { it.course_offering_id }
        } catch (_: Exception) {
            val curr = _schedule.value
            if (curr is ScheduleUiState.Loaded) {
                curr.classes.distinctBy { it.course_offering_id }
            } else {
                // Last resort: read from disk cache (works fully offline)
                TeacherCacheManager.loadSchedule().distinctBy { it.course_offering_id }
            }
        }
    }

    fun resetSubmitState() {
        _submit.value = SubmitUiState.Idle
    }

    fun postponeClass(
        offeringId: Int,
        date: String,
        reason: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = repo.postponeClass(offeringId, date, reason)
                loadSchedule()
                loadPendingClasses()
                onComplete(true, res.message)
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to postpone class.")
            }
        }
    }
}