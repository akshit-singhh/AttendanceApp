package com.akshit.attendance.data.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth")

/**
 * Single place that owns access/refresh tokens and the active role.
 * Deliberately not a full "session" abstraction — this app has one active session
 * at a time, so a key-value store is enough; don't build a multi-account manager
 * nobody asked for.
 */
class TokenManager(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val MUST_CHANGE_PW = booleanPreferencesKey("must_change_password")
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS] }
    val activeRoleFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_ROLE] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_NAME] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_EMAIL] }

    private val _sessionExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredFlow: kotlinx.coroutines.flow.SharedFlow<Unit> = _sessionExpiredFlow.asSharedFlow()

    fun notifySessionExpired() {
        _sessionExpiredFlow.tryEmit(Unit)
    }

    suspend fun accessToken(): String? = accessTokenFlow.first()
    suspend fun refreshToken(): String? = context.dataStore.data.first()[Keys.REFRESH]
    suspend fun activeRole(): String? = activeRoleFlow.first()
    suspend fun userName(): String? = userNameFlow.first()
    suspend fun userEmail(): String? = userEmailFlow.first()

    suspend fun updateTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = access
            prefs[Keys.REFRESH] = refresh
        }
    }

    suspend fun save(
        access: String,
        refresh: String,
        mustChangePassword: Boolean,
        role: String?,
        userName: String? = null,
        userEmail: String? = null,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = access
            prefs[Keys.REFRESH] = refresh
            prefs[Keys.MUST_CHANGE_PW] = mustChangePassword
            role?.let { prefs[Keys.ACTIVE_ROLE] = it }
            userName?.let { prefs[Keys.USER_NAME] = it }
            userEmail?.let { prefs[Keys.USER_EMAIL] = it }
        }
    }

    suspend fun mustChangePassword(): Boolean = context.dataStore.data.first()[Keys.MUST_CHANGE_PW] ?: false

    suspend fun clearMustChangePassword() {
        context.dataStore.edit { it[Keys.MUST_CHANGE_PW] = false }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
