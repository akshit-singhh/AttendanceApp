package com.akshit.attendance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.navigation.Routes
import kotlinx.coroutines.delay

data class NavDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val studentDestinations = listOf(
    NavDestination(Routes.STUDENT_DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    NavDestination(Routes.STUDENT_SUBJECTS, "Subjects", Icons.AutoMirrored.Filled.MenuBook),
    NavDestination(Routes.STUDENT_TIMETABLE, "Timetable", Icons.Default.Schedule),
    NavDestination(Routes.STUDENT_PLANNER, "Planner", Icons.AutoMirrored.Filled.TrendingUp),
    NavDestination(Routes.STUDENT_CALENDAR, "Calendar", Icons.Default.CalendarMonth),
)

val teacherDestinations = listOf(
    NavDestination(Routes.TEACHER_CLASSES, "Classes", Icons.Default.CalendarMonth),
    NavDestination(Routes.TEACHER_TIMETABLE, "Timetable", Icons.Default.Schedule),
    NavDestination(Routes.TEACHER_RECORDS, "Records", Icons.Default.Folder),
)

enum class ConnectivityBannerState {
    HIDDEN,
    OFFLINE,
    BACK_ONLINE
}

@Composable
fun ConnectivityStrip(modifier: Modifier = Modifier) {
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()
    val queuedCount by OfflineAttendanceManager.queuedCount.collectAsState()
    val isSyncing by OfflineAttendanceManager.isSyncing.collectAsState()

    var wasOffline by remember { mutableStateOf(false) }
    var bannerState by remember { mutableStateOf(ConnectivityBannerState.HIDDEN) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOffline = true
            bannerState = ConnectivityBannerState.OFFLINE
        } else {
            if (wasOffline) {
                bannerState = ConnectivityBannerState.BACK_ONLINE
                delay(2800L)
                bannerState = ConnectivityBannerState.HIDDEN
                wasOffline = false
            } else {
                bannerState = ConnectivityBannerState.HIDDEN
            }
        }
    }

    val isVisible = bannerState != ConnectivityBannerState.HIDDEN
    val isBackOnline = bannerState == ConnectivityBannerState.BACK_ONLINE

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isBackOnline) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
            border = BorderStroke(
                width = 1.dp,
                color = if (isBackOnline) Color(0xFF86EFAC) else Color(0xFFFDE68A)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isBackOnline) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isBackOnline) Color(0xFF15803D) else Color(0xFFB45309)
                )
                Spacer(Modifier.width(6.dp))
                val message = when {
                    isBackOnline && isSyncing -> "Back online \u2022 Syncing attendance..."
                    isBackOnline -> "Back online"
                    queuedCount > 0 -> "Offline \u2022 $queuedCount session(s) queued"
                    else -> "Offline \u2022 Working with cached data"
                }
                Text(
                    text = message,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBackOnline) Color(0xFF15803D) else Color(0xFF92400E)
                )
            }
        }
    }
}

@Composable
fun AppBottomNav(destinations: List<NavDestination>, currentRoute: String?, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ConnectivityStrip()
        NavigationBar(
            containerColor = com.akshit.attendance.ui.theme.SurfaceBg,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { dest ->
                NavigationBarItem(
                    selected = currentRoute == dest.route,
                    onClick = { onNavigate(dest.route) },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = {
                        Text(
                            dest.label,
                            maxLines = 1,
                            fontSize = 10.sp,
                            softWrap = false
                        )
                    },
                )
            }
        }
    }
}
