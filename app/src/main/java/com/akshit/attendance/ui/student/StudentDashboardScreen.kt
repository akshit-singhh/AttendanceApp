package com.akshit.attendance.ui.student

import androidx.compose.ui.text.style.TextOverflow



import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ExitToApp

import androidx.compose.material.icons.filled.*

import androidx.compose.material.icons.outlined.*

import androidx.compose.material3.*

import androidx.compose.material3.pulltorefresh.PullToRefreshContainer

import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.akshit.attendance.ui.components.PullToRefreshExpressiveIndicator
import com.akshit.attendance.notification.AttendanceNotificationManager
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.StudentCacheManager

import androidx.compose.runtime.*

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.ui.tooling.preview.Preview

import com.akshit.attendance.data.model.StudentDashboardResponse

import com.akshit.attendance.data.model.StudentScheduleSlot

import com.akshit.attendance.data.model.SubjectAttendanceHistory

import com.akshit.attendance.data.model.UserProfileResponse

import com.akshit.attendance.ui.theme.AttendanceAppTheme

import com.akshit.attendance.ui.components.DashboardHeroSkeleton

import com.akshit.attendance.ui.components.ScheduleCardSkeleton
import com.akshit.attendance.util.AppDateProvider
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat

import java.util.*



val PrimaryBlue = Color(0xFF1E3A8A)

val AccentPurple = Color(0xFF6366F1)

val PrimaryGreen = Color(0xFF10B981)

val LightGreenBg = Color(0xFFECFDF5)

val AlertRed = Color(0xFFEF4444)

val LightRedBg = Color(0xFFFEF2F2)

val WarningAmber = Color(0xFFF59E0B)

val LightAmberBg = Color(0xFFFFFBEB)

val SurfaceBg = Color(0xFFF8FAFC)

val CardBorder = Color(0xFFE2E8F0)

val TextDark = Color(0xFF0F172A)

val TextGray = Color(0xFF64748B)



@Composable

