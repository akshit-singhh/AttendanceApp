package com.akshit.attendance.ui.teacher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.data.model.AttendanceStatus
import com.akshit.attendance.data.offline.OfflineAttendanceManager
import com.akshit.attendance.data.offline.OfflineAttendanceManager.QueuedAttendanceEntry
import com.akshit.attendance.data.offline.TeacherCacheManager
import com.akshit.attendance.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val CardBorder = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherOfflineQueueScreen(
    viewModel: TeacherViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRollCall: (courseOfferingId: Int, sessionDate: String?, batchLabel: String?) -> Unit
) {
    val queuedEntries by OfflineAttendanceManager.queuedEntries.collectAsState()
    val isOnline by OfflineAttendanceManager.isOnline.collectAsState()
    val isSyncing by OfflineAttendanceManager.isSyncing.collectAsState()
    val isConnecting by OfflineAttendanceManager.isConnecting.collectAsState()
    val lastSyncMessage by OfflineAttendanceManager.lastSyncMessage.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sessionToDiscard by remember { mutableStateOf<QueuedAttendanceEntry?>(null) }
    var singleSyncingUuid by remember { mutableStateOf<String?>(null) }

    // Infinite rotation animation for sync icon when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(lastSyncMessage) {
        lastSyncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = SurfaceBg,
        topBar = {
            Surface(
                color = SurfaceBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextDark
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Offline Queue",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                if (queuedEntries.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = if (isOnline) Color(0xFFE0E7FF) else Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${queuedEntries.size}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOnline) PrimaryBlue else Color(0xFFB45309)
                                        )
                                    }
                                }
                            }
                            Text(
                                if (queuedEntries.isEmpty()) "All classes synchronized"
                                else "${queuedEntries.size} class session${if (queuedEntries.size > 1) "s" else ""} waiting to upload",
                                fontSize = 10.5.sp,
                                color = TextGray
                            )
                        }
                    }

                    if (queuedEntries.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (!isSyncing) {
                                    OfflineAttendanceManager.syncAllNow { synced ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (synced > 0) "Successfully synced $synced session(s)!"
                                                else "Sync complete."
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !isSyncing && isOnline,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFCBD5E1),
                                disabledContentColor = Color(0xFF64748B)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(13.dp)
                                    .then(if (isSyncing) Modifier.rotate(syncRotation) else Modifier)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (isSyncing) "Syncing..." else "Sync All",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(1.dp)) }

            // 1. Connectivity Status Banner Card (shown only when offline or reconnecting)
            if (!isOnline || isConnecting) {
                item {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFB45309)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isConnecting) "Reconnecting to server..." else "Device Offline \u2022 Stored Locally",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = if (isConnecting) "Testing network connection..." else "Saved safely on device. Auto-syncs when online.",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF92400E)
                                )
                            }

                            if (!isConnecting) {
                                Spacer(Modifier.width(6.dp))
                                FilledTonalButton(
                                    onClick = {
                                        OfflineAttendanceManager.checkServerHealth { success ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (success) "Connection restored! Device is online."
                                                    else "Could not reach server. Please check internet."
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFFFEF3C7),
                                        contentColor = Color(0xFF92400E)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Retry", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Queue Summary KPI Bar (if items present)
            if (queuedEntries.isNotEmpty()) {
                item {
                    val totalStudentsMarked = remember(queuedEntries) {
                        queuedEntries.sumOf { it.payload.records.size }
                    }
                    val totalPresent = remember(queuedEntries) {
                        queuedEntries.sumOf { entry -> entry.payload.records.count { it.status == AttendanceStatus.PRESENT } }
                    }
                    val totalAbsent = remember(queuedEntries) {
                        queuedEntries.sumOf { entry -> entry.payload.records.count { it.status == AttendanceStatus.ABSENT } }
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QueueSummaryStat(label = "Queued", value = "${queuedEntries.size}", color = PrimaryBlue)
                            VerticalDivider(modifier = Modifier.height(22.dp), color = CardBorder)
                            QueueSummaryStat(label = "Total Records", value = "$totalStudentsMarked", color = TextDark)
                            VerticalDivider(modifier = Modifier.height(22.dp), color = CardBorder)
                            QueueSummaryStat(label = "Present", value = "$totalPresent", color = PrimaryGreen)
                            VerticalDivider(modifier = Modifier.height(22.dp), color = CardBorder)
                            QueueSummaryStat(label = "Absent", value = "$totalAbsent", color = AlertRed)
                        }
                    }
                }
            }

            // 3. Queue Session Cards
            if (queuedEntries.isEmpty()) {
                item {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Queue is Empty",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "All offline attendance sessions have been synchronized with the university server.",
                                fontSize = 12.sp,
                                color = TextGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onNavigateBack,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PrimaryBlue),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Back to Dashboard", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "PENDING SUBMISSIONS (${queuedEntries.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                    )
                }

                items(queuedEntries, key = { it.payload.session_uuid }) { entry ->
                    QueuedSessionCard(
                        entry = entry,
                        isOnline = isOnline,
                        isSyncing = isSyncing || (singleSyncingUuid == entry.payload.session_uuid),
                        onReviewRollCall = {
                            onNavigateToRollCall(
                                entry.payload.course_offering_id,
                                entry.payload.date,
                                entry.payload.batch_label
                            )
                        },
                        onSyncSingle = {
                            singleSyncingUuid = entry.payload.session_uuid
                            OfflineAttendanceManager.syncSingleEntry(entry.payload.session_uuid) { success, errorMsg ->
                                singleSyncingUuid = null
                                scope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("Class attendance synced successfully!")
                                    } else {
                                        snackbarHostState.showSnackbar(errorMsg ?: "Failed to sync class.")
                                    }
                                }
                            }
                        },
                        onDiscard = {
                            sessionToDiscard = entry
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // Confirmation Dialog for Discarding a Queued Class
    sessionToDiscard?.let { session ->
        val resolvedInfo = remember(session) {
            TeacherCacheManager.resolveCourseInfo(session.payload.course_offering_id)
        }
        val subjName = session.subjectName ?: resolvedInfo?.subject_name ?: "this class"

        AlertDialog(
            onDismissRequest = { sessionToDiscard = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Discard Queued Attendance?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextDark
                )
            },
            text = {
                Text(
                    "Are you sure you want to discard the offline attendance records for $subjName (${session.payload.date})? This session has not been synced to the server, and its attendance data will be permanently deleted from this device.",
                    fontSize = 13.sp,
                    color = TextGray,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uuid = session.payload.session_uuid
                        sessionToDiscard = null
                        scope.launch {
                            OfflineAttendanceManager.removeEntry(uuid)
                            snackbarHostState.showSnackbar("Session discarded from queue.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Discard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDiscard = null }) {
                    Text("Keep in Queue", fontWeight = FontWeight.SemiBold, color = TextDark)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun QueueSummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Spacer(Modifier.height(1.dp))
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = TextGray)
    }
}

@Composable
private fun QueuedSessionCard(
    entry: QueuedAttendanceEntry,
    isOnline: Boolean,
    isSyncing: Boolean,
    onReviewRollCall: () -> Unit,
    onSyncSingle: () -> Unit,
    onDiscard: () -> Unit
) {
    val payload = entry.payload
    val resolvedInfo = remember(payload.course_offering_id) {
        TeacherCacheManager.resolveCourseInfo(payload.course_offering_id)
    }

    val subjectName = entry.subjectName
        ?: resolvedInfo?.subject_name
        ?: "Course Offering #${payload.course_offering_id}"
    val subjectCode = entry.subjectCode
        ?: resolvedInfo?.subject_code
        ?: "CLASS"
    val sectionName = entry.sectionName
        ?: resolvedInfo?.section_name
        ?: "Section"

    val presentCount = remember(payload.records) {
        payload.records.count { it.status == AttendanceStatus.PRESENT }
    }
    val absentCount = remember(payload.records) {
        payload.records.count { it.status == AttendanceStatus.ABSENT }
    }
    val leaveCount = remember(payload.records) {
        payload.records.count { it.status == AttendanceStatus.LEAVE }
    }
    val totalCount = payload.records.size

    val formattedDate = remember(payload.date) {
        try {
            val ld = LocalDate.parse(payload.date)
            ld.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
        } catch (_: Exception) {
            payload.date
        }
    }

    val hasFailure = entry.failureMessage != null

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hasFailure) Color(0xFFFCA5A5) else CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            // Header Row: Badges on Left, Status Badge on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Subject Code Badge
                    Surface(
                        color = Color(0xFFE8F0FE),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            subjectCode,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }

                    // Section Badge
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            sectionName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextGray
                        )
                    }

                    // Batch/Group Badge (if present)
                    if (!payload.batch_label.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFF3E8FF),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                payload.batch_label,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7E22CE)
                            )
                        }
                    }
                }

                // Status Badge on Right
                Surface(
                    color = when {
                        hasFailure -> Color(0xFFFEE2E2)
                        isSyncing -> Color(0xFFE0F2FE)
                        else -> Color(0xFFFEF3C7)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(9.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF0284C7)
                            )
                            Spacer(Modifier.width(4.dp))
                        } else if (hasFailure) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = AlertRed
                            )
                            Spacer(Modifier.width(3.dp))
                        } else {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = Color(0xFFB45309)
                            )
                            Spacer(Modifier.width(3.dp))
                        }

                        Text(
                            text = when {
                                hasFailure -> "FAILED"
                                isSyncing -> "SYNCING"
                                else -> "QUEUED"
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                hasFailure -> AlertRed
                                isSyncing -> Color(0xFF0369A1)
                                else -> Color(0xFF92400E)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // Subject Title
            Text(
                subjectName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            // Date, Period & Attendance Stats Row (Inline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formattedDate,
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )
                Text(" • ", fontSize = 11.sp, color = TextGray)
                Text(
                    "${payload.duration_periods} Period${if (payload.duration_periods > 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.weight(1f))

                // Inline Attendance Count Pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AttendanceCountPill(
                        label = "P",
                        count = presentCount,
                        bgColor = Color(0xFFDCFCE7),
                        textColor = Color(0xFF15803D)
                    )
                    AttendanceCountPill(
                        label = "A",
                        count = absentCount,
                        bgColor = Color(0xFFFEE2E2),
                        textColor = AlertRed
                    )
                    if (leaveCount > 0) {
                        AttendanceCountPill(
                            label = "L",
                            count = leaveCount,
                            bgColor = Color(0xFFFEF3C7),
                            textColor = Color(0xFFB45309)
                        )
                    }
                    Text(
                        "(${totalCount})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextGray
                    )
                }
            }

            // Failure Callout (if rejected)
            if (hasFailure) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFF1F2),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            entry.failureMessage ?: "Submission failed on server.",
                            fontSize = 10.5.sp,
                            color = Color(0xFF9F1239),
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons Row (Compact 28dp height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Review Roll Call Button
                OutlinedButton(
                    onClick = onReviewRollCall,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = TextDark
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Review",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Discard Button
                    OutlinedButton(
                        onClick = onDiscard,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AlertRed
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = AlertRed
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Discard",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlertRed
                        )
                    }

                    // Sync Now Button
                    Button(
                        onClick = onSyncSingle,
                        enabled = !isSyncing && isOnline,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFCBD5E1),
                            disabledContentColor = Color(0xFF64748B)
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Sync Now",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCountPill(
    label: String,
    count: Int,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label: ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                "$count",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
