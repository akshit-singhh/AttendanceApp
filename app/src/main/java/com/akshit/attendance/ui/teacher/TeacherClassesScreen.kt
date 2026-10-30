package com.akshit.attendance.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.akshit.attendance.data.model.CourseScheduleResponse
import com.akshit.attendance.data.model.PendingClassResponse
import com.akshit.attendance.ui.components.AppTopBar
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// --- Semantic Colors Matching the Image ---
private val PrimaryBlue = Color(0xFF002D62)
private val LightBlueBg = Color(0xFFE8F0FE)
private val SemanticGreen = Color(0xFF00C853)
private val SemanticYellow = Color(0xFFFFD54F)
private val SemanticRed = Color(0xFFE53935)
private val SurfaceLight = Color(0xFFF5F7FA)
private val TextGray = Color(0xFF545F71)

enum class ClassState { SUBMITTED, ACTIVE, MISSED, UPCOMING }

@Composable
fun TeacherClassesScreen(
    viewModel: TeacherViewModel,
    onStartRollCall: (Int, String?) -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Today's Classes, 1 = Pending Attendance
    var postponeTarget by remember { mutableStateOf<PostponeTarget?>(null) }
    var showExtraClassDialog by remember { mutableStateOf(false) }
    var allCourses by remember { mutableStateOf<List<CourseScheduleResponse>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadSchedule()
                viewModel.loadPendingClasses()
                currentTime = LocalTime.now()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSchedule()
        viewModel.loadPendingClasses()
        while (true) {
            delay(15_000L) // Refresh every 15 seconds to update real-time class states
            currentTime = LocalTime.now()
        }
    }

    val state by viewModel.schedule.collectAsState()
    val pendingState by viewModel.pending.collectAsState()

    val pendingCount = if (pendingState is PendingUiState.Loaded) {
        (pendingState as PendingUiState.Loaded).pendingClasses.size
    } else 0

    val profile by viewModel.profile.collectAsState()

    val currentHour = remember { LocalTime.now().hour }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val rawName = profile?.full_name?.takeIf { it.isNotBlank() }
        ?: (state as? ScheduleUiState.Loaded)?.teacherName?.takeIf { it.isNotBlank() }
        ?: "Faculty"
    val displayName = if (rawName.startsWith("Prof", ignoreCase = true) || rawName.startsWith("Dr", ignoreCase = true)) {
        rawName
    } else {
        "Prof. $rawName"
    }

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

    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    val deptText = profile?.department ?: profile?.school
    val subtitleMeta = if (!deptText.isNullOrBlank()) "Faculty • $deptText" else "Faculty • $currentDate"

    Scaffold(
        containerColor = com.akshit.attendance.ui.theme.SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                color = com.akshit.attendance.ui.theme.SurfaceBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$greeting,",
                            fontSize = 12.sp,
                            color = TextGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitleMeta,
                            fontSize = 11.sp,
                            color = TextGray,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { /* notifications */ }) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color(0xFF0F172A)
                            )
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
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val s = state) {
            is ScheduleUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            is ScheduleUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadSchedule() }) { Text("Retry") }
                }
            }
            is ScheduleUiState.Loaded -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    DashboardTabSelector(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        todayCount = s.classes.size,
                        pendingCount = pendingCount
                    )
                }

                if (selectedTab == 0) {
                    val completedCount = s.classes.count { s.submittedOfferingIds.contains(it.course_offering_id) }
                    item { DailyProgressSection(completedCount, s.classes.size) }

                    item {
                        SectionHeader(
                            count = s.classes.size,
                            onExtraClassClick = {
                                scope.launch {
                                    allCourses = viewModel.getAllCourses()
                                    showExtraClassDialog = true
                                }
                            }
                        )
                    }

                    if (s.classes.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("No classes scheduled for today.", color = TextGray, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        items(s.classes) { course ->
                            val isSubmitted = s.submittedOfferingIds.contains(course.course_offering_id)
                            val currentState = determineClassState(currentTime, course.start_time, course.end_time, isSubmitted)
                            ClassCard(
                                course = course,
                                state = currentState,
                                onPostpone = { offId, dt, subName, secName ->
                                    postponeTarget = PostponeTarget(offId, subName, secName, dt)
                                },
                                onStartRollCall = { offeringId ->
                                    onStartRollCall(offeringId, null)
                                },
                                onViewDetails = { offeringId ->
                                    onStartRollCall(offeringId, LocalDate.now().toString())
                                }
                            )
                        }
                    }
                } else {
                    item { PendingPolicyCard() }

                    when (val pState = pendingState) {
                        is PendingUiState.Loading -> item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(32.dp))
                            }
                        }
                        is PendingUiState.Error -> item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF1F2),
                                border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(pState.message, color = SemanticRed, fontSize = 13.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { viewModel.loadPendingClasses() }) { Text("Retry") }
                                }
                            }
                        }
                        is PendingUiState.Loaded -> {
                            if (pState.pendingClasses.isEmpty()) {
                                item { AllCaughtUpCard() }
                            } else {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Unmarked Past Sessions",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "${pState.pendingClasses.size} pending",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }

                                items(pState.pendingClasses) { item ->
                                    PendingClassCard(
                                        item = item,
                                        onStartRollCall = onStartRollCall,
                                        onPostpone = { offId, dt, subName, secName ->
                                            postponeTarget = PostponeTarget(offId, subName, secName, dt)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    postponeTarget?.let { target ->
        PostponeClassDialog(
            target = target,
            onDismiss = { postponeTarget = null },
            onConfirm = { reason ->
                postponeTarget = null
                scope.launch {
                    viewModel.postponeClass(target.offeringId, target.date, reason) { success, msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Class postponed. Students notified via push alert."
                                else (msg ?: "Failed to postpone class.")
                            )
                        }
                    }
                }
            }
        )
    }

    if (showExtraClassDialog) {
        ExtraClassDialog(
            courses = allCourses,
            onDismiss = { showExtraClassDialog = false },
            onStartExtraClass = { offeringId, date ->
                showExtraClassDialog = false
                onStartRollCall(offeringId, date)
            }
        )
    }
}

// --- Dynamic Time Logic ---
private fun determineClassState(
    now: LocalTime,
    startTimeStr: String,
    endTimeStr: String,
    isAlreadySubmitted: Boolean
): ClassState {
    if (isAlreadySubmitted) return ClassState.SUBMITTED
    return try {
        val start = safeParseLocalTime(startTimeStr) ?: return ClassState.ACTIVE
        val end = safeParseLocalTime(endTimeStr) ?: return ClassState.ACTIVE

        when {
            now.isBefore(start) -> ClassState.UPCOMING
            now.isAfter(end) -> ClassState.MISSED
            else -> ClassState.ACTIVE
        }
    } catch (_: Exception) {
        ClassState.ACTIVE
    }
}

private fun safeParseLocalTime(timeStr: String): LocalTime? {
    val clean = timeStr.trim()
    return try {
        LocalTime.parse(clean)
    } catch (_: Exception) {
        try {
            LocalTime.parse(clean, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        } catch (_: Exception) {
            try {
                LocalTime.parse(clean, DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
            } catch (_: Exception) {
                try {
                    LocalTime.parse(clean, DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH))
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

private fun formatTimeBlock(start: String, end: String): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
        val s = (safeParseLocalTime(start) ?: LocalTime.parse(start)).format(formatter)
        val e = (safeParseLocalTime(end) ?: LocalTime.parse(end)).format(formatter)
        "$s – $e"
    } catch (_: Exception) {
        "$start – $end"
    }
}

private fun getTimeUntilStart(now: LocalTime, startTimeStr: String): String {
    return try {
        val start = safeParseLocalTime(startTimeStr) ?: LocalTime.parse(startTimeStr)
        val minutesBetween = ChronoUnit.MINUTES.between(now, start)
        if (minutesBetween <= 0) return "Starting soon"
        val hours = minutesBetween / 60
        val mins = minutesBetween % 60
        if (hours > 0) "Starts in ${hours}h ${mins}m" else "Starts in ${mins}m"
    } catch (_: Exception) {
        "Upcoming"
    }
}

@Composable
private fun TeacherProfileHeader(teacherName: String?) {
    val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy"))
    val displayName = if (!teacherName.isNullOrBlank()) teacherName else "Faculty"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(32.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(SemanticGreen)
                        .border(2.dp, SurfaceLight, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Welcome,", style = MaterialTheme.typography.titleMedium, color = TextGray)
                Text(
                    text = if (displayName.startsWith("Prof", ignoreCase = true) || displayName.startsWith("Dr", ignoreCase = true)) displayName else "Prof. $displayName",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(currentDate, style = MaterialTheme.typography.labelMedium, color = TextGray)
                }
            }
        }
    }
}

@Composable
private fun DailyProgressSection(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val progressPercent = (progress * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Daily Teaching Progress", style = MaterialTheme.typography.titleSmall, color = TextGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PieChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$progressPercent% Completed", style = MaterialTheme.typography.labelMedium, color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = PrimaryBlue,
            trackColor = Color.LightGray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircleOutline,
                iconBg = LightBlueBg,
                iconTint = PrimaryBlue,
                title = "$completed of $total",
                subtitle = "Conducted"
            )
            ProgressStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PeopleAlt,
                iconBg = Color(0xFFFFF9C4),
                iconTint = Color(0xFFF57F17),
                title = "184", // Replace with actual expected student count logic if needed
                subtitle = "Expected Today"
            )
        }
    }
}

@Composable
private fun ProgressStatCard(modifier: Modifier = Modifier, icon: ImageVector, iconBg: Color, iconTint: Color, title: String, subtitle: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextGray)
            }
        }
    }
}

