package com.akshit.attendance.ui.student

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.SubjectAttendanceHistory
import com.akshit.attendance.ui.components.SkeletonBox
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentStatisticsScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val dashboardState by viewModel.dashboard.collectAsState()

    Scaffold(
        containerColor = SurfaceBg,
        topBar = {
            TopAppBar(
                title = { Text("Attendance Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        when (val state = dashboardState) {
            is DashboardUiState.Loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shimmer metrics
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonBox(modifier = Modifier.weight(1f).height(90.dp), shape = RoundedCornerShape(16.dp))
                            SkeletonBox(modifier = Modifier.weight(1f).height(90.dp), shape = RoundedCornerShape(16.dp))
                            SkeletonBox(modifier = Modifier.weight(1f).height(90.dp), shape = RoundedCornerShape(16.dp))
                        }
                    }
                    item {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(16.dp))
                    }
                    item {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().height(260.dp), shape = RoundedCornerShape(20.dp))
                    }
                    items(3) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(14.dp))
                    }
                }
            }
            is DashboardUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = AlertRed, fontSize = 14.sp)
                }
            }
            is DashboardUiState.Loaded -> {
                val subjects = state.data.subjects
                val attended = subjects.sumOf { it.periods_attended }
                val held = subjects.sumOf { it.periods_held }
                val missed = (held - attended).coerceAtLeast(0)
                val overallPct = state.data.overall_percentage
                val safeSubjects = subjects.count { it.periods_held > 0 && it.percentage >= 75.0 }
                val atRiskSubjects = subjects.count { it.periods_held > 0 && it.percentage < 75.0 }
                val notStarted = subjects.count { it.periods_held == 0 }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Top 3 Metric Cards (Attended / Missed / Total Conducted)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Attended Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).background(LightGreenBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("$attended", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryGreen)
                                    Text("Attended", fontSize = 11.sp, color = TextGray)
                                }
                            }

                            // Missed Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).background(LightRedBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Cancel, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("$missed", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (missed > 0) AlertRed else TextGray)
                                    Text("Missed", fontSize = 11.sp, color = TextGray)
                                }
                            }

                            // Total Conducted Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).background(Color(0xFFEEF2FF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Functions, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("$held", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                                    Text("Total Held", fontSize = 11.sp, color = TextGray)
                                }
                            }
                        }
                    }

                    // 2. Overview Performance Cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Overall Attendance", fontSize = 11.sp, color = TextGray)
                                    Spacer(Modifier.height(4.dp))
                                    val pctStr = if (held == 0) "0.0%" else "${String.format(Locale.ENGLISH, "%.1f", overallPct)}%"
                                    val pctCol = when {
                                        held == 0 -> TextGray
                                        overallPct >= 75.0 -> PrimaryGreen
                                        else -> AlertRed
                                    }
                                    Text(
                                        pctStr,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = pctCol
                                    )
                                    Text(if (held == 0) "No sessions held" else "Target 75% minimum", fontSize = 10.sp, color = TextGray)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Course Standing", fontSize = 11.sp, color = TextGray)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "$safeSubjects on track",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryGreen
                                    )
                                    val subText = when {
                                        atRiskSubjects > 0 -> "$atRiskSubjects require attention"
                                        notStarted > 0 -> "$notStarted not started yet"
                                        else -> "All subjects compliant"
                                    }
                                    Text(subText, fontSize = 10.sp, color = if (atRiskSubjects > 0) AlertRed else TextGray, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // 3. Real Subject Breakdown Bar Chart
                    if (subjects.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Course Attendance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                            Text("Performance across enrolled courses", fontSize = 11.sp, color = TextGray)
                                        }
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "${subjects.size} Enrolled",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(20.dp))

                                    // Dynamic Bar Chart
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val barCount = subjects.size
                                            val space = size.width / (barCount * 2 + 1)
                                            val barWidth = space
                                            val maxHeight = size.height - 24.dp.toPx()

                                            // 75% Target dashed line
                                            val y75 = maxHeight * (1f - 0.75f)
                                            drawLine(
                                                color = Color(0xFF94A3B8),
                                                start = Offset(0f, y75),
                                                end = Offset(size.width, y75),
                                                strokeWidth = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                            )

                                            // Draw vertical bars for each real course
                                            subjects.forEachIndexed { index, sub ->
                                                val x = space + index * (barWidth + space)
                                                val pct = if (sub.periods_held == 0) 0.0 else sub.percentage
                                                val barHeight = (maxHeight * (pct / 100.0).toFloat()).coerceIn(0f, maxHeight)
                                                val y = maxHeight - barHeight

                                                val barColor = when {
                                                    sub.periods_held == 0 -> Color(0xFFE2E8F0)
                                                    sub.percentage >= 75.0 -> PrimaryGreen
                                                    else -> AlertRed
                                                }

                                                if (barHeight > 0f) {
                                                    drawRoundRect(
                                                        color = barColor,
                                                        topLeft = Offset(x, y),
                                                        size = Size(barWidth, barHeight),
                                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                                    )
                                                } else {
                                                    // Draw a tiny baseline pill for 0%
                                                    drawRoundRect(
                                                        color = Color(0xFFCBD5E1),
                                                        topLeft = Offset(x, maxHeight - 4.dp.toPx()),
                                                        size = Size(barWidth, 4.dp.toPx()),
                                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // X-Axis labels (Course codes)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        subjects.forEach { sub ->
                                            Text(
                                                sub.course_code,
                                                fontSize = 10.sp,
                                                color = if (sub.periods_held == 0) TextGray else (if (sub.percentage >= 75.0) PrimaryGreen else AlertRed),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.width(16.dp).height(2.dp).background(Color(0xFF94A3B8)))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Target 75% Threshold", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // 4. Detailed Breakdown Section Header
                        item {
                            Text("Course Performance Breakdown", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }

                        // 5. Course list items
                        items(subjects) { sub ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(sub.subject_name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark, maxLines = 1)
                                        val heldText = if (sub.periods_held == 0) {
                                            "0 held • Faculty: ${sub.teacher_name}"
                                        } else {
                                            "${sub.periods_attended}/${sub.periods_held} attended • Faculty: ${sub.teacher_name}"
                                        }
                                        Text(heldText, fontSize = 11.sp, color = TextGray, maxLines = 1)
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    val pctText = if (sub.periods_held == 0) "Not Started" else "${String.format(Locale.ENGLISH, "%.1f", sub.percentage)}%"
                                    val (badgeBg, badgeTextColor) = when {
                                        sub.periods_held == 0 -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                                        sub.percentage >= 75.0 -> Pair(LightGreenBg, PrimaryGreen)
                                        else -> Pair(LightRedBg, AlertRed)
                                    }

                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            pctText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
