package com.akshit.attendance.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.StudentCacheManager
import com.akshit.attendance.data.repository.StudentRepository
import com.akshit.attendance.util.AppDateProvider
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

sealed interface SubjectHistoryUiState {
    object Loading : SubjectHistoryUiState
    data class Loaded(val data: StudentSubjectHistoryResponse) : SubjectHistoryUiState
    data class Error(val message: String) : SubjectHistoryUiState
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

    private val _subjectHistory = MutableStateFlow<SubjectHistoryUiState>(SubjectHistoryUiState.Loading)
    val subjectHistory: StateFlow<SubjectHistoryUiState> = _subjectHistory.asStateFlow()


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
        // 1. Immediately hydrate in-memory state from disk cache (0ms latency, 100% offline-ready)
        StudentCacheManager.loadProfile()?.let { _profile.value = it }

        StudentCacheManager.loadDashboard()?.let { cached ->
            _dashboard.value = DashboardUiState.Loaded(cached)
            if (cached.subjects.isNotEmpty()) {
                _plannerItems.value = cached.subjects.filter { it.requires_attendance }.map {
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

        val cachedSchedule = StudentCacheManager.loadSchedule()
        if (cachedSchedule.isNotEmpty()) {
            _schedule.value = sortSchedule(cachedSchedule)
        }

        StudentCacheManager.loadTimetable()?.let {
            _timetable.value = StudentTimetableUiState.Loaded(it)
        }

        // 2. Fetch fresh network data in background
        loadAll()
    }

    private fun sortSchedule(raw: List<StudentScheduleSlot>): List<StudentScheduleSlot> {
        return raw.sortedWith(compareBy({ slot ->
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
            try {
                val minDelayDeferred = async { delay(600L) }
                val profileDeferred = async { try { repo.getProfile() } catch (e: Exception) { null } }
                val dashboardDeferred = async { try { repo.getDashboard(null) } catch (e: Exception) { null } }
                val scheduleDeferred = async { try { repo.getSchedule() } catch (e: Exception) { null } }
                val timetableDeferred = async { try { repo.getTimetable() } catch (e: Exception) { null } }
                val currentMonthKey = AppDateProvider.todayString.take(7)
                val calendarDeferred = async { try { repo.getCalendar(currentMonthKey) } catch (e: Exception) { null } }
                val historyDeferred = async { try { repo.getHistory() } catch (e: Exception) { null } }

                val prof = profileDeferred.await()
                if (prof != null) {
                    StudentCacheManager.saveProfile(prof)
                    _profile.value = prof
                }

                val newDash = dashboardDeferred.await()
                if (newDash != null) {
                    StudentCacheManager.saveDashboard(newDash)
                    _dashboard.value = DashboardUiState.Loaded(newDash)
                    OfflineAttendanceManager.markOnline()
                    if (_plannerItems.value.isEmpty() && newDash.subjects.isNotEmpty()) {
                        _plannerItems.value = newDash.subjects.filter { it.requires_attendance }.map {
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
                } else {
                    OfflineAttendanceManager.markOffline()
                    if (_dashboard.value !is DashboardUiState.Loaded) {
                        val cached = StudentCacheManager.loadDashboard()
                        if (cached != null) {
                            _dashboard.value = DashboardUiState.Loaded(cached)
                        } else {
                            _dashboard.value = DashboardUiState.Error("You are offline. Showing cached data.")
                        }
                    }
                }

                val rawSchedule = scheduleDeferred.await()
                if (rawSchedule != null) {
                    StudentCacheManager.saveSchedule(rawSchedule)
                    _schedule.value = sortSchedule(rawSchedule)
                    OfflineAttendanceManager.markOnline()
                } else {
                    OfflineAttendanceManager.markOffline()
                    if (_schedule.value.isEmpty()) {
                        val cached = StudentCacheManager.loadSchedule()
                        if (cached.isNotEmpty()) {
                            _schedule.value = sortSchedule(cached)
                        }
                    }
                }

                val newTimetable = timetableDeferred.await()
                if (newTimetable != null) {
                    StudentCacheManager.saveTimetable(newTimetable)
                    _timetable.value = StudentTimetableUiState.Loaded(newTimetable)
                    OfflineAttendanceManager.markOnline()
                } else {
                    OfflineAttendanceManager.markOffline()
                    if (_timetable.value !is StudentTimetableUiState.Loaded) {
                        val cached = StudentCacheManager.loadTimetable()
                        if (cached != null) {
                            _timetable.value = StudentTimetableUiState.Loaded(cached)
                        }
                    }
                }

                calendarDeferred.await()?.let {
                    StudentCacheManager.saveCalendar(currentMonthKey, it)
                    _calendarState.value = CalendarUiState.Loaded(it)
                }
                historyDeferred.await()?.let { _history.value = it }
                minDelayDeferred.await()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_dashboard.value !is DashboardUiState.Loaded) {
                    val cached = StudentCacheManager.loadDashboard()
                    if (cached != null) {
                        _dashboard.value = DashboardUiState.Loaded(cached)
                    } else {
                        _dashboard.value = DashboardUiState.Error("You are offline. Connect to the internet to refresh.")
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            if (_profile.value == null) {
                StudentCacheManager.loadProfile()?.let { _profile.value = it }
            }
            try {
                val data = repo.getProfile()
                StudentCacheManager.saveProfile(data)
                _profile.value = data
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_profile.value == null) {
                    StudentCacheManager.loadProfile()?.let { _profile.value = it }
                }
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            if (_dashboard.value !is DashboardUiState.Loaded) {
                val cached = StudentCacheManager.loadDashboard()
                if (cached != null) {
                    _dashboard.value = DashboardUiState.Loaded(cached)
                } else {
                    _dashboard.value = DashboardUiState.Loading
                }
            }
            try {
                val data = repo.getDashboard(null)
                StudentCacheManager.saveDashboard(data)
                _dashboard.value = DashboardUiState.Loaded(data)
                OfflineAttendanceManager.markOnline()
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
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_dashboard.value !is DashboardUiState.Loaded) {
                    val cached = StudentCacheManager.loadDashboard()
                    if (cached != null) {
                        _dashboard.value = DashboardUiState.Loaded(cached)
                    } else {
                        _dashboard.value = DashboardUiState.Error(
                            if (StudentCacheManager.isNetworkError(e))
                                "You are offline. Connect to the internet to load attendance."
                            else (e.message ?: "Could not load dashboard.")
                        )
                    }
                }
            }
        }
    }

    fun loadSchedule() {
        viewModelScope.launch {
            if (_schedule.value.isEmpty()) {
                val cached = StudentCacheManager.loadSchedule()
                if (cached.isNotEmpty()) {
                    _schedule.value = sortSchedule(cached)
                }
            }
            try {
                val raw = repo.getSchedule()
                StudentCacheManager.saveSchedule(raw)
                _schedule.value = sortSchedule(raw)
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_schedule.value.isEmpty()) {
                    val cached = StudentCacheManager.loadSchedule()
                    if (cached.isNotEmpty()) {
                        _schedule.value = sortSchedule(cached)
                    }
                }
            }
        }
    }

    fun loadTimetable() {
        viewModelScope.launch {
            if (_timetable.value !is StudentTimetableUiState.Loaded) {
                val cached = StudentCacheManager.loadTimetable()
                if (cached != null) {
                    _timetable.value = StudentTimetableUiState.Loaded(cached)
                } else {
                    _timetable.value = StudentTimetableUiState.Loading
                }
            }
            try {
                val data = repo.getTimetable()
                StudentCacheManager.saveTimetable(data)
                _timetable.value = StudentTimetableUiState.Loaded(data)
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_timetable.value !is StudentTimetableUiState.Loaded) {
                    val cached = StudentCacheManager.loadTimetable()
                    if (cached != null) {
                        _timetable.value = StudentTimetableUiState.Loaded(cached)
                    } else {
                        _timetable.value = StudentTimetableUiState.Error(
                            if (StudentCacheManager.isNetworkError(e))
                                "You are offline. Connect to the internet to view timetable."
                            else (e.message ?: "Could not load timetable.")
                        )
                    }
                }
            }
        }
    }

    fun loadCalendar(month: String? = null) {
        val targetMonth = month ?: AppDateProvider.todayString.take(7)
        viewModelScope.launch {
            val cached = StudentCacheManager.loadCalendar(targetMonth)
            if (cached != null && _calendarState.value !is CalendarUiState.Loaded) {
                _calendarState.value = CalendarUiState.Loaded(cached)
            } else if (_calendarState.value !is CalendarUiState.Loaded) {
                _calendarState.value = CalendarUiState.Loading
            }
            try {
                val data = repo.getCalendar(targetMonth)
                StudentCacheManager.saveCalendar(targetMonth, data)
                _calendarState.value = CalendarUiState.Loaded(data)
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_calendarState.value !is CalendarUiState.Loaded) {
                    val c = StudentCacheManager.loadCalendar(targetMonth)
                    if (c != null) {
                        _calendarState.value = CalendarUiState.Loaded(c)
                    } else {
                        _calendarState.value = CalendarUiState.Error(
                            if (StudentCacheManager.isNetworkError(e))
                                "You are offline. Connect to the internet to load calendar."
                            else (e.message ?: "Could not load attendance calendar.")
                        )
                    }
                }
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

    fun loadSubjectHistory(courseCode: String) {
        viewModelScope.launch {
            val cached = StudentCacheManager.loadSubjectHistory(courseCode)
            if (cached != null) {
                _subjectHistory.value = SubjectHistoryUiState.Loaded(cached)
            } else {
                _subjectHistory.value = SubjectHistoryUiState.Loading
            }
            try {
                val data = repo.getSubjectHistory(courseCode)
                StudentCacheManager.saveSubjectHistory(courseCode, data)
                _subjectHistory.value = SubjectHistoryUiState.Loaded(data)
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                if (StudentCacheManager.isNetworkError(e)) {
                    OfflineAttendanceManager.markOffline()
                }
                if (_subjectHistory.value !is SubjectHistoryUiState.Loaded) {
                    val c = StudentCacheManager.loadSubjectHistory(courseCode)
                    if (c != null) {
                        _subjectHistory.value = SubjectHistoryUiState.Loaded(c)
                    } else {
                        _subjectHistory.value = SubjectHistoryUiState.Error(
                            if (StudentCacheManager.isNetworkError(e))
                                "You are offline. Connect to the internet to view subject records."
                            else (e.message ?: "Could not load subject attendance records.")
                        )
                    }
                }
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
            _plannerItems.value = dash.subjects.filter { it.requires_attendance }.map {
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
