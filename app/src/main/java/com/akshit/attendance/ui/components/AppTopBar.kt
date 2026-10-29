package com.akshit.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akshit.attendance.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    eyebrow: String = "", // Kept so it doesn't break StudentDashboard, but ignored in UI
    title: String,
    onLogout: () -> Unit = {},
    onOpenProfile: (() -> Unit)? = null
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out of your account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W400)
                )
            }
        },
        actions = {
            IconButton(onClick = { /* notifications */ }) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .clickable {
                        if (onOpenProfile != null) {
                            onOpenProfile()
                        } else {
                            profileMenuExpanded = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)

                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    if (onOpenProfile != null) {
                        DropdownMenuItem(
                            text = { Text("My Profile", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Primary
                                )
                            },
                            onClick = {
                                profileMenuExpanded = false
                                onOpenProfile()
                            }
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text("Log Out") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            profileMenuExpanded = false
                            showLogoutDialog = true
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent // Blends seamlessly with Scaffold background
        )
    )
}