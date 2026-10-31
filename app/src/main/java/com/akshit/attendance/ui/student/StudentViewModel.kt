package com.akshit.attendance.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.repository.StudentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Loaded(val data: StudentDashboardResponse) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

sealed interface StudentTimetableUiState {
    object Loading : StudentTimetableUiState
    data class Loaded(val data: TeacherTimetableResponse) : StudentTimetableUiState
    data class Error(val message: String) : StudentTimetableUiState
}

sealed interface CalendarUiState {
    object Loading : CalendarUiState
    data class Loaded(val data: StudentCalendarResponse) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

sealed interface LeaveUiState {
    object Idle : LeaveUiState
    object Submitting : LeaveUiState
    data class Submitted(val response: LeaveApplyResponse) : LeaveUiState
    data class Error(val message: String) : LeaveUiState
}

sealed interface BunkAdvice {
    data class Safe(val classesCanMiss: Int, val percentage: Double) : BunkAdvice
    data class AtRisk(val classesNeeded: Int, val percentage: Double) : BunkAdvice
    object NoClasses : BunkAdvice
}

data class PlannerSubjectItem(
    val code: String,
    val name: String,
    val currentAttended: Int,
    val currentHeld: Int,
    val upcomingClasses: Int = 10,
    val plannedAttend: Int = 8
)

class StudentViewModel(private val repo: StudentRepository) : ViewModel() {

    private val _dashboard = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val dashboard: StateFlow<DashboardUiState> = _dashboard.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileResponse?>(null)
    val profile: StateFlow<UserProfileResponse?> = _profile.asStateFlow()

    private val _schedule = MutableStateFlow<List<StudentScheduleSlot>>(emptyList())
    val schedule: StateFlow<List<StudentScheduleSlot>> = _schedule.asStateFlow()

    private val _timetable = MutableStateFlow<StudentTimetableUiState>(StudentTimetableUiState.Loading)
    val timetable: StateFlow<StudentTimetableUiState> = _timetable.asStateFlow()

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState.asStateFlow()

    private val _history = MutableStateFlow<List<AttendanceHistoryEntry>>(emptyList())
    val history: StateFlow<List<AttendanceHistoryEntry>> = _history.asStateFlow()

    private val _leaveState = MutableStateFlow<LeaveUiState>(LeaveUiState.Idle)
    val leaveState: StateFlow<LeaveUiState> = _leaveState.asStateFlow()

    // Self attendance check-in: Map of "slotId_date" -> "PRESENT" / "ABSENT"
    private val _selfMarked = MutableStateFlow<Map<String, String>>(emptyMap())
    val selfMarked: StateFlow<Map<String, String>> = _selfMarked.asStateFlow()

    // Planner items
    private val _plannerItems = MutableStateFlow<List<PlannerSubjectItem>>(emptyList())
    val plannerItems: StateFlow<List<PlannerSubjectItem>> = _plannerItems.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        loadProfile()
        loadDashboard()
        loadSchedule()
        loadTimetable()
        loadCalendar()
        loadHistory()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _dashboard.value = DashboardUiState.Loading
            _timetable.value = StudentTimetableUiState.Loading
            try {
                val minDelayDeferred = async { delay(600L) }
                val profileDeferred = async { try { repo.getProfile() } catch (e: Exception) { null } }
                val dashboardDeferred = async { try { repo.getDashboard(null) } catch (e: Exception) { null } }
                val scheduleDeferred = async { try { repo.getSchedule() } catch (e: Exception) { null } }
                val timetableDeferred = async { try { repo.getTimetable() } catch (e: Exception) { null } }
                val calendarDeferred = async { try { repo.getCalendar(null) } catch (e: Exception) { null } }
                val historyDeferred = async { try { repo.getHistory() } catch (e: Exception) { null } }

                profileDeferred.await()?.let { _profile.value = it }
                val newDash = dashboardDeferred.await()
                if (newDash != null) {
                    _dashboard.value = DashboardUiState.Loaded(newDash)
                    if (_plannerItems.value.isEmpty() && newDash.subjects.isNotEmpty()) {
                        _plannerItems.value = newDash.subjects.map {
                            PlannerSubjectItem(
                                code = it.course_code,
                                name = it.subject_name,
                                currentAttended = it.periods_attended,
                                currentHeld = it.periods_held,
                                upcomingClasses = 10,
                                plannedAttend = 8
                            )
                        }
                    }
                } else if (_dashboard.value is DashboardUiState.Loading) {
                    _dashboard.value = DashboardUiState.Error("Unable to refresh dashboard data.")
                }

                scheduleDeferred.await()?.let { raw ->
                    _schedule.value = raw.sortedWith(compareBy({ slot ->
                        when (slot.day_of_week.uppercase()) {
                            "MON" -> 1
                            "TUE" -> 2
                            "WED" -> 3
                            "THU" -> 4
                            "FRI" -> 5
                            "SAT" -> 6
                            else -> 7
                        }
                    }, { parseTimeToMinutes(it.start_time) }))
                }

                val newTimetable = timetableDeferred.await()
                if (newTimetable != null) {
                    _timetable.value = StudentTimetableUiState.Loaded(newTimetable)
                } else if (_timetable.value is StudentTimetableUiState.Loading) {
                    _timetable.value = StudentTimetableUiState.Error("Unable to refresh timetable data.")
                }

                calendarDeferred.await()?.let { _calendarState.value = CalendarUiState.Loaded(it) }
                historyDeferred.await()?.let { _history.value = it }
                minDelayDeferred.await()
            } catch (e: Exception) {
                if (_dashboard.value is DashboardUiState.Loading) {
                    _dashboard.value = DashboardUiState.Error(e.message ?: "Failed to refresh.")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = repo.getProfile()
            } catch (e: Exception) {
                // Profile load failure fallback
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboard.value = DashboardUiState.Loading
            _dashboard.value = try {
                val data = repo.getDashboard(null)
                // Initialize planner items from dashboard subjects if empty
                if (_plannerItems.value.isEmpty() && data.subjects.isNotEmpty()) {
                    _plannerItems.value = data.subjects.map {
                        PlannerSubjectItem(
                            code = it.course_code,
                            name = it.subject_name,
                            currentAttended = it.periods_attended,
                            currentHeld = it.periods_held,
                            upcomingClasses = 10,
                            plannedAttend = 8
                        )
                    }
                }
                DashboardUiState.Loaded(data)
            } catch (e: Exception) {
                DashboardUiState.Error(e.message ?: "Could not load dashboard.")
            }
        }
    }

