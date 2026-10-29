package com.akshit.attendance.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.akshit.attendance.AttendanceApp
import com.akshit.attendance.ui.auth.AuthViewModel
import com.akshit.attendance.ui.profile.ProfileViewModel
import com.akshit.attendance.ui.student.StudentViewModel
import com.akshit.attendance.ui.teacher.TeacherViewModel

class ViewModelFactory(private val app: AttendanceApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        AuthViewModel::class.java -> AuthViewModel(
            repo = app.authRepository,
            tokenManager = app.tokenManager // Added the missing parameter here
        ) as T
        StudentViewModel::class.java -> StudentViewModel(repo = app.studentRepository) as T
        TeacherViewModel::class.java -> TeacherViewModel(
            repo = app.teacherRepository,
            tokenManager = app.tokenManager
        ) as T
        ProfileViewModel::class.java -> ProfileViewModel(repo = app.authRepository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}