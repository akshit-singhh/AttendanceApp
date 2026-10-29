package com.akshit.attendance.ui.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akshit.attendance.data.model.AttendanceStatus
import com.akshit.attendance.data.model.CorrectionRequestCreate
import com.akshit.attendance.data.model.SessionHistoryResponse
import com.akshit.attendance.data.repository.TeacherRepository
import com.akshit.attendance.ui.components.AppTopBar
import kotlinx.coroutines.launch

/**
 * Records tab: past sessions (GET /mobile/attendance/history) + a correction
 * request form (POST /mobile/attendance/corrections). Both endpoints exist now but
 * currently 500 — see the note in TeacherRepository.getHistory(). This screen is
 * written against the intended contract; it'll start working the moment the
 * AttendanceRecord model gets its missing fields, no UI change needed.
 */
@Composable
fun TeacherRecordsScreen(repo: TeacherRepository) {
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<SessionHistoryResponse>>(emptyList()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var recordId by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            history = repo.getHistory()
        } catch (e: Exception) {
            historyError = e.message ?: "Could not load past sessions."
        }
    }

    Scaffold(
        containerColor = com.akshit.attendance.ui.theme.SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = { AppTopBar("TEACHER PORTAL", "Records") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Past Sessions", style = MaterialTheme.typography.titleMedium) }
            if (historyError != null) {
                item { Text(historyError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            items(history) { session ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(session.subject_name, style = MaterialTheme.typography.titleSmall)
                        Text("${session.section_name} • ${session.date}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { Text("Request Attendance Correction", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = recordId, onValueChange = { recordId = it },
                    label = { Text("Attendance record ID") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (required for HOD approval)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 4,
                )
            }
            result?.let { r -> item { Text(r, color = com.akshit.attendance.ui.theme.PresentText) } }
            item {
                Button(
                    onClick = {
                        val id = recordId.toIntOrNull() ?: return@Button
                        submitting = true
                        scope.launch {
                            result = try {
                                repo.submitCorrectionRequest(
                                    CorrectionRequestCreate(attendance_record_id = id, suggested_status = AttendanceStatus.PRESENT, reason = reason),
                                ).message
                            } catch (e: Exception) {
                                e.message ?: "Could not submit correction request. Please try again."
                            }
                            submitting = false
                        }
                    },
                    enabled = !submitting && reason.isNotBlank() && recordId.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (submitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Submit for HOD Approval")
                }
            }
        }
    }
}
