package com.akshit.attendance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshit.attendance.data.model.TokenResponse
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val response: TokenResponse) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val repo: AuthRepository,
    private val tokenManager: TokenManager // Injected to read and write saved sessions
) : ViewModel() {

    // Start in a Loading state so the UI doesn't flash the login form while reading DataStore
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val token = tokenManager.accessToken()
            val refresh = tokenManager.refreshToken()
            if (!token.isNullOrBlank() || !refresh.isNullOrBlank()) {
                val mustChange = tokenManager.mustChangePassword()
                val storedRole = tokenManager.activeRole() ?: ""
                val effectiveRole = if (storedRole.uppercase() in listOf("COORDINATOR", "HOD", "DEAN", "ADMIN")) "TEACHER" else storedRole
                val userName = tokenManager.userName()
                val userEmail = tokenManager.userEmail()

                // Bypass login and restore the success state from disk
                _state.value = AuthUiState.Success(
                    TokenResponse(
                        access_token = token ?: "",
                        refresh_token = refresh ?: "",
                        token_type = "bearer",
                        must_change_password = mustChange,
                        available_roles = emptyList(),
                        active_role = effectiveRole,
                        user_name = userName,
                        user_email = userEmail
                    )
                )
            } else {
                _state.value = AuthUiState.Idle
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Enter your ID and password.")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val response = repo.login(email.trim(), password)
                // App is strictly for Teacher and Student. Prioritize TEACHER if user holds faculty roles.
                val effectiveRole = when {
                    response.available_roles.any { it.role.equals("TEACHER", ignoreCase = true) } -> "TEACHER"
                    response.available_roles.any { it.role.equals("STUDENT", ignoreCase = true) } -> "STUDENT"
                    response.available_roles.any { it.role.uppercase() in listOf("COORDINATOR", "HOD", "DEAN", "ADMIN") } -> "TEACHER"
                    else -> response.active_role
                }
                tokenManager.save(
                    access = response.access_token,
                    refresh = response.refresh_token,
                    mustChangePassword = response.must_change_password,
                    role = effectiveRole,
                    userName = response.user_name,
                    userEmail = response.user_email
                )
                _state.value = AuthUiState.Success(response.copy(active_role = effectiveRole))
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Login failed. Check your ID and password.")
            }
        }
    }

    fun setPassword(oldPassword: String, newPassword: String, confirm: String) {
        if (newPassword.length < 8) {
            _state.value = AuthUiState.Error("New password must be at least 8 characters.")
            return
        }
        if (newPassword != confirm) {
            _state.value = AuthUiState.Error("Passwords don't match.")
            return
        }
        if (newPassword == oldPassword) {
            _state.value = AuthUiState.Error("New password must be different from the temporary one.")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                repo.changePassword(oldPassword, newPassword)
                // Clear the force-password-change flag on disk
                tokenManager.clearMustChangePassword()

                val prior = (state.value as? AuthUiState.Success)?.response
                _state.value = AuthUiState.Success(
                    prior?.copy(must_change_password = false)
                        ?: TokenResponse(access_token = "", refresh_token = "", must_change_password = false)
                )
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Could not update password.")
            }
        }
    }

    fun switchRole(role: String, scopeId: Int?) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val response = repo.switchRole(role, scopeId)
                // Overwrite old tokens with the newly scoped tokens
                tokenManager.save(
                    access = response.access_token,
                    refresh = response.refresh_token,
                    mustChangePassword = response.must_change_password,
                    role = response.active_role,
                    userName = response.user_name,
                    userEmail = response.user_email
                )
                _state.value = AuthUiState.Success(response)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Could not switch role.")
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.logout()
            } catch (e: Exception) {
                tokenManager.clear()
            } finally {
                _state.value = AuthUiState.Idle
                onComplete()
            }
        }
    }

    fun resetState() {
        _state.value = AuthUiState.Idle
    }
}