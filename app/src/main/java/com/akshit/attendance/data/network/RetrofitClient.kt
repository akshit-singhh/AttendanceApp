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
private class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        if (path.contains("/auth/login") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/forgot-password") ||
            path.contains("/auth/reset-password")
        ) {
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { tokenManager.accessToken() }
        val authenticatedRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder().header("Authorization", "Bearer $token").build()
        } else originalRequest

        val response = chain.proceed(authenticatedRequest)

        // The backend uses 401 for invalid/expired access tokens. A 403 is an
        // authorization denial and must not rotate the user's refresh token.
        if (response.code != 401) return response

        synchronized(refreshLock) {
            val currentToken = runBlocking { tokenManager.accessToken() }
            if (!currentToken.isNullOrBlank() && currentToken != token) {
                response.close()
                return chain.proceed(
                    originalRequest.newBuilder().header("Authorization", "Bearer $currentToken").build(),
                )
            }

            val refreshToken = runBlocking { tokenManager.refreshToken() }
            if (refreshToken.isNullOrBlank()) {
                runBlocking { tokenManager.clear() }
                tokenManager.notifySessionExpired()
                return response
            }

            return when (val outcome = performRefresh(refreshToken)) {
                is RefreshOutcome.Success -> {
                    val tokens = outcome.tokens
                    runBlocking {
                        tokenManager.save(
                            access = tokens.access_token,
                            refresh = tokens.refresh_token,
                            mustChangePassword = tokens.must_change_password,
                            role = tokens.active_role,
                            userName = tokens.user_name,
                            userEmail = tokens.user_email,
                        )
                    }
                    response.close()
                    chain.proceed(
                        originalRequest.newBuilder()
                            .header("Authorization", "Bearer ${tokens.access_token}")
                            .build(),
                    )
                }
                RefreshOutcome.Invalid -> {
                    runBlocking { tokenManager.clear() }
                    tokenManager.notifySessionExpired()
                    response
                }
                // Keep both tokens for network errors, rate limits, and server
                // failures. A later request can retry refresh without login.
                RefreshOutcome.TemporaryFailure -> response
            }
        }
    }

    private fun performRefresh(refreshToken: String): RefreshOutcome {
        return try {
            val url = "${ApiConfig.BASE_URL.trimEnd('/')}/auth/refresh"
            val body = Json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                mapOf("refresh_token" to refreshToken),
            )
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string()
                        if (responseBody.isNullOrBlank()) RefreshOutcome.TemporaryFailure
                        else {
                            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
                            try {
                                RefreshOutcome.Success(json.decodeFromString<TokenResponse>(responseBody))
                            } catch (_: Exception) {
                                RefreshOutcome.TemporaryFailure
                            }
                        }
                    }
                    response.code == 401 || response.code == 404 -> RefreshOutcome.Invalid
                    else -> RefreshOutcome.TemporaryFailure
                }
            }
        } catch (_: Exception) {
            RefreshOutcome.TemporaryFailure
        }
    }

    private sealed interface RefreshOutcome {
        data class Success(val tokens: TokenResponse) : RefreshOutcome
        data object Invalid : RefreshOutcome
        data object TemporaryFailure : RefreshOutcome
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