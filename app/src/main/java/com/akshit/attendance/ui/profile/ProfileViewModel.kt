package com.akshit.attendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.UserProfileResponse
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.data.offline.StudentCacheManager
import com.akshit.attendance.data.offline.TeacherCacheManager
import com.akshit.attendance.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Loaded(val profile: UserProfileResponse) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

sealed interface ChangePasswordUiState {
    object Idle : ChangePasswordUiState
    object Submitting : ChangePasswordUiState
    object Success : ChangePasswordUiState
    data class Error(val message: String) : ChangePasswordUiState
}

class ProfileViewModel(
    private val repo: AuthRepository,
    private val tokenManager: TokenManager? = null
) : ViewModel() {

    private val _profile = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profile: StateFlow<ProfileUiState> = _profile

    private val _changePassword = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val changePassword: StateFlow<ChangePasswordUiState> = _changePassword

    private val _notifyOnPresent = MutableStateFlow(true)
    val notifyOnPresent: StateFlow<Boolean> = _notifyOnPresent

    private val _notifyOnAbsent = MutableStateFlow(true)
    val notifyOnAbsent: StateFlow<Boolean> = _notifyOnAbsent

    private val _notifyOnCorrection = MutableStateFlow(true)
    val notifyOnCorrection: StateFlow<Boolean> = _notifyOnCorrection

    private val _isTeacher = MutableStateFlow(false)
    val isTeacher: StateFlow<Boolean> = _isTeacher

    private val _notifyTeacherPendingAttendance = MutableStateFlow(true)
    val notifyTeacherPendingAttendance: StateFlow<Boolean> = _notifyTeacherPendingAttendance

    private val _notifyTeacherUpcomingClass = MutableStateFlow(true)
    val notifyTeacherUpcomingClass: StateFlow<Boolean> = _notifyTeacherUpcomingClass

    private val _notifyTeacherLeaveRequests = MutableStateFlow(true)
    val notifyTeacherLeaveRequests: StateFlow<Boolean> = _notifyTeacherLeaveRequests

    private val _notifyTeacherAnnouncements = MutableStateFlow(true)
    val notifyTeacherAnnouncements: StateFlow<Boolean> = _notifyTeacherAnnouncements

    init {
        loadProfile()
        viewModelScope.launch {
            tokenManager?.activeRoleFlow?.collect { role ->
                if (role != null) {
                    _isTeacher.value = !role.equals("STUDENT", ignoreCase = true)
                }
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyOnPresentFlow?.collect {
                _notifyOnPresent.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyOnAbsentFlow?.collect {
                _notifyOnAbsent.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyOnCorrectionFlow?.collect {
                _notifyOnCorrection.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyTeacherPendingAttendanceFlow?.collect {
                _notifyTeacherPendingAttendance.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyTeacherUpcomingClassFlow?.collect {
                _notifyTeacherUpcomingClass.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyTeacherLeaveRequestsFlow?.collect {
                _notifyTeacherLeaveRequests.value = it
            }
        }
        viewModelScope.launch {
            tokenManager?.notifyTeacherAnnouncementsFlow?.collect {
                _notifyTeacherAnnouncements.value = it
            }
        }
    }

    fun toggleNotifyOnPresent(enabled: Boolean) {
        viewModelScope.launch {
            _notifyOnPresent.value = enabled
            tokenManager?.setNotifyOnPresent(enabled)
        }
    }

    fun toggleNotifyOnAbsent(enabled: Boolean) {
        viewModelScope.launch {
            _notifyOnAbsent.value = enabled
            tokenManager?.setNotifyOnAbsent(enabled)
        }
    }

    fun toggleNotifyOnCorrection(enabled: Boolean) {
        viewModelScope.launch {
            _notifyOnCorrection.value = enabled
            tokenManager?.setNotifyOnCorrection(enabled)
        }
    }

    fun toggleNotifyTeacherPendingAttendance(enabled: Boolean) {
        viewModelScope.launch {
            _notifyTeacherPendingAttendance.value = enabled
            tokenManager?.setNotifyTeacherPendingAttendance(enabled)
        }
    }

    fun toggleNotifyTeacherUpcomingClass(enabled: Boolean) {
        viewModelScope.launch {
            _notifyTeacherUpcomingClass.value = enabled
            tokenManager?.setNotifyTeacherUpcomingClass(enabled)
        }
    }

    fun toggleNotifyTeacherLeaveRequests(enabled: Boolean) {
        viewModelScope.launch {
            _notifyTeacherLeaveRequests.value = enabled
            tokenManager?.setNotifyTeacherLeaveRequests(enabled)
        }
    }

    fun toggleNotifyTeacherAnnouncements(enabled: Boolean) {
        viewModelScope.launch {
            _notifyTeacherAnnouncements.value = enabled
            tokenManager?.setNotifyTeacherAnnouncements(enabled)
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val role = tokenManager?.activeRole()?.uppercase()
            val isStudent = role == "STUDENT"

            // ── Step 1: Load cached profile strictly matching current user's role ──
            val cached = if (isStudent) {
                StudentCacheManager.loadProfile()
            } else if (role == "TEACHER" || role in listOf("COORDINATOR", "HOD", "DEAN", "ADMIN")) {
                TeacherCacheManager.loadProfile()
            } else {
                StudentCacheManager.loadProfile() ?: TeacherCacheManager.loadProfile()
            } ?: run {
                // If offline and cache file has not yet been written, fallback to basic credentials in TokenManager
                val name = tokenManager?.userName()
                val email = tokenManager?.userEmail()
                if (!name.isNullOrBlank() && !email.isNullOrBlank()) {
                    UserProfileResponse(
                        user_id = 0,
                        full_name = name,
                        email = email,
                        roles = listOfNotNull(role),
                        primary_role = role ?: "USER",
                        department = "Computer Science and Engineering",
                        school = "School of Information and Communication Technology",
                        roll_number = if (isStudent) "Student" else null
                    )
                } else null
            }

            if (cached != null) {
                _profile.value = ProfileUiState.Loaded(cached)
            } else {
                _profile.value = ProfileUiState.Loading
            }

            // ── Step 2: Fetch fresh profile from network in background ──
            try {
                val data = repo.getMyProfile()
                _profile.value = ProfileUiState.Loaded(data)
                val userIsStudent = isStudent || data.roles.any { it.equals("STUDENT", ignoreCase = true) } || !data.roll_number.isNullOrBlank()
                _isTeacher.value = !userIsStudent
                if (userIsStudent) {
                    StudentCacheManager.saveProfile(data)
                } else {
                    TeacherCacheManager.saveProfile(data)
                }
            } catch (e: Exception) {
                if (_profile.value !is ProfileUiState.Loaded) {
                    _profile.value = ProfileUiState.Error(e.message ?: "Failed to load profile details.")
                }
            }
        }
    }

    fun changePassword(oldPass: String, newPass: String, confirmPass: String) {
        if (newPass != confirmPass) {
            _changePassword.value = ChangePasswordUiState.Error("New password and confirm password do not match.")
            return
        }
        if (newPass.length < 6) {
            _changePassword.value = ChangePasswordUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _changePassword.value = ChangePasswordUiState.Submitting
            try {
                repo.changePassword(oldPass, newPass)
                _changePassword.value = ChangePasswordUiState.Success
            } catch (e: Exception) {
                _changePassword.value = ChangePasswordUiState.Error(e.message ?: "Failed to update password.")
            }
        }
    }

    fun resetChangePasswordState() {
        _changePassword.value = ChangePasswordUiState.Idle
    }
}