fun StudentDashboardScreen(

    viewModel: StudentViewModel,

    onLogout: () -> Unit,

    onOpenProfile: () -> Unit = {},

    onNavigateToSubjects: () -> Unit = {},

    onNavigateToPlanner: () -> Unit = {},

    onNavigateToCalendar: () -> Unit = {},

    onNavigateToStatistics: () -> Unit = {},

    onNavigateToTimetable: () -> Unit = {},

    onNavigateToNotifications: () -> Unit = {},

    onNavigateToDocuments: () -> Unit = {}

) {

    val profile by viewModel.profile.collectAsState()

    val dashboardState by viewModel.dashboard.collectAsState()

    val schedule by viewModel.schedule.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()
    var wasOffline by remember { mutableStateOf(!isOnline) }
    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            viewModel.refreshAll()
            wasOffline = false
        } else if (!isOnline) {
            wasOffline = true
        }
    }

    val context = LocalContext.current
    LaunchedEffect(profile?.roll_number) {
        val roll = profile?.roll_number
        if (!roll.isNullOrBlank()) {
            AttendanceNotificationManager.startListening(context, roll)
        }
    }



    StudentDashboardContent(

        profile = profile,

        dashboardState = dashboardState,

        schedule = schedule,

        isRefreshing = isRefreshing,

        onRefresh = { viewModel.refreshAll() },

        getBunkAdvice = { attended, held, target -> viewModel.getBunkAdvice(attended, held, target) },

        onLogout = onLogout,

        onOpenProfile = onOpenProfile,

        onNavigateToSubjects = onNavigateToSubjects,

        onNavigateToPlanner = onNavigateToPlanner,

        onNavigateToCalendar = onNavigateToCalendar,

        onNavigateToStatistics = onNavigateToStatistics,

        onNavigateToTimetable = onNavigateToTimetable,

        onNavigateToNotifications = onNavigateToNotifications,

        onNavigateToDocuments = onNavigateToDocuments

    )

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun StudentDashboardContent(

    profile: UserProfileResponse?,

    dashboardState: DashboardUiState,

    schedule: List<StudentScheduleSlot>,

    isRefreshing: Boolean,

    onRefresh: () -> Unit,

    getBunkAdvice: (Int, Int, Double) -> BunkAdvice,

    onLogout: () -> Unit,

    onOpenProfile: () -> Unit = {},

    onNavigateToSubjects: () -> Unit = {},

    onNavigateToPlanner: () -> Unit = {},

    onNavigateToCalendar: () -> Unit = {},

    onNavigateToStatistics: () -> Unit = {},

    onNavigateToTimetable: () -> Unit = {},

    onNavigateToNotifications: () -> Unit = {},

    onNavigateToDocuments: () -> Unit = {}

) {

    var showLogoutDialog by remember { mutableStateOf(false) }
    val notifications by AttendanceNotificationManager.notifications.collectAsState()
    val unreadNotificationsCount = notifications.count { !it.isRead }
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()



    val effectiveToday = AppDateProvider.today

    val fullDateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH) }

    val todayShortDay = remember(effectiveToday) {
        effectiveToday.dayOfWeek.name.take(3).uppercase()
    }

    val todayDateStr = remember(effectiveToday) {
        effectiveToday.format(fullDateFormat)
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Welcome back"
        }
    }

    val targetDay = todayShortDay

    val todaySlots = remember(schedule, targetDay) {
        schedule.filter { it.day_of_week.equals(targetDay, ignoreCase = true) }

            .sortedBy { parseTimeToMinutes(it.start_time) }

    }



    if (showLogoutDialog) {

        AlertDialog(

            onDismissRequest = { showLogoutDialog = false },

            title = { Text("Log Out") },

            text = { Text("Are you sure you want to log out of your student account?") },

            confirmButton = {

                TextButton(

                    onClick = {

                        showLogoutDialog = false

                        onLogout()

                    }

                ) {

                    Text("Log Out", color = MaterialTheme.colorScheme.error)

                }

            },

            dismissButton = {

                TextButton(onClick = { showLogoutDialog = false }) {

                    Text("Cancel")

                }

            }

        )

    }



    Scaffold(

        containerColor = SurfaceBg,

        contentWindowInsets = WindowInsets(0.dp),

        topBar = {

            Surface(

                color = SurfaceBg,

                modifier = Modifier

                    .fillMaxWidth()

                    .statusBarsPadding()

            ) {

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),

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

                            profile?.full_name ?: "Student",

                            fontSize = 18.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = TextDark,

                            maxLines = 1,

                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis

                        )

                        val rollText = profile?.roll_number?.let { "Roll: $it" } ?: ""

                        val progText = profile?.program_name ?: ""

                        val meta = listOfNotNull(rollText.ifEmpty { null }, progText.ifEmpty { null }).joinToString(" • ")

                        if (meta.isNotEmpty()) {

                            Spacer(Modifier.height(2.dp))

                            Text(

                                meta,

                                fontSize = 11.sp,

                                color = TextGray,

                                fontWeight = FontWeight.Medium,

                                maxLines = 1,

                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis

                            )

                        }

                    }

                    Spacer(Modifier.width(8.dp))

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(4.dp)

                    ) {

                        IconButton(onClick = onNavigateToNotifications) {

                            Box {

                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = TextDark)

                                if (unreadNotificationsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(AlertRed, CircleShape)
                                            .align(Alignment.TopEnd)
                                    )
                                }

                            }

                        }

                        IconButton(onClick = onOpenProfile) {

                            Surface(

                                shape = CircleShape,

                                color = PrimaryBlue,

                                modifier = Modifier.size(34.dp)

                            ) {

                                Box(contentAlignment = Alignment.Center) {

                                    val initials = profile?.full_name?.split(" ")

                                        ?.mapNotNull { it.firstOrNull()?.toString() }

                                        ?.take(2)?.joinToString("") ?: "ST"

                                    Text(

                                        initials.ifEmpty { "ST" },

                                        fontWeight = FontWeight.Bold,

                                        color = Color.White,

                                        fontSize = 12.sp

                                    )

                                }

                            }

                        }

                    }

                }

            }

        }

    ) { padding ->

        val pullToRefreshState = rememberPullToRefreshState()

        LaunchedEffect(pullToRefreshState.isRefreshing) {
            if (pullToRefreshState.isRefreshing) {
                onRefresh()
            }
        }

        LaunchedEffect(isRefreshing) {
            if (!isRefreshing) {
                pullToRefreshState.endRefresh()
            }
        }

        val showSkeleton = dashboardState is DashboardUiState.Loading



        Box(

            modifier = Modifier

                .fillMaxSize()

                .padding(padding)

                .nestedScroll(pullToRefreshState.nestedScrollConnection)

        ) {

            LazyColumn(

                modifier = Modifier.fillMaxSize(),

                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp)

            ) {

                // 2. Main Overall Attendance Hero Card with Safe Bunk calculation

                item {

                    if (showSkeleton) {

                        DashboardHeroSkeleton()

                    } else {

                        when (val state = dashboardState) {

                            is DashboardUiState.Loading -> {

                                DashboardHeroSkeleton()

                            }

                            is DashboardUiState.Error -> {
                                val isOffline = !isOnline || StudentCacheManager.isNetworkError(state.message)
                                Surface(
                                    color = if (isOffline) Color(0xFFFEF3C7) else LightRedBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isOffline) Color(0xFFD97706) else AlertRed
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            if (isOffline) {
                                                Text("Offline Mode", color = Color(0xFF92400E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text("Showing cached data. Connect to internet to refresh.", color = Color(0xFFB45309), fontSize = 12.sp)
                                            } else {
                                                Text(state.message, color = AlertRed, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            is DashboardUiState.Loaded -> {

                                LiveOverallAttendanceCard(

                                    data = state.data,

                                    getBunkAdvice = getBunkAdvice,

                                    onViewSubjects = onNavigateToSubjects,

                                    onNavigateToStatistics = onNavigateToStatistics

                                )

                            }

                        }

                    }

                }



                // 3. Today's Timetable Section

                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Today's Classes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            val countText = if (todaySlots.isEmpty()) "No Classes Scheduled" else "${todaySlots.size} Scheduled"
                            Text("$todayDateStr • $countText", fontSize = 12.sp, color = TextGray)
                        }
                    }

                }



                if (schedule.isEmpty() && showSkeleton) {
                    items(3) {
                        ScheduleCardSkeleton()
                    }
                } else {

                    if (todaySlots.isEmpty()) {

                            item {

                                Surface(

                                    color = Color.White,

                                    shape = RoundedCornerShape(14.dp),

                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

                                    modifier = Modifier.fillMaxWidth()

                                ) {

                                    Row(

                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),

                                        verticalAlignment = Alignment.CenterVertically

                                    ) {

                                        Box(

                                            modifier = Modifier

                                                .size(36.dp)

                                                .background(Color(0xFFECFDF5), CircleShape),

                                            contentAlignment = Alignment.Center

                                        ) {

                                            Icon(

                                                Icons.Outlined.EventAvailable,

                                                contentDescription = null,

                                                tint = PrimaryGreen,

                                                modifier = Modifier.size(20.dp)

                                            )

                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {

                                            Text(

                                                "No Scheduled Classes Today",

                                                fontWeight = FontWeight.Bold,

                                                color = TextDark,

                                                fontSize = 13.sp

                                            )

                                            Spacer(Modifier.height(2.dp))

                                            Text(

                                                "Enjoy your study break or prepare for upcoming sessions.",

                                                fontSize = 11.sp,

                                                color = TextGray

                                            )

                                        }

                                    }

                                }

                            }

                        } else {

                            items(todaySlots) { slot ->

                                LiveScheduleCard(slot = slot)

                            }

                        }

                    }

                // 5. Weekly Timetable Matrix Card removed as requested

            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                indicator = { state ->
                    PullToRefreshExpressiveIndicator(
                        state = state,
                        color = PrimaryBlue,
                    )
                }
            )

        }

    }

}

