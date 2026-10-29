package com.akshit.attendance.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.TokenManager
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
        val teacherName: String? = null
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
            _schedule.value = ScheduleUiState.Loading
            _schedule.value = try {
                val classes = repo.getSchedule(null, day)
                val teacherName = tokenManager?.userName()
                val todayStr = LocalDate.now().toString()

                val submittedToday = try {
                    repo.getHistory()
                        .filter { it.date == todayStr }
                        .map { it.course_offering_id }
                        .toSet()
                } catch (_: Exception) {
                    emptySet()
                }

                ScheduleUiState.Loaded(
                    classes = classes,
                    submittedOfferingIds = submittedToday,
                    teacherName = teacherName
                )
            } catch (e: Exception) {
                ScheduleUiState.Error(e.message ?: "Could not load today's classes.")
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
            _roster.value = try {
                // Ensure the schedule is loaded so we can lookup course details
                if (_schedule.value !is ScheduleUiState.Loaded) {
                    try {
                        val classes = repo.getSchedule(null, null)
                        val teacherName = tokenManager?.userName()
                        val todayStr = LocalDate.now().toString()
                        val submittedToday = try {
                            repo.getHistory()
                                .filter { it.date == todayStr }
                                .map { it.course_offering_id }
                                .toSet()
                        } catch (_: Exception) { emptySet() }
                        _schedule.value = ScheduleUiState.Loaded(
                            classes = classes,
                            submittedOfferingIds = submittedToday,
                            teacherName = teacherName
                        )
                    } catch (_: Exception) { /* best effort */ }
                }

                val targetDate = sessionDate ?: LocalDate.now().toString()
                val roster = repo.getRoster(courseOfferingId, targetDate)

                // Lookup the course details from our already loaded schedule or custom course info
                val currentSchedule = _schedule.value
                val courseInfo = customCourseInfo ?: if (currentSchedule is ScheduleUiState.Loaded) {
                    currentSchedule.classes.find { it.course_offering_id == courseOfferingId }
                } else null

                RosterUiState.Loaded(
                    roster = roster,
                    marks = roster.associate { it.student_id to (it.status ?: AttendanceStatus.PRESENT) },
                    courseInfo = courseInfo,
                    sessionDate = sessionDate
                )
            } catch (e: Exception) {
                RosterUiState.Error(e.message ?: "Could not load roster.")
            }
        }
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
                SubmitUiState.Error(e.message ?: "Could not submit. Check your connection and retry.")
            }
        }
    }

    suspend fun getAllCourses(): List<CourseScheduleResponse> {
        return try {
            repo.getSchedule(null, "ALL").distinctBy { it.course_offering_id }
        } catch (_: Exception) {
            val curr = _schedule.value
            if (curr is ScheduleUiState.Loaded) curr.classes.distinctBy { it.course_offering_id } else emptyList()
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