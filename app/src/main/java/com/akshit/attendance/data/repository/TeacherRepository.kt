package com.akshit.attendance.data.repository

import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.ApiService

interface TeacherRepository {
    suspend fun getSchedule(termId: Int? = null, day: String? = null): List<CourseScheduleResponse>
    suspend fun getPendingClasses(): List<PendingClassResponse>
    suspend fun getRoster(courseOfferingId: Int, date: String? = null): List<StudentRosterResponse>
    suspend fun submitAttendance(payload: OfflineSyncPayload): SyncResponse
    suspend fun getHistory(): List<SessionHistoryResponse>
    suspend fun submitCorrectionRequest(request: CorrectionRequestCreate): SimpleStatus
    suspend fun postponeClass(offeringId: Int, date: String, reason: String): SimpleStatus
    suspend fun getTimetable(teacherName: String? = null, facultyId: String? = null): TeacherTimetableResponse
    suspend fun getProfile(): UserProfileResponse
}

class RealTeacherRepository(private val api: ApiService) : TeacherRepository {
    override suspend fun getSchedule(termId: Int?, day: String?) = api.getSchedule(termId, day)
    override suspend fun getPendingClasses() = api.getPendingClasses()
    override suspend fun getRoster(courseOfferingId: Int, date: String?) = api.getRoster(courseOfferingId, date)
    override suspend fun submitAttendance(payload: OfflineSyncPayload) = api.syncAttendance(payload)
    override suspend fun getHistory() = api.getAttendanceHistory()
    override suspend fun submitCorrectionRequest(request: CorrectionRequestCreate) = api.submitCorrection(request)
    override suspend fun postponeClass(offeringId: Int, date: String, reason: String) =
        api.postponeClass(PostponeClassRequest(offeringId, date, reason))
    override suspend fun getTimetable(teacherName: String?, facultyId: String?) =
        api.getTeacherTimetable(teacherName, facultyId)
    override suspend fun getProfile(): UserProfileResponse = api.getMyProfile()
}

/** Sample data for UI work while fully offline. */
class MockTeacherRepository : TeacherRepository {
    override suspend fun getSchedule(termId: Int?, day: String?) = listOf(
        CourseScheduleResponse(
            course_offering_id = 1,
            subject_name = "Data Structures & Algorithms",
            subject_code = "CS401",
            section_name = "Section A",
            start_time = "09:30:00",
            end_time = "10:30:00",
            room = "IL203"
        ),
        CourseScheduleResponse(
            course_offering_id = 2,
            subject_name = "Operating Systems",
            subject_code = "CS502",
            section_name = "Section B",
            start_time = "11:30:00",
            end_time = "12:30:00",
            room = "IL204"
        ),
        CourseScheduleResponse(
            course_offering_id = 3,
            subject_name = "Database Management Systems",
            subject_code = "CS303",
            section_name = "Section C",
            start_time = "14:30:00",
            end_time = "15:30:00",
            room = "IL205"
        )
    )

    override suspend fun getPendingClasses(): List<PendingClassResponse> = emptyList()

    override suspend fun getRoster(courseOfferingId: Int, date: String?) = listOf(
        StudentRosterResponse(1, "21CS001", "Aarav Patel"),
        StudentRosterResponse(2, "21CS002", "Ananya Iyer"),
        StudentRosterResponse(3, "21CS003", "Devendra Kumar"),
        StudentRosterResponse(4, "21CS004", "Fatima Zahra"),
        StudentRosterResponse(5, "21CS005", "Ishaan Verma"),
    )

    override suspend fun submitAttendance(payload: OfflineSyncPayload) =
        SyncResponse("success", "Successfully synchronized ${payload.records.size} records.", session_id = 1)

    override suspend fun getHistory() = listOf(
        SessionHistoryResponse(1, "Data Structures & Algorithms", "Section A", "2023-10-19"),
        SessionHistoryResponse(2, "Operating Systems", "Section B", "2023-10-18"),
    )

    override suspend fun submitCorrectionRequest(request: CorrectionRequestCreate) =
        SimpleStatus("success", "Submitted for HOD approval.")

    override suspend fun postponeClass(offeringId: Int, date: String, reason: String) =
        SimpleStatus("success", "Class postponed successfully.")

    override suspend fun getTimetable(teacherName: String?, facultyId: String?) =
        TeacherTimetableResponse(
            title = "Raju Pal (CSE)",
            subtitle = "Faculty Timetable • SOICT",
            semester_info = "GBU 2026-27 (ODD SEMESTER) • Faculty Weekly Load",
            total_hours = 20.0,
            total_slots = 5,
            slots = listOf(
                TimetableSlotItem(
                    day = 1,
                    day_name = "Mon",
                    period = 2,
                    period_time = "09:30-10:30",
                    subject_code = "CSE283",
                    subject_name = "Data Structures Lab",
                    teacher_name = "Raju Pal",
                    teacher_abbr = "RP",
                    section_name = "CS-II-A",
                    room = "IP109",
                    building = "SOICT",
                    activity = "Lab"
                ),
                TimetableSlotItem(
                    day = 1,
                    day_name = "Mon",
                    period = 3,
                    period_time = "10:30-11:30",
                    subject_code = "CSE283",
                    subject_name = "Data Structures Lab",
                    teacher_name = "Raju Pal",
                    teacher_abbr = "RP",
                    section_name = "CS-II-A",
                    room = "IP109",
                    building = "SOICT",
                    activity = "Lab"
                ),
                TimetableSlotItem(
                    day = 2,
                    day_name = "Tue",
                    period = 4,
                    period_time = "11:30-12:30",
                    subject_code = "CS523",
                    subject_name = "Advanced Data Structure and Algorithm",
                    teacher_name = "Raju Pal",
                    teacher_abbr = "RP",
                    section_name = "MT-DS-I",
                    room = "ICT-Conf Hall",
                    building = "SOICT",
                    activity = "Lecture"
                ),
            ),
            workload = listOf(
                TeachingWorkloadItem("CS-II-A", "CSE203", "Data Structures", "LECTURE", 3.0),
                TeachingWorkloadItem("CS-II-A", "CSE283", "Data Structures Lab", "LAB", 8.0),
                TeachingWorkloadItem("MT-AIR-I", "CS523", "Advanced Data Structure and Algorithm", "LECTURE", 3.0),
                TeachingWorkloadItem("MT-DS-I", "CS523", "Advanced Data Structure and Algorithm", "LECTURE", 3.0),
                TeachingWorkloadItem("MT-SE-I", "CS523", "Advanced Data Structure and Algorithm", "LECTURE", 3.0)
            )
        )

    override suspend fun getProfile(): UserProfileResponse = UserProfileResponse(
        user_id = 1,
        email = "teacher@gbu.ac.in",
        full_name = "Prof. Vidushi Sharma",
        department = "Computer Science and Engineering",
        school = "School of Information and Communication Technology",
        roles = listOf("TEACHER")
    )
}