private data class ExamComplianceState(
    val bg: Color,
    val border: Color,
    val textColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)

@Composable

fun LiveOverallAttendanceCard(

    data: com.akshit.attendance.data.model.StudentDashboardResponse,

    getBunkAdvice: (Int, Int, Double) -> BunkAdvice,

    onViewSubjects: () -> Unit,

    onNavigateToStatistics: () -> Unit = {}

) {

    val overallPct = data.overall_percentage

    val trackedSubjects = data.subjects.filter { it.requires_attendance }

    val totalAttended = trackedSubjects.sumOf { it.periods_attended }

    val totalHeld = trackedSubjects.sumOf { it.periods_held }

    // Evaluated subjects: subjects where at least 1 session has been held
    val activeSubjects = trackedSubjects.filter { it.periods_held > 0 }
    val evaluatedCount = if (activeSubjects.isNotEmpty()) activeSubjects.size else trackedSubjects.size
    val subjectsOnTrack = trackedSubjects.count { it.periods_held > 0 && it.percentage >= 75.0 }
    val subjectsAtRisk = trackedSubjects.count { it.periods_held > 0 && it.percentage < 75.0 }
    val subjectsNotStarted = trackedSubjects.count { it.periods_held == 0 }
    val totalSubjects = trackedSubjects.size

    Card(

        colors = CardDefaults.cardColors(containerColor = Color.White),

        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),

        shape = RoundedCornerShape(20.dp),

        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

        modifier = Modifier.fillMaxWidth().clickable { onNavigateToStatistics() }

    ) {

        Column(modifier = Modifier.padding(15.dp)) {

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Exam Eligibility & Attendance",
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gauge & Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Ring Gauge (Per-Subject Exam Eligibility)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(88.dp)
                ) {
                    val fraction = if (evaluatedCount == 0) 0f else (subjectsOnTrack.toFloat() / evaluatedCount.toFloat())
                    val sweep = if (totalHeld == 0) 0f else (360f * fraction).coerceIn(0f, 360f)

                    val ringColor = when {
                        totalHeld == 0 -> Color(0xFFCBD5E1)
                        subjectsAtRisk == 0 -> PrimaryGreen
                        subjectsOnTrack > 0 -> WarningAmber
                        else -> AlertRed
                    }

                    Canvas(modifier = Modifier.size(88.dp)) {
                        drawArc(
                            color = Color(0xFFE2E8F0),
                            startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        if (sweep > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val ratioText = if (totalHeld == 0) "—" else "$subjectsOnTrack/$evaluatedCount"
                        Text(
                            ratioText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalHeld == 0) TextGray else TextDark
                        )
                        Text(
                            if (totalHeld == 0) "No Sessions" else "Eligible",
                            fontSize = 9.sp,
                            color = TextGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))



                // Stats Column

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Aggregate Attendance Card

                    Surface(

                        color = SurfaceBg,

                        shape = RoundedCornerShape(10.dp),

                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

                        modifier = Modifier.fillMaxWidth()

                    ) {

                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {

                            Text("Aggregate Attendance", fontSize = 10.5.sp, color = TextGray)

                            Row(verticalAlignment = Alignment.CenterVertically) {

                                Text(

                                    if (totalHeld == 0) "—" else "${String.format(Locale.ENGLISH, "%.1f", overallPct)}%",

                                    fontSize = 14.sp,

                                    fontWeight = FontWeight.ExtraBold,

                                    color = TextDark

                                )

                                Spacer(Modifier.width(6.dp))

                                Text(

                                    if (totalHeld == 0) "(0 / 0)" else "($totalAttended / $totalHeld Periods)",

                                    fontSize = 10.5.sp,

                                    color = TextGray,

                                    fontWeight = FontWeight.Medium

                                )

                            }

                        }

                    }



                    // Actionable Exam Compliance Card

                    val compliance = when {

                        totalHeld == 0 -> ExamComplianceState(

                            bg = Color(0xFFF1F5F9),

                            border = Color(0xFFE2E8F0),

                            textColor = Color(0xFF64748B),

                            icon = Icons.Outlined.Info,

                            title = "Classes Pending",

                            subtitle = "No sessions held yet"

                        )

                        subjectsAtRisk > 0 -> ExamComplianceState(

                            bg = LightRedBg,

                            border = Color(0xFFFECACA),

                            textColor = AlertRed,

                            icon = Icons.Outlined.WarningAmber,

                            title = "Action Required",

                            subtitle = "$subjectsAtRisk ${if (subjectsAtRisk == 1) "subject" else "subjects"} below 75% cutoff"

                        )

                        else -> ExamComplianceState(

                            bg = LightGreenBg,

                            border = Color(0xFFA7F3D0),

                            textColor = PrimaryGreen,

                            icon = Icons.Outlined.CheckCircle,

                            title = "All Clear",

                            subtitle = "Eligible in all evaluated subjects"

                        )

                    }



                    Surface(

                        color = compliance.bg,

                        shape = RoundedCornerShape(10.dp),

                        border = androidx.compose.foundation.BorderStroke(1.dp, compliance.border),

                        modifier = Modifier.fillMaxWidth().clickable { onViewSubjects() }

                    ) {

                        Row(

                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Column(modifier = Modifier.weight(1f)) {

                                Text(compliance.title, fontSize = 10.sp, color = compliance.textColor, fontWeight = FontWeight.SemiBold)

                                Text(

                                    compliance.subtitle,

                                    fontSize = 11.5.sp,

                                    fontWeight = FontWeight.Bold,

                                    color = compliance.textColor,

                                    maxLines = 1,

                                    overflow = TextOverflow.Ellipsis

                                )

                            }

                            Spacer(Modifier.width(4.dp))

                            Icon(compliance.icon, contentDescription = null, tint = compliance.textColor, modifier = Modifier.size(16.dp))

                        }

                    }

                }

            }



            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Spacer(modifier = Modifier.height(8.dp))



            // Footer Sub-stat row

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Row(

                    verticalAlignment = Alignment.CenterVertically,

                    modifier = Modifier

                        .clip(RoundedCornerShape(6.dp))

                        .clickable { onViewSubjects() }

                        .padding(vertical = 2.dp, horizontal = 2.dp)

                ) {

                    Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))

                    Spacer(Modifier.width(5.dp))

                    Text(

                        "Subjects: $subjectsOnTrack of $evaluatedCount On Track",

                        fontSize = 11.5.sp,

                        fontWeight = FontWeight.SemiBold,

                        color = TextDark

                    )

                    Spacer(Modifier.width(2.dp))

                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(13.dp))

                }



                Surface(

                    color = Color(0xFFEFF6FF),

                    shape = RoundedCornerShape(8.dp),

                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),

                    modifier = Modifier.clickable { onNavigateToStatistics() }

                ) {

                    Row(

                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Icon(

                            Icons.Outlined.BarChart,

                            contentDescription = null,

                            tint = PrimaryBlue,

                            modifier = Modifier.size(14.dp)

                        )

                        Spacer(Modifier.width(4.dp))

                        Text(

                            "Statistics ↗",

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            color = PrimaryBlue

                        )

                    }

                }

            }

        }

    }

}



