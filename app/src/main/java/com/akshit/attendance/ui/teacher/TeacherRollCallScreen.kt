package com.akshit.attendance.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.AttendanceStatus
import com.akshit.attendance.data.model.CourseScheduleResponse
import com.akshit.attendance.data.model.StudentRosterResponse
import com.akshit.attendance.ui.components.AppTopBar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// --- Semantic Colors matching Image ---
private val PrimaryBlue = Color(0xFF0D3B85)
private val HeaderAccentBlue = Color(0xFF2856A0)
private val LightBlueBg = Color(0xFFE8F0FE)
private val SemanticGreen = Color(0xFF00C853)
private val SemanticRed = Color(0xFFD50000)
private val SemanticYellow = Color(0xFFFFD600)
private val SurfaceLight = Color(0xFFF8F9FB)
private val AbsentBg = Color(0xFFFFF5F5)
private val TextGray = Color(0xFF545F71)

@Composable
fun TeacherRollCallScreen(
    viewModel: TeacherViewModel,
    courseOfferingId: Int,
    sessionDate: String? = null,
    onSubmitted: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val rosterState by viewModel.roster.collectAsState()
    val submitState by viewModel.submit.collectAsState()
    val isEditing = (rosterState as? RosterUiState.Loaded)?.roster?.any { it.status != null } == true

    LaunchedEffect(courseOfferingId, sessionDate) { viewModel.loadRoster(courseOfferingId, sessionDate) }
    LaunchedEffect(submitState) { if (submitState is SubmitUiState.Done) onSubmitted() }

    Scaffold(
        topBar = { AppTopBar(title = if (isEditing) "Edit Attendance" else "Roll Call") },
        containerColor = SurfaceLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (rosterState is RosterUiState.Loaded) {
                val s = rosterState as RosterUiState.Loaded
                val totalStudents = s.roster.size
                val presentCount = s.roster.count { (s.marks[it.student_id] ?: AttendanceStatus.PRESENT) == AttendanceStatus.PRESENT }
                val absentCount = totalStudents - presentCount
                val attendancePercentage = if (totalStudents > 0) (presentCount.toFloat() / totalStudents) * 100 else 100f

                BottomSubmitBar(
                    presentCount = presentCount,
                    absentCount = absentCount,
                    percentage = attendancePercentage,
                    isSubmitting = submitState is SubmitUiState.Submitting,
                    isEditing = isEditing,
                    onSubmit = { showConfirmDialog = true }
                )

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = {
                            Text(if (isEditing) "Confirm Attendance Update" else "Confirm Attendance Submission", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isEditing) "Please review your edits before updating the attendance record:"
                                    else "Please verify the roll call details before finalizing:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                s.courseInfo?.let { c ->
                                    Text("• Course: ${c.subject_code} - ${c.subject_name}", fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                    Text("• Section: ${c.section_name}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                                }
                                Text("• Present: $presentCount students", color = SemanticGreen, fontWeight = FontWeight.Bold)
                                Text("• Absent: $absentCount students", color = if (absentCount > 0) SemanticRed else TextGray, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                if (isEditing) {
                                    Text("• Target Date: ${s.sessionDate ?: "Today"} (Editing Session)", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                    Text("Once updated, this will modify the attendance records directly without requiring approval.", style = MaterialTheme.typography.bodySmall, color = TextGray)
                                } else if (s.sessionDate != null) {
                                    Text("• Target Date: ${s.sessionDate} (Past / Pending Session)", fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                    Text("Once submitted, this will record the official attendance session for ${s.sessionDate}.", style = MaterialTheme.typography.bodySmall, color = TextGray)
                                } else {
                                    Text("Once submitted, this will record the official attendance session for today.", style = MaterialTheme.typography.bodySmall, color = TextGray)
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showConfirmDialog = false
                                    viewModel.submitAttendance()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text(if (isEditing) "Save Changes" else "Confirm & Submit")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) {
                                Text("Cancel", color = TextGray)
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        when (val s = rosterState) {
            is RosterUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            is RosterUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is RosterUiState.Loaded -> {
                val totalStudents = s.roster.size
                val presentCount = s.roster.count { (s.marks[it.student_id] ?: AttendanceStatus.PRESENT) == AttendanceStatus.PRESENT }
                val absentCount = totalStudents - presentCount

                val filteredRoster = s.roster.filter { student ->
                    val matchesSearch = search.isBlank() || student.full_name.contains(search, ignoreCase = true) || student.roll_number.contains(search, ignoreCase = true)
                    val status = s.marks[student.student_id] ?: AttendanceStatus.PRESENT
                    val matchesFilter = when (filterStatus) {
                        "PRESENT" -> status == AttendanceStatus.PRESENT
                        "ABSENT" -> status == AttendanceStatus.ABSENT
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }

                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { RollCallHeader(s.courseInfo, isEditing) }

                        item {
                            SearchAndFilterRow(
                                search = search,
                                onSearchChange = { search = it },
                                filterStatus = filterStatus,
                                onFilterChange = { filterStatus = it },
                                total = totalStudents,
                                present = presentCount,
                                absent = absentCount,
                                onMarkAllPresent = { viewModel.markAll(AttendanceStatus.PRESENT) }
                            )
                        }

                        items(filteredRoster, key = { it.student_id }) { student ->
                            val status = s.marks[student.student_id] ?: AttendanceStatus.PRESENT
                            RosterRow(student, status) {
                                viewModel.toggleMark(student.student_id)
                            }
                        }
                    }

                    if (submitState is SubmitUiState.Error) {
                        Text(
                            text = (submitState as SubmitUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RollCallHeader(course: CourseScheduleResponse?, isEditing: Boolean = false) {
    val subjectCode = course?.subject_code ?: "---"
    val subjectName = course?.subject_name ?: "Loading..."
    val sectionName = course?.section_name ?: "--"
    val room = course?.room ?: "--"

    val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd"))
    val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm\na"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 95.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isEditing) SemanticGreen else SemanticYellow))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isEditing) "EDIT ATTENDANCE" else "ROLL CALL",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)) {
                        Text("${course?.credits ?: 3} Credits", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "$subjectCode • $subjectName",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                val typeStr = course?.course_type?.let { " • $it" } ?: ""
                Text(
                    "Section $sectionName • Room $room$typeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = HeaderAccentBlue,
                shape = RoundedCornerShape(bottomStart = 20.dp, topEnd = 16.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentDate, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(currentTime, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterRow(
    search: String, onSearchChange: (String) -> Unit,
    filterStatus: String, onFilterChange: (String) -> Unit,
    total: Int, present: Int, absent: Int,
    onMarkAllPresent: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = { Text("Search roll or name...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.LightGray.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.LightGray.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f).height(50.dp)
            )
            Surface(
                onClick = onMarkAllPresent,
                shape = RoundedCornerShape(12.dp),
                color = LightBlueBg,
                modifier = Modifier.height(50.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mark All", color = Color.Black, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipItem("All", total, filterStatus == "ALL", Color.White, PrimaryBlue) { onFilterChange("ALL") }
            FilterChipItem("Absent", absent, filterStatus == "ABSENT", SemanticRed, Color.White) { onFilterChange("ABSENT") }
            FilterChipItem("Present", present, filterStatus == "PRESENT", SemanticYellow, Color.White) { onFilterChange("PRESENT") }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, count: Int, isSelected: Boolean, dotColor: Color, activeBgColor: Color, onClick: () -> Unit) {
    val bgColor = if (isSelected) activeBgColor else Color.White
    val contentColor = if (isSelected && activeBgColor == PrimaryBlue) Color.White else Color.DarkGray
    val badgeBg = if (isSelected && activeBgColor == PrimaryBlue) Color.White.copy(alpha = 0.2f) else SurfaceLight
    val badgeTextColor = if (isSelected && activeBgColor == PrimaryBlue) Color.White else if (label == "Absent") SemanticRed else Color.DarkGray

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = if (!isSelected) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (label != "All") {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(label, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = badgeBg) {
                Text(count.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = badgeTextColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RosterRow(student: StudentRosterResponse, status: AttendanceStatus, onToggle: () -> Unit) {
    val isPresent = status == AttendanceStatus.PRESENT
    val bgColor = if (isPresent) Color.White else AbsentBg
    val borderColor = if (isPresent) Color.Transparent else SemanticRed.copy(alpha = 0.1f)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPresent) 1.dp else 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(LightBlueBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = student.full_name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
                    Text(initials, color = PrimaryBlue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(student.full_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(student.roll_number, style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPresent) SemanticGreen.copy(alpha = 0.1f) else SemanticRed.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isPresent) SemanticGreen else SemanticRed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPresent) "Present" else "Absent", color = if (isPresent) SemanticGreen else SemanticRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isPresent) SemanticGreen.copy(alpha = 0.15f) else SemanticRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isPresent) SemanticGreen else SemanticRed,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSubmitBar(
    presentCount: Int,
    absentCount: Int,
    percentage: Float,
    isSubmitting: Boolean,
    isEditing: Boolean = false,
    onSubmit: () -> Unit
) {
    Surface(shadowElevation = 16.dp, color = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Stats & Percentage
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$presentCount Present • $absentCount Absent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(shape = RoundedCornerShape(12.dp), color = LightBlueBg) {
                    Text(
                        String.format("%.1f%% Attendance", percentage),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Submit Button
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(if (isEditing) Icons.Default.DoneAll else Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEditing) "Update Attendance" else "Submit Attendance",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}