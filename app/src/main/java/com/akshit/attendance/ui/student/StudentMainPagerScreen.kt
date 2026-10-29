package com.akshit.attendance.ui.student

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import com.akshit.attendance.ui.theme.SurfaceBg
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.akshit.attendance.ui.components.AppBottomNav
import com.akshit.attendance.ui.components.studentDestinations
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StudentMainPagerScreen(
    viewModel: StudentViewModel,
    initialPage: Int = 0,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, studentDestinations.size - 1),
        pageCount = { studentDestinations.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = SurfaceBg,
        bottomBar = {
            AppBottomNav(
                destinations = studentDestinations,
                currentRoute = studentDestinations.getOrNull(pagerState.currentPage)?.route
            ) { targetRoute ->
                val targetIndex = studentDestinations.indexOfFirst { it.route == targetRoute }
                if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> StudentDashboardScreen(
                        viewModel = viewModel,
                        onLogout = onLogout,
                        onOpenProfile = onOpenProfile,
                        onNavigateToSubjects = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onNavigateToPlanner = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        onNavigateToCalendar = {
                            coroutineScope.launch { pagerState.animateScrollToPage(4) }
                        },
                        onNavigateToStatistics = onNavigateToStatistics,
                        onNavigateToTimetable = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onNavigateToNotifications = onNavigateToNotifications
                    )
                    1 -> StudentSubjectsScreen(
                        viewModel = viewModel,
                        onSelectSubject = onSelectSubject
                    )
                    2 -> StudentTimetableScreen(
                        viewModel = viewModel,
                        onLogout = onLogout,
                        onOpenProfile = onOpenProfile
                    )
                    3 -> AttendancePlannerScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                    4 -> StudentCalendarScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                }
            }
        }
    }
}