@Composable

fun QuickActionsRow(

    onPlannerClick: () -> Unit,

    onCalendarClick: () -> Unit,

    onStatisticsClick: () -> Unit

) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(10.dp)

    ) {

        // Planner Button

        Surface(

            color = Color.White,

            shape = RoundedCornerShape(14.dp),

            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

            modifier = Modifier.weight(1f).clickable { onPlannerClick() }

        ) {

            Column(Modifier.padding(12.dp)) {

                Box(

                    modifier = Modifier.size(32.dp).background(Color(0xFFEEF2FF), CircleShape),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(18.dp))

                }

                Spacer(Modifier.height(8.dp))

                Text("Planner", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)

                Text("What if I skip?", fontSize = 10.sp, color = TextGray)

            }

        }



        // Calendar Button

        Surface(

            color = Color.White,

            shape = RoundedCornerShape(14.dp),

            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

            modifier = Modifier.weight(1f).clickable { onCalendarClick() }

        ) {

            Column(Modifier.padding(12.dp)) {

                Box(

                    modifier = Modifier.size(32.dp).background(Color(0xFFECFDF5), CircleShape),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))

                }

                Spacer(Modifier.height(8.dp))

                Text("Calendar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)

                Text("Monthly review", fontSize = 10.sp, color = TextGray)

            }

        }



        // Statistics Button

        Surface(

            color = Color.White,

            shape = RoundedCornerShape(14.dp),

            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

            modifier = Modifier.weight(1f).clickable { onStatisticsClick() }

        ) {

            Column(Modifier.padding(12.dp)) {

                Box(

                    modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(Icons.Outlined.Leaderboard, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))

                }

                Spacer(Modifier.height(8.dp))

                Text("Statistics", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)

                Text("Weekly trends", fontSize = 10.sp, color = TextGray)

            }

        }

    }

}



