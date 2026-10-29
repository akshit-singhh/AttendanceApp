package com.akshit.attendance.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import com.akshit.attendance.data.model.TeacherTimetableResponse
import com.akshit.attendance.data.model.TeachingWorkloadItem
import com.akshit.attendance.data.model.TimetableSlotItem
import com.akshit.attendance.ui.components.AppTopBar
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

// --- Theme Colors matching the official GBU Timetable portal web design ---
private val NavyHeader = Color(0xFF1A2758)
private val NavyDark = Color(0xFF0F1A3C)
private val LectureBg = Color(0xFFEFF6FF)
private val LectureBorder = Color(0xFFBFDBFE)
private val LectureText = Color(0xFF1E40AF)
private val LabBg = Color(0xFFECFDF5)
private val LabBorder = Color(0xFFA7F3D0)
private val LabText = Color(0xFF065F46)
private val AccentYellow = Color(0xFFFEF08A)
private val AccentYellowText = Color(0xFF854D0E)
private val YellowBoxBorder = Color(0xFFF59E0B) // Dynamic moving yellow rectangle border
private val YellowBoxBg = Color(0xFFFEF9C3) // Dynamic yellow background highlight
private val ActiveGreen = Color(0xFF16A34A)
private val SurfaceBg = Color(0xFFF8FAFC)
private val TextDark = Color(0xFF1E293B)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherTimetableScreen(
    viewModel: TeacherViewModel,
    onStartRollCall: (Int) -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val state by viewModel.timetable.collectAsState()

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = "Timetable & Workload",
                onLogout = onLogout,
                onOpenProfile = onOpenProfile
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val current = state) {
                is TimetableUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NavyHeader)
                    }
                }
                is TimetableUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = current.message,
                                color = TextDark,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadTimetable() },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyHeader)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is TimetableUiState.Loaded -> {
                    TimetableContent(
                        data = current.data,
                        onStartRollCall = onStartRollCall,
                        onRefresh = { viewModel.loadTimetable() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableContent(
    data: TeacherTimetableResponse,
    onStartRollCall: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L) // Refresh every 10 seconds so the yellow rectangle moves dynamically with time
            currentTime = LocalTime.now()
            currentDate = LocalDate.now()
        }
    }

    val currentDay = currentDate.dayOfWeek.value // 1 = Mon ... 7 = Sun
    val currentPeriod = remember(currentTime, data.period_definitions) {
        calculateCurrentPeriod(currentTime, data.period_definitions)
    }

    val activeSlot = remember(currentDay, currentPeriod, data.slots) {
        if (currentPeriod != null) {
            data.slots.find { it.day == currentDay && it.period == currentPeriod }
        } else null
    }

    val activeTimeStr = remember(currentPeriod, data.period_definitions) {
        currentPeriod?.let { data.period_definitions[it.toString()] ?: periodFallbackTime(it) } ?: ""
    }

    var selectedDay by remember { mutableStateOf(if (currentDay in 1..7) currentDay else 1) }
    var viewMode by remember { mutableStateOf(0) } // Default to 0 (Day Schedule first)

    val daysList = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        item {
            TimetableHeaderCard(data = data)
        }

        // 2. Currently Active Class Status Card (tells what is active currently)
        item {
            CurrentActiveStatusCard(
                currentPeriod = currentPeriod,
                activeSlot = activeSlot,
                activeTimeStr = activeTimeStr
            )
        }

        // 3. View Mode Toggle (Day Schedule vs Weekly Matrix)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = viewMode == 0,
                        onClick = { viewMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ViewAgenda, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Day Schedule", fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                    SegmentedButton(
                        selected = viewMode == 1,
                        onClick = { viewMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.GridOn, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Weekly Matrix", fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        if (viewMode == 0) {
            // Day selector pills
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(daysList) { (dayNum, dayName) ->
                        val isSelected = selectedDay == dayNum
                        val isToday = dayNum == currentDay

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NavyHeader else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) NavyHeader else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedDay = dayNum }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else TextDark
                                )
                                if (isToday) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) AccentYellow else Color(0xFF00C853))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Day's slots
            val daySlots = data.slots.filter { it.day == selectedDay }
            if (daySlots.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No classes scheduled for this day",
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark,
                                fontSize = 14.sp
                            )
                            Text(
                                "Enjoy your free period or preparation time",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(daySlots) { slot ->
                    val isNow = (selectedDay == currentDay && slot.period == currentPeriod)
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PeriodSlotCard(slot = slot, isNow = isNow, onStartRollCall = onStartRollCall)
                    }
                }
            }
        } else {
            // Full Weekly Matrix with moving yellow rectangle
            item {
                WeeklyMatrixView(
                    data = data,
                    currentDay = currentDay,
                    currentPeriod = currentPeriod
                )
            }
        }

        // 3. Teaching Details & Assigned Workload Section
        item {
            TeachingWorkloadSection(data = data)
        }
    }
}

