package com.akshit.attendance.ui.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.akshit.attendance.R
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.notification.AttendanceNotificationManager
import com.akshit.attendance.notification.NotificationItem
import com.akshit.attendance.ui.components.NoConnectionView
import com.akshit.attendance.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentNotificationsScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit,
    onSelectSubject: (String) -> Unit = {}
) {
    val notifications by AttendanceNotificationManager.notifications.collectAsState()
    val isLoading by AttendanceNotificationManager.isLoading.collectAsState()
    val connectionStatus by AttendanceNotificationManager.connectionStatus.collectAsState()
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()
    val isSyncFailed = connectionStatus == "Sync failed"

    val plannerItems by viewModel.plannerItems.collectAsState()
    val enrolledCodes = remember(plannerItems) { plannerItems.map { it.code.uppercase() }.toSet() }
    val enrolledNames = remember(plannerItems) {
        plannerItems.associate { it.name.trim().lowercase() to it.code.uppercase() }
    }

    var isScreenLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            isScreenLoading = true
            val minDelay = async { delay(650L) }
            val fetch = async { AttendanceNotificationManager.refreshHistory() }
            minDelay.await()
            fetch.await()
            isScreenLoading = false
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            onRefresh()
        }
    }

    LaunchedEffect(Unit) {
        isScreenLoading = true
        val minDelay = async { delay(650L) }
        val fetch = async { AttendanceNotificationManager.refreshHistory() }
        minDelay.await()
        fetch.await()
        isScreenLoading = false
    }

    var filterCategory by remember { mutableStateOf("ALL") }

    val attendanceCount = remember(notifications) { notifications.count { it.category == "ATTENDANCE" } }
    val noticesCount = remember(notifications) { notifications.count { it.category in listOf("NOTICE", "ACADEMIC", "GENERAL", "ANNOUNCEMENT") || it.category != "ATTENDANCE" } }
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    val displayedList = remember(notifications, filterCategory) {
        when (filterCategory) {
            "ATTENDANCE" -> notifications.filter { it.category == "ATTENDANCE" }
            "NOTICE" -> notifications.filter { it.category in listOf("NOTICE", "ACADEMIC", "GENERAL", "ANNOUNCEMENT") || it.category != "ATTENDANCE" }
            "UNREAD" -> notifications.filter { !it.isRead }
            else -> notifications
        }
    }

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (unreadCount > 0) "Inbox ($unreadCount new)" else "Inbox",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { AttendanceNotificationManager.markAllAsRead() }) {
                            Text("Mark all read", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
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
            if (isScreenLoading) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_dots))
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations = LottieConstants.IterateForever
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.size(160.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Loading notifications...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Fetching notices and attendance alerts",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }
                }
            } else if (notifications.isEmpty() && (!isOnline || isSyncFailed)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NoConnectionView(
                        message = "No internet connection",
                        subMessage = "Connect to check for incoming notifications.",
                        onRetry = { AttendanceNotificationManager.fetchHistory() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Filter Chips Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filterCategory == "ALL",
                                onClick = { filterCategory = "ALL" },
                                label = { Text("All (${notifications.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterCategory == "ATTENDANCE",
                                onClick = { filterCategory = "ATTENDANCE" },
                                label = { Text("Attendance ($attendanceCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = filterCategory == "NOTICE",
                                onClick = { filterCategory = "NOTICE" },
                                label = { Text("Notices ($noticesCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                            if (unreadCount > 0) {
                                FilterChip(
                                    selected = filterCategory == "UNREAD",
                                    onClick = { filterCategory = "UNREAD" },
                                    label = { Text("Unread ($unreadCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AlertRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (displayedList.isEmpty()) {
                        item {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryBlue.copy(alpha = 0.08f),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.NotificationsActive,
                                                contentDescription = null,
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    val filterTitle = when (filterCategory) {
                                        "ATTENDANCE" -> "No Attendance Alerts"
                                        "NOTICE" -> "No Notices"
                                        "UNREAD" -> "No Unread Notifications"
                                        else -> "No Notifications Yet"
                                    }
                                    Text(
                                        filterTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (filterCategory == "ALL")
                                            "Real-time alerts will appear here whenever faculty records attendance or publishes notices."
                                        else
                                            "There are currently no items under this category.",
                                        fontSize = 12.sp,
                                        color = TextGray,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(displayedList, key = { it.id }) { item ->
                            val subjectCode = remember(item, enrolledCodes, enrolledNames) {
                                extractSubjectCode(item, enrolledCodes, enrolledNames)
                            }
                            NotificationCard(
                                item = item,
                                subjectCode = subjectCode,
                                onMarkRead = { AttendanceNotificationManager.markAsRead(item.id) },
                                onSelectSubject = onSelectSubject
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = PrimaryBlue
            )
        }
    }
}

/**
 * Extracts a subject code (e.g. CS403, CS407, MA402) from notification metadata, title, or message.
 */
private fun extractSubjectCode(
    item: NotificationItem,
    enrolledCodes: Set<String> = emptySet(),
    enrolledNames: Map<String, String> = emptyMap()
): String? {
    // 1. Check actionUrl if present
    if (!item.actionUrl.isNullOrBlank()) {
        val segment = item.actionUrl.trim().split("/").lastOrNull { it.isNotBlank() }
        if (!segment.isNullOrBlank()) {
            val upper = segment.uppercase()
            if (enrolledCodes.contains(upper)) return upper
            if (upper.length in 3..12 && upper.any { it.isDigit() }) {
                return upper
            }
        }
    }

    // 2. Extract code inside parentheses in title: e.g. "Attendance: Present (CS403)" -> "CS403"
    val titleParen = Regex("""\(([A-Za-z0-9_-]+)\)""").find(item.title)
    if (titleParen != null) {
        val candidate = titleParen.groupValues[1].trim().uppercase()
        if (candidate.isNotBlank() && candidate.any { it.isDigit() }) {
            return candidate
        }
    }

    // 3. Extract code inside parentheses in message
    val msgParen = Regex("""\(([A-Za-z0-9_-]+)\)""").find(item.message)
    if (msgParen != null) {
        val candidate = msgParen.groupValues[1].trim().uppercase()
        if (candidate.isNotBlank() && candidate.any { it.isDigit() }) {
            return candidate
        }
    }

    // 4. Match against enrolled subject codes
    val textToSearch = "${item.title} ${item.message}".uppercase()
    for (code in enrolledCodes) {
        if (Regex("""\b${Regex.escape(code)}\b""").containsMatchIn(textToSearch)) {
            return code
        }
    }

    // 5. Match against enrolled subject names
    val lowerText = "${item.title} ${item.message}".lowercase()
    for ((subName, code) in enrolledNames) {
        if (subName.length >= 4 && lowerText.contains(subName)) {
            return code
        }
    }

    // 6. Generic university course code pattern: 2-5 letters followed by 3-4 digits (e.g. CS403, CAI287, AI309, MA402)
    val genericMatch = Regex("""\b([A-Z]{2,5}\d{3,4}[A-Z]?)\b""").find(textToSearch)
    if (genericMatch != null) {
        return genericMatch.groupValues[1]
    }

    return null
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    subjectCode: String?,
    onMarkRead: () -> Unit,
    onSelectSubject: (String) -> Unit
) {
    val isPostponed = item.title.contains("Postpone", ignoreCase = true) || item.message.contains("Postpone", ignoreCase = true) || item.title.contains("Reschedule", ignoreCase = true) || item.message.contains("Reschedule", ignoreCase = true)
    val isCancelled = item.title.contains("Cancel", ignoreCase = true) || item.message.contains("Cancel", ignoreCase = true)
    val isPresent = !isPostponed && !isCancelled && (item.title.contains("Present", ignoreCase = true) || item.message.contains("Present", ignoreCase = true))
    val isAbsent = !isPostponed && !isCancelled && (item.title.contains("Absent", ignoreCase = true) || item.message.contains("Absent", ignoreCase = true))
    val isLeave = !isPostponed && !isCancelled && (item.title.contains("Leave", ignoreCase = true) || item.message.contains("Leave", ignoreCase = true))
    val isTimetable = item.category == "TIMETABLE" && !isPostponed && !isCancelled

    val (iconBg, iconTint, icon) = when {
        isPostponed -> Triple(LightAmberBg, WarningAmber, Icons.Outlined.Schedule)
        isCancelled -> Triple(LightRedBg, AlertRed, Icons.Outlined.Cancel)
        isPresent -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Outlined.CheckCircle)
        isAbsent -> Triple(LightRedBg, AlertRed, Icons.Outlined.Cancel)
        isLeave -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), Icons.Outlined.EventBusy)
        isTimetable -> Triple(LightAmberBg, WarningAmber, Icons.Outlined.Schedule)
        else -> Triple(Color(0xFFEEF2FF), AccentPurple, Icons.Outlined.Campaign)
    }

    Surface(
        color = if (item.isRead) Color.White else Color(0xFFF9FBFF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isRead) CardBorder else PrimaryBlue.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (!item.isRead) {
                    onMarkRead()
                }
                if (!subjectCode.isNullOrBlank()) {
                    onSelectSubject(subjectCode)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Compact Leading Icon Badge (34dp)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconBg,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                // Header Row: Status & Subject Badges, Timestamp, Unread Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Status badge (if attendance or schedule)
                        when {
                            isPostponed -> {
                                Surface(
                                    color = LightAmberBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "POSTPONED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningAmber,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            isCancelled -> {
                                Surface(
                                    color = LightRedBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "CANCELLED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlertRed,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            isPresent -> {
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PRESENT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            isAbsent -> {
                                Surface(
                                    color = LightRedBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ABSENT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlertRed,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            isLeave -> {
                                Surface(
                                    color = Color(0xFFE0F2FE),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LEAVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            isTimetable -> {
                                Surface(
                                    color = LightAmberBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "TIMETABLE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningAmber,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }

                        // Subject Code Badge (if detected)
                        if (!subjectCode.isNullOrBlank()) {
                            Surface(
                                color = PrimaryBlue.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = subjectCode,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        } else if (!isPresent && !isAbsent && !isLeave && !isTimetable && !isPostponed && !isCancelled) {
                            Text(
                                text = item.title,
                                fontWeight = if (item.isRead) FontWeight.Bold else FontWeight.ExtraBold,
                                color = TextDark,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.timestamp,
                            fontSize = 10.sp,
                            color = TextGray
                        )
                        if (!item.isRead) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(PrimaryBlue, CircleShape)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Message Text
                Text(
                    text = item.message,
                    fontSize = 11.5.sp,
                    color = if (item.isRead) TextGray else TextDark,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Interactive Footer / Action Affordance
                if (!subjectCode.isNullOrBlank() || !item.isRead) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!subjectCode.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (!item.isRead) onMarkRead()
                                    onSelectSubject(subjectCode)
                                }
                            ) {
                                Text(
                                    text = "View $subjectCode attendance",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        if (!item.isRead) {
                            Text(
                                text = "Mark read",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryBlue,
                                modifier = Modifier
                                    .clickable { onMarkRead() }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
