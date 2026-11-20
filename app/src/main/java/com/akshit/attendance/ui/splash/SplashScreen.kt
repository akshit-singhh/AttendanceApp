package com.akshit.attendance.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.akshit.attendance.R
import com.akshit.attendance.data.network.TokenManager
import com.akshit.attendance.navigation.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    tokenManager: TokenManager,
    onNavigate: (String) -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.82f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    val lottieComp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_dots))
    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComp,
        iterations = LottieConstants.IterateForever
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        val minDelay = async { delay(1200L) }
        val sessionCheck = async {
            val token = tokenManager.accessToken()
            val refresh = tokenManager.refreshToken()
            val hasToken = !token.isNullOrBlank() || !refresh.isNullOrBlank()
            if (hasToken) {
                val mustChange = tokenManager.mustChangePassword()
                val role = tokenManager.activeRole() ?: ""
                val isFaculty = role.uppercase() in listOf("TEACHER", "COORDINATOR", "HOD", "DEAN", "ADMIN")
                when {
                    mustChange -> Routes.CREATE_PASSWORD
                    isFaculty -> Routes.TEACHER_CLASSES
                    else -> Routes.STUDENT_DASHBOARD
                }
            } else {
                Routes.LOGIN
            }
        }

        minDelay.await()
        val targetDestination = sessionCheck.await()
        onNavigate(targetDestination)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC),
                        Color(0xFFF1F5F9)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .scale(logoScale)
                .alpha(contentAlpha)
        ) {
            // High-Resolution Gautam Buddha University Circular Emblem
            Surface(
                modifier = Modifier
                    .size(175.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        spotColor = Color(0x33002D62),
                        ambientColor = Color(0x1F002D62)
                    ),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(2.dp, Color(0xFFF1F5F9))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gbu_logo),
                    contentDescription = "Gautam Buddha University Emblem",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            // Hindi Name
            Text(
                text = "गौतम बुद्ध विश्वविद्यालय",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF475569),
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // English University Name
            Text(
                text = "GAUTAM BUDDHA UNIVERSITY",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF002D62),
                letterSpacing = 1.3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // University Portal Badge Pill
            Surface(
                color = Color(0xFF002D62).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF002D62).copy(alpha = 0.22f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "ATTENDANCE APP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = Color(0xFF002D62),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Lottie Loading Dots & Department Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LottieAnimation(
                composition = lottieComp,
                progress = { lottieProgress },
                modifier = Modifier.size(65.dp)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "School of Information & Communication Technology",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                letterSpacing = 0.3.sp
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Gautam Buddha University • 2026-27",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
