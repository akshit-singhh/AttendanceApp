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

    onNavigateToNotifications: () -> Unit = {}

) {

    val profile by viewModel.profile.collectAsState()

    val dashboardState by viewModel.dashboard.collectAsState()

    val schedule by viewModel.schedule.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()

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

        onNavigateToNotifications = onNavigateToNotifications

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

    onNavigateToNotifications: () -> Unit = {}

) {

    var showLogoutDialog by remember { mutableStateOf(false) }
    val notifications by AttendanceNotificationManager.notifications.collectAsState()
    val unreadNotificationsCount = notifications.count { !it.isRead }



    val todayCalendar = remember { Calendar.getInstance() }

    val dayFormat = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }

    val fullDateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH) }

    val todayShortDay = remember { dayFormat.format(todayCalendar.time).uppercase() } // MON, TUE, etc.

    val todayDateStr = remember { fullDateFormat.format(todayCalendar.time) }

    val greeting = remember {

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        when (hour) {

            in 5..11 -> "Good morning"

            in 12..16 -> "Good afternoon"

            in 17..20 -> "Good evening"

            else -> "Welcome back"

        }

    }



    // Map Calendar.DAY_OF_WEEK to API day format

    val targetDay = when (todayCalendar.get(Calendar.DAY_OF_WEEK)) {

        Calendar.MONDAY -> "MON"

        Calendar.TUESDAY -> "TUE"

        Calendar.WEDNESDAY -> "WED"

        Calendar.THURSDAY -> "THU"

        Calendar.FRIDAY -> "FRI"

        Calendar.SATURDAY -> "SAT"

        Calendar.SUNDAY -> "SUN"

        else -> "MON"

    }



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

        if (pullToRefreshState.isRefreshing) {

            LaunchedEffect(true) {

                onRefresh()

            }

        }

        LaunchedEffect(isRefreshing) {

            if (!isRefreshing) {

                pullToRefreshState.endRefresh()

            }

        }

        val showSkeleton = isRefreshing || pullToRefreshState.isRefreshing || dashboardState is DashboardUiState.Loading



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

                                Surface(

                                    color = LightRedBg,

                                    shape = RoundedCornerShape(12.dp),

                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)

                                ) {

                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {

                                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = AlertRed)

                                        Spacer(Modifier.width(10.dp))

                                        Text(state.message, color = AlertRed, fontSize = 13.sp)

                                    }

                                }

                            }

                            is DashboardUiState.Loaded -> {

                                LiveOverallAttendanceCard(

                                    data = state.data,

                                    getBunkAdvice = getBunkAdvice,

                                    onViewSubjects = onNavigateToSubjects

                                )

                            }

                        }

                    }

                }



                // 3. Quick Action Grid (Planner, Calendar, Statistics)

                item {

                    QuickActionsRow(

                        onPlannerClick = onNavigateToPlanner,

                        onCalendarClick = onNavigateToCalendar,

                        onStatisticsClick = onNavigateToStatistics

                    )

                }



                // 4. Today's Timetable Section

                item {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Column {

                            Text("Today's Classes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)

                            val countText = if (todaySlots.isEmpty()) "No Classes Scheduled" else "${todaySlots.size} Scheduled"

                            Text("$todayDateStr • $countText", fontSize = 12.sp, color = TextGray)

                        }

                        IconButton(

                            onClick = onRefresh,

                            modifier = Modifier.size(32.dp).background(Color(0xFFE2E8F0), CircleShape)

                        ) {

                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextDark, modifier = Modifier.size(16.dp))

                        }

                    }

                }



                if (showSkeleton) {

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





@Composable

fun LiveOverallAttendanceCard(

    data: com.akshit.attendance.data.model.StudentDashboardResponse,

    getBunkAdvice: (Int, Int, Double) -> BunkAdvice,

    onViewSubjects: () -> Unit

) {

    val overallPct = data.overall_percentage

    val totalAttended = data.subjects.sumOf { it.periods_attended }

    val totalHeld = data.subjects.sumOf { it.periods_held }

    val subjectsOnTrack = data.subjects.count { it.percentage >= 75.0 }

    val totalSubjects = data.subjects.size



    val advice = getBunkAdvice(totalAttended, totalHeld, 75.0)



    Card(

        colors = CardDefaults.cardColors(containerColor = Color.White),

        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),

        shape = RoundedCornerShape(20.dp),

        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

        modifier = Modifier.fillMaxWidth().clickable { onViewSubjects() }

    ) {

        Column(modifier = Modifier.padding(15.dp)) {

            // Header Row

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(Icons.Outlined.BarChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("Overall Attendance", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 15.sp)

                }



                val (badgeBg, badgeText, badgeColor) = when {

                    totalHeld == 0 -> Triple(Color(0xFFF1F5F9), "No Sessions", Color(0xFF64748B))

                    overallPct >= 75.0 -> Triple(LightGreenBg, "On Track", PrimaryGreen)

                    else -> Triple(LightRedBg, "Needs Attention", AlertRed)

                }



                Surface(color = badgeBg, shape = RoundedCornerShape(12.dp)) {

                    Row(

                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Box(modifier = Modifier.size(6.dp).background(badgeColor, CircleShape))

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(badgeText, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.Bold)

                    }

                }

            }



            Spacer(modifier = Modifier.height(12.dp))



            // Gauge & Stats Row

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically

            ) {

                // Circular Ring Gauge

                Box(

                    contentAlignment = Alignment.Center,

                    modifier = Modifier.size(96.dp)

                ) {

                    val sweep = if (totalHeld == 0) 0f else (360f * (overallPct / 100f).toFloat()).coerceIn(0f, 360f)

                    val ringColor = when {

                        totalHeld == 0 -> Color(0xFFCBD5E1)

                        overallPct >= 75.0 -> PrimaryGreen

                        else -> AlertRed

                    }



                    Canvas(modifier = Modifier.size(96.dp)) {

                        drawArc(

                            color = Color(0xFFE2E8F0),

                            startAngle = 0f, sweepAngle = 360f, useCenter = false,

                            style = Stroke(width = 8.5.dp.toPx(), cap = StrokeCap.Round)

                        )

                        if (sweep > 0f) {

                            drawArc(

                                color = ringColor,

                                startAngle = -90f, sweepAngle = sweep, useCenter = false,

                                style = Stroke(width = 8.5.dp.toPx(), cap = StrokeCap.Round)

                            )

                        }

                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        val pctText = if (totalHeld == 0) "—" else "${String.format(Locale.ENGLISH, "%.1f", overallPct)}%"

                        Text(

                            pctText,

                            fontSize = 22.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = if (totalHeld == 0) TextGray else TextDark

                        )

                        Text(

                            if (totalHeld == 0) "No Sessions" else "Target 75%",

                            fontSize = 9.sp,

                            color = TextGray,

                            fontWeight = FontWeight.SemiBold

                        )

                    }

                }



                Spacer(modifier = Modifier.width(18.dp))



                // Stats Column

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Ratio Card

                    Surface(

                        color = SurfaceBg,

                        shape = RoundedCornerShape(10.dp),

                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),

                        modifier = Modifier.fillMaxWidth()

                    ) {

                        Column(modifier = Modifier.padding(10.dp)) {

                            Text("Attended Ratio", fontSize = 11.sp, color = TextGray)

                            Text(

                                if (totalHeld == 0) "0 / 0 Periods" else "$totalAttended / $totalHeld Periods",

                                fontSize = 13.sp,

                                fontWeight = FontWeight.Bold,

                                color = TextDark

                            )

                        }

                    }



                    // Safe Bunk Buffer Card

                    val (bunkBg, bunkTextColor, bunkText) = if (totalHeld == 0) {

                        Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "No sessions held yet")

                    } else when (advice) {

                        is BunkAdvice.Safe -> Triple(

                            LightGreenBg,

                            PrimaryGreen,

                            if (advice.classesCanMiss > 0) "+${advice.classesCanMiss} Classes Safe Bunk" else "At 75% Limit"

                        )

                        is BunkAdvice.AtRisk -> Triple(

                            LightAmberBg,

                            WarningAmber,

                            "Need +${advice.classesNeeded} Classes to 75%"

                        )

                        BunkAdvice.NoClasses -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Classes Pending")

                    }



                    Surface(

                        color = bunkBg,

                        shape = RoundedCornerShape(10.dp),

                        modifier = Modifier.fillMaxWidth()

                    ) {

                        Row(

                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Column(modifier = Modifier.weight(1f)) {

                                Text("Safe Bunk Buffer", fontSize = 10.sp, color = bunkTextColor, fontWeight = FontWeight.SemiBold)

                                Text(bunkText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = bunkTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)

                            }

                            Spacer(Modifier.width(4.dp))

                            Icon(Icons.Outlined.Checklist, contentDescription = null, tint = bunkTextColor, modifier = Modifier.size(15.dp))

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

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))

                    Spacer(Modifier.width(6.dp))

                    Text(

                        "Subjects On Track: $subjectsOnTrack of $totalSubjects",

                        fontSize = 11.sp,

                        fontWeight = FontWeight.SemiBold,

                        color = TextDark

                    )

                }



                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text("View Subjects", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentPurple)

                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(12.dp))

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

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))

                    Spacer(Modifier.width(4.dp))

                    val timeDisp = formatTimeRange12Hour(slot.start_time, slot.end_time)

                    Text(timeDisp, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)

                }



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

                        roomDisp,

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

                "${slot.subject_code}: ${slot.subject_name}",

                fontWeight = FontWeight.Bold,

                fontSize = 14.sp,

                color = TextDark

            )



            // Faculty

            val teacher = slot.teacher_name ?: "Faculty"

            Row(

                verticalAlignment = Alignment.CenterVertically,

                modifier = Modifier.padding(top = 4.dp)

            ) {

                Icon(Icons.Outlined.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(12.dp))

                Spacer(Modifier.width(4.dp))

                Text(teacher, fontSize = 11.sp, color = TextGray)

            }

        }

    }

}



fun parseTimeToMinutes(timeStr: String?): Int {

    if (timeStr.isNullOrBlank()) return 0

    return try {

        val parts = timeStr.trim().split(":")

        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0

        val m = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0

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

    val todayDay = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {

        Calendar.MONDAY -> "MON"

        Calendar.TUESDAY -> "TUE"

        Calendar.WEDNESDAY -> "WED"

        Calendar.THURSDAY -> "THU"

        Calendar.FRIDAY -> "FRI"

        Calendar.SATURDAY -> "SAT"

        Calendar.SUNDAY -> "SUN"

        else -> "MON"

    }



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

