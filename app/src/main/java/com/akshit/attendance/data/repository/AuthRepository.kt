package com.akshit.attendance.data.repository

import com.akshit.attendance.data.model.*
import com.akshit.attendance.data.network.ApiService
import com.akshit.attendance.data.network.TokenManager

class AuthRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager,
) {
    /** Returns the full response so the caller can branch on must_change_password
     *  and available_roles (role picker only shows when there's more than one). */
    suspend fun login(email: String, password: String): TokenResponse {
        val response = api.login(LoginRequest(email, password))
        tokenManager.save(
            access = response.access_token,
            refresh = response.refresh_token,
            mustChangePassword = response.must_change_password,
            role = response.active_role,
            userName = response.user_name,
            userEmail = response.user_email,
        )
        return response
    }

    /** Reissues tokens under a different role/scope the user is authorized for.
     *  Backend verifies the (role, scope_id) pair against the user's actual role
     *  assignments — this call fails with 403 if the app sends one it shouldn't. */
    suspend fun switchRole(role: String, scopeId: Int?): TokenResponse {
        val response = api.switchRole(SwitchRoleRequest(role, scopeId))
        tokenManager.save(
            access = response.access_token,
            refresh = response.refresh_token,
            mustChangePassword = response.must_change_password,
            role = response.active_role,
            userName = response.user_name,
            userEmail = response.user_email,
        )
        return response
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        api.changePassword(ChangePasswordRequest(oldPassword, newPassword))
        tokenManager.clearMustChangePassword()
    }

    suspend fun forgotPassword(email: String) = api.forgotPassword(ForgotPasswordRequest(email))

    suspend fun resetPassword(email: String, otp: String, newPassword: String) =
        api.resetPassword(ResetPasswordRequest(email, otp, newPassword))

    suspend fun logout() {
        tokenManager.refreshToken()?.let { api.logout(RefreshTokenRequest(it)) }
        tokenManager.clear()
    }

    suspend fun getMyProfile(): UserProfileResponse = api.getMyProfile()

    suspend fun mustChangePassword(): Boolean = tokenManager.mustChangePassword()
}
