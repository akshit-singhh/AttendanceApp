package com.akshit.attendance.data.repository

import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.ApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

interface StudentRepository {
    suspend fun getDashboard(termId: Int? = null): StudentDashboardResponse
    suspend fun getProfile(): UserProfileResponse
    suspend fun getSchedule(): List<StudentScheduleSlot>
    suspend fun getTimetable(): TeacherTimetableResponse
    suspend fun getCalendar(month: String? = null): StudentCalendarResponse
    suspend fun applyLeave(startDate: String, endDate: String, reason: String, document: File): LeaveApplyResponse
    suspend fun getHistory(): List<AttendanceHistoryEntry>
}

class RealStudentRepository(private val api: ApiService) : StudentRepository {
    override suspend fun getDashboard(termId: Int?) = api.getStudentDashboard(termId)
    override suspend fun getProfile(): UserProfileResponse = api.getMyProfile()
    override suspend fun getSchedule(): List<StudentScheduleSlot> = api.getStudentSchedule()
    override suspend fun getTimetable(): TeacherTimetableResponse = api.getStudentTimetable()
    override suspend fun getCalendar(month: String?) = api.getStudentCalendar(month)

    override suspend fun applyLeave(startDate: String, endDate: String, reason: String, document: File): LeaveApplyResponse {
        val docBody = document.asRequestBody("application/octet-stream".toMediaType())
        val docPart = MultipartBody.Part.createFormData("document", document.name, docBody)
        return api.applyLeave(
            startDate = startDate.toRequestBody("text/plain".toMediaType()),
            endDate = endDate.toRequestBody("text/plain".toMediaType()),
            reason = reason.toRequestBody("text/plain".toMediaType()),
            document = docPart,
        )
    }

    override suspend fun getHistory(): List<AttendanceHistoryEntry> {
        return emptyList()
    }
}

/** Sample data matching the subject_wise_history mockup. */
class MockStudentRepository : StudentRepository {
    override suspend fun getDashboard(termId: Int?) = StudentDashboardResponse(
        current_semester = "Semester 5 (2026-27 Odd)",
        overall_percentage = 82.4,
        highest_score_subject = "Internet of Things (92.0%)",
        risk_level = "1 Critical",
        subjects = listOf(
            SubjectAttendanceHistory("Internet of Things", "CS401", "Dr. Vidushi Sharma", 37, 34, 91.9, "Excellent"),
            SubjectAttendanceHistory("Operating Systems", "CS502", "Prof. Sharma", 36, 32, 88.9, "Good Standing"),
            SubjectAttendanceHistory("Computer Networks", "CS403", "Prof. Iyer", 36, 31, 86.1, "Good Standing"),
            SubjectAttendanceHistory("Database Systems", "CS303", "Prof. Verma", 27, 19, 70.4, "Low Attendance"),
            SubjectAttendanceHistory("Machine Learning", "CS405", "Dr. Gautam", 25, 20, 80.0, "Good Standing"),
        ),
    )

    override suspend fun getProfile() = UserProfileResponse(
        user_id = 1,
        full_name = "Aditya kumar",
        email = "235ics002@gbu.ac.in",
        roles = listOf("STUDENT"),
        primary_role = "STUDENT",
        roll_number = "235ICS002",
        program_name = "Integrated Btech-Mtech",
        batch_name = "2024-2028",
        department = "Computer Science and Engineering"
    )

    override suspend fun getSchedule() = listOf(
        StudentScheduleSlot(1, "CS401", "Internet of Things", "CS-IV-A", "MON", "09:30:00", "10:30:00", "IL203", teacher_name = "Dr. Vidushi Sharma"),
        StudentScheduleSlot(2, "CS502", "Operating Systems", "CS-IV-A", "MON", "10:30:00", "11:30:00", "IL204", teacher_name = "Prof. Sharma"),
        StudentScheduleSlot(3, "CS403", "Computer Networks", "CS-IV-A", "MON", "11:30:00", "12:30:00", "IL203", teacher_name = "Prof. Iyer"),
    )

    override suspend fun getTimetable() = TeacherTimetableResponse(
        title = "Aditya kumar (235ICS002)",
        subtitle = "Student Timetable • CS-IV-A"
    )

    override suspend fun getCalendar(month: String?) = StudentCalendarResponse("2026-10", "October 2026")

    override suspend fun applyLeave(startDate: String, endDate: String, reason: String, document: File) =
        LeaveApplyResponse("success", "Leave request submitted successfully.", leave_id = 1)

    override suspend fun getHistory() = listOf(
        AttendanceHistoryEntry("2026-10-12", "Database Management Systems", "ABSENT", "Unexcused"),
        AttendanceHistoryEntry("2026-09-28", "Database Management Systems", "MEDICAL_LEAVE", "Ref: MED-8829"),
        AttendanceHistoryEntry("2026-09-15", "Database Management Systems", "ABSENT"),
    )
}
