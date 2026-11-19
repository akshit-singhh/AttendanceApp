package com.akshit.attendance.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.akshit.attendance.data.model.TokenResponse
import com.akshit.attendance.ui.theme.Primary

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: (response: TokenResponse, passwordUsed: String) -> Unit,
    onForgotPassword: () -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val s = state
        if (s is AuthUiState.Success) onLoggedIn(s.response, password)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.School, contentDescription = null, tint = Primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Attendance Portal", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sign in with your email or student roll number and password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (state is AuthUiState.Error) viewModel.resetState()
            },
            label = { Text("Email or Roll Number") },
            placeholder = { Text("e.g. 235ICS001 or name@gbu.ac.in") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (state is AuthUiState.Error) viewModel.resetState()
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state is AuthUiState.Error) {
            val rawError = (state as AuthUiState.Error).message
            val friendlyError = when {
                rawError.contains("401", ignoreCase = true) || rawError.startsWith("HTTP", ignoreCase = true) ->
                    "Invalid email/roll number or password. Please try again."
                rawError.contains("Failed to connect", ignoreCase = true) ||
                rawError.contains("Unable to resolve host", ignoreCase = true) ||
                rawError.contains("timeout", ignoreCase = true) ->
                    "Unable to connect to server. Please check your internet connection."
                else -> rawError
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = friendlyError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.login(email, password) },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Log in")
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Forgot password?")
        }
    }
}
