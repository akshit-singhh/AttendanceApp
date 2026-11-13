package com.akshit.attendance.ui.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.AttendanceStatus
import com.akshit.attendance.data.model.SessionHistoryResponse
import com.akshit.attendance.data.model.StudentRosterResponse
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.TeacherCacheManager
import com.akshit.attendance.data.repository.TeacherRepository
import com.akshit.attendance.ui.components.AppTopBar
import com.akshit.attendance.ui.components.NoConnectionView
import com.akshit.attendance.ui.components.TeacherSessionCardSkeleton
import com.akshit.attendance.ui.components.TeacherRosterStudentCardSkeleton
import com.akshit.attendance.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BorderSubtle = Color(0xFFE2E8F0)
private val GreenText = Color(0xFF16A34A)
private val GreenBg = Color(0xFFDCFCE7)
private val RedText = Color(0xFFDC2626)
private val RedBg = Color(0xFFFEE2E2)
private val AmberText = Color(0xFFD97706)
private val AmberBg = Color(0xFFFEF3C7)
private val IndigoText = Color(0xFF4338CA)
private val IndigoBg = Color(0xFFEEF2FF)

private fun formatSessionDate(dateStr: String): String {
    return try {
        val parsed = LocalDate.parse(dateStr)
        parsed.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatSessionDateCompact(dateStr: String): String {
    return try {
        val parsed = LocalDate.parse(dateStr)
        parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        dateStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRecordsScreen(
    repo: TeacherRepository,
    onEditSession: (courseOfferingId: Int, sessionDate: String) -> Unit = { _, _ -> },
    onOpenProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var history by remember { mutableStateOf<List<SessionHistoryResponse>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(true) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Selected Session Navigation State
    var selectedSession by remember { mutableStateOf<SessionHistoryResponse?>(null) }
    var roster by remember { mutableStateOf<List<StudentRosterResponse>>(emptyList()) }
    var rosterLoading by remember { mutableStateOf(false) }
    var rosterError by remember { mutableStateOf<String?>(null) }
    var rosterFilter by remember { mutableStateOf("ALL") } // ALL, PRESENT, ABSENT
    var studentSearch by remember { mutableStateOf("") }

    val cachedProfile = remember { TeacherCacheManager.loadProfile() }
    val userInitials = remember(cachedProfile) {
        val rawName = cachedProfile?.full_name?.takeIf { it.isNotBlank() }
        if (rawName == null) null
        else {
            val clean = rawName
                .replace("Prof.", "", ignoreCase = true)
                .replace("Prof", "", ignoreCase = true)
                .replace("Dr.", "", ignoreCase = true)
                .replace("Dr", "", ignoreCase = true)
                .trim()
            clean.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()
                .ifEmpty { "FC" }
        }
    }

    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()
    var isHistoryOfflineNotice by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(!isOnline) }

    // Load History
    val loadHistory: (Boolean) -> Unit = { forceSkeleton ->
        scope.launch {
            historyLoading = true
            historyError = null

            // ── Step 1: Instant cached history if available (unless forcing skeleton on refresh) ──
            if (!forceSkeleton) {
                val cachedHistory = TeacherCacheManager.loadHistory()
                if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                    history = cachedHistory
                    historyLoading = false
                }
            }

            // ── Step 2: Fresh history from network ──
            try {
                val list = repo.getHistory()
                history = list
                TeacherCacheManager.saveHistory(list)
                historyError = null
                isHistoryOfflineNotice = false
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                val isNetworkErr = TeacherCacheManager.isNetworkError(e.message) || !OfflineAttendanceManager.isOnline.value
                if (isNetworkErr) {
                    OfflineAttendanceManager.markOffline()
                    isHistoryOfflineNotice = true
                }
                if (history.isEmpty()) {
                    historyError = if (isNetworkErr) {
                        "No internet connection. Please check your network to load past sessions."
                    } else {
                        e.message ?: "Failed to load session history."
                    }
                }
            } finally {
                historyLoading = false
            }
        }
    }

    // Load Roster for Selected Session
    val loadRosterForSession: (SessionHistoryResponse, Boolean) -> Unit = { session, forceSkeleton ->
        scope.launch {
            rosterLoading = true
            rosterError = null
            rosterFilter = "ALL"
            studentSearch = ""

            // ── Step 1: Instant cached roster if available (unless forcing skeleton on refresh) ──
            if (!forceSkeleton) {
                val cachedRoster = TeacherCacheManager.loadRoster(session.course_offering_id)
                if (cachedRoster != null && cachedRoster.isNotEmpty()) {
                    roster = cachedRoster
                    rosterLoading = false
                }
            }
            try {
                val fresh = repo.getRoster(session.course_offering_id, date = session.date)
                roster = fresh
                TeacherCacheManager.saveRoster(session.course_offering_id, fresh)
                rosterError = null
                OfflineAttendanceManager.markOnline()
            } catch (e: Exception) {
                val isNetworkErr = TeacherCacheManager.isNetworkError(e.message) || !OfflineAttendanceManager.isOnline.value
                if (isNetworkErr) {
                    OfflineAttendanceManager.markOffline()
                }
                if (roster.isEmpty()) {
                    rosterError = if (isNetworkErr) {
                        "No internet connection. Unable to load student roster."
                    } else {
                        e.message ?: "Failed to load class roster for this session."
                    }
                }
            } finally {
                rosterLoading = false
            }
        }
    }

    // Auto-refresh with skeleton animation when internet connectivity is restored
    LaunchedEffect(isOnline) {
        if (isOnline) {
            val animateSkeleton = wasOffline
            loadHistory(animateSkeleton)
            val current = selectedSession
            if (current != null) {
                loadRosterForSession(current, animateSkeleton)
            }
            wasOffline = false
        } else {
            wasOffline = true
        }
    }

    LaunchedEffect(Unit) {
        loadHistory(false)
    }

    LaunchedEffect(selectedSession) {
        val session = selectedSession ?: return@LaunchedEffect
        loadRosterForSession(session, false)
    }

    // Intercept Back Press when viewing a session
    BackHandler(enabled = selectedSession != null) {
        selectedSession = null
    }

    // Filtered Sessions List
    val filteredSessions = remember(history, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) history
        else history.filter {
            it.subject_name.lowercase().contains(q) ||
            it.section_name.lowercase().contains(q) ||
            it.date.contains(q)
        }
    }

    // Filtered Roster
    val filteredRoster = remember(roster, rosterFilter, studentSearch) {
        val query = studentSearch.trim().lowercase()
        roster.filter { student ->
            val matchesFilter = when (rosterFilter) {
                "PRESENT" -> student.status == AttendanceStatus.PRESENT
                "ABSENT" -> student.status == AttendanceStatus.ABSENT
                else -> true
            }
            val matchesQuery = if (query.isBlank()) true else {
                student.full_name.lowercase().contains(query) ||
                student.roll_number.lowercase().contains(query)
            }
            matchesFilter && matchesQuery
        }
    }

    val totalStudents = roster.size
    val presentCount = remember(roster) { roster.count { it.status == AttendanceStatus.PRESENT } }
    val absentCount = remember(roster) { roster.count { it.status == AttendanceStatus.ABSENT } }
    val ratePct = if (totalStudents > 0) (presentCount * 100) / totalStudents else 0

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (selectedSession == null) {
                AppTopBar(
                    title = "Attendance Records",
                    onLogout = onLogout,
                    onOpenProfile = onOpenProfile,
                    onNotificationsClick = onNavigateToNotifications,
                    userInitials = userInitials
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                selectedSession!!.subject_name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${selectedSession!!.section_name} • ${formatSessionDateCompact(selectedSession!!.date)}",
                                fontSize = 12.sp,
                                color = TextGray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedSession = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedSession == null) {
                // ==========================================
                // VIEW 1: Past Sessions List
                // ==========================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Card
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryBlue.copy(alpha = 0.08f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.History,
                                                contentDescription = null,
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            "Submitted Class Sessions",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextDark
                                        )
                                        Text(
                                            "Select a class session to review or edit attendance",
                                            fontSize = 11.sp,
                                            color = TextGray
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                // Search Field
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search by subject or section…", fontSize = 12.sp, color = TextGray) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedBorderColor = PrimaryBlue
                                    )
                                )
                            }
                        }
                    }

                    // Warning banner if offline but cached sessions exist
                    if (isHistoryOfflineNotice && history.isNotEmpty()) {
                        item {
                            Surface(
                                color = Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.CloudOff,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "No Internet Connection",
                                                color = Color(0xFF92400E),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Showing saved sessions. Reconnect to get latest data.",
                                                color = Color(0xFFB45309),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { loadHistory(true) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF92400E)),
                                        border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (historyLoading) {
                        items(5) {
                            TeacherSessionCardSkeleton()
                        }
                    } else if (historyError != null && history.isEmpty()) {
                        item {
                            val isOffline = TeacherCacheManager.isNetworkError(historyError) || !isOnline
                            if (isOffline) {
                                NoConnectionView(
                                    message = "No internet connection",
                                    subMessage = "Connect to view past attendance records.",
                                    onRetry = { loadHistory(true) }
                                )
                            } else {
                                val bg = RedBg
                                val border = RedText.copy(alpha = 0.3f)
                                val txt = RedText
                                val icon = Icons.Outlined.ErrorOutline
                                val message = historyError!!

                                Surface(
                                    color = bg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, tint = txt, modifier = Modifier.size(20.dp))
                                        Text(message, color = txt, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { loadHistory(true) }) {
                                            Text("Retry", fontWeight = FontWeight.Bold, color = txt)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (filteredSessions.isEmpty()) {
                        item {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryBlue.copy(alpha = 0.08f),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.EventBusy,
                                                contentDescription = null,
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        if (searchQuery.isNotBlank()) "No Matching Sessions" else "No Sessions Recorded Yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (searchQuery.isNotBlank()) "Try clearing your search query"
                                        else "Classes marked in Roll Call will appear here automatically",
                                        fontSize = 12.sp,
                                        color = TextGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredSessions) { session ->
                            SessionCard(
                                session = session,
                                onClick = { selectedSession = session }
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // VIEW 2: Selected Session Roster & Details
                // ==========================================
                val currentSession = selectedSession!!

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Session KPI Summary Card
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    formatSessionDate(currentSession.date),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDark
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Section ${currentSession.section_name} • ${currentSession.subject_name}",
                                    fontSize = 12.sp,
                                    color = TextGray
                                )

                                Spacer(Modifier.height(14.dp))

                                // KPI Metric Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricChip(
                                        label = "Total",
                                        value = "$totalStudents",
                                        bgColor = Color(0xFFF1F5F9),
                                        textColor = Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        label = "Present",
                                        value = "$presentCount",
                                        bgColor = GreenBg,
                                        textColor = GreenText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        label = "Absent",
                                        value = "$absentCount",
                                        bgColor = RedBg,
                                        textColor = RedText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        label = "Rate",
                                        value = "$ratePct%",
                                        bgColor = IndigoBg,
                                        textColor = IndigoText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        onEditSession(currentSession.course_offering_id, currentSession.date)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit Attendance", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Search & Filters Row
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = studentSearch,
                                onValueChange = { studentSearch = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Filter by student name or roll…", fontSize = 12.sp, color = TextGray) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedBorderColor = PrimaryBlue
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = rosterFilter == "ALL",
                                    onClick = { rosterFilter = "ALL" },
                                    label = { Text("All ($totalStudents)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = rosterFilter == "PRESENT",
                                    onClick = { rosterFilter = "PRESENT" },
                                    label = { Text("Present ($presentCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GreenText,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = rosterFilter == "ABSENT",
                                    onClick = { rosterFilter = "ABSENT" },
                                    label = { Text("Absent ($absentCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RedText,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (rosterLoading) {
                        items(6) {
                            TeacherRosterStudentCardSkeleton()
                        }
                    } else if (rosterError != null && roster.isEmpty()) {
                        item {
                            val isOffline = TeacherCacheManager.isNetworkError(rosterError) || !isOnline
                            if (isOffline) {
                                NoConnectionView(
                                    message = "No internet connection",
                                    subMessage = "Connect to view full session roster.",
                                    onRetry = { loadRosterForSession(currentSession, true) }
                                )
                            } else {
                                val bg = RedBg
                                val border = RedText.copy(alpha = 0.3f)
                                val txt = RedText
                                val icon = Icons.Outlined.ErrorOutline
                                val message = rosterError!!
                                Surface(
                                    color = bg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, tint = txt, modifier = Modifier.size(20.dp))
                                        Text(
                                            message,
                                            color = txt,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { loadRosterForSession(currentSession, true) }) {
                                            Text("Retry", fontWeight = FontWeight.Bold, color = txt)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (filteredRoster.isEmpty()) {
                        item {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            ) {
                                Text(
                                    "No students found matching your criteria",
                                    color = TextGray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredRoster, key = { it.student_id }) { student ->
                            RosterStudentCard(
                                student = student
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionHistoryResponse,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        session.subject_name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            session.section_name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text("•", fontSize = 11.sp, color = TextGray)

                    Text(
                        formatSessionDateCompact(session.date),
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = CircleShape,
                color = GreenBg,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Completed",
                        tint = GreenText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = textColor)
            Spacer(Modifier.height(1.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun RosterStudentCard(
    student: StudentRosterResponse
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    student.full_name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Roll No: ${student.roll_number}",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }

            Spacer(Modifier.width(8.dp))

            StatusPill(status = student.status)
        }
    }
}

@Composable
private fun StatusPill(status: AttendanceStatus?) {
    val (bg, txt, icon, label) = when (status) {
        AttendanceStatus.PRESENT -> Quadruple(GreenBg, GreenText, Icons.Outlined.CheckCircle, "Present")
        AttendanceStatus.ABSENT -> Quadruple(RedBg, RedText, Icons.Outlined.Cancel, "Absent")
        AttendanceStatus.LEAVE -> Quadruple(AmberBg, AmberText, Icons.Outlined.EventBusy, "On Leave")
        else -> Quadruple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.AutoMirrored.Outlined.HelpOutline, "Unrecorded")
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = txt, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txt)
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
