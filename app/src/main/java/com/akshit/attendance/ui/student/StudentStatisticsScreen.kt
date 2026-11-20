package com.akshit.attendance.ui.student

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.akshit.attendance.R
import com.akshit.attendance.data.model.SubjectAttendanceHistory
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.StudentCacheManager
import com.akshit.attendance.ui.components.PullToRefreshExpressiveIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentStatisticsScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit,
    onSelectSubject: (String) -> Unit = {}
) {
    val dashboardState by viewModel.dashboard.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshAll()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Attendance Statistics",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when (val state = dashboardState) {
                is DashboardUiState.Loading -> {
                    // Modern Lottie Loading Animation as requested by user
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_documents))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = LottieConstants.IterateForever
                    )
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            LottieAnimation(
                                composition = composition,
                                progress = { progress },
                                modifier = Modifier.size(170.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Analyzing attendance statistics...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Computing course standing & compliance",
                                fontSize = 12.sp,
                                color = TextGray
                            )
                        }
                    }
                }

                is DashboardUiState.Error -> {
                    val isOffline = !isOnline || StudentCacheManager.isNetworkError(state.message)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = if (isOffline) Color(0xFFFEF3C7) else LightRedBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isOffline) Color(0xFFFDE68A) else Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (isOffline) Color(0xFFD97706) else AlertRed,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (isOffline) "Offline Mode" else "Unable to Load Statistics",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOffline) Color(0xFF92400E) else AlertRed
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (isOffline) "Connect to the internet to fetch latest statistics." else state.message,
                                    fontSize = 12.sp,
                                    color = if (isOffline) Color(0xFFB45309) else TextGray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { viewModel.refreshAll() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOffline) Color(0xFFD97706) else PrimaryBlue
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Retry", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                is DashboardUiState.Loaded -> {
                    val allSubjects = state.data.subjects
                    val subjects = allSubjects.filter { it.requires_attendance }
                    val sortedSubjectsForList = allSubjects.sortedBy { !it.requires_attendance }
                    val attended = subjects.sumOf { it.periods_attended }
                    val held = subjects.sumOf { it.periods_held }
                    val missed = (held - attended).coerceAtLeast(0)
                    val overallPct = state.data.overall_percentage
                    val activeSubjects = subjects.count { it.periods_held > 0 }
                    val safeSubjects = subjects.count { it.periods_held > 0 && it.percentage >= 75.0 }
                    val atRiskSubjects = subjects.count { it.periods_held > 0 && it.percentage < 75.0 }
                    val notStarted = subjects.count { it.periods_held == 0 }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Top 3 Metric Cards (Attended / Missed / Total Held)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Attended
                                StatMetricCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Attended",
                                    value = "$attended",
                                    subtitle = if (held > 0) "${String.format(Locale.ENGLISH, "%.0f", (attended.toDouble() / held) * 100)}% of held" else "0%",
                                    valueColor = PrimaryGreen,
                                    accentBg = Color(0xFFECFDF5),
                                    icon = Icons.Outlined.CheckCircle,
                                    iconTint = PrimaryGreen
                                )

                                // Missed
                                StatMetricCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Missed",
                                    value = "$missed",
                                    subtitle = if (held > 0) "${String.format(Locale.ENGLISH, "%.0f", (missed.toDouble() / held) * 100)}% of held" else "0%",
                                    valueColor = if (missed > 0) AlertRed else TextGray,
                                    accentBg = if (missed > 0) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                                    icon = Icons.Outlined.Cancel,
                                    iconTint = if (missed > 0) AlertRed else TextGray
                                )

                                // Total Held
                                StatMetricCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Total Held",
                                    value = "$held",
                                    subtitle = "${subjects.size} subjects",
                                    valueColor = PrimaryBlue,
                                    accentBg = Color(0xFFEFF6FF),
                                    icon = Icons.Outlined.EventNote,
                                    iconTint = PrimaryBlue
                                )
                            }
                        }

                        // 2. Premium Overall Compliance Banner
                        item {
                            OverallComplianceCard(
                                overallPct = overallPct,
                                held = held,
                                safeSubjects = safeSubjects,
                                atRiskSubjects = atRiskSubjects,
                                notStarted = notStarted,
                                activeSubjects = if (activeSubjects > 0) activeSubjects else subjects.size
                            )
                        }

                        // 3. Pixel-Perfect Course Attendance Bar Chart
                        if (subjects.isNotEmpty()) {
                            item {
                                CourseAttendanceChartCard(subjects = subjects)
                            }

                            // 4. Detailed Breakdown Section Header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Course Performance Breakdown",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        "Tap to view sessions",
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }
                            }

                            // 5. Course list items (Tracked courses first, exempted courses at the end)
                            items(sortedSubjectsForList) { sub ->
                                CoursePerformanceRowCard(
                                    subject = sub,
                                    onClick = { onSelectSubject(sub.course_code) }
                                )
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                indicator = { s -> PullToRefreshExpressiveIndicator(state = s, color = PrimaryBlue) }
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    accentBg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(accentBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OverallComplianceCard(
    overallPct: Double,
    held: Int,
    safeSubjects: Int,
    atRiskSubjects: Int,
    notStarted: Int,
    activeSubjects: Int
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (activeSubjects == 0) 0f else (safeSubjects.toFloat() / activeSubjects.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "overallProgress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Exam Eligibility Status", fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$safeSubjects / $activeSubjects",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (held == 0) TextGray else if (atRiskSubjects == 0) PrimaryGreen else AlertRed
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Subjects Eligible",
                            fontSize = 11.sp,
                            color = TextGray,
                            modifier = Modifier.padding(bottom = 3.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = if (held == 0) Color(0xFFF1F5F9) else if (atRiskSubjects == 0) LightGreenBg else LightRedBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (held == 0) TextGray else if (atRiskSubjects == 0) PrimaryGreen else AlertRed,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                held == 0 -> "No Data"
                                atRiskSubjects == 0 -> "All Clear"
                                else -> "$atRiskSubjects Below Cutoff"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (held == 0) TextGray else if (atRiskSubjects == 0) PrimaryGreen else AlertRed
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Linear Progress Bar with Target 75% tick marker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (held == 0) TextGray else if (atRiskSubjects == 0) PrimaryGreen else AlertRed,
                    trackColor = Color(0xFFF1F5F9)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Status Breakdown Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$safeSubjects", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                        Text("On Track", fontSize = 10.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    color = if (atRiskSubjects > 0) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$atRiskSubjects",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (atRiskSubjects > 0) AlertRed else TextGray
                        )
                        Text(
                            "At Risk",
                            fontSize = 10.sp,
                            color = if (atRiskSubjects > 0) AlertRed else TextGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$notStarted", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Text("No Classes Yet", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseAttendanceChartCard(subjects: List<SubjectAttendanceHistory>) {
    val activeSubjects = subjects.count { it.periods_held > 0 }
    val notStarted = subjects.count { it.periods_held == 0 }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Course Attendance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text("Subject comparison vs 75% target", fontSize = 11.sp, color = TextGray)
                }
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (notStarted > 0) "$activeSubjects Active • $notStarted Pending" else "${subjects.size} Enrolled",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Percentage Labels Row - positioned right above each corresponding bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subjects.forEach { sub ->
                    val held = sub.periods_held
                    val pct = if (held == 0) 0.0 else sub.percentage
                    val isSafe = held > 0 && pct >= 75.0
                    val textColor = when {
                        held == 0 -> Color(0xFF94A3B8)
                        isSafe -> PrimaryGreen
                        else -> AlertRed
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (held == 0) "—" else "${pct.toInt()}%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Bar Track Area (Height: 110.dp) - Both the 75% Canvas Line and the Bar Tracks live in this EXACT same container!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                // Background reference line at exactly 75% height from bottom (25% from top)
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val y75 = size.height * 0.25f
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(0f, y75),
                        end = Offset(size.width, y75),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // Row of Bar Tracks
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subjects.forEach { sub ->
                        val held = sub.periods_held
                        val pct = if (held == 0) 0.0 else sub.percentage
                        val isSafe = held > 0 && pct >= 75.0
                        val barColor = when {
                            held == 0 -> Color(0xFFCBD5E1)
                            isSafe -> PrimaryGreen
                            else -> AlertRed
                        }
                        val barFillFraction = if (held == 0) 0f else (pct / 100.0).toFloat().coerceIn(0.04f, 1f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Rounded Bar with gray background track
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (held > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(barFillFraction)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(barColor)
                                    )
                                } else {
                                    // Subtle indicator for unstarted courses
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .background(Color(0xFFCBD5E1))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Course code labels below each bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subjects.forEach { sub ->
                    val held = sub.periods_held
                    val pct = if (held == 0) 0.0 else sub.percentage
                    val isSafe = held > 0 && pct >= 75.0
                    val textColor = when {
                        held == 0 -> TextGray
                        isSafe -> PrimaryGreen
                        else -> AlertRed
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sub.course_code,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(Color(0xFF94A3B8))
                )
                Spacer(Modifier.width(6.dp))
                Text("75% Minimum Target", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CoursePerformanceRowCard(
    subject: SubjectAttendanceHistory,
    onClick: () -> Unit
) {
    val held = subject.periods_held
    val pct = subject.percentage
    val isCompliant = held > 0 && pct >= 75.0
    val isExempt = !subject.requires_attendance

    val (badgeBg, badgeTextColor, pctText) = when {
        isExempt -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Exempt")
        held == 0 -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Not Started")
        isCompliant -> Triple(LightGreenBg, PrimaryGreen, "${String.format(Locale.ENGLISH, "%.1f", pct)}%")
        else -> Triple(LightRedBg, AlertRed, "${String.format(Locale.ENGLISH, "%.1f", pct)}%")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Course code badge
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            subject.course_code,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        subject.subject_name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        pctText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }

                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Faculty + attendance fraction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        subject.teacher_name,
                        fontSize = 11.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    if (isExempt) "No attendance required" else if (held == 0) "0 classes held" else "${subject.periods_attended} / $held attended",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExempt) Color(0xFF92400E) else if (held > 0 && !isCompliant) AlertRed else TextDark
                )
            }

            if (held > 0 && !isExempt) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isCompliant) PrimaryGreen else AlertRed,
                    trackColor = Color(0xFFF1F5F9)
                )
            }
        }
    }
}
