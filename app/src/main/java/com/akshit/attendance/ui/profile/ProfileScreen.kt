package com.akshit.attendance.ui.profile

import java.util.Locale



import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.automirrored.filled.ExitToApp

import androidx.compose.material.icons.filled.*

import androidx.compose.material.icons.outlined.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.akshit.attendance.data.model.UserProfileResponse



private val NavyHeader = Color(0xFF1A2758)

private val PrimaryBlue = Color(0xFF002D62)

private val SurfaceLight = Color(0xFFF8FAFC)

private val CardBorder = Color(0xFFE2E8F0)

private val TextDark = Color(0xFF0F172A)

private val TextMuted = Color(0xFF64748B)

private val SemanticGreen = Color(0xFF16A34A)

private val SemanticAmber = Color(0xFFD97706)

private val SemanticRed = Color(0xFFDC2626)



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun ProfileScreen(

    viewModel: ProfileViewModel,

    onNavigateBack: () -> Unit,

    onLogout: () -> Unit

) {

    val profileState by viewModel.profile.collectAsState()

    val changePasswordState by viewModel.changePassword.collectAsState()



    var showChangePasswordDialog by remember { mutableStateOf(false) }

    var showLogoutDialog by remember { mutableStateOf(false) }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp) },

                navigationIcon = {

                    IconButton(onClick = onNavigateBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

                    }

                },

                colors = TopAppBarDefaults.topAppBarColors(

                    containerColor = Color.White,

                    titleContentColor = NavyHeader,

                    navigationIconContentColor = NavyHeader

                )

            )

        },

        containerColor = SurfaceLight

    ) { padding ->

        when (val s = profileState) {

            is ProfileUiState.Loading -> Box(

                modifier = Modifier.fillMaxSize().padding(padding),

                contentAlignment = Alignment.Center

            ) {

                CircularProgressIndicator(color = PrimaryBlue)

            }

            is ProfileUiState.Error -> Box(

                modifier = Modifier.fillMaxSize().padding(padding),

                contentAlignment = Alignment.Center

            ) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(s.message, color = SemanticRed)

                    Spacer(Modifier.height(8.dp))

                    Button(onClick = { viewModel.loadProfile() }) { Text("Retry") }

                }

            }

            is ProfileUiState.Loaded -> {

                val profile = s.profile

                LazyColumn(

                    modifier = Modifier.fillMaxSize().padding(padding),

                    contentPadding = PaddingValues(16.dp),

                    verticalArrangement = Arrangement.spacedBy(16.dp)

                ) {

                    // 1. User Header Card

                    item {

                        UserProfileHeaderCard(profile = profile)

                    }



                    // 2. Academic / University Details Card

                    item {

                        AcademicDetailsCard(profile = profile)

                    }



                    // 3. Teaching or Student Statistics Card

                    item {

                        PerformanceStatsCard(profile = profile)

                    }



                    // 4. Security & Account Actions Card

                    item {

                        SecuritySettingsCard(

                            onChangePasswordClick = {

                                viewModel.resetChangePasswordState()

                                showChangePasswordDialog = true

                            }

                        )

                    }



                    // 5. Logout Button

                    item {

                        Button(

                            onClick = { showLogoutDialog = true },

                            modifier = Modifier.fillMaxWidth().height(50.dp),

                            shape = RoundedCornerShape(12.dp),

                            colors = ButtonDefaults.buttonColors(

                                containerColor = Color(0xFFFEE2E2),

                                contentColor = SemanticRed

                            )

                        ) {

                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))

                            Spacer(Modifier.width(8.dp))

                            Text("Sign Out of Account", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        }

                    }



                    // 6. University Footer

                    item {

                        Spacer(Modifier.height(8.dp))

                        Text(

                            text = "Gautam Buddha University • Attendance Management System\nVersion 1.2.0 • 2026-27",

                            fontSize = 11.sp,

                            color = TextMuted,

                            textAlign = TextAlign.Center,

                            modifier = Modifier.fillMaxWidth()

                        )

                    }

                }

            }

        }

    }



    // Change Password Dialog

    if (showChangePasswordDialog) {

        ChangePasswordModal(

            state = changePasswordState,

            onSubmit = { old, new, confirm -> viewModel.changePassword(old, new, confirm) },

            onDismiss = {

                showChangePasswordDialog = false

                viewModel.resetChangePasswordState()

            }

        )

    }



    // Logout Dialog

    if (showLogoutDialog) {

        AlertDialog(

            onDismissRequest = { showLogoutDialog = false },

            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },

            text = { Text("Are you sure you want to sign out from this device?") },

            confirmButton = {

                Button(

                    onClick = {

                        showLogoutDialog = false

                        onLogout()

                    },

                    colors = ButtonDefaults.buttonColors(containerColor = SemanticRed)

                ) {

                    Text("Sign Out")

                }

            },

            dismissButton = {

                TextButton(onClick = { showLogoutDialog = false }) {

                    Text("Cancel")

                }

            }

        )

    }

}