@Composable
private fun SectionHeader(count: Int, onExtraClassClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today's Classes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.LightGray.copy(alpha = 0.4f)
            ) {
                Text(
                    "$count",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onExtraClassClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Extra Class",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ClassCard(
    course: CourseScheduleResponse,
    state: ClassState,
    onPostpone: (Int, String, String, String) -> Unit,
    onStartRollCall: (Int) -> Unit,
    onViewDetails: (Int) -> Unit = {}
) {
    val indicatorColor = when (state) {
        ClassState.SUBMITTED -> SemanticGreen
        ClassState.ACTIVE -> SemanticYellow
        ClassState.MISSED -> SemanticRed
        ClassState.UPCOMING -> Color.LightGray.copy(alpha = 0.8f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left Status Indicator Strip
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(indicatorColor))

            Column(Modifier.padding(16.dp)) {
                // Top Row: Code, Section & Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = LightBlueBg) {
                            Text(
                                course.subject_code,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9)) {
                            Text(
                                "${course.credits ?: 3} Cr",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (course.course_type != null && course.course_type != "LECTURE") {
                            Spacer(modifier = Modifier.width(4.dp))
                            val isLab = course.course_type.contains("LAB", ignoreCase = true)
                            Surface(shape = RoundedCornerShape(6.dp), color = if (isLab) Color(0xFFECFDF5) else Color(0xFFFAF5FF)) {
                                Text(
                                    course.course_type,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLab) Color(0xFF047857) else Color(0xFF7E22CE),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Sec ${course.section_name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextGray,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    when (state) {
                        ClassState.SUBMITTED -> StatusBadge("Submitted", SemanticGreen, Color.White, Icons.Default.CheckCircle)
                        ClassState.ACTIVE -> StatusBadge("Action Req", SemanticYellow, Color(0xFF424242), Icons.Default.AssignmentLate)
                        ClassState.MISSED -> StatusBadge("Overdue", Color(0xFFFFEBEE), SemanticRed, Icons.Default.Warning)
                        ClassState.UPCOMING -> StatusBadge("Upcoming", SurfaceLight, TextGray, Icons.Default.Schedule)
                    }
                }

                if (state == ClassState.ACTIVE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = SemanticRed.copy(alpha = 0.1f)) {
                        Text("Now Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = SemanticRed, fontWeight = FontWeight.Bold)
                    }
                } else if (state == ClassState.MISSED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFF3E0)) {
                        Text("Class Ended • Unmarked", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFD84315), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(course.subject_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Time & Status Text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextGray)
                    Spacer(modifier = Modifier.width(6.dp))

                    val statusText = when (state) {
                        ClassState.SUBMITTED -> "Completed"
                        ClassState.ACTIVE -> "In Progress"
                        ClassState.MISSED -> "Ended (Not Marked)"
                        ClassState.UPCOMING -> getTimeUntilStart(LocalTime.now(), course.start_time)
                    }
                    val statusColor = when (state) {
                        ClassState.SUBMITTED -> SemanticGreen
                        ClassState.ACTIVE -> Color(0xFF8D6E63) // Brownish for active progress
                        ClassState.MISSED -> SemanticRed
                        ClassState.UPCOMING -> TextGray
                    }
                    Text(formatTimeBlock(course.start_time, course.end_time), style = MaterialTheme.typography.bodySmall, color = TextGray)
                    Text("  •  ", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Room
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextGray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(course.room ?: "Room 302, Engineering Block", style = MaterialTheme.typography.bodySmall, color = TextGray) // Fallback for UI matching
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceLight)
                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Bottom Actions
                when (state) {
                    ClassState.SUBMITTED -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp), tint = SemanticGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Attendance Recorded", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceLight,
                                modifier = Modifier.clickable { onViewDetails(course.course_offering_id) }
                            ) {
                                Text(
                                    "View Details >",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    ClassState.ACTIVE, ClassState.MISSED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onStartRollCall(course.course_offering_id) },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    if (state == ClassState.MISSED) Icons.Default.EditNote else Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (state == ClassState.MISSED) "Mark Attendance" else "Start Roll Call",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onPostpone(
                                        course.course_offering_id,
                                        LocalDate.now().toString(),
                                        course.subject_name,
                                        course.section_name
                                    )
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Outlined.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Postpone",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    ClassState.UPCOMING -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PeopleAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextGray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scheduled Session", style = MaterialTheme.typography.labelMedium, color = TextGray)
                            }
                            OutlinedButton(
                                onClick = {
                                    onPostpone(
                                        course.course_offering_id,
                                        LocalDate.now().toString(),
                                        course.subject_name,
                                        course.section_name
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Outlined.EventBusy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Postpone",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
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
private fun StatusBadge(text: String, bgColor: Color, textColor: Color, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(16.dp), color = bgColor) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = textColor)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DashboardTabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    todayCount: Int,
    pendingCount: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE2E8F0),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            // Tab 0: Today's Classes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 0) Color.White else Color.Transparent)
                    .clickable { onTabSelected(0) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = if (selectedTab == 0) PrimaryBlue else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Today ($todayCount)",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (selectedTab == 0) PrimaryBlue else Color(0xFF64748B)
                    )
                }
            }

            // Tab 1: Pending Attendance
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 1) Color.White else Color.Transparent)
                    .clickable { onTabSelected(1) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = if (selectedTab == 1) Color(0xFFB45309) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pending",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (selectedTab == 1) Color(0xFFB45309) else Color(0xFF64748B)
                    )
                    if (pendingCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (selectedTab == 1) Color(0xFFDC2626) else Color(0xFFEA580C)
                        ) {
                            Text(
                                text = "$pendingCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPolicyCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "15-Day Attendance Grace Window",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Classes conducted within the last 15 days can be marked here anytime. After 15 days, retroactive marking is permanently locked.",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun AllCaughtUpCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = SemanticGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "All Attendance Up to Date!",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You have no unsubmitted class sessions from the last 15 days.",
                fontSize = 12.sp,
                color = TextGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PendingClassCard(
    item: PendingClassResponse,
    onStartRollCall: (Int, String?) -> Unit,
    onPostpone: (Int, String, String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (item.is_expiring_soon) Color(0xFFFCA5A5) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Course Code, Course Name + Expiration Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.subject_code,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = item.subject_name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.is_expiring_soon) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (item.is_expiring_soon) "⚠️ ${item.days_remaining}d left" else "⏳ ${item.days_remaining}d left",
                        color = if (item.is_expiring_soon) Color(0xFFB91C1C) else Color(0xFFB45309),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Date & Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${item.formatted_date} (${item.day_name})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "Sec: ${item.section_name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Time Slot & Room
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val startDisp = if (item.start_time.length >= 5) item.start_time.substring(0, 5) else item.start_time
                    val endDisp = if (item.end_time.length >= 5) item.end_time.substring(0, 5) else item.end_time
                    Text(
                        text = "$startDisp - $endDisp",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.room ?: "TBA",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Mark Past Attendance & Postpone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStartRollCall(item.course_offering_id, item.date) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mark Past",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        onPostpone(
                            item.course_offering_id,
                            item.date,
                            item.subject_name,
                            item.section_name
                        )
                    },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EventBusy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Postpone",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class PostponeTarget(
    val offeringId: Int,
    val subjectName: String,
    val sectionName: String,
    val date: String
)

@Composable
private fun PostponeClassDialog(
    target: PostponeTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("Faculty on Leave") }
    val quickReasons = listOf(
        "Faculty on Leave",
        "Campus Event / Fest",
        "Official Duty",
        "Inclement Weather",
        "Holiday"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EventBusy,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Postpone Class",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = target.subjectName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF92400E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sec: ${target.sectionName} • Date: ${target.date}",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    "Quick select or type reason:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextGray
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickReasons.forEach { qReason ->
                        val isSelected = reason == qReason
                        FilterChip(
                            selected = isSelected,
                            onClick = { reason = qReason },
                            label = { Text(qReason, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFD97706),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Postponement") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Text(
                    "Students will be notified via push notification and this class won't count against their attendance.",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Confirm Postpone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Cancel", color = TextGray, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun ExtraClassDialog(
    courses: List<CourseScheduleResponse>,
    onDismiss: () -> Unit,
    onStartExtraClass: (Int, String) -> Unit
) {
    var selectedCourseId by remember(courses) {
        mutableStateOf(courses.firstOrNull()?.course_offering_id ?: 0)
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Conduct Extra / Make-up Class",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Select course and date to conduct an unscheduled or make-up attendance session:",
                    fontSize = 12.sp,
                    color = TextGray
                )

                if (courses.isEmpty()) {
                    Text("No course offerings found.", fontSize = 13.sp, color = SemanticRed)
                } else {
                    Text("Course:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        courses.forEach { course ->
                            val isSelected = course.course_offering_id == selectedCourseId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) LightBlueBg else Color(0xFFF1F5F9),
                                border = if (isSelected) BorderStroke(1.5.dp, PrimaryBlue) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCourseId = course.course_offering_id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            course.subject_name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) PrimaryBlue else Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${course.subject_code} • Section: ${course.section_name}",
                                            fontSize = 11.sp,
                                            color = TextGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text("Date (YYYY-MM-DD):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    OutlinedTextField(
                        value = selectedDate,
                        onValueChange = { selectedDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextGray)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCourseId > 0 && selectedDate.isNotBlank()) {
                        onStartExtraClass(selectedCourseId, selectedDate)
                    }
                },
                enabled = selectedCourseId > 0 && selectedDate.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Start Attendance", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Cancel", color = TextGray, fontSize = 13.sp)
            }
        }
    )
}