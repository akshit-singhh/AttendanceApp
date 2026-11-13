package com.akshit.attendance.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
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
import com.akshit.attendance.notification.AttendanceNotificationManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Semantic Colors matching App Theme ---
private val PrimaryBlue = Color(0xFF002D62)
private val LightBlueBg = Color(0xFFE8F0FE)
private val SemanticGreen = Color(0xFF00C853)
private val SemanticRed = Color(0xFFD50000)
private val SemanticYellow = Color(0xFFFFD600)
private val SemanticAmber = Color(0xFFD97706)
private val AmberBg = Color(0xFFFEF3C7)
private val LeaveBg = Color(0xFFFFFBEB)
private val SurfaceLight = Color(0xFFF8F9FB)
private val AbsentBg = Color(0xFFFFF5F5)
private val TextGray = Color(0xFF545F71)
private val TextDark = Color(0xFF0F172A)
private val BorderSubtle = Color(0xFFE2E8F0)

// Temporary date override: set to 12 Nov 2026 (Thursday) as requested by user
private val EffectiveDate: LocalDate = LocalDate.of(2026, 11, 12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRollCallScreen(
    viewModel: TeacherViewModel,
    courseOfferingId: Int,
    sessionDate: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onSubmitted: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showUnmarkedWarningDialog by remember { mutableStateOf(false) }

    val rosterState by viewModel.roster.collectAsState()
    val submitState by viewModel.submit.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isEditing = (rosterState as? RosterUiState.Loaded)?.roster?.any { it.status != null } == true

    val notifications by AttendanceNotificationManager.notifications.collectAsState()
    val unreadNotificationCount = remember(notifications) { notifications.count { !it.isRead } }

    val rawName = profile?.full_name?.takeIf { it.isNotBlank() } ?: "Faculty"
    val cleanNameForInitials = rawName
        .replace("Prof.", "", ignoreCase = true)
        .replace("Prof", "", ignoreCase = true)
        .replace("Dr.", "", ignoreCase = true)
        .replace("Dr", "", ignoreCase = true)
        .trim()
    val initials = cleanNameForInitials.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "FC" }

    LaunchedEffect(courseOfferingId, sessionDate) { viewModel.loadRoster(courseOfferingId, sessionDate) }
    LaunchedEffect(submitState) { if (submitState is SubmitUiState.Done) onSubmitted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Attendance" else "Roll Call",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge(
                                        containerColor = SemanticRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color(0xFF0F172A)
                            )
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    initials,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceLight
                )
            )
        },
        containerColor = SurfaceLight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (rosterState is RosterUiState.Loaded) {
                val s = rosterState as RosterUiState.Loaded
                val presentCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.PRESENT }
                val absentCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.ABSENT }
                val leaveCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.LEAVE }
                val unmarkedCount = s.roster.count { s.marks[it.student_id] == null }
                val markedTotal = presentCount + absentCount + leaveCount
                val attendancePercentage = if (markedTotal > 0) (presentCount.toFloat() / markedTotal) * 100 else 0f

                BottomSubmitBar(
                    presentCount = presentCount,
                    absentCount = absentCount,
                    leaveCount = leaveCount,
                    unmarkedCount = unmarkedCount,
                    percentage = attendancePercentage,
                    isSubmitting = submitState is SubmitUiState.Submitting,
                    isEditing = isEditing,
                    onSubmit = {
                        if (unmarkedCount > 0) {
                            showUnmarkedWarningDialog = true
                        } else {
                            showConfirmDialog = true
                        }
                    }
                )

                if (showUnmarkedWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showUnmarkedWarningDialog = false },
                        title = {
                            Text("Unmarked Students ($unmarkedCount)", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "There ${if (unmarkedCount == 1) "is 1 student" else "are $unmarkedCount students"} not marked yet.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "You can mark all remaining students as Absent and submit directly, or review them before submitting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showUnmarkedWarningDialog = false
                                    viewModel.submitAttendance(defaultUnmarkedTo = AttendanceStatus.ABSENT)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Mark Absent & Submit")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    showUnmarkedWarningDialog = false
                                    filterStatus = "UNMARKED"
                                }) {
                                    Text("Review", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { showUnmarkedWarningDialog = false }) {
                                    Text("Cancel", color = TextGray)
                                }
                            }
                        }
                    )
                }

                if (showConfirmDialog) {
                    if (isEditing) {
                        val changedStudents = remember(s.marks, s.initialMarks) {
                            s.roster.mapNotNull { student ->
                                val before = s.initialMarks[student.student_id]
                                val after = s.marks[student.student_id]
                                if (before != after) Triple(student, before, after) else null
                            }
                        }

                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            title = {
                                Text("Confirm Attendance Update", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    s.courseInfo?.let { c ->
                                        Text(
                                            "${c.subject_name} (${c.section_name})",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = PrimaryBlue
                                        )
                                    }
                                    Text(
                                        "Target Date: ${s.sessionDate ?: EffectiveDate.toString()}",
                                        fontSize = 12.sp,
                                        color = TextGray
                                    )

                                    if (changedStudents.isEmpty()) {
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BorderSubtle),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "No attendance statuses were changed.",
                                                fontSize = 13.sp,
                                                color = TextGray,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            "${changedStudents.size} student record${if (changedStudents.size > 1) "s" else ""} modified:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextDark
                                        )

                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BorderSubtle),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .heightIn(max = 200.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                changedStudents.forEach { (student, before, after) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                            Text(
                                                                student.full_name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 12.sp,
                                                                color = TextDark,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                student.roll_number,
                                                                fontSize = 10.sp,
                                                                color = TextGray
                                                            )
                                                        }

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            StatusDiffBadge(before)
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                                contentDescription = null,
                                                                tint = TextGray,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            StatusDiffBadge(after)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Present: $presentCount", color = SemanticGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Absent: $absentCount", color = if (absentCount > 0) SemanticRed else TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (leaveCount > 0) {
                                            Text("Leave: $leaveCount", color = SemanticAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Text(
                                        "Once saved, these changes will directly update the official attendance records.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
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
                                    Text(if (changedStudents.isEmpty()) "Save Anyway" else "Save ${changedStudents.size} Change${if (changedStudents.size > 1) "s" else ""}")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmDialog = false }) {
                                    Text("Cancel", color = TextGray)
                                }
                            }
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            title = {
                                Text("Confirm Attendance Submission", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Please verify the roll call details before finalizing:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    s.courseInfo?.let { c ->
                                        Text("\u2022 Course: ${c.subject_code} - ${c.subject_name}", fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                        Text("\u2022 Section: ${c.section_name}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                                    }
                                    Text("\u2022 Present: $presentCount students", color = SemanticGreen, fontWeight = FontWeight.Bold)
                                    Text("\u2022 Absent: $absentCount students", color = if (absentCount > 0) SemanticRed else TextGray, fontWeight = FontWeight.Bold)
                                    if (leaveCount > 0) {
                                        Text("\u2022 On Leave: $leaveCount students", color = SemanticAmber, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (s.sessionDate != null) {
                                        Text("\u2022 Target Date: ${s.sessionDate} (Past / Pending Session)", fontWeight = FontWeight.Bold, color = SemanticAmber)
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
                                    Text("Confirm & Submit")
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
                val presentCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.PRESENT }
                val absentCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.ABSENT }
                val leaveCount = s.roster.count { s.marks[it.student_id] == AttendanceStatus.LEAVE }
                val unmarkedCount = s.roster.count { s.marks[it.student_id] == null }

                val filteredRoster = s.roster.filter { student ->
                    val matchesSearch = search.isBlank() || student.full_name.contains(search, ignoreCase = true) || student.roll_number.contains(search, ignoreCase = true)
                    val status = s.marks[student.student_id]
                    val matchesFilter = when (filterStatus) {
                        "PRESENT" -> status == AttendanceStatus.PRESENT
                        "ABSENT" -> status == AttendanceStatus.ABSENT
                        "LEAVE" -> status == AttendanceStatus.LEAVE
                        "UNMARKED" -> status == null
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
                        item { RollCallHeader(s.courseInfo, isEditing, s.sessionDate) }

                        item {
                            SearchAndFilterRow(
                                search = search,
                                onSearchChange = { search = it },
                                filterStatus = filterStatus,
                                onFilterChange = { filterStatus = it },
                                total = totalStudents,
                                unmarked = unmarkedCount,
                                present = presentCount,
                                absent = absentCount,
                                leave = leaveCount,
                                onMarkAll = { status -> viewModel.markAll(status) },
                                onResetAll = { viewModel.markAll(null) }
                            )
                        }

                        items(filteredRoster, key = { it.student_id }) { student ->
                            val status = s.marks[student.student_id]
                            RosterRow(student, status) { targetStatus ->
                                viewModel.setMark(student.student_id, targetStatus)
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
private fun RollCallHeader(
    course: CourseScheduleResponse?,
    isEditing: Boolean = false,
    sessionDate: String? = null
) {
    val subjectCode = course?.subject_code ?: "---"
    val subjectName = course?.subject_name ?: "Loading..."
    val sectionName = course?.section_name ?: "--"
    val room = course?.room ?: "Room TBD"
    val formattedDate = sessionDate ?: EffectiveDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Code, Credits, Type, and Mode Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            subjectCode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${course?.credits ?: 3} Credits",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!course?.course_type.isNullOrBlank() && course?.course_type != "LECTURE") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                course!!.course_type!!,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isEditing) SemanticGreen else SemanticYellow)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            if (isEditing) "EDITING" else "ROLL CALL",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Course Name
            Text(
                subjectName,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Meta: Section, Room, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PeopleAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Sec $sectionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "\u2022",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    Icons.Default.MeetingRoom,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    room,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "\u2022",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun SearchAndFilterRow(
    search: String,
    onSearchChange: (String) -> Unit,
    filterStatus: String,
    onFilterChange: (String) -> Unit,
    total: Int,
    unmarked: Int,
    present: Int,
    absent: Int,
    leave: Int,
    onMarkAll: (AttendanceStatus) -> Unit,
    onResetAll: () -> Unit
) {
    var showMarkAllMenu by remember { mutableStateOf(false) }

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

            Box {
                Surface(
                    onClick = { showMarkAllMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    color = LightBlueBg,
                    modifier = Modifier.height(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark All", color = PrimaryBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }

                DropdownMenu(
                    expanded = showMarkAllMenu,
                    onDismissRequest = { showMarkAllMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Present", fontWeight = FontWeight.SemiBold, color = SemanticGreen) },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = SemanticGreen) },
                        onClick = {
                            showMarkAllMenu = false
                            onMarkAll(AttendanceStatus.PRESENT)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("All Absent", fontWeight = FontWeight.SemiBold, color = SemanticRed) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = SemanticRed) },
                        onClick = {
                            showMarkAllMenu = false
                            onMarkAll(AttendanceStatus.ABSENT)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reset to Unmarked", fontWeight = FontWeight.Medium, color = TextGray) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TextGray) },
                        onClick = {
                            showMarkAllMenu = false
                            onResetAll()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipItem("All", total, filterStatus == "ALL", Color.White, PrimaryBlue) { onFilterChange("ALL") }
            FilterChipItem("Unmarked", unmarked, filterStatus == "UNMARKED", SemanticAmber, AmberBg) { onFilterChange("UNMARKED") }
            FilterChipItem("Present", present, filterStatus == "PRESENT", SemanticGreen, LightBlueBg) { onFilterChange("PRESENT") }
            FilterChipItem("Absent", absent, filterStatus == "ABSENT", SemanticRed, AbsentBg) { onFilterChange("ABSENT") }
            FilterChipItem("Leave", leave, filterStatus == "LEAVE", SemanticAmber, LeaveBg) { onFilterChange("LEAVE") }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    dotColor: Color,
    activeBgColor: Color,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        if (label == "All") PrimaryBlue else activeBgColor
    } else {
        Color.White
    }
    val contentColor = if (isSelected && label == "All") Color.White else Color(0xFF0F172A)
    val badgeBg = if (isSelected && label == "All") {
        Color.White.copy(alpha = 0.2f)
    } else if (isSelected) {
        Color.White.copy(alpha = 0.85f)
    } else {
        SurfaceLight
    }
    val badgeTextColor = if (isSelected && label == "All") {
        Color.White
    } else when (label) {
        "Absent" -> SemanticRed
        "Present" -> SemanticGreen
        "Unmarked" -> SemanticAmber
        "Leave" -> SemanticAmber
        else -> Color.DarkGray
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = if (!isSelected) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else BorderStroke(1.dp, if (label == "All") PrimaryBlue else dotColor.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (label != "All") {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(label, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = badgeBg) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RosterRow(
    student: StudentRosterResponse,
    status: AttendanceStatus?,
    onSetMark: (AttendanceStatus) -> Unit
) {
    val isPresent = status == AttendanceStatus.PRESENT
    val isAbsent = status == AttendanceStatus.ABSENT
    val isLeave = status == AttendanceStatus.LEAVE

    val bgColor = when {
        isAbsent -> AbsentBg
        isLeave -> LeaveBg
        isPresent -> Color.White
        else -> Color.White
    }
    val borderColor = when {
        isAbsent -> SemanticRed.copy(alpha = 0.25f)
        isLeave -> SemanticAmber.copy(alpha = 0.35f)
        isPresent -> SemanticGreen.copy(alpha = 0.25f)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPresent || isAbsent || isLeave) 1.dp else 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Student details
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(LightBlueBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = student.full_name
                        .split(" ")
                        .mapNotNull { it.firstOrNull() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    Text(
                        initials,
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        student.full_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        student.roll_number,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons: P, A, and L side-by-side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Present Button (P)
                Surface(
                    onClick = { onSetMark(AttendanceStatus.PRESENT) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPresent) SemanticGreen else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isPresent) SemanticGreen else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "P",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isPresent) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                // Absent Button (A)
                Surface(
                    onClick = { onSetMark(AttendanceStatus.ABSENT) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAbsent) SemanticRed else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isAbsent) SemanticRed else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "A",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isAbsent) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                // Leave Button (L)
                Surface(
                    onClick = { onSetMark(AttendanceStatus.LEAVE) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLeave) SemanticAmber else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isLeave) SemanticAmber else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "L",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isLeave) Color.White else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSubmitBar(
    presentCount: Int,
    absentCount: Int,
    leaveCount: Int,
    unmarkedCount: Int,
    percentage: Float,
    isSubmitting: Boolean,
    isEditing: Boolean = false,
    onSubmit: () -> Unit
) {
    Surface(shadowElevation = 16.dp, color = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Stats & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$presentCount Present",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SemanticGreen
                    )
                    Text(
                        " \u2022 ",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextGray
                    )
                    Text(
                        "$absentCount Absent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (absentCount > 0) SemanticRed else TextGray
                    )
                    if (leaveCount > 0) {
                        Text(
                            " \u2022 ",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextGray
                        )
                        Text(
                            "$leaveCount Leave",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SemanticAmber
                        )
                    }
                }

                if (unmarkedCount > 0) {
                    Surface(shape = RoundedCornerShape(12.dp), color = AmberBg) {
                        Text(
                            "$unmarkedCount Unmarked",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = LightBlueBg) {
                        Text(
                            String.format(Locale.ENGLISH, "%.1f%% Attendance", percentage),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

@Composable
private fun StatusDiffBadge(status: AttendanceStatus?) {
    val (label, bg, txt) = when (status) {
        AttendanceStatus.PRESENT -> Triple("P", LightBlueBg, SemanticGreen)
        AttendanceStatus.ABSENT -> Triple("A", AbsentBg, SemanticRed)
        AttendanceStatus.LEAVE -> Triple("L", AmberBg, SemanticAmber)
        null -> Triple("-", Color(0xFFF1F5F9), Color(0xFF64748B))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, txt.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = txt,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