@Composable

private fun UserProfileHeaderCard(profile: UserProfileResponse) {

    Surface(

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(16.dp),

        color = NavyHeader,

        shadowElevation = 3.dp

    ) {

        Column(

            modifier = Modifier.padding(20.dp),

            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            // Avatar

            val initials = profile.full_name

                .split(" ")

                .filter { it.isNotBlank() }

                .take(2)

                .map { it.first().uppercase() }

                .joinToString("")

                .ifBlank { "U" }



            Box(

                modifier = Modifier

                    .size(72.dp)

                    .clip(CircleShape)

                    .background(Color.White.copy(alpha = 0.2f)),

                contentAlignment = Alignment.Center

            ) {

                Text(

                    text = initials,

                    fontSize = 28.sp,

                    fontWeight = FontWeight.Bold,

                    color = Color.White

                )

            }



            Spacer(Modifier.height(12.dp))



            // Name

            Text(

                text = profile.full_name,

                fontSize = 20.sp,

                fontWeight = FontWeight.Bold,

                color = Color.White

            )



            // Email

            Text(

                text = profile.email,

                fontSize = 13.sp,

                color = Color.White.copy(alpha = 0.8f)

            )



            Spacer(Modifier.height(14.dp))



            // Assigned Roles Row

            Row(

                horizontalArrangement = Arrangement.spacedBy(8.dp),

                verticalAlignment = Alignment.CenterVertically

            ) {

                profile.roles.forEach { role ->

                    Surface(

                        shape = RoundedCornerShape(20.dp),

                        color = when (role.uppercase()) {

                            "TEACHER" -> Color(0xFF3B82F6)

                            "COORDINATOR" -> Color(0xFFF59E0B)

                            "STUDENT" -> Color(0xFF10B981)

                            "ADMIN" -> Color(0xFFEF4444)

                            else -> Color(0xFF6B7280)

                        }

                    ) {

                        Text(

                            text = role.uppercase(),

                            color = Color.White,

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)

                        )

                    }

                }

            }

        }

    }

}



@Composable

private fun AcademicDetailsCard(profile: UserProfileResponse) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(containerColor = Color.White),

        shape = RoundedCornerShape(16.dp),

        border = BorderStroke(1.dp, CardBorder)

    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(

                text = "Academic Affiliation",

                fontWeight = FontWeight.Bold,

                fontSize = 15.sp,

                color = TextDark

            )

            Spacer(Modifier.height(12.dp))



            DetailItem(icon = Icons.Outlined.School, label = "School", value = profile.school)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

            DetailItem(icon = Icons.Outlined.Business, label = "Department", value = profile.department)



            if (!profile.roll_number.isNullOrBlank()) {

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                DetailItem(icon = Icons.Outlined.Badge, label = "Roll Number", value = profile.roll_number)

            }

            if (!profile.employee_code.isNullOrBlank()) {

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                DetailItem(icon = Icons.Outlined.Badge, label = "Faculty ID", value = profile.employee_code)

            }

            if (!profile.program_name.isNullOrBlank()) {

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                DetailItem(icon = Icons.Outlined.MenuBook, label = "Program", value = profile.program_name)

            }

            if (!profile.batch_name.isNullOrBlank()) {

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                DetailItem(icon = Icons.Outlined.Groups, label = "Batch", value = profile.batch_name)

            }

        }

    }

}



