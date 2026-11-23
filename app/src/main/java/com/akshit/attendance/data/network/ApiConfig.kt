package com.akshit.attendance.data.network

import com.akshit.attendance.BuildConfig

object ApiConfig {
    val BASE_URL: String = BuildConfig.API_BASE_URL.let {
        if (it.endsWith("/")) it else "$it/"
    }
}
