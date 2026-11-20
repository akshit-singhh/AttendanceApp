package com.akshit.attendance.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.akshit.attendance.R
import com.akshit.attendance.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit
) {
    val state by viewModel.forgotPasswordState.collectAsState()

    var identifier by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    // Step 1: Request OTP. Step 2: Verify OTP & Change Password.
    var currentStep by remember { mutableIntStateOf(1) }
    var isOtpVerified by remember { mutableStateOf(false) }

    // Resend countdown timer
    var resendCooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    // Lottie compositions
    val forgotComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.forgot_password))
    val forgotProgress by animateLottieCompositionAsState(
        composition = forgotComposition,
        iterations = LottieConstants.IterateForever
    )

    val otpComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.otp_verification))
    val otpProgress by animateLottieCompositionAsState(
        composition = otpComposition,
        iterations = LottieConstants.IterateForever
    )

    LaunchedEffect(state) {
        when (state) {
            is ForgotPasswordUiState.OtpSent -> {
                if (currentStep == 1) {
                    currentStep = 2
                    resendCooldown = 45
                }
            }
            is ForgotPasswordUiState.OtpVerified -> {
                isOtpVerified = true
            }
            else -> {}
        }
    }

    // Handle system back navigation
    BackHandler {
        if (state is ForgotPasswordUiState.ResetSuccess) {
            onResetSuccess()
        } else if (currentStep == 2) {
            currentStep = 1
            isOtpVerified = false
            viewModel.resetForgotPasswordState()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state is ForgotPasswordUiState.ResetSuccess -> "Password Reset"
                            currentStep == 1 -> "Forgot Password"
                            else -> "Verify & Reset"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is ForgotPasswordUiState.ResetSuccess) {
                            onResetSuccess()
                        } else if (currentStep == 2) {
                            currentStep = 1
                            isOtpVerified = false
                            viewModel.resetForgotPasswordState()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state is ForgotPasswordUiState.ResetSuccess) {
                // ==========================================
                // SUCCESS STATE
                // ==========================================
                Spacer(Modifier.height(36.dp))
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "Password Reset Successfully",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    (state as ForgotPasswordUiState.ResetSuccess).message.ifBlank {
                        "Your password has been reset. You can now securely log in with your new credentials."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onResetSuccess,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Return to Log In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else if (currentStep == 1) {
                // ==========================================
                // STEP 1: FORGOT PASSWORD (REQUEST OTP)
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = forgotComposition,
                        progress = { forgotProgress },
                        modifier = Modifier.size(200.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Forgot your password?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter your university email address or student roll number to receive a 6-digit verification code.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(20.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "Account Identification",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(Modifier.height(10.dp))

                        // Identifier field (Clean single-line, no wrapping placeholder)
                        OutlinedTextField(
                            value = identifier,
                            onValueChange = {
                                identifier = it
                                viewModel.clearForgotPasswordError()
                            },
                            label = { Text("Email or Student Roll Number") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        if (state is ForgotPasswordUiState.Error) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        (state as ForgotPasswordUiState.Error).message,
                                        color = Color(0xFFB91C1C),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.sendForgotPasswordOtp(identifier) },
                            enabled = state !is ForgotPasswordUiState.Loading && identifier.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (state is ForgotPasswordUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Send Verification Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onNavigateBack) {
                    Text("Remember password? Log In", color = Primary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))

            } else {
                // ==========================================
                // STEP 2: VERIFY OTP THEN ENABLE PASSWORD CHANGE
                // (Compacted layout so Reset & Save button is moved up)
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = otpComposition,
                        progress = { otpProgress },
                        modifier = Modifier.size(68.dp)
                    )
                }

                Text(
                    "Verification & Reset",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Code sent to ",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        identifier,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick = {
                            currentStep = 1
                            isOtpVerified = false
                            viewModel.resetForgotPasswordState()
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Change", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Card 1: OTP Code Verification Block
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOtpVerified) Color(0xFF22C55E) else Color(0xFFE2E8F0)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Step 1: Enter 6-Digit OTP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOtpVerified) Color(0xFF16A34A) else Color(0xFF1E293B)
                            )
                            if (isOtpVerified) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Verified",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // OTP text field (Clean single-line, no wrapping placeholder)
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    otpCode = it
                                    viewModel.clearForgotPasswordError()
                                }
                            },
                            enabled = !isOtpVerified,
                            label = { Text("6-Digit OTP Code") },
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                if (isOtpVerified) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        if (!isOtpVerified) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.verifyOtp(identifier, otpCode) },
                                enabled = state !is ForgotPasswordUiState.Loading && otpCode.length == 6,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                            ) {
                                if (state is ForgotPasswordUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verify OTP Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Didn't receive code? ",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                if (resendCooldown > 0) {
                                    Text(
                                        "Resend in ${resendCooldown}s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8)
                                    )
                                } else {
                                    TextButton(
                                        onClick = {
                                            viewModel.sendForgotPasswordOtp(identifier)
                                            resendCooldown = 45
                                        },
                                        enabled = state !is ForgotPasswordUiState.Loading,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Resend Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
                                    }
                                }
                            }
                        }
                    }
                }

                if (state is ForgotPasswordUiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                (state as ForgotPasswordUiState.Error).message,
                                color = Color(0xFFB91C1C),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Card 2: Password Change Block (Enabled ONLY after OTP is verified!)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOtpVerified) Color.White else Color(0xFFF1F5F9)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOtpVerified) Primary.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isOtpVerified) 2.dp else 0.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isOtpVerified) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (isOtpVerified) Primary else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Step 2: Create New Password",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOtpVerified) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                        }

                        if (!isOtpVerified) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Verify the 6-digit OTP above to unlock password creation.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // New Password Field (Clean single-line, no wrapping placeholder)
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                viewModel.clearForgotPasswordError()
                            },
                            enabled = isOtpVerified,
                            label = { Text("New Password (min 8 chars)") },
                            singleLine = true,
                            maxLines = 1,
                            visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showNewPassword = !showNewPassword },
                                    enabled = isOtpVerified
                                ) {
                                    Icon(
                                        if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                        tint = if (isOtpVerified) Color(0xFF64748B) else Color(0xFFCBD5E1)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                disabledBorderColor = Color(0xFFE2E8F0),
                                disabledTextColor = Color(0xFF94A3B8),
                                disabledLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        Spacer(Modifier.height(10.dp))

                        // Confirm Password Field (Clean single-line, no wrapping placeholder)
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                viewModel.clearForgotPasswordError()
                            },
                            enabled = isOtpVerified,
                            label = { Text("Confirm New Password") },
                            singleLine = true,
                            maxLines = 1,
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showConfirmPassword = !showConfirmPassword },
                                    enabled = isOtpVerified
                                ) {
                                    Icon(
                                        if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showConfirmPassword) "Hide password" else "Show password",
                                        tint = if (isOtpVerified) Color(0xFF64748B) else Color(0xFFCBD5E1)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                disabledBorderColor = Color(0xFFE2E8F0),
                                disabledTextColor = Color(0xFF94A3B8),
                                disabledLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        Spacer(Modifier.height(14.dp))

                        // Reset & Save Password Button (prominently visible and moved up)
                        Button(
                            onClick = {
                                viewModel.submitResetPassword(
                                    identifier = identifier,
                                    otp = otpCode,
                                    newPassword = newPassword,
                                    confirm = confirmPassword
                                )
                            },
                            enabled = isOtpVerified &&
                                    state !is ForgotPasswordUiState.Loading &&
                                    newPassword.length >= 8 &&
                                    confirmPassword.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (state is ForgotPasswordUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Reset & Save Password", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
