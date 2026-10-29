package com.akshit.attendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.UserProfileResponse
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

class ProfileViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _profile = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profile: StateFlow<ProfileUiState> = _profile

    private val _changePassword = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val changePassword: StateFlow<ChangePasswordUiState> = _changePassword

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = ProfileUiState.Loading
            _profile.value = try {
                val data = repo.getMyProfile()
                ProfileUiState.Loaded(data)
            } catch (e: Exception) {
                ProfileUiState.Error(e.message ?: "Failed to load profile details.")
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
