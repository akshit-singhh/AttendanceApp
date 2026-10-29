package com.akshit.attendance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.navigation.Routes

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

@Composable
fun AppBottomNav(destinations: List<NavDestination>, currentRoute: String?, onNavigate: (String) -> Unit) {
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
