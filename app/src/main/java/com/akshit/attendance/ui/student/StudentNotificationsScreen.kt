package com.akshit.attendance.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.notification.AttendanceNotificationManager
import com.akshit.attendance.notification.NotificationItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentNotificationsScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val notifications by AttendanceNotificationManager.notifications.collectAsState()
    val connectionStatus by AttendanceNotificationManager.connectionStatus.collectAsState()

    var filterCategory by remember { mutableStateOf("ALL") }

    val unreadCount = notifications.count { !it.isRead }

    val displayedList = remember(notifications, filterCategory) {
        when (filterCategory) {
            "ATTENDANCE" -> notifications.filter { it.category == "ATTENDANCE" }
            "ACADEMIC" -> notifications.filter { it.category == "ACADEMIC" }
            "TIMETABLE" -> notifications.filter { it.category == "TIMETABLE" }
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
                    Column {
                        Text(
                            "NOTIFICATIONS",
                            fontSize = 10.sp,
                            color = TextGray,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (unreadCount > 0) "Inbox ($unreadCount new)" else "Inbox",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    IconButton(onClick = { AttendanceNotificationManager.fetchHistory() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = TextDark)
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter Chips Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        label = { Text("Attendance", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryGreen,
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
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
                            Text(
                                "No Notifications Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextDark
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Real-time alerts will appear here whenever faculty records attendance or updates your schedule.",
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
                    NotificationCard(
                        item = item,
                        onMarkRead = { AttendanceNotificationManager.markAsRead(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    onMarkRead: () -> Unit
) {
    val isPresent = item.title.contains("Present", ignoreCase = true) || item.message.contains("Present", ignoreCase = true)
    val isAbsent = item.title.contains("Absent", ignoreCase = true) || item.message.contains("Absent", ignoreCase = true)

    val (iconBg, iconTint, icon) = when {
        isPresent -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Outlined.CheckCircle)
        isAbsent -> Triple(LightRedBg, AlertRed, Icons.Outlined.WarningAmber)
        item.category == "TIMETABLE" -> Triple(LightAmberBg, WarningAmber, Icons.Outlined.CalendarMonth)
        else -> Triple(Color(0xFFEEF2FF), AccentPurple, Icons.Outlined.Campaign)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRead) Color.White else Color(0xFFF8FAFC)
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (item.isRead) 1.dp else 1.5.dp,
            color = if (item.isRead) CardBorder else PrimaryBlue.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!item.isRead) onMarkRead()
            }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        fontWeight = if (item.isRead) FontWeight.Bold else FontWeight.ExtraBold,
                        color = TextDark,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.timestamp,
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    item.message,
                    fontSize = 12.sp,
                    color = if (item.isRead) TextGray else TextDark,
                    lineHeight = 16.sp
                )

                if (!item.isRead) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(6.dp)
                        ) {}

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryBlue.copy(alpha = 0.08f),
                            modifier = Modifier.clickable { onMarkRead() }
                        ) {
                            Text(
                                "Mark as read",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
