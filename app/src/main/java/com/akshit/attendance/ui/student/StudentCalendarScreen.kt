package com.akshit.attendance.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.CalendarClassItem
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.akshit.attendance.ui.components.PullToRefreshExpressiveIndicator
import com.akshit.attendance.ui.components.SkeletonBox
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentCalendarScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val dashboardState by viewModel.dashboard.collectAsState()
    val calendarState by viewModel.calendarState.collectAsState()

    var selectedSubjectCode by remember { mutableStateOf<String?>("ALL") }

    val calendar = remember { Calendar.getInstance() }
    var currentMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDayNumber by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.ENGLISH) }
    val monthKeyFormat = remember { SimpleDateFormat("yyyy-MM", Locale.ENGLISH) }
    val dayFullFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH) }

    val displayMonth = remember(currentMonthCalendar) { monthYearFormat.format(currentMonthCalendar.time) }
    val monthKey = remember(currentMonthCalendar) { monthKeyFormat.format(currentMonthCalendar.time) }

    // Fetch calendar data whenever current month changes
    LaunchedEffect(monthKey) {
        viewModel.loadCalendar(monthKey)
    }

    val isRefreshing = calendarState is CalendarUiState.Loading
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadCalendar(monthKey)
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // Compute days in month
    val daysInMonth = remember(currentMonthCalendar) {
        val cal = currentMonthCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Monday = index 0 offset
        val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
        Pair(offset, maxDays)
    }

    val calData = (calendarState as? CalendarUiState.Loaded)?.data
    val selectedDateStr = String.format(Locale.ENGLISH, "%s-%02d", monthKey, selectedDayNumber)
    val currentDayData = calData?.days?.get(selectedDateStr)

    val selectedDayFullDate = remember(currentMonthCalendar, selectedDayNumber) {
        val cal = currentMonthCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, selectedDayNumber.coerceIn(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        dayFullFormat.format(cal.time)
    }

    val dayClasses = remember(currentDayData, selectedSubjectCode) {
        val list = currentDayData?.classes ?: emptyList()
        if (selectedSubjectCode == "ALL" || selectedSubjectCode.isNullOrBlank()) {
            list
        } else {
            list.filter { it.subject_code.equals(selectedSubjectCode, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Attendance Calendar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadCalendar(monthKey) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. Horizontal Subject Filter Pills
            item {
                val subjects = (dashboardState as? DashboardUiState.Loaded)?.data?.subjects ?: emptyList()
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSubjectCode == "ALL",
                            onClick = { selectedSubjectCode = "ALL" },
                            label = { Text("All Subjects", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(subjects) { subj ->
                        FilterChip(
                            selected = selectedSubjectCode == subj.course_code,
                            onClick = { selectedSubjectCode = subj.course_code },
                            label = { Text(subj.subject_name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 2. Month Selector & Calendar Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Month navigation row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val c = currentMonthCalendar.clone() as Calendar
                                c.add(Calendar.MONTH, -1)
                                currentMonthCalendar = c
                                selectedDayNumber = 1
                            }) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prev Month", tint = TextDark)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(displayMonth, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                                if (calData != null) {
                                    val totHeld = calData.total_attended + calData.total_missed
                                    val pct = if (totHeld > 0) (calData.total_attended * 100.0 / totHeld) else 100.0
                                    Text(
                                        "${calData.total_attended} Attended • ${calData.total_missed} Missed • ${pct.toInt()}% Avg",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextGray
                                    )
                                }
                            }

                            IconButton(onClick = {
                                val c = currentMonthCalendar.clone() as Calendar
                                c.add(Calendar.MONTH, 1)
                                currentMonthCalendar = c
                                selectedDayNumber = 1
                            }) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next Month", tint = TextDark)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Weekdays Header: M T W T F S S
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextGray)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Calendar Grid Days
                        val (offset, maxDays) = daysInMonth
                        val totalCells = offset + maxDays
                        val rows = (totalCells + 6) / 7

                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - offset + 1
                                    if (dayNum in 1..maxDays) {
                                        val isSelected = dayNum == selectedDayNumber
                                        val cellDateStr = String.format(Locale.ENGLISH, "%s-%02d", monthKey, dayNum)
                                        val cellDayData = calData?.days?.get(cellDateStr)

                                        // Compute real dot color based on attendance
                                        val dotColor = if (cellDayData == null || !cellDayData.has_classes) {
                                            null
                                        } else if (selectedSubjectCode != "ALL" && !selectedSubjectCode.isNullOrBlank()) {
                                            val subClass = cellDayData.classes.firstOrNull { it.subject_code.equals(selectedSubjectCode, ignoreCase = true) }
                                            when (subClass?.status) {
                                                "PRESENT" -> PrimaryGreen
                                                "ABSENT" -> AlertRed
                                                else -> null
                                            }
                                        } else {
                                            when (cellDayData.dot_status) {
                                                "PRESENT" -> PrimaryGreen
                                                "ABSENT", "MIXED" -> AlertRed
                                                else -> null
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) AccentPurple else Color.Transparent)
                                                .clickable { selectedDayNumber = dayNum },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    "$dayNum",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else TextDark
                                                )
                                                if (dotColor != null && !isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .background(dotColor, CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.size(38.dp))
                                    }
                                }
                            }
                        }

                        // Legend Row
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(PrimaryGreen, CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("Attended", fontSize = 11.sp, color = TextGray)
                            }
                            Spacer(Modifier.width(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(AlertRed, CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("Missed", fontSize = 11.sp, color = TextGray)
                            }
                        }
                    }
                }
            }

            // 3. Selected Day Class Details Drawer
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val attCount = dayClasses.count { it.status == "PRESENT" }
                        val misCount = dayClasses.count { it.status == "ABSENT" }
                        val schedCount = dayClasses.count { it.status == "SCHEDULED" }
                        val postpCount = dayClasses.count { it.status == "POSTPONED" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedDayFullDate, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                val summaryText = when {
                                    dayClasses.isEmpty() -> "No classes scheduled"
                                    schedCount == dayClasses.size -> "${dayClasses.size} Classes Scheduled"
                                    misCount > 0 -> "${dayClasses.size} Classes • $attCount Attended, $misCount Missed"
                                    postpCount > 0 && attCount == 0 && misCount == 0 -> "${dayClasses.size} Classes • $postpCount Postponed"
                                    attCount > 0 -> "${dayClasses.size} Classes • All $attCount Attended"
                                    else -> "${dayClasses.size} Classes"
                                }
                                Text(summaryText, fontSize = 11.sp, color = TextGray)
                            }

                            val badgeBg = if (dayClasses.isEmpty()) Color(0xFFF1F5F9) else if (misCount > 0) LightRedBg else if (postpCount > 0 && attCount == 0) Color(0xFFFEF3C7) else LightGreenBg
                            val badgeColor = if (dayClasses.isEmpty()) TextGray else if (misCount > 0) AlertRed else if (postpCount > 0 && attCount == 0) Color(0xFFB45309) else PrimaryGreen
                            val badgeLabel = if (dayClasses.isEmpty()) "Holiday / Free" else if (misCount > 0) "$misCount Missed" else if (postpCount > 0 && attCount == 0) "$postpCount Postponed" else "Complete"

                            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    badgeLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        if (calendarState is CalendarUiState.Loading) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(3) {
                                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp))
                                }
                            }
                        } else if (dayClasses.isEmpty()) {
                            // Empty State
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.EventAvailable,
                                    contentDescription = null,
                                    tint = TextGray,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (selectedSubjectCode != "ALL" && !selectedSubjectCode.isNullOrBlank())
                                        "No $selectedSubjectCode class on this day"
                                    else
                                        "No classes scheduled for this day",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Text(
                                    "Enjoy your free day or prepare for upcoming sessions.",
                                    fontSize = 11.sp,
                                    color = TextGray
                                )
                            }
                        } else {
                            // Real timetable class entries for the selected day
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                dayClasses.forEach { cls ->
                                    DayClassScheduleCard(item = cls)
                                }
                            }
                        }
                    }
                }
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = Color.White,
            contentColor = PrimaryBlue,
            indicator = { state ->
                PullToRefreshExpressiveIndicator(
                    state = state,
                    color = PrimaryBlue
                )
            }
        )
    }
}
}

