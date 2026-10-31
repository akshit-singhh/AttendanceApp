package com.akshit.attendance.ui.student

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
import com.akshit.attendance.data.model.UserProfileResponse
import com.akshit.attendance.data.model.TeachingWorkloadItem
import com.akshit.attendance.data.model.TimetableSlotItem
import com.akshit.attendance.ui.components.AppTopBar
import com.akshit.attendance.ui.components.ScheduleCardSkeleton
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

// Colors matching the official GBU Timetable portal design
private val NavyHeader = Color(0xFF1A2758)
private val LectureBg = Color(0xFFEFF6FF)
private val LectureBorder = Color(0xFFBFDBFE)
private val LectureText = Color(0xFF1E40AF)
private val LabBg = Color(0xFFECFDF5)
private val LabBorder = Color(0xFFA7F3D0)
private val LabText = Color(0xFF065F46)
private val AccentYellow = Color(0xFFFEF08A)
private val AccentYellowText = Color(0xFF854D0E)
private val YellowBoxBorder = Color(0xFFF59E0B)
private val YellowBoxBg = Color(0xFFFEF9C3)
private val ActiveGreen = Color(0xFF16A34A)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTimetableScreen(
    viewModel: StudentViewModel,
    onLogout: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val state by viewModel.timetable.collectAsState()

    val profile by viewModel.profile.collectAsState()

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(28.dp),
                            tint = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "My Timetable",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        )
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
        ) {
            when (val current = state) {
                is StudentTimetableUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(5) {
                            ScheduleCardSkeleton()
                        }
                    }
                }
                is StudentTimetableUiState.Error -> {
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
                is StudentTimetableUiState.Loaded -> {
                    StudentTimetableContent(
                        data = current.data,
                        profile = profile,
                        onRefresh = { viewModel.loadTimetable() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentTimetableContent(
    data: TeacherTimetableResponse,
    profile: UserProfileResponse?,
    onRefresh: () -> Unit
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L) // Refresh every 10 seconds for dynamic current period tracking
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
        currentPeriod?.let {
            val raw = data.period_definitions[it.toString()] ?: periodFallbackTime(it)
            formatPeriodTo12Hour(raw)
        } ?: ""
    }

    var selectedDay by remember { mutableStateOf(if (currentDay in 1..7) currentDay else 1) }
    var viewMode by remember { mutableStateOf(0) } // 0 = Day Schedule, 1 = Weekly Matrix

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
        // 1. Class Details Card (Department, Class, Section, Semester)
        item {
            StudentClassDetailsCard(data = data, profile = profile)
        }

        // 2. Currently Active Class Status Card
        item {
            StudentActiveStatusCard(
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
                            Icon(Icons.Outlined.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Day Schedule", fontSize = 13.sp)
                        }
                    }
                    SegmentedButton(
                        selected = viewMode == 1,
                        onClick = { viewMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Weekly Matrix", fontSize = 13.sp)
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

            // Day's slots (sorted chronologically)
            val daySlots = data.slots.filter { it.day == selectedDay }.sortedBy { it.period }
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
                                "Enjoy your free time and self-study",
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
                        StudentPeriodSlotCard(slot = slot, isNow = isNow)
                    }
                }
            }
        } else {
            // Full Weekly Matrix with moving active highlight
            item {
                StudentWeeklyMatrixView(
                    data = data,
                    currentDay = currentDay,
                    currentPeriod = currentPeriod
                )
            }
        }

        // 4. Enrolled Courses & Weekly Load Breakdown
        item {
            StudentCourseWorkloadSection(data = data)
        }
    }
}