@Composable

private fun PerformanceStatsCard(profile: UserProfileResponse) {

    val isStudent = !profile.roll_number.isNullOrBlank() || profile.roles.contains("STUDENT")



    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(containerColor = Color.White),

        shape = RoundedCornerShape(16.dp),

        border = BorderStroke(1.dp, CardBorder)

    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(

                text = if (isStudent) "Attendance Standing" else "Teaching Workload",

                fontWeight = FontWeight.Bold,

                fontSize = 15.sp,

                color = TextDark

            )

            Spacer(Modifier.height(12.dp))



            if (isStudent) {

                val pct = profile.overall_attendance_pct ?: 100.0

                val isGood = pct >= 75.0

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Column(modifier = Modifier.weight(1f)) {

                        Text("Cumulative Attendance", fontSize = 12.sp, color = TextMuted)

                        val pctText = if (pct % 1.0 == 0.0) "${pct.toInt()}.0%" else "${String.format(Locale.ENGLISH, "%.1f", pct)}%"

                        Text(

                            text = pctText,

                            fontSize = 22.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = if (isGood) SemanticGreen else SemanticAmber

                        )

                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(

                        shape = RoundedCornerShape(8.dp),

                        color = if (isGood) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)

                    ) {

                        Text(

                            text = if (isGood) "Eligible (≥ 75%)" else "Shortage (< 75%)",

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            color = if (isGood) SemanticGreen else SemanticAmber,

                            maxLines = 1,

                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)

                        )

                    }

                }

            } else {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(16.dp)

                ) {

                    Surface(

                        modifier = Modifier.weight(1f),

                        shape = RoundedCornerShape(12.dp),

                        color = Color(0xFFEFF6FF),

                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))

                    ) {

                        Column(modifier = Modifier.padding(14.dp)) {

                            Text("Courses / Batches", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)

                            Spacer(Modifier.height(4.dp))

                            Text(

                                text = "${profile.total_courses ?: 0}",

                                fontSize = 20.sp,

                                fontWeight = FontWeight.Bold,

                                color = PrimaryBlue

                            )

                        }

                    }



                    Surface(

                        modifier = Modifier.weight(1f),

                        shape = RoundedCornerShape(12.dp),

                        color = Color(0xFFECFDF5),

                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))

                    ) {

                        Column(modifier = Modifier.padding(14.dp)) {

                            Text("Weekly Teaching", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)

                            Spacer(Modifier.height(4.dp))

                            Text(

                                text = "${profile.weekly_hours?.toInt() ?: 0} hrs/wk",

                                fontSize = 20.sp,

                                fontWeight = FontWeight.Bold,

                                color = Color(0xFF065F46)

                            )

                        }

                    }

                }

            }

        }

    }

}



@Composable

private fun SecuritySettingsCard(onChangePasswordClick: () -> Unit) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(containerColor = Color.White),

        shape = RoundedCornerShape(16.dp),

        border = BorderStroke(1.dp, CardBorder)

    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(

                text = "Security & Credentials",

                fontWeight = FontWeight.Bold,

                fontSize = 15.sp,

                color = TextDark

            )

            Spacer(Modifier.height(12.dp))



            Surface(

                modifier = Modifier

                    .fillMaxWidth()

                    .clip(RoundedCornerShape(10.dp))

                    .clickable { onChangePasswordClick() },

                color = Color(0xFFF8FAFC),

                border = BorderStroke(1.dp, Color(0xFFE2E8F0))

            ) {

                Row(

                    modifier = Modifier.padding(14.dp),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))

                        Spacer(Modifier.width(12.dp))

                        Column {

                            Text("Change Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)

                            Text("Update your portal login password", fontSize = 11.sp, color = TextMuted)

                        }

                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)

                }

            }

        }

    }

}



