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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    subjectCode: String,
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val dashboardState by viewModel.dashboard.collectAsState()
    val schedule by viewModel.schedule.collectAsState()

    val subject = (dashboardState as? DashboardUiState.Loaded)
        ?.data?.subjects?.firstOrNull { it.course_code.equals(subjectCode, ignoreCase = true) }

    val subjectSlots = remember(schedule, subjectCode) {
        schedule.filter { it.subject_code.equals(subjectCode, ignoreCase = true) }
            .sortedWith(compareBy({ slot ->
                when (slot.day_of_week.uppercase()) {
                    "MON" -> 1
                    "TUE" -> 2
                    "WED" -> 3
                    "THU" -> 4
                    "FRI" -> 5
                    "SAT" -> 6
                    else -> 7
                }
            }, { parseTimeToMinutes(it.start_time) }))
    }

    Scaffold(
        containerColor = SurfaceBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(subject?.course_code ?: subjectCode, fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Bold)
                        Text(subject?.subject_name ?: "Subject Detail", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        if (subject == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Subject details not found", color = TextGray)
            }
            return@Scaffold
        }

        val advice = viewModel.getBunkAdvice(subject.periods_attended, subject.periods_held, 75.0)
        val missed = (subject.periods_held - subject.periods_attended).coerceAtLeast(0)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Ring Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Standing Chip
                        val (chipBg, chipColor, chipText) = if (subject.periods_held == 0) {
                            Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Not Started")
                        } else if (subject.percentage >= 75.0) {
                            Triple(LightGreenBg, PrimaryGreen, "On Track")
                        } else {
                            Triple(LightRedBg, AlertRed, "Needs Attention")
                        }
                        Surface(color = chipBg, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                chipText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = chipColor
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Circular Gauge
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                            val sweep = if (subject.periods_held == 0) 0f else (360f * (subject.percentage / 100.0).toFloat()).coerceIn(0f, 360f)
                            val ringColor = when {
                                subject.periods_held == 0 -> Color(0xFFCBD5E1)
                                subject.percentage >= 75.0 -> PrimaryGreen
                                else -> AlertRed
                            }

                            Canvas(modifier = Modifier.size(120.dp)) {
                                drawArc(
                                    color = Color(0xFFE2E8F0),
                                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                )
                                if (sweep > 0f) {
                                    drawArc(
                                        color = ringColor,
                                        startAngle = -90f, sweepAngle = sweep, useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val pctText = if (subject.periods_held == 0) "—" else "${String.format(Locale.ENGLISH, "%.1f", subject.percentage)}%"
                                Text(
                                    pctText,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (subject.periods_held == 0) TextGray else TextDark
                                )
                                Text(
                                    if (subject.periods_held == 0) "No Sessions" else "Target 75%",
                                    fontSize = 10.sp,
                                    color = TextGray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Advice line
                        val adviceText = if (subject.periods_held == 0) {
                            "No attendance sessions have been conducted yet"
                        } else when (advice) {
                            is BunkAdvice.Safe -> if (advice.classesCanMiss > 0)
                                "✓ You can miss ${advice.classesCanMiss} classes and still meet 75%"
                            else "At safe limit • Must attend next session"
                            is BunkAdvice.AtRisk -> "⚠ Attend the next ${advice.classesNeeded} classes to reach 75%"
                            BunkAdvice.NoClasses -> "No sessions held yet"
                        }
                        Text(
                            adviceText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (subject.periods_held == 0) TextGray else if (subject.percentage >= 75.0) PrimaryGreen else AlertRed
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Faculty: ${subject.teacher_name}",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }
                }
            }

            // 2. Unified Metric Card (Attended / Missed / Total Conducted)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attended
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${subject.periods_attended}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryGreen
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Attended", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        // Missed
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$missed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (missed > 0) AlertRed else TextGray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Missed", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color(0xFFE2E8F0))
                        )

                        // Conducted
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${subject.periods_held}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Conducted", fontSize = 11.sp, color = TextGray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 3. Weekly Schedule for this Subject
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Schedule", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text("${subjectSlots.size} classes a week", fontSize = 12.sp, color = TextGray)
                }
            }

            if (subjectSlots.isEmpty()) {
                item {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No timetable slots mapped yet for this subject.", fontSize = 12.sp, color = TextGray)
                        }
                    }
                }
            } else {
                items(subjectSlots) { slot ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(slot.day_of_week.take(3), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryBlue)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    val timeDisp = formatTimeRange12Hour(slot.start_time, slot.end_time)
                                    Text(timeDisp, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                    Text(slot.teacher_name ?: subject.teacher_name, fontSize = 11.sp, color = TextGray)
                                }
                            }

                            val roomDisp = slot.room ?: "TBA"
                            Surface(
                                color = LightGreenBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    roomDisp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
