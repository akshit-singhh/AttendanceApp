package com.akshit.attendance.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CREATE_PASSWORD = "create_password"
    const val ROLE_PICKER = "role_picker"

    const val STUDENT_DASHBOARD = "student_dashboard"
    const val STUDENT_SUBJECTS = "student_subjects"
    const val STUDENT_SUBJECT_DETAIL = "student_subject_detail/{subjectCode}"
    const val STUDENT_PLANNER = "student_planner"
    const val STUDENT_CALENDAR = "student_calendar"
    const val STUDENT_STATISTICS = "student_statistics"
    const val STUDENT_HISTORY = "student_history"
    const val STUDENT_LEAVES = "student_leaves"
    const val STUDENT_TIMETABLE = "student_timetable"
    const val STUDENT_NOTIFICATIONS = "student_notifications"
    const val UNIVERSITY_DOCS = "university_docs"

    const val TEACHER_CLASSES = "teacher_classes"
    const val TEACHER_ROLL_CALL = "teacher_roll_call/{courseOfferingId}?sessionDate={sessionDate}&batchLabel={batchLabel}"
    const val TEACHER_TIMETABLE = "teacher_timetable"
    const val TEACHER_ANALYTICS = "teacher_analytics"
    const val TEACHER_RECORDS = "teacher_records"
    const val TEACHER_NOTIFICATIONS = "teacher_notifications"
    const val TEACHER_OFFLINE_QUEUE = "teacher_offline_queue"
    const val PROFILE = "profile"
    const val NOTIFICATION_SETTINGS = "notification_settings"

    fun rollCall(courseOfferingId: Int, sessionDate: String? = null, batchLabel: String? = null): String {
        val params = mutableListOf<String>()
        if (!sessionDate.isNullOrBlank()) params.add("sessionDate=$sessionDate")
        if (!batchLabel.isNullOrBlank()) params.add("batchLabel=$batchLabel")
        return if (params.isNotEmpty()) "teacher_roll_call/$courseOfferingId?${params.joinToString("&")}"
        else "teacher_roll_call/$courseOfferingId"
    }

    fun subjectDetail(subjectCode: String) = "student_subject_detail/$subjectCode"
}
