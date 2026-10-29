package com.akshit.attendance.data.network

import com.akshit.attendance.data.model.TokenResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

/**
 * Attaches the stored access token to every request and automatically handles
 * token refresh when receiving a 401 Unauthorized or 403 Forbidden.
 * If the refresh token also fails, the session is cleared and a session-expired
 * event is emitted to navigate the user back to the login screen.
 */
private class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Do not attach token or attempt auto-refresh on authentication endpoints
        if (path.contains("/auth/login") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/forgot-password") ||
            path.contains("/auth/reset-password")
        ) {
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { tokenManager.accessToken() }
        val authenticatedRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authenticatedRequest)

        // Handle 401 Unauthorized or 403 Forbidden (token expired/invalid)
        if (response.code == 401 || response.code == 403) {
            synchronized(refreshLock) {
                // Check if another thread already refreshed while this thread was waiting
                val currentToken = runBlocking { tokenManager.accessToken() }
                if (!currentToken.isNullOrBlank() && currentToken != token) {
                    response.close()
                    val retried = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                    return chain.proceed(retried)
                }

                val refreshToken = runBlocking { tokenManager.refreshToken() }
                if (refreshToken.isNullOrBlank()) {
                    runBlocking { tokenManager.clear() }
                    tokenManager.notifySessionExpired()
                    return response
                }

                val newTokens = performRefresh(refreshToken)
                if (newTokens != null) {
                    runBlocking {
                        tokenManager.save(
                            access = newTokens.access_token,
                            refresh = newTokens.refresh_token,
                            mustChangePassword = newTokens.must_change_password,
                            role = newTokens.active_role,
                            userName = newTokens.user_name,
                            userEmail = newTokens.user_email
                        )
                    }
                    response.close()
                    val retried = originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.access_token}")
                        .build()
                    return chain.proceed(retried)
                } else {
                    runBlocking { tokenManager.clear() }
                    tokenManager.notifySessionExpired()
                    return response
                }
            }
        }

        return response
    }

    private fun performRefresh(refreshToken: String): TokenResponse? {
        return try {
            val url = "${ApiConfig.BASE_URL.trimEnd('/')}/auth/refresh"
            val body = """{"refresh_token":"$refreshToken"}"""
            val requestBody = body.toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val unauthenticatedClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val response = unauthenticatedClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonString = response.body?.string()
                if (!jsonString.isNullOrBlank()) {
                    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
                    json.decodeFromString<TokenResponse>(jsonString)
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object RetrofitClient {
    private var api: ApiService? = null

    fun get(tokenManager: TokenManager): ApiService {
        return api ?: build(tokenManager).also { api = it }
    }

    private fun build(tokenManager: TokenManager): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
