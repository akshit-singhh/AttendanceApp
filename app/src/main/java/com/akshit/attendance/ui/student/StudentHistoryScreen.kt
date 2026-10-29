package com.akshit.attendance.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.SubjectAttendanceHistory
import com.akshit.attendance.ui.components.SkeletonBox
import com.akshit.attendance.ui.components.SubjectCardSkeleton
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHistoryScreen(model: StudentViewModel) {
    val dashboardState by model.dashboard.collectAsState()
    var filterMode by remember { mutableStateOf("ALL") } // ALL, ATTENTION, ON_TRACK

    Scaffold(
        containerColor = SurfaceBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("STUDENT PORTAL", fontSize = 10.sp, color = TextGray, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        Text("Attendance History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.School, contentDescription = "Logo", tint = PrimaryBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { model.loadDashboard() }) {
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
                    item {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(16.dp))
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonBox(modifier = Modifier.width(80.dp).height(32.dp), shape = RoundedCornerShape(16.dp))
                            SkeletonBox(modifier = Modifier.width(100.dp).height(32.dp), shape = RoundedCornerShape(16.dp))
                            SkeletonBox(modifier = Modifier.width(80.dp).height(32.dp), shape = RoundedCornerShape(16.dp))
                        }
                    }
                    items(4) {
                        SubjectCardSkeleton()
                    }
                }
            }
            is DashboardUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = AlertRed, fontSize = 14.sp)
                }
            }
            is DashboardUiState.Loaded -> {
                val allSubjects = state.data.subjects
                val totalAttended = allSubjects.sumOf { it.periods_attended }
                val totalHeld = allSubjects.sumOf { it.periods_held }
                val needsAttentionCount = allSubjects.count { it.periods_held > 0 && it.percentage < 75.0 }
                val onTrackCount = allSubjects.count { it.periods_held > 0 && it.percentage >= 75.0 }

                val displayedSubjects = when (filterMode) {
                    "ATTENTION" -> allSubjects.filter { it.periods_held > 0 && it.percentage < 75.0 }
                    "ON_TRACK" -> allSubjects.filter { it.periods_held > 0 && it.percentage >= 75.0 }
                    else -> allSubjects
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Real Term Summary Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(36.dp).background(Color(0xFFEEF2FF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("CURRENT TERM", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
                                            Text(state.data.current_semester, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                        }
                                    }
                                    Surface(
                                        color = if (totalHeld == 0) Color(0xFFF1F5F9) else if (state.data.overall_percentage >= 75.0) LightGreenBg else LightRedBg,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            if (totalHeld == 0) "Not Started" else if (state.data.overall_percentage >= 75.0) "Compliant" else "At Risk",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (totalHeld == 0) Color(0xFF64748B) else if (state.data.overall_percentage >= 75.0) PrimaryGreen else AlertRed,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(verticalAlignment = Alignment.Bottom) {
                                    val pctStr = if (totalHeld == 0) "0.0%" else "${String.format(Locale.ENGLISH, "%.1f", state.data.overall_percentage)}%"
                                    Text(
                                        pctStr,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (totalHeld == 0) TextGray else if (state.data.overall_percentage >= 75.0) PrimaryGreen else AlertRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("cumulative", fontSize = 13.sp, color = TextGray, modifier = Modifier.padding(bottom = 6.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val totalRatioText = if (totalHeld == 0) "0 attended of 0 total lectures" else "$totalAttended attended of $totalHeld total lectures"
                                    Text(totalRatioText, fontSize = 12.sp, color = TextGray)
                                    Text("Policy: ≥75%", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val prog = if (totalHeld == 0) 0f else (state.data.overall_percentage / 100.0).toFloat().coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (state.data.overall_percentage >= 75.0) PrimaryGreen else AlertRed,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }

                    // 2. Filter Chips Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filterMode == "ALL",
                                onClick = { filterMode = "ALL" },
                                label = { Text("All • ${allSubjects.size}", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterMode == "ATTENTION",
                                onClick = { filterMode = "ATTENTION" },
                                label = { Text("Needs Attention • $needsAttentionCount", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AlertRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterMode == "ON_TRACK",
                                onClick = { filterMode = "ON_TRACK" },
                                label = { Text("On Track • $onTrackCount", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (displayedSubjects.isEmpty()) {
                        item {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No Courses in this Category", fontWeight = FontWeight.Bold, color = TextDark)
                                    Text("All your courses are within academic requirements.", fontSize = 12.sp, color = TextGray)
                                }
                            }
                        }
                    } else {
                        items(displayedSubjects) { subject ->
                            val advice = model.getBunkAdvice(subject.periods_attended, subject.periods_held, 75.0)
                            RealCourseHistoryCard(subject = subject, advice = advice)
                        }
                    }

                    // 3. Footer Regulations Card
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "University policy mandates a minimum of 75% attendance in each course to be eligible for end-term examinations.",
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RealCourseHistoryCard(
    subject: SubjectAttendanceHistory,
    advice: BunkAdvice
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = AccentPurple.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(subject.course_code.take(2).uppercase(), fontWeight = FontWeight.ExtraBold, color = AccentPurple, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        subject.subject_name,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        "Faculty: ${subject.teacher_name}",
                        fontSize = 11.sp,
                        color = TextGray,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(8.dp))

                val pctText = if (subject.periods_held == 0) "—" else "${String.format(Locale.ENGLISH, "%.1f", subject.percentage)}%"
                val pctColor = when {
                    subject.periods_held == 0 -> TextGray
                    subject.percentage >= 75.0 -> PrimaryGreen
                    else -> AlertRed
                }
                Text(
                    pctText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = pctColor
                )
            }

            Spacer(Modifier.height(12.dp))

            // Attendance ratio and status note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ratioText = if (subject.periods_held == 0) "0 of 0 lectures conducted" else "${subject.periods_attended} of ${subject.periods_held} lectures attended"
                Text(ratioText, fontSize = 12.sp, color = TextGray)

                val (badgeBg, badgeText, badgeColor) = when {
                    subject.periods_held == 0 -> Triple(Color(0xFFF1F5F9), "Not Started", Color(0xFF64748B))
                    subject.percentage >= 85.0 -> Triple(LightGreenBg, "Safe Buffer", PrimaryGreen)
                    subject.percentage >= 75.0 -> Triple(LightGreenBg, "On Track", PrimaryGreen)
                    else -> Triple(LightRedBg, "Needs Attention", AlertRed)
                }

                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                val prog = if (subject.periods_held == 0) 0f else (subject.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                val barColor = if (subject.percentage >= 75.0) PrimaryGreen else AlertRed
                if (prog > 0f) {
                    Box(modifier = Modifier.fillMaxWidth(prog).fillMaxHeight().background(barColor))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Advice Pill
            if (subject.periods_held == 0) {
                Text("No sessions held yet for this subject", fontSize = 11.sp, color = Color(0xFF64748B))
            } else {
                when (advice) {
                    is BunkAdvice.Safe -> {
                        val txt = if (advice.classesCanMiss > 0)
                            "✓ Safe to miss ${advice.classesCanMiss} upcoming lectures"
                        else
                            "⚠ At 75% threshold — attend next session to maintain standing"
                        Text(txt, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                    }
                    is BunkAdvice.AtRisk -> {
                        Text("⚠ Attend next ${advice.classesNeeded} lectures to reach 75%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                    }
                    BunkAdvice.NoClasses -> {
                        Text("No sessions held yet", fontSize = 11.sp, color = TextGray)
                    }
                }
            }
        }
    }
}