    fun loadSchedule() {
        viewModelScope.launch {
            try {
                val raw = repo.getSchedule()
                _schedule.value = raw.sortedWith(compareBy({ slot ->
                    when (slot.day_of_week.uppercase()) {
                        "MON" -> 1
                        "TUE" -> 2
                        "WED" -> 3
                        "THU" -> 4
                        "FRI" -> 5
                        "SAT" -> 6
                        else -> 7
                    }
                }, { parseTimeToMinutes(it.start_time) }))
            } catch (e: Exception) {
                _schedule.value = emptyList()
            }
        }
    }

    fun loadTimetable() {
        viewModelScope.launch {
            _timetable.value = StudentTimetableUiState.Loading
            _timetable.value = try {
                val data = repo.getTimetable()
                StudentTimetableUiState.Loaded(data)
            } catch (e: Exception) {
                StudentTimetableUiState.Error(e.message ?: "Could not load timetable.")
            }
        }
    }

    fun loadCalendar(month: String? = null) {
        viewModelScope.launch {
            _calendarState.value = CalendarUiState.Loading
            _calendarState.value = try {
                val data = repo.getCalendar(month)
                CalendarUiState.Loaded(data)
            } catch (e: Exception) {
                CalendarUiState.Error(e.message ?: "Could not load attendance calendar.")
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _history.value = try {
                repo.getHistory()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun toggleSelfAttendance(key: String, status: String) {
        val curr = _selfMarked.value.toMutableMap()
        if (curr[key] == status) {
            curr.remove(key)
        } else {
            curr[key] = status
        }
        _selfMarked.value = curr
    }

    // --- Bunk Calculator Helpers ---
    fun getBunkAdvice(attended: Int, held: Int, target: Double = 75.0): BunkAdvice {
        if (held == 0) return BunkAdvice.NoClasses
        val pct = (attended.toDouble() / held.toDouble()) * 100.0
        return if (pct >= target) {
            val safe = ((4 * attended - 3 * held) / 3).coerceAtLeast(0)
            BunkAdvice.Safe(safe, pct)
        } else {
            val needed = ceil((3.0 * held - 4.0 * attended)).toInt().coerceAtLeast(1)
            BunkAdvice.AtRisk(needed, pct)
        }
    }

    // --- Planner Actions ---
    fun updatePlannerUpcoming(code: String, delta: Int) {
        _plannerItems.value = _plannerItems.value.map { item ->
            if (item.code == code) {
                val newUpcoming = (item.upcomingClasses + delta).coerceAtLeast(0)
                val newAttend = item.plannedAttend.coerceAtMost(newUpcoming)
                item.copy(upcomingClasses = newUpcoming, plannedAttend = newAttend)
            } else item
        }
    }

    fun updatePlannerAttend(code: String, delta: Int) {
        _plannerItems.value = _plannerItems.value.map { item ->
            if (item.code == code) {
                val newAttend = (item.plannedAttend + delta).coerceIn(0, item.upcomingClasses)
                item.copy(plannedAttend = newAttend)
            } else item
        }
    }

    fun applyPlannerScenario(weeks: Int) {
        val multiplier = weeks.coerceIn(1, 6)
        _plannerItems.value = _plannerItems.value.map { item ->
            val upcoming = 3 * multiplier
            val attend = 3 * multiplier
            item.copy(upcomingClasses = upcoming, plannedAttend = attend)
        }
    }

    fun resetPlanner() {
        val dash = (_dashboard.value as? DashboardUiState.Loaded)?.data
        if (dash != null) {
            _plannerItems.value = dash.subjects.map {
                PlannerSubjectItem(
                    code = it.course_code,
                    name = it.subject_name,
                    currentAttended = it.periods_attended,
                    currentHeld = it.periods_held,
                    upcomingClasses = 10,
                    plannedAttend = 8
                )
            }
        }
    }

    fun applyLeave(startDate: String, endDate: String, reason: String, document: File) {
        _leaveState.value = LeaveUiState.Submitting
        viewModelScope.launch {
            _leaveState.value = try {
                LeaveUiState.Submitted(repo.applyLeave(startDate, endDate, reason, document))
            } catch (e: Exception) {
                LeaveUiState.Error(e.message ?: "Could not submit leave request.")
            }
        }
    }
}
