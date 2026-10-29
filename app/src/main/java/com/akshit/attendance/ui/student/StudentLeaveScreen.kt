package com.akshit.attendance.ui.student

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akshit.attendance.ui.components.AppTopBar
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy")
private const val MAX_REASON = 300
private const val MAX_FILE_BYTES = 5L * 1024 * 1024

/** UI-only row for "Recent Applications". Feed it from a history endpoint when one exists. */
data class LeaveHistoryItem(
    val title: String,     // "Medical Leave"
    val ref: String,       // "#ML-2023-089"
    val dates: String,     // "Oct 14 – Oct 16, 2023 (3 Days)"
    val note: String,      // approver / rejection note
    val status: String,    // "Approved" | "Rejected" | "Pending"
)

/**
 * Medical leave only: LeaveRequest has no leave_type column, so Duty/Special are shown
 * disabled instead of pretending to submit. Enable them when the backend field exists.
 * Not built (no data source): "Affects: N lectures" row. Needs a timetable-overlap endpoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLeaveScreen(
    viewModel: StudentViewModel,
    recent: List<LeaveHistoryItem> = emptyList(),
) {
    val context = LocalContext.current
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end, null = closed
    var reason by remember { mutableStateOf("") }
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val leaveState by viewModel.leaveState.collectAsState()

    val s = start
    val e = end
    val days = if (s != null && e != null) ChronoUnit.DAYS.between(s, e) + 1 else null
    val rangeOk = days != null && days > 0

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep the real file name (and extension): the old timestamp-only temp name had no extension.
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: "leave_doc"
        val dir = File(context.cacheDir, "leave_${System.currentTimeMillis()}").apply { mkdirs() }
        val temp = File(dir, File(name).name) // File(name).name strips any path segments
        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (temp.length() > MAX_FILE_BYTES) {
            temp.delete()
            pickedFile = null
            fileError = "File must be under 5MB"
        } else {
            pickedFile = temp
            fileError = null
        }
    }

    picking?.let { isStart ->
        val ps = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (isStart) start = d else end = d
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state = ps) }
    }

    Scaffold(topBar = { AppTopBar("STUDENT PORTAL", "Leave Applications") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Request Medical Leave", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Upload a medical certificate for attendance waiver approval.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.MedicalServices, null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Label("LEAVE CATEGORY")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Medical", "Duty (OD)", "Special").forEachIndexed { i, text ->
                            SegmentedButton(
                                selected = i == 0,
                                onClick = {},
                                enabled = i == 0,
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(text) }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Label("ABSENCE INTERVAL", Modifier.weight(1f))
                        if (rangeOk) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF4D6)) {
                                Text(
                                    "Duration: $days ${if (days == 1L) "Day" else "Days"}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF8A6D00),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField("Start Date", s, Modifier.weight(1f)) { picking = true }
                        DateField("End Date", e, Modifier.weight(1f)) { picking = false }
                    }
                    if (days != null && !rangeOk) {
                        Text("End date must be on or after start date", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(Modifier.fillMaxWidth()) {
                        Label("REASON FOR ABSENCE", Modifier.weight(1f))
                        Label("${reason.length}/$MAX_REASON")
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(MAX_REASON) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )

                    Label("VERIFICATION DOCUMENT")
                    val f = pickedFile
                    if (f == null) {
                        OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.UploadFile, null)
                                Text("Tap to attach medical certificate")
                                Text("PDF, JPG, or PNG under 5MB", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text("%.1f MB".format(f.length() / 1_048_576.0), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { pickedFile = null }) { Icon(Icons.Default.Close, "Remove file") }
                            }
                        }
                    }
                    fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    when (val st = leaveState) {
                        is LeaveUiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                        is LeaveUiState.Submitted -> Text(st.response.message, color = com.akshit.attendance.ui.theme.PresentText)
                        else -> {}
                    }

                    Button(
                        onClick = {
                            val file = pickedFile
                            if (s != null && e != null && file != null) viewModel.applyLeave(s.toString(), e.toString(), reason, file)
                        },
                        enabled = leaveState !is LeaveUiState.Submitting && pickedFile != null && rangeOk && reason.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (leaveState is LeaveUiState.Submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Submit Leave Request")
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Applications", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("${recent.size} records", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                recent.forEach { HistoryCard(it) }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun DateField(label: String, date: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date?.format(DATE_FMT) ?: "Select", fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.CalendarMonth, null)
        }
    }
}

@Composable
private fun HistoryCard(item: LeaveHistoryItem) {
    val statusColor = when (item.status) {
        "Approved" -> Color(0xFF1B7F3B)
        "Rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.title} • ${item.ref}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(shape = RoundedCornerShape(16.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        item.status,
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(item.dates, style = MaterialTheme.typography.bodySmall)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Text(item.note, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}