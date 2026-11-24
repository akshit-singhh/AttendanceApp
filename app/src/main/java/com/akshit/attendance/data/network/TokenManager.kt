package com.akshit.attendance.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "auth")

/** Owns the single active user session and encrypts bearer tokens with an Android Keystore key. */
class TokenManager(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val MUST_CHANGE_PW = booleanPreferencesKey("must_change_password")
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val NOTIFY_ON_PRESENT = booleanPreferencesKey("notify_on_present")
        val NOTIFY_ON_ABSENT = booleanPreferencesKey("notify_on_absent")
        val NOTIFY_ON_CORRECTION = booleanPreferencesKey("notify_on_correction")
        val NOTIFY_TEACHER_PENDING_ATTENDANCE = booleanPreferencesKey("notify_teacher_pending_attendance")
        val NOTIFY_TEACHER_UPCOMING_CLASS = booleanPreferencesKey("notify_teacher_upcoming_class")
        val NOTIFY_TEACHER_LEAVE_REQUESTS = booleanPreferencesKey("notify_teacher_leave_requests")
        val NOTIFY_TEACHER_ANNOUNCEMENTS = booleanPreferencesKey("notify_teacher_announcements")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { decryptToken(it[Keys.ACCESS]) }
    val activeRoleFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_ROLE] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_NAME] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_EMAIL] }
    val notifyOnPresentFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_ON_PRESENT] ?: true }
    val notifyOnAbsentFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_ON_ABSENT] ?: true }
    val notifyOnCorrectionFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_ON_CORRECTION] ?: true }
    val notifyTeacherPendingAttendanceFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_TEACHER_PENDING_ATTENDANCE] ?: true }
    val notifyTeacherUpcomingClassFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_TEACHER_UPCOMING_CLASS] ?: true }
    val notifyTeacherLeaveRequestsFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_TEACHER_LEAVE_REQUESTS] ?: true }
    val notifyTeacherAnnouncementsFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_TEACHER_ANNOUNCEMENTS] ?: true }

    private val _sessionExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredFlow: kotlinx.coroutines.flow.SharedFlow<Unit> = _sessionExpiredFlow

    @Volatile
    private var isLoggingOut = false

    @Volatile
    private var lastSessionExpiredTime = 0L

    fun markLoggingOut() {
        isLoggingOut = true
    }

    fun resetLoggingOut() {
        isLoggingOut = false
    }

    fun isLoggingOut(): Boolean = isLoggingOut

    fun notifySessionExpired() {
        if (isLoggingOut) return
        val now = System.currentTimeMillis()
        if (now - lastSessionExpiredTime < 4000L) {
            return
        }
        lastSessionExpiredTime = now
        _sessionExpiredFlow.tryEmit(Unit)
    }

    suspend fun accessToken(): String? = readAndMigrateToken(Keys.ACCESS)
    suspend fun refreshToken(): String? = readAndMigrateToken(Keys.REFRESH)
    suspend fun activeRole(): String? = activeRoleFlow.first()
    suspend fun userName(): String? = userNameFlow.first()
    suspend fun userEmail(): String? = userEmailFlow.first()
    suspend fun isNotifyOnPresentEnabled(): Boolean = notifyOnPresentFlow.first()
    suspend fun isNotifyOnAbsentEnabled(): Boolean = notifyOnAbsentFlow.first()
    suspend fun isNotifyOnCorrectionEnabled(): Boolean = notifyOnCorrectionFlow.first()
    suspend fun isNotifyTeacherPendingAttendanceEnabled(): Boolean = notifyTeacherPendingAttendanceFlow.first()
    suspend fun isNotifyTeacherUpcomingClassEnabled(): Boolean = notifyTeacherUpcomingClassFlow.first()
    suspend fun isNotifyTeacherLeaveRequestsEnabled(): Boolean = notifyTeacherLeaveRequestsFlow.first()
    suspend fun isNotifyTeacherAnnouncementsEnabled(): Boolean = notifyTeacherAnnouncementsFlow.first()

    suspend fun setNotifyOnPresent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_PRESENT] = enabled }
    }

    suspend fun setNotifyOnAbsent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_ABSENT] = enabled }
    }

    suspend fun setNotifyOnCorrection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_CORRECTION] = enabled }
    }

    suspend fun setNotifyTeacherPendingAttendance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TEACHER_PENDING_ATTENDANCE] = enabled }
    }

    suspend fun setNotifyTeacherUpcomingClass(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TEACHER_UPCOMING_CLASS] = enabled }
    }

    suspend fun setNotifyTeacherLeaveRequests(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TEACHER_LEAVE_REQUESTS] = enabled }
    }

    suspend fun setNotifyTeacherAnnouncements(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TEACHER_ANNOUNCEMENTS] = enabled }
    }

    suspend fun updateTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = encryptToken(access)
            prefs[Keys.REFRESH] = encryptToken(refresh)
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
            prefs[Keys.ACCESS] = encryptToken(access)
            prefs[Keys.REFRESH] = encryptToken(refresh)
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

    private suspend fun readAndMigrateToken(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        val stored = context.dataStore.data.first()[key] ?: return null
        val decrypted = decryptToken(stored) ?: return null
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            val encrypted = encryptToken(decrypted)
            context.dataStore.edit { prefs ->
                if (prefs[key] == stored) prefs[key] = encrypted
            }
        }
        return decrypted
    }

    private fun encryptToken(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decryptToken(value: String?): String? {
        if (value == null) return null
        if (!value.startsWith(ENCRYPTED_PREFIX)) return value // migrated on the first token read
        return try {
            val encrypted = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            val iv = encrypted.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = encrypted.copyOfRange(GCM_IV_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "attendance_session_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFIX = "v1:"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        val KEY_LOCK = Any()
    }
}