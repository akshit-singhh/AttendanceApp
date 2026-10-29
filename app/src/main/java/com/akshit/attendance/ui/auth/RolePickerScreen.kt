package com.akshit.attendance.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akshit.attendance.data.model.RoleOption
import com.akshit.attendance.data.model.TokenResponse

/**
 * Shown only when login's available_roles has more than one entry — most users
 * have exactly one role and never see this screen. Calls POST /auth/switch-role
 * to reissue tokens scoped to the chosen role, per the backend's actual contract.
 */
@Composable
fun RolePickerScreen(
    viewModel: AuthViewModel,
    availableRoles: List<RoleOption>,
    onRoleChosen: (TokenResponse) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val s = state
        if (s is AuthUiState.Success) onRoleChosen(s.response)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Who are you acting as today?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your account holds more than one role. Pick one to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(availableRoles) { option ->
                Card(
                    onClick = { viewModel.switchRole(option.role, option.scope_id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(option.role, style = MaterialTheme.typography.titleMedium)
                        Text(option.scope_type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (state is AuthUiState.Loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        if (state is AuthUiState.Error) {
            Spacer(Modifier.height(16.dp))
            Text((state as AuthUiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