@Composable

private fun DetailItem(icon: ImageVector, label: String, value: String) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.SpaceBetween,

        verticalAlignment = Alignment.CenterVertically

    ) {

        Row(

            verticalAlignment = Alignment.CenterVertically,

            modifier = Modifier.weight(1f)

        ) {

            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))

            Spacer(Modifier.width(8.dp))

            Text(label, fontSize = 12.5.sp, color = TextMuted)

        }

        Spacer(Modifier.width(8.dp))

        Text(

            text = value,

            fontSize = 12.5.sp,

            fontWeight = FontWeight.SemiBold,

            color = TextDark,

            textAlign = TextAlign.End,

            modifier = Modifier.weight(1.8f)

        )

    }

}



@Composable

private fun ChangePasswordModal(

    state: ChangePasswordUiState,

    onSubmit: (String, String, String) -> Unit,

    onDismiss: () -> Unit

) {

    var oldPass by remember { mutableStateOf("") }

    var newPass by remember { mutableStateOf("") }

    var confirmPass by remember { mutableStateOf("") }

    var showOld by remember { mutableStateOf(false) }

    var showNew by remember { mutableStateOf(false) }



    AlertDialog(

        onDismissRequest = onDismiss,

        title = { Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 18.sp) },

        text = {

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                if (state is ChangePasswordUiState.Success) {

                    Surface(

                        shape = RoundedCornerShape(8.dp),

                        color = Color(0xFFDCFCE7),

                        modifier = Modifier.fillMaxWidth()

                    ) {

                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticGreen)

                            Spacer(Modifier.width(8.dp))

                            Text("Password updated successfully!", color = SemanticGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        }

                    }

                } else {

                    if (state is ChangePasswordUiState.Error) {

                        Surface(

                            shape = RoundedCornerShape(8.dp),

                            color = Color(0xFFFEE2E2),

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Text(

                                text = state.message,

                                color = SemanticRed,

                                fontSize = 12.sp,

                                modifier = Modifier.padding(10.dp)

                            )

                        }

                    }



                    OutlinedTextField(

                        value = oldPass,

                        onValueChange = { oldPass = it },

                        label = { Text("Current Password") },

                        visualTransformation = if (showOld) VisualTransformation.None else PasswordVisualTransformation(),

                        trailingIcon = {

                            IconButton(onClick = { showOld = !showOld }) {

                                Icon(if (showOld) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)

                            }

                        },

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true

                    )



                    OutlinedTextField(

                        value = newPass,

                        onValueChange = { newPass = it },

                        label = { Text("New Password (min 6 chars)") },

                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),

                        trailingIcon = {

                            IconButton(onClick = { showNew = !showNew }) {

                                Icon(if (showNew) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)

                            }

                        },

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true

                    )



                    OutlinedTextField(

                        value = confirmPass,

                        onValueChange = { confirmPass = it },

                        label = { Text("Confirm New Password") },

                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true

                    )

                }

            }

        },

        confirmButton = {

            if (state is ChangePasswordUiState.Success) {

                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {

                    Text("Done")

                }

            } else {

                Button(

                    onClick = { onSubmit(oldPass, newPass, confirmPass) },

                    enabled = state !is ChangePasswordUiState.Submitting && oldPass.isNotBlank() && newPass.isNotBlank(),

                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)

                ) {

                    if (state is ChangePasswordUiState.Submitting) {

                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))

                    } else {

                        Text("Update Password")

                    }

                }

            }

        },

        dismissButton = {

            if (state !is ChangePasswordUiState.Success) {

                TextButton(onClick = onDismiss) { Text("Cancel") }

            }

        }

    )

}

