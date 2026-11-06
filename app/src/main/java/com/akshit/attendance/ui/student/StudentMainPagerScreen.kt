package com.akshit.attendance.ui.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import com.akshit.attendance.ui.theme.SurfaceBg
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

    // History stack to track visited tab pages for natural back navigation
    val pageHistory = remember { mutableStateListOf<Int>() }
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }
    var isBackNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val current = pagerState.currentPage
        if (current != previousPage) {
            if (!isBackNavigation) {
                // Record previous page into history, avoiding duplicate entries
                if (pageHistory.lastOrNull() != previousPage) {
                    pageHistory.add(previousPage)
                }
            }
            isBackNavigation = false
            previousPage = current
        }
        // When user explicitly arrives at the Home Dashboard, clear back history
        if (current == 0) {
            pageHistory.clear()
        }
    }

    // Intercept back button: go back to the previous screen/tab instead of exiting app
    BackHandler(enabled = pagerState.currentPage != 0 || pageHistory.isNotEmpty()) {
        if (pageHistory.isNotEmpty()) {
            val targetPage = pageHistory.removeAt(pageHistory.lastIndex)
            isBackNavigation = true
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetPage)
            }
        } else if (pagerState.currentPage != 0) {
            isBackNavigation = true
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

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
                        viewModel = viewModel
                    )
                    4 -> StudentCalendarScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
