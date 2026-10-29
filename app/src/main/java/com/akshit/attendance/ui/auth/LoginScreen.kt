package com.akshit.attendance.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.akshit.attendance.data.model.TokenResponse
import com.akshit.attendance.ui.theme.Primary

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: (response: TokenResponse, passwordUsed: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
            "Sign in with the ID and password provided by your admin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
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
        TextButton(onClick = { /* TODO: forgot-password flow (OTP screen not built yet) */ }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Forgot password?")
        }
    }
}
