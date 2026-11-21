package com.akshit.attendance.ui.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.akshit.attendance.data.repository.TeacherRepository
import com.akshit.attendance.ui.components.AppBottomNav
import com.akshit.attendance.ui.components.teacherDestinations
import com.akshit.attendance.ui.theme.SurfaceBg
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeacherMainPagerScreen(
    viewModel: TeacherViewModel,
    repo: TeacherRepository,
    initialPage: Int = 0,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToOfflineQueue: () -> Unit = {},
    onStartRollCall: (courseOfferingId: Int, sessionDate: String?, batchLabel: String?) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, teacherDestinations.size - 1),
        pageCount = { teacherDestinations.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // History stack to track visited tab pages for natural back navigation
    val pageHistory = remember { mutableStateListOf<Int>() }
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }
    var isBackNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

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
        // When user explicitly arrives at Classes (Home), clear back history
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
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AppBottomNav(
                destinations = teacherDestinations,
                currentRoute = teacherDestinations.getOrNull(pagerState.currentPage)?.route,
                onNavigateToOfflineQueue = onNavigateToOfflineQueue
            ) { targetRoute ->
                val targetIndex = teacherDestinations.indexOfFirst { it.route == targetRoute }
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
                    0 -> TeacherClassesScreen(
                        viewModel = viewModel,
                        onStartRollCall = onStartRollCall,
                        onLogout = onLogout,
                        onOpenProfile = onOpenProfile,
                        onNavigateToNotifications = onNavigateToNotifications,
                        onNavigateToOfflineQueue = onNavigateToOfflineQueue
                    )
                    1 -> TeacherTimetableScreen(
                        viewModel = viewModel,
                        onStartRollCall = { courseOfferingId ->
                            onStartRollCall(courseOfferingId, null, null)
                        },
                        onLogout = onLogout,
                        onOpenProfile = onOpenProfile,
                        onNavigateToNotifications = onNavigateToNotifications
                    )
                    2 -> TeacherAnalyticsScreen(
                        viewModel = viewModel,
                        repo = repo,
                        onOpenProfile = onOpenProfile,
                        onLogout = onLogout,
                        onNavigateToNotifications = onNavigateToNotifications
                    )
                    3 -> TeacherRecordsScreen(
                        repo = repo,
                        onEditSession = { offeringId, sessionDate, batchLabel ->
                            onStartRollCall(offeringId, sessionDate, batchLabel)
                        },
                        onOpenProfile = onOpenProfile,
                        onLogout = onLogout,
                        onNavigateToNotifications = onNavigateToNotifications
                    )
                }
            }
        }
    }
}