@Composable
fun LiveScheduleCard(
    slot: StudentScheduleSlot
) {
    val relativeTime = remember(slot.start_time, slot.end_time) {
        getRelativeClassTime(slot.start_time, slot.end_time)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top timing & room row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    val timeDisp = formatTimeRange12Hour(slot.start_time, slot.end_time)
                    Text(
                        text = timeDisp,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                // Room badge (e.g. IL-106 (CLT))
                val roomDisp = buildString {
                    append(slot.room ?: "TBA")
                    if (!slot.batch_label.isNullOrBlank()) {
                        append(" (")
                        append(slot.batch_label)
                        append(")")
                    }
                }
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = roomDisp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Subject name & code
            Text(
                text = "${slot.subject_code}: ${slot.subject_name}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextDark
            )

            // Bottom row: Faculty on left, Relative Countdown on right (no bg chip)
            val teacher = slot.teacher_name ?: "Faculty"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = teacher,
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }

                // Relative countdown text at bottom-right corner without background chip
                if (relativeTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (relativeTime.isOngoing) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(relativeTime.textColor, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = relativeTime.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = relativeTime.textColor
                        )
                    }
                }
            }
        }
    }
}

data class RelativeClassTime(
    val label: String,
    val textColor: Color,
    val bgColor: Color,
    val borderColor: Color,
    val isOngoing: Boolean = false
)