@Composable
private fun TimetableHeaderCard(data: TeacherTimetableResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = NavyHeader,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Badge: GBU 2026-27 (ODD SEMESTER)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = data.semester_info.uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Teacher Name & Dept
            Text(
                text = data.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = data.subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Legend & Total Hours Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF60A5FA))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lecture", color = Color.White, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lab", color = Color.White, fontSize = 11.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Total: ${data.total_hours.toInt()} hrs/wk",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSlotCard(
    slot: TimetableSlotItem,
    isNow: Boolean = false,
    onStartRollCall: (Int) -> Unit
) {
    val isLab = slot.activity.equals("Lab", ignoreCase = true)
    val cardBg = if (isLab) LabBg else LectureBg
    val cardBorder = if (isNow) YellowBoxBorder else (if (isLab) LabBorder else LectureBorder)
    val borderWidth = if (isNow) 2.5.dp else 1.dp
    val primaryText = if (isLab) LabText else LectureText

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(borderWidth, cardBorder),
        shadowElevation = if (isNow) 3.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Period label & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isNow) YellowBoxBorder else Color.White,
                        border = BorderStroke(1.dp, if (isNow) YellowBoxBorder else cardBorder)
                    ) {
                        Text(
                            text = "Period ${romanPeriod(slot.period)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNow) Color.White else primaryText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isNow) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF08A)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ActiveGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "HAPPENING NOW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentYellowText
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slot.period_time,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }

                // Teacher initials badge
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Text(
                        text = slot.teacher_abbr.ifBlank { "RP" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject Code & Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slot.subject_code,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isLab) Color(0xFFD1FAE5) else Color(0xFFDBEAFE)
                ) {
                    Text(
                        text = slot.activity.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (slot.subject_name.isNotBlank()) {
                Text(
                    text = slot.subject_name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location & Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = slot.room.ifBlank { "TBA" },
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = slot.section_name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentActiveStatusCard(
    currentPeriod: Int?,
    activeSlot: TimetableSlotItem?,
    activeTimeStr: String
) {
    if (currentPeriod != null) {
        if (activeSlot != null) {
            val isLab = activeSlot.activity.equals("Lab", ignoreCase = true)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = YellowBoxBg,
                border = BorderStroke(2.dp, YellowBoxBorder),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ActiveGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HAPPENING NOW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentYellowText,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = YellowBoxBorder
                            ) {
                                Text(
                                    text = "Period ${romanPeriod(currentPeriod)}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = activeTimeStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentYellowText
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${activeSlot.subject_code} • ${activeSlot.subject_name.ifBlank { activeSlot.activity }}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Room: ${activeSlot.room.ifBlank { "TBA" }}  •  Section: ${activeSlot.section_name}",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLab) LabBg else LectureBg,
                            border = BorderStroke(1.dp, if (isLab) LabBorder else LectureBorder)
                        ) {
                            Text(
                                text = activeSlot.activity.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLab) LabText else LectureText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Free period during active hours
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF9C3).copy(alpha = 0.6f),
                border = BorderStroke(1.5.dp, YellowBoxBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF08A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = AccentYellowText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Period ${romanPeriod(currentPeriod)} NOW",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentYellowText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "($activeTimeStr)",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = "No lecture or lab assigned currently • Free Period",
                            fontSize = 12.sp,
                            color = TextDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    } else {
        // Outside scheduled periods
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Schedule Hours: 08:30 AM – 07:30 PM (Mon–Sat)",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WeeklyMatrixView(
    data: TeacherTimetableResponse,
    currentDay: Int,
    currentPeriod: Int?
) {
    val horizontalScroll = rememberScrollState()
    var selectedJointSlots by remember { mutableStateOf<List<TimetableSlotItem>?>(null) }

    // Smoothly scroll to active period column when it exists
    LaunchedEffect(currentPeriod) {
        if (currentPeriod != null && currentPeriod > 1) {
            val targetScroll = ((currentPeriod - 2) * 260).coerceAtLeast(0)
            horizontalScroll.animateScrollTo(targetScroll)
        }
    }

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val periods = (1..11).toList()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll)
                .padding(12.dp)
        ) {
            // Header Row: DAY | I | II | III (NOW) ...
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("DAY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMuted)
                }

                periods.forEach { p ->
                    val timeStr = data.period_definitions[p.toString()] ?: periodFallbackTime(p)
                    val isNowPeriod = (p == currentPeriod)

                    Column(
                        modifier = Modifier
                            .width(110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isNowPeriod) AccentYellow.copy(alpha = 0.6f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = romanPeriod(p),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isNowPeriod) AccentYellowText else NavyHeader
                            )
                            if (isNowPeriod) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = YellowBoxBorder
                                ) {
                                    Text(
                                        "NOW",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = timeStr,
                            fontSize = 9.sp,
                            color = if (isNowPeriod) AccentYellowText else TextMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

            // Data Rows for each Day
            (1..7).forEach { dayNum ->
                val dayName = days[dayNum - 1]
                val isToday = dayNum == currentDay
                val slotsForDay = data.slots.filter { it.day == dayNum }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day column (e.g. Mon Today in yellow)
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isToday) AccentYellow.copy(alpha = 0.45f) else Color.Transparent)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayName,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isToday) NavyHeader else TextDark
                        )
                        if (isToday) {
                            Text(
                                "Today",
                                fontSize = 9.sp,
                                color = AccentYellowText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Periods 1 to 11
                    periods.forEach { p ->
                        val matchingSlots = slotsForDay.filter { it.period == p }
                        val isCurrentCell = (dayNum == currentDay && p == currentPeriod)

                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(76.dp)
                                .padding(2.dp)
                        ) {
                            if (matchingSlots.isNotEmpty()) {
                                val matchingSlot = matchingSlots.first()
                                val isMulti = matchingSlots.size > 1
                                val isLab = matchingSlots.any { it.activity.equals("Lab", ignoreCase = true) }
                                val baseBorder = if (isLab) LabBorder else (if (isMulti) Color(0xFF93C5FD) else LectureBorder)
                                val borderStroke = if (isCurrentCell) {
                                    BorderStroke(2.5.dp, YellowBoxBorder)
                                } else {
                                    BorderStroke(if (isMulti) 1.5.dp else 1.dp, baseBorder)
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = isMulti) {
                                            selectedJointSlots = matchingSlots
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLab) LabBg else LectureBg,
                                    border = borderStroke,
                                    shadowElevation = if (isCurrentCell) 2.dp else 0.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val distinctCodes = matchingSlots.map { it.subject_code }.distinct()
                                        val distinctSections = matchingSlots.map { it.section_name }.distinct()

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = distinctCodes.joinToString("/"),
                                                fontSize = if (distinctCodes.size > 2) 8.sp else if (distinctCodes.size > 1) 9.sp else 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLab) LabText else LectureText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            if (isCurrentCell) {
                                                Surface(
                                                    shape = RoundedCornerShape(2.dp),
                                                    color = YellowBoxBorder
                                                ) {
                                                    Text(
                                                        "NOW",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                                    )
                                                }
                                            } else if (isMulti) {
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = (if (isLab) LabText else LectureText).copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "+${matchingSlots.size - 1}",
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isLab) LabText else LectureText,
                                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    matchingSlot.teacher_abbr.ifBlank { "AAK" },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                matchingSlot.room,
                                                fontSize = 9.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = distinctSections.joinToString(", "),
                                            fontSize = if (distinctSections.size > 1) 7.5.sp else 9.sp,
                                            lineHeight = if (distinctSections.size > 1) 9.sp else 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (distinctSections.size > 1) LectureText else TextDark,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                if (isCurrentCell) {
                                    // Highlighted current empty cell (Free Period with yellow rectangle)
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(6.dp),
                                        color = YellowBoxBg.copy(alpha = 0.6f),
                                        border = BorderStroke(2.dp, YellowBoxBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                "FREE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentYellowText
                                            )
                                            Text(
                                                "NOW",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = YellowBoxBorder
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("—", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
            }
        }
    }

    if (selectedJointSlots != null) {
        val joint = selectedJointSlots!!
        val firstSlot = joint.firstOrNull()
        AlertDialog(
            onDismissRequest = { selectedJointSlots = null },
            confirmButton = {
                TextButton(onClick = { selectedJointSlots = null }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = LectureText)
                }
            },
            title = {
                Column {
                    Text(
                        text = "Joint Classes (${joint.size} Sections)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = NavyHeader
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${firstSlot?.day_name ?: ""} Period ${firstSlot?.period ?: ""} (${firstSlot?.period_time ?: ""}) • Room: ${firstSlot?.room ?: "TBA"}",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    joint.forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LectureBg,
                            border = BorderStroke(1.dp, LectureBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.subject_code, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LectureText)
                                    Text(s.section_name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NavyHeader)
                                }
                                if (s.subject_name.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(s.subject_name, fontSize = 11.sp, color = TextDark)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun TeachingWorkloadSection(data: TeacherTimetableResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = NavyHeader,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Teaching Details & Assigned Workload",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            }

            Text(
                text = "Complete teaching load distribution across all sections for this faculty member",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Total Hours Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Text(
                        text = "Total: ${data.total_hours.toInt()} Hours / Week",
                        color = NavyHeader,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Workload items
            if (data.workload.isEmpty()) {
                Text(
                    text = "No workload entries found",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                data.workload.forEachIndexed { index, item ->
                    WorkloadRowCard(item = item)
                    if (index < data.workload.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkloadRowCard(item: TeachingWorkloadItem) {
    val isLab = item.activity.equals("Lab", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text(
                            text = item.section_name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.subject_code,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyHeader
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.subject_name,
                    fontSize = 12.sp,
                    color = TextDark,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isLab) Color(0xFFD1FAE5) else Color(0xFFDBEAFE)
                ) {
                    Text(
                        text = item.activity.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLab) LabText else LectureText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${item.hours} hrs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }
        }
    }
}

private fun romanPeriod(period: Int): String = when (period) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    6 -> "VI"
    7 -> "VII"
    8 -> "VIII"
    9 -> "IX"
    10 -> "X"
    11 -> "XI"
    else -> period.toString()
}

private fun periodFallbackTime(period: Int): String = when (period) {
    1 -> "08:30-09:30"
    2 -> "09:30-10:30"
    3 -> "10:30-11:30"
    4 -> "11:30-12:30"
    5 -> "12:30-13:30"
    6 -> "13:30-14:30"
    7 -> "14:30-15:30"
    8 -> "15:30-16:30"
    9 -> "16:30-17:30"
    10 -> "17:30-18:30"
    11 -> "18:30-19:30"
    else -> ""
}

private fun parseFlexibleTime(str: String): LocalTime? {
    return try {
        val s = str.trim()
        val colonIdx = s.indexOf(':')
        if (colonIdx > 0) {
            val hour = s.substring(0, colonIdx).trim().toInt()
            val minute = s.substring(colonIdx + 1).take(2).trim().toInt()
            LocalTime.of(hour, minute)
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun parsePeriodRange(rawTime: String): Pair<LocalTime, LocalTime>? {
    return try {
        val parts = rawTime.split("-")
        if (parts.size == 2) {
            val start = parseFlexibleTime(parts[0])
            val end = parseFlexibleTime(parts[1])
            if (start != null && end != null) start to end else null
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun calculateCurrentPeriod(
    time: LocalTime,
    periodDefinitions: Map<String, String>
): Int? {
    for (p in 1..11) {
        val timeStr = periodDefinitions[p.toString()] ?: periodFallbackTime(p)
        val range = parsePeriodRange(timeStr)
        if (range != null) {
            val (start, end) = range
            if (!time.isBefore(start) && time.isBefore(end)) {
                return p
            }
        }
    }
    return null
}
