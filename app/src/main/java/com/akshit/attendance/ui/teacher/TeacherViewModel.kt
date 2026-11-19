package com.akshit.attendance.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.TeacherCacheManager
import com.akshit.attendance.data.repository.TeacherRepository
import com.akshit.attendance.util.AppDateProvider
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
        val marks: Map<Int, AttendanceStatus?>,
        val initialMarks: Map<Int, AttendanceStatus?> = emptyMap(),
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

    companion object {
        private val courseInfoCache = java.util.concurrent.ConcurrentHashMap<Int, CourseScheduleResponse>()
    }

    // Dynamic date & day provider (toggleable via AppDateProvider)
    private val EffectiveDate: LocalDate get() = AppDateProvider.today
    private val EffectiveDay: String get() = AppDateProvider.todayDayOfWeek.take(3).uppercase()

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
            val cached = TeacherCacheManager.loadProfile()
            if (cached != null) {
                _profile.value = cached
            }
            try {
                val fetched = repo.getProfile()
                _profile.value = fetched
                TeacherCacheManager.saveProfile(fetched)
            } catch (_: Exception) {
                if (_profile.value == null) {
                    val name = tokenManager?.userName()
                    val email = tokenManager?.userEmail()
                    if (!name.isNullOrBlank()) {
                        val synthetic = UserProfileResponse(
                            user_id = 0,
                            email = email ?: "",
                            full_name = name,
                            roles = listOf("TEACHER")
                        )
                        _profile.value = synthetic
                        TeacherCacheManager.saveProfile(synthetic)
                    }
                }
            }
        }
    }

    fun loadPendingClasses() {
        viewModelScope.launch {
            _pending.value = PendingUiState.Loading
            _pending.value = try {
                val list = repo.getPendingClasses()
                OfflineAttendanceManager.markOnline()
                PendingUiState.Loaded(list)
            } catch (e: Exception) {
                if (TeacherCacheManager.isNetworkError(e.message)) {
                    OfflineAttendanceManager.markOffline()
                }
                val msg = if (TeacherCacheManager.isNetworkError(e.message)) {
                    "No internet connection. Unable to check pending classes."
                } else {
                    e.message ?: "Could not load pending classes."
                }
                PendingUiState.Error(msg)
            }
        }
    }

    fun loadTimetable(teacherName: String? = null, facultyId: String? = null, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _timetable.value = TimetableUiState.Loading
            } else {
                // ── Step 1: Show cached timetable instantly if available ──
                val cached = TeacherCacheManager.loadTimetable()
                if (cached != null) {
                    _timetable.value = TimetableUiState.Loaded(cached)
                } else {
                    _timetable.value = TimetableUiState.Loading
                }
            }

            // ── Step 2: Fetch fresh from network in background ──
            try {
                // When teacherName & facultyId are null, backend automatically and securely resolves
                // the authenticated teacher's timetable from their token and employee_code (0% collision risk)
                val response = repo.getTimetable(teacherName, facultyId)
                _timetable.value = TimetableUiState.Loaded(response)
                TeacherCacheManager.saveTimetable(response)
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (TeacherCacheManager.isNetworkError(e.message)) {
                    OfflineAttendanceManager.markOffline()
                }
                // Only show error if we have nothing cached to show!
                if (_timetable.value !is TimetableUiState.Loaded) {
                    val msg = if (TeacherCacheManager.isNetworkError(e.message)) {
                        "No internet connection. Please check your network to load timetable."
                    } else {
                        e.message ?: "Could not load timetable."
                    }
                    _timetable.value = TimetableUiState.Error(msg)
                }
            }
        }
    }

    fun loadSchedule(day: String? = null, isRefresh: Boolean = false) {
        viewModelScope.launch {
            val teacherName = tokenManager?.userName()
            val todayStr = EffectiveDate.toString()

            if (isRefresh) {
                _schedule.value = ScheduleUiState.Loading
            } else {
                // ── Step 1: Show cached data instantly only if it matches today's day of week ──
                val cached = TeacherCacheManager.loadSchedule()
                val isCacheForToday = TeacherCacheManager.isScheduleCacheForToday()
                val dayMatches = cached.all { it.day_of_week.isNullOrBlank() || it.day_of_week.equals(EffectiveDay, ignoreCase = true) }

                if (cached.isNotEmpty() && (isCacheForToday || dayMatches)) {
                    cached.forEach { courseInfoCache[it.course_offering_id] = it }
                    val queuedIds = OfflineAttendanceManager.submittedOfferingIds.value
                    val persistentSubmitted = TeacherCacheManager.getSubmittedOfferingIdsForDate(todayStr)
                    _schedule.value = ScheduleUiState.Loaded(
                        classes = cached,
                        submittedOfferingIds = persistentSubmitted + queuedIds,
                        teacherName = teacherName,
                        fromCache = !isCacheForToday
                    )
                } else {
                    _schedule.value = ScheduleUiState.Loading
                }
            }

            // ── Step 2: Fetch fresh from network in background ──
            try {
                val targetDay = day ?: EffectiveDay
                val classes = repo.getSchedule(null, targetDay)
                classes.forEach { courseInfoCache[it.course_offering_id] = it }
                val submittedToday = try {
                    val history = repo.getHistory()
                    TeacherCacheManager.saveHistory(history)
                    history.forEach {
                        TeacherCacheManager.recordSubmittedClass(it.course_offering_id, it.date)
                    }
                    history.filter { it.date == todayStr }
                        .map { it.course_offering_id }
                        .toSet()
                } catch (_: Exception) {
                    TeacherCacheManager.getSubmittedOfferingIdsForDate(todayStr)
                }

                val queuedIds = OfflineAttendanceManager.submittedOfferingIds.value
                val persistentSubmitted = TeacherCacheManager.getSubmittedOfferingIdsForDate(todayStr)
                _schedule.value = ScheduleUiState.Loaded(
                    classes = classes,
                    submittedOfferingIds = submittedToday + persistentSubmitted + queuedIds,
                    teacherName = teacherName,
                    fromCache = false
                )

                // Save fresh schedule to disk
                TeacherCacheManager.saveSchedule(classes)
                OfflineAttendanceManager.markOnline()

                // ── Step 3: Pre-fetch all rosters for today's classes in background ──
                // So the teacher can open any class card and start roll call even with no signal.
                TeacherCacheManager.prefetchRosters(
                    offeringIds = classes.map { it.course_offering_id }
                ) { offeringId ->
                    repo.getRoster(offeringId, todayStr)
                }
            } catch (e: Exception) {
                if (TeacherCacheManager.isNetworkError(e.message)) {
                    OfflineAttendanceManager.markOffline()
                }
                // Network failed — keep whatever is currently displayed (cache or loading)
                // Only show error if we have nothing at all to show
                if (_schedule.value is ScheduleUiState.Loading) {
                    val msg = if (TeacherCacheManager.isNetworkError(e.message)) {
                        "No internet connection. Please check your network to load today's classes."
                    } else {
                        e.message ?: "Could not load today's classes."
                    }
                    _schedule.value = ScheduleUiState.Error(msg)
                }
            }
        }
    }

    fun loadRoster(
        courseOfferingId: Int,
        sessionDate: String? = null,
        batchLabel: String? = null,
        customCourseInfo: CourseScheduleResponse? = null
    ) {
        activeCourseOfferingId = courseOfferingId
        activeSessionDate = sessionDate
        _roster.value = RosterUiState.Loading
        viewModelScope.launch {
            val targetDate = sessionDate ?: EffectiveDate.toString()
            var courseInfo = customCourseInfo ?: resolvecourseInfo(courseOfferingId, null, targetDate, batchLabel)
            if (batchLabel != null && courseInfo != null) {
                courseInfo = courseInfo.copy(batch_label = batchLabel)
            }

            // ── Step 1: Show cached roster instantly ──
            val cachedRoster = TeacherCacheManager.loadRoster(courseOfferingId)
            if (cachedRoster != null) {
                val marksMap = cachedRoster.associate { it.student_id to it.status }
                _roster.value = RosterUiState.Loaded(
                    roster = cachedRoster,
                    marks = marksMap,
                    initialMarks = marksMap,
                    courseInfo = courseInfo,
                    sessionDate = sessionDate
                )
            }

            // ── Step 2: Try to fetch fresh roster from network ──
            try {
                val freshRoster = repo.getRoster(courseOfferingId, targetDate, batchLabel)
                if (courseInfo == null) {
                    courseInfo = resolvecourseInfo(courseOfferingId, customCourseInfo, targetDate, batchLabel)
                }
                if (batchLabel != null && courseInfo != null) {
                    courseInfo = courseInfo.copy(batch_label = batchLabel)
                }
                val marksMap = freshRoster.associate { it.student_id to it.status }
                _roster.value = RosterUiState.Loaded(
                    roster = freshRoster,
                    marks = marksMap,
                    initialMarks = marksMap,
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

    /** Resolves CourseScheduleResponse from memory, active schedule, disk cache, network schedule, or session history. */
    private suspend fun resolvecourseInfo(
        courseOfferingId: Int,
        customCourseInfo: CourseScheduleResponse?,
        targetDate: String? = null,
        batchLabel: String? = null
    ): CourseScheduleResponse? {
        if (customCourseInfo != null) {
            courseInfoCache[courseOfferingId] = customCourseInfo
            return customCourseInfo
        }

        val targetDow = if (targetDate != null) {
            try {
                java.time.LocalDate.parse(targetDate).dayOfWeek.name.take(3)
            } catch (_: Exception) { null }
        } else null

        // 1. If batchLabel is provided, search matching slot by both targetDate/dayOfWeek AND batchLabel
        if (batchLabel != null) {
            val pendingList = (_pending.value as? PendingUiState.Loaded)?.pendingClasses
            val pendingMatch = pendingList?.find {
                it.course_offering_id == courseOfferingId &&
                (targetDate == null || it.date == targetDate) &&
                it.batch_label?.equals(batchLabel, ignoreCase = true) == true
            }
            if (pendingMatch != null) {
                return CourseScheduleResponse(
                    course_offering_id = pendingMatch.course_offering_id,
                    subject_name = pendingMatch.subject_name,
                    subject_code = pendingMatch.subject_code,
                    section_name = pendingMatch.section_name,
                    start_time = pendingMatch.start_time,
                    end_time = pendingMatch.end_time,
                    room = pendingMatch.room,
                    duration_periods = pendingMatch.duration_periods,
                    batch_label = pendingMatch.batch_label,
                    day_of_week = pendingMatch.day_of_week
                )
            }

            val diskSchedule = TeacherCacheManager.loadSchedule()
            val diskExact = diskSchedule.find {
                it.course_offering_id == courseOfferingId &&
                (targetDow == null || it.day_of_week?.equals(targetDow, ignoreCase = true) == true) &&
                it.batch_label?.equals(batchLabel, ignoreCase = true) == true
            }
            if (diskExact != null) return diskExact

            try {
                val allSchedule = repo.getSchedule(null, "ALL")
                val allExact = allSchedule.find {
                    it.course_offering_id == courseOfferingId &&
                    (targetDow == null || it.day_of_week?.equals(targetDow, ignoreCase = true) == true) &&
                    it.batch_label?.equals(batchLabel, ignoreCase = true) == true
                }
                if (allExact != null) return allExact
            } catch (_: Exception) {}
        }

        // 2. If targetDate is specified, prioritize matching the exact pending class or day-of-week slot
        if (targetDate != null) {
            val pendingList = (_pending.value as? PendingUiState.Loaded)?.pendingClasses
            val pendingFound = pendingList?.find { it.course_offering_id == courseOfferingId && it.date == targetDate }
            if (pendingFound != null) {
                return CourseScheduleResponse(
                    course_offering_id = pendingFound.course_offering_id,
                    subject_name = pendingFound.subject_name,
                    subject_code = pendingFound.subject_code,
                    section_name = pendingFound.section_name,
                    start_time = pendingFound.start_time,
                    end_time = pendingFound.end_time,
                    room = pendingFound.room,
                    duration_periods = pendingFound.duration_periods,
                    batch_label = pendingFound.batch_label,
                    day_of_week = pendingFound.day_of_week
                )
            }

            if (targetDow != null) {
                // Check in memory schedule
                val sched = _schedule.value
                if (sched is ScheduleUiState.Loaded) {
                    val found = sched.classes.find {
                        it.course_offering_id == courseOfferingId && it.day_of_week?.equals(targetDow, ignoreCase = true) == true
                    }
                    if (found != null) return found
                }

                // Check cached disk schedule
                val diskSchedule = TeacherCacheManager.loadSchedule()
                val diskFound = diskSchedule.find {
                    it.course_offering_id == courseOfferingId && it.day_of_week?.equals(targetDow, ignoreCase = true) == true
                }
                if (diskFound != null) return diskFound

                // Query all schedule items from repository
                try {
                    val allSchedule = repo.getSchedule(null, "ALL")
                    val found = allSchedule.find {
                        it.course_offering_id == courseOfferingId && it.day_of_week?.equals(targetDow, ignoreCase = true) == true
                    }
                    if (found != null) return found
                } catch (_: Exception) {}
            }
        }

        courseInfoCache[courseOfferingId]?.let { return if (batchLabel != null) it.copy(batch_label = batchLabel) else it }

        val sched = _schedule.value
        if (sched is ScheduleUiState.Loaded) {
            val found = sched.classes.find { it.course_offering_id == courseOfferingId }
            if (found != null) {
                courseInfoCache[courseOfferingId] = found
                return found
            }
        }

        // Check cached schedule on disk
        val diskSchedule = TeacherCacheManager.loadSchedule()
        diskSchedule.forEach { courseInfoCache[it.course_offering_id] = it }
        val diskFound = diskSchedule.find { it.course_offering_id == courseOfferingId }
        if (diskFound != null) return diskFound

        // Query all schedule items from repository (day = "ALL")
        try {
            val allSchedule = repo.getSchedule(null, "ALL")
            allSchedule.forEach { courseInfoCache[it.course_offering_id] = it }
            val found = allSchedule.find { it.course_offering_id == courseOfferingId }
            if (found != null) return found
        } catch (_: Exception) {}

        // Check pending classes
        val pendingList = (_pending.value as? PendingUiState.Loaded)?.pendingClasses
        val pendingFound = pendingList?.find { it.course_offering_id == courseOfferingId }
        if (pendingFound != null) {
            val synthesized = CourseScheduleResponse(
                course_offering_id = pendingFound.course_offering_id,
                subject_name = pendingFound.subject_name,
                subject_code = pendingFound.subject_code,
                section_name = pendingFound.section_name,
                start_time = pendingFound.start_time,
                end_time = pendingFound.end_time,
                room = pendingFound.room,
                duration_periods = pendingFound.duration_periods,
                batch_label = pendingFound.batch_label
            )
            courseInfoCache[courseOfferingId] = synthesized
            return synthesized
        }

        // Fallback: Reconstruct basic course info from session history
        try {
            val history = repo.getHistory()
            val histItem = history.find { it.course_offering_id == courseOfferingId }
            if (histItem != null) {
                val synthesized = CourseScheduleResponse(
                    course_offering_id = histItem.course_offering_id,
                    subject_name = histItem.subject_name,
                    subject_code = "---",
                    section_name = histItem.section_name,
                    start_time = "",
                    end_time = "",
                    room = "Campus Room"
                )
                courseInfoCache[courseOfferingId] = synthesized
                return synthesized
            }
        } catch (_: Exception) {}

        return null
    }

    fun setMark(studentId: Int, status: AttendanceStatus) {
        val current = _roster.value
        if (current is RosterUiState.Loaded) {
            val existing = current.marks[studentId]
            val updated = if (existing == status) null else status // tap active one deselects back to unmarked
            _roster.value = current.copy(marks = current.marks + (studentId to updated))
        }
    }

    fun toggleMark(studentId: Int) {
        val current = _roster.value
        if (current is RosterUiState.Loaded) {
            val existing = current.marks[studentId]
            val next = when (existing) {
                null -> AttendanceStatus.PRESENT
                AttendanceStatus.PRESENT -> AttendanceStatus.ABSENT
                AttendanceStatus.ABSENT -> AttendanceStatus.LEAVE
                AttendanceStatus.LEAVE -> null
            }
            _roster.value = current.copy(marks = current.marks + (studentId to next))
        }
    }

    fun markAll(status: AttendanceStatus?, targetStudentIds: Set<Int>? = null) {
        val current = _roster.value
        if (current is RosterUiState.Loaded) {
            val newMarks = current.marks.toMutableMap()
            val targets = if (targetStudentIds != null) {
                current.roster.filter { it.student_id in targetStudentIds }
            } else {
                current.roster
            }
            targets.forEach { student ->
                if (status == null) {
                    newMarks.remove(student.student_id)
                } else {
                    newMarks[student.student_id] = status
                }
            }
            _roster.value = current.copy(marks = newMarks)
        }
    }

    fun updateStudentGroup(studentId: Int, newGroup: String) {
        val offeringId = activeCourseOfferingId ?: return
        val current = _roster.value as? RosterUiState.Loaded ?: return
        val updatedRoster = current.roster.map {
            if (it.student_id == studentId) it.copy(lab_group = newGroup) else it
        }
        _roster.value = current.copy(roster = updatedRoster)
        TeacherCacheManager.saveRoster(offeringId, updatedRoster)
        viewModelScope.launch {
            try {
                repo.updateStudentLabGroup(offeringId, studentId, newGroup)
            } catch (_: Exception) {}
        }
    }

    fun submitAttendance(
        durationPeriods: Int? = null,
        batchLabel: String? = null,
        defaultUnmarkedTo: AttendanceStatus = AttendanceStatus.ABSENT,
        targetStudentIds: Set<Int>? = null
    ) {
        val current = _roster.value
        val courseOfferingId = activeCourseOfferingId
        if (current !is RosterUiState.Loaded || courseOfferingId == null) return

        val resolvedDuration = durationPeriods
            ?: current.courseInfo?.duration_periods
            ?: courseInfoCache[courseOfferingId]?.duration_periods
            ?: 1

        val resolvedBatchLabel = batchLabel
            ?: current.courseInfo?.batch_label

        val submittingStudents = if (targetStudentIds != null) {
            current.roster.filter { it.student_id in targetStudentIds }
        } else {
            current.roster
        }

        _submit.value = SubmitUiState.Submitting
        viewModelScope.launch {
            val nowUtc = Instant.now().toString()
            val localToday = EffectiveDate.toString()
            val targetDate = activeSessionDate ?: localToday
            val payload = OfflineSyncPayload(
                session_uuid = UUID.randomUUID().toString(),
                course_offering_id = courseOfferingId,
                date = targetDate,
                duration_periods = resolvedDuration,
                batch_label = resolvedBatchLabel,
                records = submittingStudents.map { student ->
                    val finalStatus = current.marks[student.student_id] ?: defaultUnmarkedTo
                    SyncRecordItem(
                        record_uuid = UUID.randomUUID().toString(),
                        student_id = student.student_id,
                        status = finalStatus,
                        captured_at = nowUtc
                    )
                },
            )
            _submit.value = try {
                val response = repo.submitAttendance(payload)
                TeacherCacheManager.recordSubmittedClass(courseOfferingId, targetDate)
                val updatedRoster = current.roster.map { student ->
                    if (targetStudentIds == null || student.student_id in targetStudentIds) {
                        val finalStatus = current.marks[student.student_id] ?: defaultUnmarkedTo
                        student.copy(status = finalStatus)
                    } else {
                        student
                    }
                }
                TeacherCacheManager.saveRoster(courseOfferingId, updatedRoster)
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
                TeacherCacheManager.recordSubmittedClass(courseOfferingId, targetDate)
                val updatedRoster = current.roster.map { student ->
                    if (targetStudentIds == null || student.student_id in targetStudentIds) {
                        val finalStatus = current.marks[student.student_id] ?: defaultUnmarkedTo
                        student.copy(status = finalStatus)
                    } else {
                        student
                    }
                }
                TeacherCacheManager.saveRoster(courseOfferingId, updatedRoster)
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