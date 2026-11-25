import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val localDebugUrl: String? = localProps.getProperty("ATTENDANCE_API_BASE_URL")
val localReleaseUrl: String? = localProps.getProperty("RELEASE_API_BASE_URL")

val debugApiBaseUrl = providers.gradleProperty("ATTENDANCE_API_BASE_URL")
    .orElse(providers.environmentVariable("ATTENDANCE_API_BASE_URL"))
    .let { if (!localDebugUrl.isNullOrBlank()) it.orElse(localDebugUrl) else it }
    .orElse("https://api.example.com/api/v1/")
    .get()

val releaseApiBaseUrl: String? = providers.gradleProperty("RELEASE_API_BASE_URL")
    .orElse(providers.environmentVariable("RELEASE_API_BASE_URL"))
    .let { if (!localReleaseUrl.isNullOrBlank()) it.orElse(localReleaseUrl) else it }
    .orNull
val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

fun normalizedBaseUrl(value: String): String = value.trim().let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.akshit.attendance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.akshit.attendance"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"${normalizedBaseUrl(debugApiBaseUrl)}\"")

        // ponytail: single flavor pointing at one backend URL. When staging/prod
        // environments exist, move this to buildConfigField per build type instead
        // of a single constant in ApiConfig.
    }

    signingConfigs {
        create("release") {
            if (!releaseKeystorePath.isNullOrBlank()) {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword ?: ""
                keyAlias = releaseKeyAlias ?: ""
                keyPassword = releaseKeyPassword ?: ""
            }
        }
    }

    buildTypes {
        release {
            val releaseUrlValue = if (!releaseApiBaseUrl.isNullOrBlank()) normalizedBaseUrl(releaseApiBaseUrl) else normalizedBaseUrl(debugApiBaseUrl)
            buildConfigField("String", "API_BASE_URL", "\"$releaseUrlValue\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking: Retrofit + OkHttp + kotlinx.serialization (no Gson/Moshi codegen needed)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Token + "must change password" flag persisted locally
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Lottie Animation
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Firebase Cloud Messaging & Analytics BoM
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.7.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


val validateReleaseEnvironment = tasks.register("validateReleaseEnvironment") {
    doLast {
        val apiUrl = releaseApiBaseUrl?.trim()
        val keyFile = releaseKeystorePath?.let(::file)
        val missing = mutableListOf<String>()
        if (apiUrl.isNullOrBlank() || !apiUrl.startsWith("https://", ignoreCase = true) || apiUrl.contains(".ts.net", ignoreCase = true)) {
            missing += "RELEASE_API_BASE_URL (public HTTPS API endpoint, not the development tailnet host)"
        }
        if (keyFile == null || !keyFile.isFile) missing += "RELEASE_KEYSTORE_PATH (existing keystore file)"
        if (releaseKeystorePassword.isNullOrBlank()) missing += "RELEASE_KEYSTORE_PASSWORD"
        if (releaseKeyAlias.isNullOrBlank()) missing += "RELEASE_KEY_ALIAS"
        if (releaseKeyPassword.isNullOrBlank()) missing += "RELEASE_KEY_PASSWORD"
        if (missing.isNotEmpty()) {
            throw GradleException("Release configuration is incomplete: ${missing.joinToString()}")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseEnvironment)
}
