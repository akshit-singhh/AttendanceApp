package com.akshit.attendance.data.network

import com.akshit.attendance.data.model.*
import retrofit2.http.*

// Every endpoint below exists in the backend as read from the repo. Nothing here is
// speculative — if a screen needs data with no matching call, that's handled by a
// mock repository (see data/repository/), not by inventing an endpoint here.
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequest): TokenResponse

    @POST("auth/switch-role")
    suspend fun switchRole(@Body body: SwitchRoleRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequest): SimpleStatus

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): SimpleStatus

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): SimpleStatus

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): SimpleStatus

    @GET("auth/me")
    suspend fun getMyProfile(): UserProfileResponse

    // --- Teacher ---

    @GET("mobile/attendance/schedule")
    suspend fun getSchedule(
        @Query("term_id") termId: Int? = null,
        @Query("day") day: String? = null
    ): List<CourseScheduleResponse>

    @GET("mobile/attendance/pending")
    suspend fun getPendingClasses(): List<PendingClassResponse>

    @GET("mobile/attendance/roster/{courseOfferingId}")
    suspend fun getRoster(
        @Path("courseOfferingId") courseOfferingId: Int,
        @Query("date") date: String? = null
    ): List<StudentRosterResponse>

    @POST("mobile/attendance/sync")
    suspend fun syncAttendance(@Body body: OfflineSyncPayload): SyncResponse

    @GET("mobile/attendance/history")
    suspend fun getAttendanceHistory(): List<SessionHistoryResponse>

    @POST("mobile/attendance/corrections")
    suspend fun submitCorrection(@Body body: CorrectionRequestCreate): SimpleStatus

    @POST("mobile/attendance/postpone")
    suspend fun postponeClass(@Body body: PostponeClassRequest): SimpleStatus

    @GET("mobile/timetable/teacher")
    suspend fun getTeacherTimetable(
        @Query("teacher_name") teacherName: String? = null,
        @Query("faculty_id") facultyId: String? = null
    ): TeacherTimetableResponse

    // --- Student ---

    @GET("mobile/student/dashboard")
    suspend fun getStudentDashboard(@Query("term_id") termId: Int? = null): StudentDashboardResponse

    @GET("mobile/timetable/schedule")
    suspend fun getStudentSchedule(): List<StudentScheduleSlot>

    @GET("mobile/timetable/student")
    suspend fun getStudentTimetable(): TeacherTimetableResponse

    @GET("mobile/student/calendar")
    suspend fun getStudentCalendar(@Query("month") month: String? = null): StudentCalendarResponse

    @Multipart
    @POST("mobile/leaves/apply")
    suspend fun applyLeave(
        @Part("start_date") startDate: okhttp3.RequestBody,
        @Part("end_date") endDate: okhttp3.RequestBody,
        @Part("reason") reason: okhttp3.RequestBody,
        @Part document: okhttp3.MultipartBody.Part,
    ): LeaveApplyResponse
}
