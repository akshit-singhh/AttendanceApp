package com.akshit.attendance.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import com.akshit.attendance.data.model.TokenResponse

/**
 * Shown exactly once, right after a temp-password login (must_change_password == true).
 * Not seen again for this user after a successful submit.
 */
@Composable
fun CreatePasswordScreen(
    viewModel: AuthViewModel,
    tempPassword: String,
    onPasswordSet: (TokenResponse) -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val s = state
        if (s is AuthUiState.Success) onPasswordSet(s.response)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Create your password", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This is your first login. Set a permanent password to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state is AuthUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((state as AuthUiState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.setPassword(tempPassword, newPassword, confirm) },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Set password and continue")
            }
        }
    }
}
