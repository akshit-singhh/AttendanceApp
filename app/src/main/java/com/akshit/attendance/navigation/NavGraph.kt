package com.akshit.attendance.navigation

import com.akshit.attendance.data.offline.OfflineAttendanceManager

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.akshit.attendance.AttendanceApp
import com.akshit.attendance.data.model.RoleOption
import com.akshit.attendance.data.model.TokenResponse
import com.akshit.attendance.ui.auth.AuthUiState
import com.akshit.attendance.ui.auth.AuthViewModel
import com.akshit.attendance.ui.auth.CreatePasswordScreen
import com.akshit.attendance.ui.auth.ForgotPasswordScreen
import com.akshit.attendance.ui.auth.LoginScreen
import com.akshit.attendance.ui.auth.RolePickerScreen
import com.akshit.attendance.ui.components.AppBottomNav
import com.akshit.attendance.ui.theme.SurfaceBg
import com.akshit.attendance.ui.components.studentDestinations
import com.akshit.attendance.ui.components.teacherDestinations
import com.akshit.attendance.ui.student.*
import com.akshit.attendance.ui.teacher.TeacherMainPagerScreen
import com.akshit.attendance.ui.teacher.TeacherNotificationsScreen
import com.akshit.attendance.ui.teacher.TeacherOfflineQueueScreen
import com.akshit.attendance.ui.teacher.TeacherRollCallScreen
import com.akshit.attendance.ui.teacher.TeacherViewModel
import com.akshit.attendance.ui.profile.NotificationSettingsScreen
import com.akshit.attendance.ui.profile.ProfileScreen
import com.akshit.attendance.ui.profile.ProfileViewModel
import com.akshit.attendance.ui.splash.SplashScreen
import kotlinx.coroutines.launch

private fun destinationForUser(role: String, availableRoles: List<RoleOption> = emptyList()): String {
    val roles = availableRoles.map { it.role.uppercase() }
    return when {
        // In mobile app, all faculty/coordinator/admin roles continue directly as TEACHER
        roles.contains("TEACHER") || roles.contains("COORDINATOR") || roles.contains("HOD") || roles.contains("DEAN") || roles.contains("ADMIN") -> Routes.TEACHER_CLASSES
        roles.contains("STUDENT") -> Routes.STUDENT_DASHBOARD
        role.uppercase() in listOf("TEACHER", "COORDINATOR", "HOD", "DEAN", "ADMIN") -> Routes.TEACHER_CLASSES
        else -> Routes.STUDENT_DASHBOARD
    }
}