@Composable
private fun StudentClassDetailsCard(
    data: TeacherTimetableResponse,
    profile: UserProfileResponse?
) {
    val department = profile?.department?.ifEmpty { null } ?: "Computer Science & Engineering"
    val school = profile?.school?.ifEmpty { null } ?: "School of ICT"
    val section = data.subtitle.substringAfter("•", "").trim().ifEmpty {
        profile?.batch_name ?: "CS-IV-A"
    }
    val className = profile?.program_name?.ifEmpty { null } ?: profile?.batch_name ?: "B.Tech. CSE"
    val semester = data.semester_info.split("•").firstOrNull()?.trim() ?: "2026-27 Odd Semester"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top: Department & School header with Semester tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = department,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = school,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = semester,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Class details row: Class, Section, and Total Load
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Class / Program
                Surface(
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "CLASS / PROGRAM",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = className,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Section
                Surface(
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "SECTION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = section,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                }

                // Weekly Load
                Surface(
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "WEEKLY LOAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${data.total_slots} Slots (${data.total_hours.toInt()}h)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF047857)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentPeriodSlotCard(
    slot: TimetableSlotItem,
    isNow: Boolean = false
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
            // Period label & 12-hour Time
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
                        text = formatPeriodTo12Hour(slot.period_time),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
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

            Spacer(modifier = Modifier.height(10.dp))

            // Subject Code & Name
            Text(
                text = "${slot.subject_code}: ${slot.subject_name.ifBlank { slot.subject_code }}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Teacher Name & Room / Venue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = slot.teacher_name.ifBlank { "Faculty" },
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
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = slot.room.ifBlank { "TBA" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentActiveStatusCard(
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Faculty: ${activeSlot.teacher_name}  •  Room: ${activeSlot.room.ifBlank { "TBA" }}",
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
            // Free period during active college hours
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
                            text = "No lecture or lab scheduled currently • Free Period",
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
                    text = "College Hours: 08:30 AM – 07:30 PM (Mon–Sat)",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StudentWeeklyMatrixView(
    data: TeacherTimetableResponse,
    currentDay: Int,
    currentPeriod: Int?
) {
    val horizontalScroll = rememberScrollState()

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
            // Header Row: DAY | I | II | III ...
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
                            text = formatPeriodTo12Hour(timeStr),
                            fontSize = 8.sp,
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
                    // Day column
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

                    // Periods 1 to 11 cells
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
                                val isLab = matchingSlots.any { it.activity.equals("Lab", ignoreCase = true) }
                                val baseBorder = if (isLab) LabBorder else LectureBorder
                                val borderStroke = if (isCurrentCell) {
                                    BorderStroke(2.5.dp, YellowBoxBorder)
                                } else {
                                    BorderStroke(1.dp, baseBorder)
                                }

                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCurrentCell) YellowBoxBg else (if (isLab) LabBg else LectureBg),
                                    border = borderStroke
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = matchingSlot.subject_code,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLab) LabText else LectureText
                                            )
                                            Text(
                                                text = if (isLab) "LAB" else "LEC",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isLab) LabText else LectureText
                                            )
                                        }

                                        Text(
                                            text = matchingSlot.subject_name.ifBlank { matchingSlot.subject_code },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = matchingSlot.room.ifBlank { "TBA" },
                                                fontSize = 8.sp,
                                                color = TextMuted,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = matchingSlot.teacher_abbr.ifBlank { matchingSlot.teacher_name.take(3) },
                                                fontSize = 8.sp,
                                                color = PrimaryBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Empty slot cell
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCurrentCell) Color(0xFFFEF9C3).copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        if (isCurrentCell) 2.dp else 1.dp,
                                        if (isCurrentCell) YellowBoxBorder else Color(0xFFF1F5F9)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isCurrentCell) "FREE" else "—",
                                            fontSize = 9.sp,
                                            color = if (isCurrentCell) AccentYellowText else Color(0xFFCBD5E1),
                                            fontWeight = if (isCurrentCell) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCourseWorkloadSection(data: TeacherTimetableResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = NavyHeader,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enrolled Courses & Weekly Load",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "${data.workload.size} Subjects",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyHeader,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            data.workload.forEachIndexed { idx, item ->
                val teacher = item.teacher_name
                    ?: data.slots.firstOrNull { it.subject_code.equals(item.subject_code, ignoreCase = true) }?.teacher_name
                    ?: "Faculty"
                StudentWorkloadRow(item = item, teacherName = teacher)
                if (idx < data.workload.lastIndex) {
                    HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentWorkloadRow(item: TeachingWorkloadItem, teacherName: String) {
    val isLab = item.activity.equals("Lab", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                        fontSize = 10.sp,
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

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = teacherName,
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )
            }
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
                text = "${item.hours.toInt()} hrs/wk",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
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

private fun formatPeriodTo12Hour(periodRange: String): String {
    return try {
        val parts = periodRange.split("-")
        if (parts.size == 2) {
            val start12 = formatTo12Hour(parts[0].trim())
            val end12 = formatTo12Hour(parts[1].trim())
            "$start12 - $end12"
        } else {
            periodRange
        }
    } catch (_: Exception) {
        periodRange
    }
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
