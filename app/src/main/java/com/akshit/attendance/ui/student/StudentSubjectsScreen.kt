package com.akshit.attendance.ui.student

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.SubjectAttendanceHistory
import com.akshit.attendance.ui.components.SubjectCardSkeleton
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSubjectsScreen(
    viewModel: StudentViewModel,
    onSelectSubject: (String) -> Unit
) {
    val dashboardState by viewModel.dashboard.collectAsState()
    var filterMode by remember { mutableStateOf("ALL") } // ALL, ATTENTION, ON_TRACK

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("STUDENT PORTAL", fontSize = 10.sp, color = TextGray, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        Text("Subjects & Workload", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(5) {
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
                val needsAttentionCount = allSubjects.count { it.percentage < 75.0 }
                val onTrackCount = allSubjects.count { it.percentage >= 75.0 }

                val displayedSubjects = when (filterMode) {
                    "ATTENTION" -> allSubjects.filter { it.percentage < 75.0 }
                    "ON_TRACK" -> allSubjects.filter { it.percentage >= 75.0 }
                    else -> allSubjects
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Filter Chips Row
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
                                label = { Text("Needs attention • $needsAttentionCount", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AlertRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterMode == "ON_TRACK",
                                onClick = { filterMode = "ON_TRACK" },
                                label = { Text("On track • $onTrackCount", fontWeight = FontWeight.Bold) },
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
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No Subjects in this Category", fontWeight = FontWeight.Bold, color = TextDark)
                                    Text("All your subjects are currently meeting criteria.", fontSize = 12.sp, color = TextGray)
                                }
                            }
                        }
                    } else {
                        items(displayedSubjects) { subject ->
                            val advice = viewModel.getBunkAdvice(subject.periods_attended, subject.periods_held, 75.0)
                            SubjectCardItem(
                                subject = subject,
                                advice = advice,
                                onClick = { onSelectSubject(subject.course_code) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectCardItem(
    subject: SubjectAttendanceHistory,
    advice: BunkAdvice,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Code avatar + Title + Percentage
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
                        val badge = subject.course_code.take(2).uppercase()
                        Text(badge, fontWeight = FontWeight.ExtraBold, color = AccentPurple, fontSize = 13.sp)
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
                    val subtitleText = if (subject.periods_held == 0) {
                        "Faculty: ${subject.teacher_name} • Not Started"
                    } else {
                        "${subject.periods_attended} of ${subject.periods_held} attended • ${subject.teacher_name}"
                    }
                    Text(
                        subtitleText,
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

            // Progress Bar with 75% Target Notch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                val progressFraction = if (subject.periods_held == 0) 0f else (subject.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                val barColor = if (subject.percentage >= 75.0) PrimaryGreen else AlertRed

                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }

                // 75% notch mark
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dynamic Bunk Advice Badge
            if (subject.periods_held == 0) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No sessions conducted yet for this course",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                when (advice) {
                    is BunkAdvice.Safe -> {
                        Surface(
                            color = LightGreenBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (advice.classesCanMiss > 0)
                                        "You can miss ${advice.classesCanMiss} classes and still meet 75%"
                                    else
                                        "At 75% threshold • Attend next class to stay safe",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen
                                )
                            }
                        }
                    }
                    is BunkAdvice.AtRisk -> {
                        Surface(
                            color = LightAmberBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Attend the next ${advice.classesNeeded} classes to reach 75%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                            }
                        }
                    }
                    BunkAdvice.NoClasses -> {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "No classes held yet for this subject",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
