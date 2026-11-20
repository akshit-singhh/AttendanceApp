package com.akshit.attendance.ui.profile

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.ui.theme.SurfaceBg

private val PrimaryBlue = Color(0xFF002D62)
private val SurfaceLight = Color(0xFFF8FAFC)
private val CardBorder = Color(0xFFE2E8F0)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val SemanticGreen = Color(0xFF16A34A)
private val SemanticRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val isTeacher by viewModel.isTeacher.collectAsState()

    // Student settings
    val notifyOnPresent by viewModel.notifyOnPresent.collectAsState()
    val notifyOnAbsent by viewModel.notifyOnAbsent.collectAsState()
    val notifyOnCorrection by viewModel.notifyOnCorrection.collectAsState()

    // Teacher settings
    val notifyTeacherPendingAttendance by viewModel.notifyTeacherPendingAttendance.collectAsState()
    val notifyTeacherUpcomingClass by viewModel.notifyTeacherUpcomingClass.collectAsState()
    val notifyTeacherLeaveRequests by viewModel.notifyTeacherLeaveRequests.collectAsState()
    val notifyTeacherAnnouncements by viewModel.notifyTeacherAnnouncements.collectAsState()

    val context = LocalContext.current

    Scaffold(
        containerColor = SurfaceBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen description
            Text(
                text = if (isTeacher) {
                    "Manage your teaching schedule alerts, lecture reminders, and departmental circulars."
                } else {
                    "Manage how you receive attendance notifications, lecture confirmations, and absence alerts."
                },
                fontSize = 13.sp,
                color = TextMuted,
                lineHeight = 18.sp
            )

            if (isTeacher) {
                // ==================== TEACHER UI ====================

                // Card 1: Teaching & Classroom Alerts
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Teaching & Classroom Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = TextDark
                        )
                        Spacer(Modifier.height(12.dp))

                        // Item 1: Upcoming Lecture Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Upcoming Class Reminders",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Receive a heads-up 10–15 minutes before your scheduled lecture starts.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyTeacherUpcomingClass,
                                onCheckedChange = { viewModel.toggleNotifyTeacherUpcomingClass(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFF1F5F9)
                        )

                        // Item 2: Pending Attendance Reminders
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.EditCalendar,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Pending Attendance Reminders",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Alerts you if attendance has not been submitted for a completed lecture.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyTeacherPendingAttendance,
                                onCheckedChange = { viewModel.toggleNotifyTeacherPendingAttendance(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFD97706),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }

                // Card 2: Student & Department Notices
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Student & Department Notices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = TextDark
                        )
                        Spacer(Modifier.height(12.dp))

                        // Item 1: Student Leave Requests
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF3E8FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.EventNote,
                                        contentDescription = null,
                                        tint = Color(0xFF9333EA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Student Leave Applications",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Immediate alert when a student in your course submits a leave request.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyTeacherLeaveRequests,
                                onCheckedChange = { viewModel.toggleNotifyTeacherLeaveRequests(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF9333EA),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFF1F5F9)
                        )

                        // Item 2: Official Circulars & Announcements
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDCFCE7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Campaign,
                                        contentDescription = null,
                                        tint = SemanticGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Official Circulars & Notices",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Broadcast notices from HoD, Dean, and University Administration.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyTeacherAnnouncements,
                                onCheckedChange = { viewModel.toggleNotifyTeacherAnnouncements(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SemanticGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }
            } else {
                // ==================== STUDENT UI ====================

                // Card 1: Attendance Confirmations (Present)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Class Confirmations",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = TextDark
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDCFCE7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = SemanticGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Notify when marked Present",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Receive an alert each time your attendance is marked as Present.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyOnPresent,
                                onCheckedChange = { viewModel.toggleNotifyOnPresent(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Present notifications arrive quietly on a dedicated silent channel so they won't interrupt class.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Card 2: Absence & Correction Alerts
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Urgent Attendance Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = TextDark
                        )
                        Spacer(Modifier.height(12.dp))

                        // Item 1: Absence Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEF2F2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Notifications,
                                        contentDescription = null,
                                        tint = SemanticRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Absence Warnings",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "High-priority alert when you are marked Absent so you can request a correction.",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyOnAbsent,
                                onCheckedChange = { viewModel.toggleNotifyOnAbsent(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SemanticRed,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFF1F5F9)
                        )

                        // Item 2: Correction Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.EditCalendar,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Attendance Corrections",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Immediate notification when faculty updates your attendance (e.g. Absent \u2192 Present).",
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = notifyOnCorrection,
                                onCheckedChange = { viewModel.toggleNotifyOnCorrection(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }
            }

            // Card 3: Android System Settings Shortcut (Shared for both roles)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Sound & Vibration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = TextDark
                    )
                    Spacer(Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                try {
                                    val intent = Intent().apply {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        } else {
                                            action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                            putExtra("app_package", context.packageName)
                                            putExtra("app_uid", context.applicationInfo.uid)
                                        }
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        color = SurfaceLight,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEDE9FE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = null,
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "System Notification Channels",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = TextDark
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Configure ringtones, banner popups, and vibration",
                                        fontSize = 11.5.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