fun getRelativeClassTime(startTimeStr: String?, endTimeStr: String?): RelativeClassTime? {
    val startMin = parseTimeToMinutes(startTimeStr)
    val endMin = parseTimeToMinutes(endTimeStr)
    if (startMin <= 0) return null

    val now = java.time.LocalTime.now()
    val nowMin = now.hour * 60 + now.minute

    val effectiveEnd = if (endMin > startMin) endMin else startMin + 60

    return when {
        nowMin in startMin..effectiveEnd -> {
            RelativeClassTime(
                label = "Ongoing",
                textColor = Color(0xFF059669),
                bgColor = Color(0xFFECFDF5),
                borderColor = Color(0xFFA7F3D0),
                isOngoing = true
            )
        }
        nowMin < startMin -> {
            val diff = startMin - nowMin
            val text = when {
                diff < 60 -> "in ${diff} mins"
                diff % 60 == 0 -> {
                    val h = diff / 60
                    if (h == 1) "in 1 hr" else "in $h hrs"
                }
                else -> {
                    val h = diff / 60
                    val m = diff % 60
                    "in ${h}h ${m}m"
                }
            }
            if (diff <= 30) {
                RelativeClassTime(
                    label = text,
                    textColor = Color(0xFFD97706),
                    bgColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A)
                )
            } else {
                RelativeClassTime(
                    label = text,
                    textColor = PrimaryBlue,
                    bgColor = Color(0xFFEFF6FF),
                    borderColor = Color(0xFFBFDBFE)
                )
            }
        }
        else -> {
            RelativeClassTime(
                label = "Completed",
                textColor = TextGray,
                bgColor = Color(0xFFF8FAFC),
                borderColor = CardBorder
            )
        }
    }
}

fun parseTimeToMinutes(timeStr: String?): Int {
    if (timeStr.isNullOrBlank()) return 0
    return try {
        val clean = timeStr.trim().uppercase()
        val isPm = clean.contains("PM")
        val isAm = clean.contains("AM")
        val stripped = clean.replace("AM", "").replace("PM", "").trim()
        val parts = stripped.split(":")
        var h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0
        if (isPm && h < 12) h += 12
        if (isAm && h == 12) h = 0
        h * 60 + m
    } catch (_: Exception) { 0 }
}



