package com.akshit.attendance.data.model

import kotlinx.serialization.Serializable

// --- Auth (matches app/models/auth.py + auth.py LoginRequest) ---

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RoleOption(val role: String, val scope_type: String, val scope_id: Int? = null)

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
    val must_change_password: Boolean = false,
    val available_roles: List<RoleOption> = emptyList(),
    val active_role: String = "",
    val user_name: String? = null,
    val user_email: String? = null,
)

@Serializable
data class SwitchRoleRequest(val role: String, val scope_id: Int? = null)

@Serializable
data class RefreshTokenRequest(val refresh_token: String)

@Serializable
data class ChangePasswordRequest(val old_password: String, val new_password: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val email: String, val otp: String, val new_password: String)

@Serializable
data class UserProfileResponse(
    val user_id: Int,
    val full_name: String,
    val email: String,
    val roles: List<String> = emptyList(),
    val primary_role: String = "USER",
    val department: String = "Computer Science and Engineering",
    val school: String = "School of Information and Communication Technology",
    val total_courses: Int? = null,
    val weekly_hours: Double? = null,
    val roll_number: String? = null,
    val employee_code: String? = null,
    val program_name: String? = null,
    val batch_name: String? = null,
    val overall_attendance_pct: Double? = null
)

@Serializable
data class SimpleStatus(val status: String, val message: String? = null)

// --- Teacher (matches app/api/v1/mobile/attendance.py) ---

@Serializable
data class CourseScheduleResponse(
    val course_offering_id: Int,
    val subject_name: String,
    val subject_code: String,
    val section_name: String,
    val start_time: String,
    val end_time: String,
    val room: String? = null,
    val credits: Int? = 3,
    val course_type: String? = "LECTURE",
    val weekly_hours: Double? = null
)

@Serializable
data class StudentRosterResponse(
    val student_id: Int,
    val roll_number: String,
    val full_name: String,
    val status: AttendanceStatus? = null,
)

@Serializable
enum class AttendanceStatus { PRESENT, ABSENT }

@Serializable
data class SyncRecordItem(
    val record_uuid: String,
    val student_id: Int,
    val status: AttendanceStatus,
    val captured_at: String, // ISO-8601; device time at the moment the teacher tapped
)

@Serializable
data class OfflineSyncPayload(
    val session_uuid: String,
    val course_offering_id: Int,
    val date: String, // yyyy-MM-dd
    val duration_periods: Int = 1,
    val mode: String = "MANUAL",
    val is_held: Boolean = true,
    val reason_not_held: String? = null,
    val records: List<SyncRecordItem>,
)

@Serializable
data class SyncResponse(val status: String, val message: String, val session_id: Int? = null)

@Serializable
data class PendingClassResponse(
    val course_offering_id: Int,
    val subject_name: String,
    val subject_code: String,
    val section_name: String,
    val date: String,
    val formatted_date: String,
    val day_name: String,
    val start_time: String,
    val end_time: String,
    val room: String? = "TBA",
    val days_remaining: Int,
    val is_expiring_soon: Boolean = false,
)

// --- Student (matches app/api/v1/mobile/student.py) ---

@Serializable
data class SubjectAttendanceHistory(
    val subject_name: String,
    val course_code: String,
    val teacher_name: String,
    val periods_held: Int,
    val periods_attended: Int,
    val percentage: Double,
    val standing: String,
)

@Serializable
data class StudentDashboardResponse(
    val current_semester: String,
    val overall_percentage: Double,
    val highest_score_subject: String,
    val risk_level: String,
    val subjects: List<SubjectAttendanceHistory>,
)

@Serializable
data class StudentScheduleSlot(
    val course_offering_id: Int,
    val subject_code: String,
    val subject_name: String,
    val section_name: String,
    val day_of_week: String,
    val start_time: String,
    val end_time: String,
    val room: String? = null,
    val batch_label: String? = null,
    val teacher_name: String? = null,
)

// --- Leaves (matches app/api/v1/mobile/leaves.py — Medical only; the mockup's
// Duty/Special categories have no backend support yet, so they're left out of the
// request model rather than built as dead UI) ---

@Serializable
data class LeaveApplyResponse(val status: String, val message: String, val leave_id: Int? = null)

// --- Teacher history & corrections (endpoints exist now — see note in
// TeacherRepository about a server-side bug that currently makes both 500) ---

@Serializable
data class SessionHistoryResponse(
    val course_offering_id: Int,
    val subject_name: String,
    val section_name: String,
    val date: String,
)

@Serializable
data class CorrectionRequestCreate(
    val attendance_record_id: Int,
    val suggested_status: AttendanceStatus,
    val reason: String,
)

// --- Student history: still no endpoint (dashboard only returns per-subject
// aggregates) — this stays mock-backed. ---

@Serializable
data class AttendanceHistoryEntry(
    val date: String,
    val subject_name: String,
    val status: String, // PRESENT / ABSENT / MEDICAL_LEAVE
    val note: String? = null,
)

// --- Timetable & Faculty Workload (Official Samay Live Data) ---

@Serializable
data class TimetableSlotItem(
    val day: Int,
    val day_name: String,
    val period: Int,
    val period_time: String,
    val subject_code: String,
    val subject_name: String,
    val teacher_name: String,
    val teacher_abbr: String,
    val section_name: String,
    val room: String,
    val building: String,
    val activity: String
)

@Serializable
data class TeachingWorkloadItem(
    val section_name: String,
    val subject_code: String,
    val subject_name: String,
    val activity: String,
    val hours: Double,
    val teacher_name: String? = null
)

@Serializable
data class TeacherTimetableResponse(
    val title: String,
    val subtitle: String,
    val semester_info: String = "GBU 2026-27 (ODD SEMESTER) • Faculty Weekly Load",
    val total_hours: Double = 0.0,
    val total_slots: Int = 0,
    val slots: List<TimetableSlotItem> = emptyList(),
    val workload: List<TeachingWorkloadItem> = emptyList(),
    val period_definitions: Map<String, String> = emptyMap(),
    val day_definitions: Map<String, String> = emptyMap()
)

@Serializable
data class CalendarClassItem(
    val course_offering_id: Int,
    val subject_code: String,
    val subject_name: String,
    val teacher_name: String,
    val room: String,
    val start_time: String,
    val end_time: String,
    val status: String,
    val batch_label: String? = null
)

@Serializable
data class CalendarDayData(
    val date: String,
    val day_name: String,
    val day_number: Int,
    val has_classes: Boolean,
    val dot_status: String? = null,
    val classes: List<CalendarClassItem> = emptyList()
)

@Serializable
data class StudentCalendarResponse(
    val month: String,
    val month_label: String,
    val total_attended: Int = 0,
    val total_missed: Int = 0,
    val days: Map<String, CalendarDayData> = emptyMap()
)

@Serializable
data class PostponeClassRequest(
    val course_offering_id: Int,
    val date: String,
    val reason: String
)
