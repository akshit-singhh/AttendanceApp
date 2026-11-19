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
import com.akshit.attendance.util.AppDateProvider
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

// Dynamic date provider (toggleable via AppDateProvider)
private val EffectiveDate: LocalDate get() = AppDateProvider.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRollCallScreen(
    viewModel: TeacherViewModel,
    courseOfferingId: Int,
    sessionDate: String? = null,
    initialBatchLabel: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onSubmitted: () -> Unit
) {
    val rosterState by viewModel.roster.collectAsState()
    val submitState by viewModel.submit.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val loadedState = rosterState as? RosterUiState.Loaded
    val isEditing = loadedState?.roster?.any { it.status != null } == true

    var search by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }

    val isLabCourse = remember(rosterState) {
        val s = rosterState as? RosterUiState.Loaded
        val info = s?.courseInfo
        info?.course_type?.contains("LAB", ignoreCase = true) == true ||
        info?.subject_name?.contains("Lab", ignoreCase = true) == true ||
        !info?.batch_label.isNullOrBlank()
    }

    var showGroupsInNormalClass by remember { mutableStateOf(false) }
    val shouldShowGroups = isLabCourse || showGroupsInNormalClass

    val hostGroup = remember(initialBatchLabel, loadedState?.courseInfo?.batch_label, loadedState?.roster, isLabCourse) {
        if (!isLabCourse) null
        else {
            val b = initialBatchLabel ?: loadedState?.courseInfo?.batch_label
            when {
                b?.contains("2", ignoreCase = true) == true || b?.equals("G2", ignoreCase = true) == true -> "G2"
                b?.contains("1", ignoreCase = true) == true || b?.equals("G1", ignoreCase = true) == true -> "G1"
                else -> {
                    val markedG1Count = loadedState?.roster?.count { it.lab_group == "G1" && it.status != null } ?: 0
                    val markedG2Count = loadedState?.roster?.count { it.lab_group == "G2" && it.status != null } ?: 0
                    if (markedG1Count > 0 && markedG2Count == 0) "G1"
                    else if (markedG2Count > 0 && markedG1Count == 0) "G2"
                    else null
                }
            }
        }
    }

    val defaultGroup = remember(hostGroup, isLabCourse) {
        if (isLabCourse) hostGroup ?: "ALL" else "ALL"
    }
    var selectedGroup by remember { mutableStateOf(defaultGroup) }
    var userManuallyChangedGroup by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showUnmarkedWarningDialog by remember { mutableStateOf(false) }

    val g1Students = remember(loadedState?.roster) {
        loadedState?.roster?.filter { it.lab_group == "G1" } ?: emptyList()
    }
    val g2Students = remember(loadedState?.roster) {
        loadedState?.roster?.filter { it.lab_group == "G2" } ?: emptyList()
    }

    LaunchedEffect(loadedState?.courseInfo?.batch_label, loadedState?.roster, initialBatchLabel, isLabCourse) {
        if (!isLabCourse) return@LaunchedEffect
        val b = initialBatchLabel ?: loadedState?.courseInfo?.batch_label
        val markedG1Count = loadedState?.roster?.count { it.lab_group == "G1" && it.status != null } ?: 0
        val markedG2Count = loadedState?.roster?.count { it.lab_group == "G2" && it.status != null } ?: 0

        if (markedG2Count > 0 && markedG1Count == 0) {
            selectedGroup = "G2"
        } else if (markedG1Count > 0 && markedG2Count == 0) {
            selectedGroup = "G1"
        } else if (markedG1Count > 0 && markedG2Count > 0) {
            if (!userManuallyChangedGroup) selectedGroup = "ALL"
        } else if (!userManuallyChangedGroup) {
            if (b != null) {
                if (b.contains("2", ignoreCase = true) || b.equals("G2", ignoreCase = true)) {
                    selectedGroup = "G2"
                } else if (b.contains("1", ignoreCase = true) || b.equals("G1", ignoreCase = true)) {
                    selectedGroup = "G1"
                }
            }
        }
    }

    val targetHost = hostGroup ?: if (selectedGroup != "ALL") selectedGroup else null

    val guestStudents = remember(loadedState?.roster, selectedGroup, shouldShowGroups, isLabCourse, loadedState?.marks, targetHost) {
        val roster = loadedState?.roster ?: emptyList()
        val marks = loadedState?.marks ?: emptyMap()
        // Guest students can ONLY belong to the session's active host group and ONLY display on that host group's tab
        if (shouldShowGroups && selectedGroup != "ALL" && isLabCourse && targetHost != null && selectedGroup == targetHost) {
            // STRICT SAFETY NET: Guest students are students from ANOTHER group explicitly marked PRESENT in this session
            roster.filter { it.lab_group != targetHost && marks[it.student_id] == AttendanceStatus.PRESENT }
        } else {
            emptyList()
        }
    }

    val activeGroupStudents = remember(loadedState?.roster, selectedGroup, shouldShowGroups, guestStudents) {
        val roster = loadedState?.roster ?: emptyList()
        if (shouldShowGroups && selectedGroup != "ALL") {
            val regular = roster.filter { it.lab_group == selectedGroup }
            regular + guestStudents
        } else {
            roster
        }
    }

    val currentTabStudentIds = remember(activeGroupStudents, selectedGroup, shouldShowGroups) {
        if (shouldShowGroups && selectedGroup != "ALL") activeGroupStudents.map { it.student_id }.toSet() else null
    }

    val targetGroupIds = remember(loadedState?.roster, targetHost, shouldShowGroups, isLabCourse, loadedState?.marks) {
        val roster = loadedState?.roster ?: emptyList()
        val marks = loadedState?.marks ?: emptyMap()
        if (shouldShowGroups && targetHost != null && isLabCourse) {
            val regular = roster.filter { it.lab_group == targetHost }
            val guests = roster.filter { it.lab_group != targetHost && marks[it.student_id] == AttendanceStatus.PRESENT }
            (regular + guests).map { it.student_id }.toSet()
        } else null
    }

    val batchLabelToSend = remember(targetHost, isLabCourse) {
        if (isLabCourse) targetHost else null
    }

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

    LaunchedEffect(courseOfferingId, sessionDate, initialBatchLabel) {
        viewModel.loadRoster(courseOfferingId, sessionDate, initialBatchLabel)
    }
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
                    if (!isLabCourse) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (showGroupsInNormalClass) PrimaryBlue else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (showGroupsInNormalClass) PrimaryBlue else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable {
                                    showGroupsInNormalClass = !showGroupsInNormalClass
                                    if (!showGroupsInNormalClass) selectedGroup = "ALL"
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (showGroupsInNormalClass) Icons.Default.Check else Icons.Default.PeopleAlt,
                                    contentDescription = null,
                                    tint = if (showGroupsInNormalClass) Color.White else Color(0xFF334155),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Groups",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showGroupsInNormalClass) Color.White else Color(0xFF334155)
                                )
                            }
                        }
                    }
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
                val presentCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.PRESENT }
                val absentCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.ABSENT }
                val leaveCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.LEAVE }
                val unmarkedCount = activeGroupStudents.count { s.marks[it.student_id] == null }
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
                                    "There ${if (unmarkedCount == 1) "is 1 student" else "are $unmarkedCount students"} not marked yet in ${if (isLabCourse && selectedGroup != "ALL") selectedGroup else "roster"}.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "You can mark all remaining students in this group as Absent and submit directly, or review them before submitting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showUnmarkedWarningDialog = false
                                    viewModel.submitAttendance(
                                        batchLabel = batchLabelToSend,
                                        defaultUnmarkedTo = AttendanceStatus.ABSENT,
                                        targetStudentIds = targetGroupIds
                                    )
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
                        val changedStudents = remember(s.marks, s.initialMarks, targetGroupIds) {
                            val pool = if (targetGroupIds != null) s.roster.filter { it.student_id in targetGroupIds } else s.roster
                            pool.mapNotNull { student ->
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
                                    if (isLabCourse) {
                                        val groupDisplay = when (targetHost) {
                                            "G1" -> "Group 1 (G1) only"
                                            "G2" -> "Group 2 (G2) only"
                                            else -> "All Groups (Combined)"
                                        }
                                        Text(
                                            "\u2022 Scope: $groupDisplay",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryBlue
                                        )
                                    }

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
                                        viewModel.submitAttendance(
                                            batchLabel = batchLabelToSend,
                                            targetStudentIds = targetGroupIds
                                        )
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
                                    if (isLabCourse) {
                                        val groupDisplay = when (targetHost) {
                                            "G1" -> "Group 1 only (Group 2 unaffected)"
                                            "G2" -> "Group 2 only (Group 1 unaffected)"
                                            else -> "All Groups (Combined Session)"
                                        }
                                        Text("\u2022 Scope: $groupDisplay", fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                        if (guestStudents.isNotEmpty()) {
                                            Text(
                                                "\u2022 Guest / Makeup: ${guestStudents.size} student(s) from other group included",
                                                color = Color(0xFF7E22CE),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
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
                                        viewModel.submitAttendance(
                                            batchLabel = batchLabelToSend,
                                            targetStudentIds = targetGroupIds
                                        )
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
                val totalStudents = activeGroupStudents.size
                val presentCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.PRESENT }
                val absentCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.ABSENT }
                val leaveCount = activeGroupStudents.count { s.marks[it.student_id] == AttendanceStatus.LEAVE }
                val unmarkedCount = activeGroupStudents.count { s.marks[it.student_id] == null }

                val candidateStudents = if (search.isNotBlank()) s.roster else activeGroupStudents
                val filteredRoster = candidateStudents.filter { student ->
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

                        if (shouldShowGroups) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val groupTabs = listOf(
                                            Triple("ALL", "All", s.roster.size),
                                            Triple("G1", "Group 1", g1Students.size),
                                            Triple("G2", "Group 2", g2Students.size)
                                        )
                                        groupTabs.forEach { (key, label, count) ->
                                            val isSelected = selectedGroup == key
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (isSelected) PrimaryBlue else Color.Transparent)
                                                    .clickable {
                                                        userManuallyChangedGroup = true
                                                        selectedGroup = key
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$label ($count)",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF475569)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                                onMarkAll = { status -> viewModel.markAll(status, currentTabStudentIds) },
                                onResetAll = { viewModel.markAll(null, currentTabStudentIds) }
                            )
                        }

                        items(filteredRoster, key = { it.student_id }) { student ->
                            val status = s.marks[student.student_id]
                            RosterRow(
                                student = student,
                                status = status,
                                isLabCourse = shouldShowGroups,
                                selectedGroup = selectedGroup,
                                hostGroup = targetHost,
                                onToggleGroup = { nextGroup ->
                                    viewModel.updateStudentGroup(student.student_id, nextGroup)
                                },
                                onSetMark = { targetStatus ->
                                    viewModel.setMark(student.student_id, targetStatus)
                                }
                            )
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
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            subjectCode,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${course?.credits ?: 3} Cr",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (!course?.course_type.isNullOrBlank() && course?.course_type != "LECTURE") {
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                course!!.course_type!!,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    val dur = course?.duration_periods ?: 1
                    if (dur > 1) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$dur Periods",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Course Name + Lab Group Badge in front of Lab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    subjectName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!course?.batch_label.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            course!!.batch_label!!,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
    isLabCourse: Boolean = false,
    selectedGroup: String = "ALL",
    hostGroup: String? = null,
    onToggleGroup: (String) -> Unit = {},
    onSetMark: (AttendanceStatus) -> Unit
) {
    val currentGroup = student.lab_group ?: "G1"
    val effectiveHost = hostGroup ?: if (selectedGroup != "ALL") selectedGroup else null
    val isGuest = isLabCourse && effectiveHost != null && currentGroup != effectiveHost && selectedGroup == effectiveHost
    val isPresent = status == AttendanceStatus.PRESENT
    val isAbsent = !isGuest && status == AttendanceStatus.ABSENT
    val isLeave = !isGuest && status == AttendanceStatus.LEAVE

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            student.roll_number,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                        if (isLabCourse) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isGuest) Color(0xFFF3E8FF) else if (currentGroup == "G1") Color(0xFFEFF6FF) else Color(0xFFFEF3C7),
                                border = BorderStroke(0.5.dp, if (isGuest) Color(0xFFD8B4FE) else if (currentGroup == "G1") Color(0xFF93C5FD) else Color(0xFFFDE68A)),
                                modifier = Modifier.clickable {
                                    val nextGroup = if (currentGroup == "G1") "G2" else "G1"
                                    onToggleGroup(nextGroup)
                                }
                            ) {
                                Text(
                                    text = if (isGuest) "$currentGroup • Guest" else currentGroup,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGuest) Color(0xFF7E22CE) else if (currentGroup == "G1") PrimaryBlue else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
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

                // Absent Button (A) - Disabled for guest students (cannot penalize for an unscheduled lab)
                Surface(
                    onClick = { if (!isGuest) onSetMark(AttendanceStatus.ABSENT) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGuest) Color(0xFFF8FAFC) else if (isAbsent) SemanticRed else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isGuest) Color(0xFFE2E8F0) else if (isAbsent) SemanticRed else Color(0xFFCBD5E1)
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
                            color = if (isGuest) Color(0xFFCBD5E1) else if (isAbsent) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                // Leave Button (L) - Disabled for guest students
                Surface(
                    onClick = { if (!isGuest) onSetMark(AttendanceStatus.LEAVE) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGuest) Color(0xFFF8FAFC) else if (isLeave) SemanticAmber else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isGuest) Color(0xFFE2E8F0) else if (isLeave) SemanticAmber else Color(0xFFCBD5E1)
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
                            color = if (isGuest) Color(0xFFCBD5E1) else if (isLeave) Color.White else Color(0xFF64748B)
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