@Composable
private fun DayClassScheduleCard(item: CalendarClassItem) {
    val isPresent = item.status == "PRESENT"
    val isAbsent = item.status == "ABSENT"
    val isScheduled = item.status == "SCHEDULED"
    val isPostponed = item.status == "POSTPONED"

    Surface(
        color = SurfaceBg,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            // Top row: Time & Room on left, Status Pill on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatTimeRange12Hour(item.start_time, item.end_time),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        val roomDisp = buildString {
                            append(item.room.ifBlank { "TBA" })
                            if (!item.batch_label.isNullOrBlank()) {
                                append(" (")
                                append(item.batch_label)
                                append(")")
                            }
                        }
                        Text(
                            roomDisp,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Attendance Status Pill
                when {
                    isPresent -> {
                        Surface(
                            color = LightGreenBg,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Attended",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen
                                )
                            }
                        }
                    }
                    isAbsent -> {
                        Surface(
                            color = LightRedBg,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Missed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertRed
                                )
                            }
                        }
                    }
                    isScheduled -> {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                "Scheduled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    isPostponed -> {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.EventBusy,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Postponed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "No Record",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Subject name & code - Full width with 2 lines allowed
            Text(
                text = "${item.subject_code}: ${item.subject_name}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Faculty name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    item.teacher_name.ifBlank { "Faculty" },
                    fontSize = 11.sp,
                    color = TextGray
                )
            }
        }
    }
}