fun formatTo12Hour(timeStr: String?): String {

    if (timeStr.isNullOrBlank()) return ""

    val clean = timeStr.trim()

    if (clean.contains("AM", ignoreCase = true) || clean.contains("PM", ignoreCase = true)) {

        return clean

    }

    return try {

        val parts = clean.split(":")

        if (parts.size >= 2) {

            val hour = parts[0].toIntOrNull() ?: return clean

            val minute = parts[1].take(2)

            val amPm = if (hour >= 12) "PM" else "AM"

            val hour12 = when (val h = hour % 12) {

                0 -> 12

                else -> h

            }

            String.format(Locale.ENGLISH, "%02d:%s %s", hour12, minute, amPm)

        } else {

            clean

        }

    } catch (_: Exception) {

        clean

    }

}



fun formatTimeRange12Hour(startTime: String?, endTime: String?): String {

    val start = formatTo12Hour(startTime)

    val end = formatTo12Hour(endTime)

    return when {

        start.isNotBlank() && end.isNotBlank() -> "$start - $end"

        start.isNotBlank() -> start

        end.isNotBlank() -> end

        else -> "TBA"

    }

}



@Preview(showBackground = true)

@Composable

fun StudentDashboardScreenPreview() {

    val todayDay = AppDateProvider.todayDayOfWeek.take(3).uppercase()



    AttendanceAppTheme {

        StudentDashboardContent(

            profile = UserProfileResponse(

                user_id = 1,

                full_name = "Aditya Kumar",

                email = "235ics002@gbu.ac.in",

                roles = listOf("STUDENT"),

                primary_role = "STUDENT",

                roll_number = "235ICS002",

                program_name = "Integrated Btech-Mtech",

                batch_name = "2024-2028",

                department = "Computer Science and Engineering"

            ),

            dashboardState = DashboardUiState.Loaded(

                data = StudentDashboardResponse(

                    current_semester = "Semester 5 (2026-27 Odd)",

                    overall_percentage = 82.4,

                    highest_score_subject = "Internet of Things (92.0%)",

                    risk_level = "1 Critical",

                    subjects = listOf(

                        SubjectAttendanceHistory("Internet of Things", "CS401", "Dr. Vidushi Sharma", 37, 34, 91.9, "Excellent"),

                        SubjectAttendanceHistory("Operating Systems", "CS502", "Prof. Sharma", 36, 32, 88.9, "Good Standing"),

                        SubjectAttendanceHistory("Computer Networks", "CS403", "Prof. Iyer", 36, 31, 86.1, "Good Standing"),

                        SubjectAttendanceHistory("Database Systems", "CS303", "Prof. Verma", 27, 19, 70.4, "Low Attendance"),

                        SubjectAttendanceHistory("Machine Learning", "CS405", "Dr. Gautam", 25, 20, 80.0, "Good Standing")

                    )

                )

            ),

            schedule = listOf(

                StudentScheduleSlot(1, "CS401", "Internet of Things", "CS-IV-A", todayDay, "09:30:00", "10:30:00", "IL203", teacher_name = "Dr. Vidushi Sharma"),

                StudentScheduleSlot(2, "CS502", "Operating Systems", "CS-IV-A", todayDay, "10:30:00", "11:30:00", "IL204", teacher_name = "Prof. Sharma"),

                StudentScheduleSlot(3, "CS403", "Computer Networks", "CS-IV-A", todayDay, "11:30:00", "12:30:00", "IL203", teacher_name = "Prof. Iyer")

            ),

            isRefreshing = false,

            onRefresh = {},

            getBunkAdvice = { attended, held, target ->

                if (held == 0) BunkAdvice.NoClasses

                else {

                    val pct = (attended.toDouble() / held.toDouble()) * 100.0

                    if (pct >= target) {

                        val safe = ((4 * attended - 3 * held) / 3).coerceAtLeast(0)

                        BunkAdvice.Safe(safe, pct)

                    } else {

                        val needed = kotlin.math.ceil(3.0 * held - 4.0 * attended).toInt().coerceAtLeast(1)

                        BunkAdvice.AtRisk(needed, pct)

                    }

                }

            },

            onLogout = {}

        )

    }

}