@Composable
fun AppNavGraph(app: AttendanceApp, startDestination: String = Routes.LOGIN) {
    val navController = rememberNavController()
    val factory = remember { ViewModelFactory(app) }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val queuedAttendanceCount by OfflineAttendanceManager.queuedCount.collectAsState()

    var pendingPassword by remember { mutableStateOf("") }
    var cachedRoles by remember { mutableStateOf<List<RoleOption>>(emptyList()) }

    fun routeAfterAuth(response: TokenResponse) {
        // App is strictly for Teacher & Student — bypass RolePicker and continue as Teacher directly
        val destination = when {
            response.must_change_password -> Routes.CREATE_PASSWORD
            else -> destinationForUser(response.active_role, response.available_roles)
        }
        val isIntermediate = destination == Routes.CREATE_PASSWORD
        navController.navigate(destination) {
            popUpTo(Routes.LOGIN) { inclusive = !isIntermediate }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastToastTime by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.tokenManager.sessionExpiredFlow.collect {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val now = System.currentTimeMillis()
            if (currentRoute != Routes.LOGIN && currentRoute != Routes.SPLASH && !app.tokenManager.isLoggingOut()) {
                if (now - lastToastTime > 4000L) {
                    lastToastTime = now
                    android.widget.Toast.makeText(context, "Session expired. Please log in again.", android.widget.Toast.LENGTH_LONG).show()
                }
                authViewModel.resetState()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val performLogout: () -> Unit = {
        app.tokenManager.markLoggingOut()
        authViewModel.logout {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(
            Routes.SPLASH,
            exitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            SplashScreen(
                tokenManager = app.tokenManager,
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                app.tokenManager.resetLoggingOut()
            }
            LoginScreen(
                viewModel = authViewModel,
                onLoggedIn = { response, passwordUsed ->
                    app.tokenManager.resetLoggingOut()
                    pendingPassword = passwordUsed
                    cachedRoles = response.available_roles
                    authViewModel.resetState()
                    routeAfterAuth(response)
                },
                onForgotPassword = {
                    authViewModel.resetForgotPasswordState()
                    navController.navigate(Routes.FORGOT_PASSWORD)
                }
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = {
                    authViewModel.resetForgotPasswordState()
                    navController.popBackStack()
                },
                onResetSuccess = {
                    authViewModel.resetForgotPasswordState()
                    navController.popBackStack(Routes.LOGIN, inclusive = false)
                }
            )
        }

        composable(Routes.CREATE_PASSWORD) {
            CreatePasswordScreen(
                viewModel = authViewModel,
                tempPassword = pendingPassword,
                onPasswordSet = { response ->
                    authViewModel.resetState()
                    routeAfterAuth(response)
                },
                onBackToLogin = {
                    authViewModel.resetState()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CREATE_PASSWORD) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ROLE_PICKER) {
            // RolePicker completely bypassed — automatically forward to teacher classes
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val dest = destinationForUser(
                    (authViewModel.state.value as? AuthUiState.Success)?.response?.active_role ?: "TEACHER",
                    cachedRoles
                )
                navController.navigate(dest) {
                    popUpTo(Routes.ROLE_PICKER) { inclusive = true }
                }
            }
        }

        @Composable
        fun RenderStudentPager(initialPage: Int) {
            val studentViewModel: StudentViewModel = viewModel(factory = factory)
            StudentMainPagerScreen(
                viewModel = studentViewModel,
                initialPage = initialPage,
                onLogout = performLogout,
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onSelectSubject = { code -> navController.navigate(Routes.subjectDetail(code)) },
                onNavigateToStatistics = { navController.navigate(Routes.STUDENT_STATISTICS) },
                onNavigateToNotifications = { navController.navigate(Routes.STUDENT_NOTIFICATIONS) },
                onNavigateToDocuments = { navController.navigate(Routes.UNIVERSITY_DOCS) }
            )
        }

        composable(Routes.STUDENT_DASHBOARD) { RenderStudentPager(0) }
        composable(Routes.STUDENT_TIMETABLE) { RenderStudentPager(1) }
        composable(Routes.STUDENT_SUBJECTS) { RenderStudentPager(2) }
        composable(Routes.STUDENT_PLANNER) { RenderStudentPager(3) }
        composable(Routes.STUDENT_CALENDAR) { RenderStudentPager(4) }
        composable(
            route = Routes.STUDENT_SUBJECT_DETAIL,
            arguments = listOf(navArgument("subjectCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val studentViewModel: StudentViewModel = viewModel(factory = factory)
            SubjectDetailScreen(
                subjectCode = code,
                viewModel = studentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STUDENT_NOTIFICATIONS) {
            val studentViewModel: StudentViewModel = viewModel(factory = factory)
            StudentNotificationsScreen(
                viewModel = studentViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSelectSubject = { code -> navController.navigate(Routes.subjectDetail(code)) }
            )
        }
        composable(Routes.UNIVERSITY_DOCS) {
            UniversityDocumentsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STUDENT_STATISTICS) {
            val studentViewModel: StudentViewModel = viewModel(factory = factory)
            StudentStatisticsScreen(
                viewModel = studentViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSelectSubject = { courseCode -> navController.navigate(Routes.subjectDetail(courseCode)) }
            )
        }
        composable(Routes.STUDENT_HISTORY) { StudentScaffold(navController, factory) { StudentHistoryScreen(it) } }
        composable(Routes.STUDENT_LEAVES) { StudentScaffold(navController, factory) { StudentLeaveScreen(it) } }
        @Composable
        fun RenderTeacherPager(initialPage: Int) {
            val teacherViewModel: TeacherViewModel = viewModel(factory = factory)
            TeacherMainPagerScreen(
                viewModel = teacherViewModel,
                repo = app.teacherRepository,
                initialPage = initialPage,
                onLogout = performLogout,
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToNotifications = { navController.navigate(Routes.TEACHER_NOTIFICATIONS) },
                onNavigateToOfflineQueue = { navController.navigate(Routes.TEACHER_OFFLINE_QUEUE) },
                onStartRollCall = { offeringId, sessionDate, batchLabel ->
                    navController.navigate(Routes.rollCall(offeringId, sessionDate, batchLabel))
                }
            )
        }

        composable(Routes.TEACHER_CLASSES) { RenderTeacherPager(0) }
        composable(Routes.TEACHER_TIMETABLE) { RenderTeacherPager(1) }
        composable(Routes.TEACHER_ANALYTICS) { RenderTeacherPager(2) }
        composable(Routes.TEACHER_RECORDS) { RenderTeacherPager(3) }

        composable(
            route = Routes.TEACHER_ROLL_CALL,
            arguments = listOf(
                navArgument("courseOfferingId") { type = NavType.IntType },
                navArgument("sessionDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("batchLabel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val offeringId = backStackEntry.arguments?.getInt("courseOfferingId") ?: 0
            val parentEntry = remember(backStackEntry) {
                try {
                    navController.getBackStackEntry(Routes.TEACHER_CLASSES)
                } catch (_: Exception) {
                    null
                }
            }
            val teacherViewModel: TeacherViewModel = if (parentEntry != null) {
                viewModel(parentEntry, factory = factory)
            } else {
                viewModel(factory = factory)
            }
            val sessionDate = backStackEntry.arguments?.getString("sessionDate")
            val batchLabel = backStackEntry.arguments?.getString("batchLabel")
            TeacherRollCallScreen(
                viewModel = teacherViewModel,
                courseOfferingId = offeringId,
                sessionDate = sessionDate,
                initialBatchLabel = batchLabel,
                onNavigateBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToNotifications = { navController.navigate(Routes.TEACHER_NOTIFICATIONS) },
                onSubmitted = {
                    teacherViewModel.loadSchedule(isRefresh = false)
                    teacherViewModel.loadPendingClasses()
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TEACHER_NOTIFICATIONS) {
            TeacherNotificationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TEACHER_OFFLINE_QUEUE) {
            val teacherViewModel: TeacherViewModel = viewModel(factory = factory)
            TeacherOfflineQueueScreen(
                viewModel = teacherViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRollCall = { offeringId, sessionDate, batchLabel ->
                    navController.navigate(Routes.rollCall(offeringId, sessionDate, batchLabel))
                }
            )
        }

        composable(Routes.PROFILE) {
            val profileVm: ProfileViewModel = viewModel(factory = factory)
            ProfileScreen(
                viewModel = profileVm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationSettings = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onNavigateToDocuments = { navController.navigate(Routes.UNIVERSITY_DOCS) },
                onLogout = performLogout,
                pendingAttendanceCount = queuedAttendanceCount
            )
        }
        composable(Routes.NOTIFICATION_SETTINGS) {
            val profileVm: ProfileViewModel = viewModel(factory = factory)
            NotificationSettingsScreen(
                viewModel = profileVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun StudentScaffold(navController: NavHostController, factory: ViewModelFactory, content: @Composable (StudentViewModel) -> Unit) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val studentViewModel: StudentViewModel = viewModel(factory = factory)
    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AppBottomNav(studentDestinations, backStackEntry?.destination?.route) { route ->
                navController.navigate(route) { launchSingleTop = true }
            }
        },
    ) { padding ->
        // FIX: Eliminates the double-padding white gap at the top
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) { content(studentViewModel) }
    }
}


