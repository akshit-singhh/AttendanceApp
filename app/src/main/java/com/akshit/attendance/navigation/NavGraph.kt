package com.akshit.attendance.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.akshit.attendance.ui.auth.LoginScreen
import com.akshit.attendance.ui.auth.RolePickerScreen
import com.akshit.attendance.ui.components.AppBottomNav
import com.akshit.attendance.ui.theme.SurfaceBg
import com.akshit.attendance.ui.components.studentDestinations
import com.akshit.attendance.ui.components.teacherDestinations
import com.akshit.attendance.ui.student.*
import com.akshit.attendance.ui.teacher.TeacherTimetableScreen
import com.akshit.attendance.ui.teacher.TeacherClassesScreen
import com.akshit.attendance.ui.teacher.TeacherRecordsScreen
import com.akshit.attendance.ui.teacher.TeacherRollCallScreen
import com.akshit.attendance.ui.teacher.TeacherViewModel
import com.akshit.attendance.ui.profile.ProfileScreen
import com.akshit.attendance.ui.profile.ProfileViewModel
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

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun AppNavGraph(app: AttendanceApp) {
    val navController = rememberNavController()
    val factory = remember { ViewModelFactory(app) }
    val authViewModel: AuthViewModel = viewModel(factory = factory)

    var pendingPassword by remember { mutableStateOf("") }
    var cachedRoles by remember { mutableStateOf<List<RoleOption>>(emptyList()) }

    val startDest = remember(authViewModel.state.value) {
        val currentState = authViewModel.state.value
        if (currentState is AuthUiState.Success) {
            destinationForUser(currentState.response.active_role, currentState.response.available_roles)
        } else {
            Routes.LOGIN
        }
    }

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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.tokenManager.sessionExpiredFlow.collect {
            android.widget.Toast.makeText(context, "Session expired. Please log in again.", android.widget.Toast.LENGTH_LONG).show()
            authViewModel.resetState()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val performLogout: () -> Unit = {
        authViewModel.logout {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoggedIn = { response, passwordUsed ->
                    pendingPassword = passwordUsed
                    cachedRoles = response.available_roles
                    authViewModel.resetState()
                    routeAfterAuth(response)
                },
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
                onNavigateToNotifications = { navController.navigate(Routes.STUDENT_NOTIFICATIONS) }
            )
        }

        composable(Routes.STUDENT_DASHBOARD) { RenderStudentPager(0) }
        composable(Routes.STUDENT_SUBJECTS) { RenderStudentPager(1) }
        composable(Routes.STUDENT_TIMETABLE) { RenderStudentPager(2) }
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
            StudentScaffold(navController, factory) { vm ->
                StudentNotificationsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.STUDENT_STATISTICS) {
            StudentScaffold(navController, factory) { vm ->
                StudentStatisticsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.STUDENT_HISTORY) { StudentScaffold(navController, factory) { StudentHistoryScreen(it) } }
        composable(Routes.STUDENT_LEAVES) { StudentScaffold(navController, factory) { StudentLeaveScreen(it) } }
        composable(Routes.TEACHER_CLASSES) {
            TeacherScaffold(navController, factory) { vm ->
                TeacherClassesScreen(
                    viewModel = vm,
                    onStartRollCall = { courseOfferingId, sessionDate ->
                        navController.navigate(Routes.rollCall(courseOfferingId, sessionDate))
                    },
                    onLogout = performLogout,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }

        composable(
            route = Routes.TEACHER_ROLL_CALL,
            arguments = listOf(
                navArgument("courseOfferingId") { type = NavType.IntType },
                navArgument("sessionDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val offeringId = backStackEntry.arguments?.getInt("courseOfferingId") ?: 0
            val teacherViewModel: TeacherViewModel = viewModel(factory = factory)
            val sessionDate = backStackEntry.arguments?.getString("sessionDate")
            TeacherRollCallScreen(teacherViewModel, offeringId, sessionDate) { navController.popBackStack() }
        }

        composable(Routes.TEACHER_TIMETABLE) {
            TeacherScaffold(navController, factory) { vm ->
                TeacherTimetableScreen(
                    viewModel = vm,
                    onStartRollCall = { courseOfferingId ->
                        navController.navigate(Routes.rollCall(courseOfferingId))
                    },
                    onLogout = performLogout,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }
        composable(Routes.TEACHER_RECORDS) { TeacherScaffold(navController, factory) { TeacherRecordsScreen(app.teacherRepository) } }

        composable(Routes.PROFILE) {
            val profileVm: ProfileViewModel = viewModel(factory = factory)
            ProfileScreen(
                viewModel = profileVm,
                onNavigateBack = { navController.popBackStack() },
                onLogout = performLogout
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

@Composable
private fun TeacherScaffold(navController: NavHostController, factory: ViewModelFactory, content: @Composable (TeacherViewModel) -> Unit) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val teacherViewModel: TeacherViewModel = viewModel(factory = factory)
    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AppBottomNav(teacherDestinations, backStackEntry?.destination?.route) { route ->
                val target = if (route == Routes.TEACHER_ROLL_CALL) Routes.TEACHER_CLASSES else route
                navController.navigate(target) { launchSingleTop = true }
            }
        },
    ) { padding ->
        // FIX: Eliminates the double-padding white gap at the top
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) { content(teacherViewModel) }
    }
}
