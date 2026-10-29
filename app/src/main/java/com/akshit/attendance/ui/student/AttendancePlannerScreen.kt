package com.akshit.attendance.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshit.attendance.ui.components.SkeletonBox
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendancePlannerScreen(
    viewModel: StudentViewModel,
    onNavigateBack: () -> Unit
) {
    val plannerItems by viewModel.plannerItems.collectAsState()
    val dashboardState by viewModel.dashboard.collectAsState()

    // Calculate projected overall
    val (projectedOverall, delta, isAboveTarget) = remember(plannerItems, dashboardState) {
        val totalCurrentAttended = plannerItems.sumOf { it.currentAttended }
        val totalCurrentHeld = plannerItems.sumOf { it.currentHeld }
        val currentPct = if (totalCurrentHeld > 0) (totalCurrentAttended.toDouble() / totalCurrentHeld.toDouble()) * 100.0 else 0.0

        val totalProjectedAttended = plannerItems.sumOf { it.currentAttended + it.plannedAttend }
        val totalProjectedHeld = plannerItems.sumOf { it.currentHeld + it.upcomingClasses }
        val projPct = if (totalProjectedHeld > 0) (totalProjectedAttended.toDouble() / totalProjectedHeld.toDouble()) * 100.0 else currentPct

        val diff = projPct - currentPct
        Triple(projPct, diff, projPct >= 75.0)
    }

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Attendance Planner", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetPlanner() }) {
                        Text("Reset", color = AccentPurple, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Projected Overall Hero Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Projected overall", fontSize = 12.sp, color = TextGray)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${String.format(Locale.ENGLISH, "%.1f", projectedOverall)}%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAboveTarget) PrimaryGreen else AlertRed
                            )
                            Spacer(Modifier.width(10.dp))
                            val deltaText = if (delta >= 0) "+${String.format(Locale.ENGLISH, "%.1f", delta)}%" else "${String.format(Locale.ENGLISH, "%.1f", delta)}%"
                            val deltaColor = if (delta >= 0) PrimaryGreen else AlertRed
                            Text(deltaText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = deltaColor)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Progress Bar with 75% Target Notch
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE2E8F0))
                        ) {
                            val fraction = (projectedOverall / 100.0).toFloat().coerceIn(0f, 1f)
                            val barColor = if (isAboveTarget) PrimaryGreen else AlertRed

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(barColor)
                            )

                            // 75% Target notch
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .fillMaxHeight()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(Color.White)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isAboveTarget) "You'd be at or above your 75% target."
                            else "Warning: You'd drop below the mandatory 75% threshold.",
                            fontSize = 11.sp,
                            color = if (isAboveTarget) PrimaryGreen else AlertRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Quick Scenarios Row
            item {
                Column {
                    Text("Quick scenarios", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyPlannerScenario(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TextDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next week", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.applyPlannerScenario(2) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TextDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next 2 wks", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.applyPlannerScenario(4) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TextDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next 4 wks", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Subject Steppers List
            item {
                Text("Subject adjustments", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }

            if (plannerItems.isEmpty() && dashboardState is DashboardUiState.Loading) {
                items(4) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(16.dp))
                }
            } else {
                items(plannerItems) { item ->
                    val totalAttended = item.currentAttended + item.plannedAttend
                    val totalHeld = item.currentHeld + item.upcomingClasses
                    val projPct = if (totalHeld > 0) (totalAttended.toDouble() / totalHeld.toDouble()) * 100.0 else 0.0
                    val missedUpcoming = (item.upcomingClasses - item.plannedAttend).coerceAtLeast(0)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = AccentPurple.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(item.code.take(2).uppercase(), fontWeight = FontWeight.Bold, color = AccentPurple, fontSize = 12.sp)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                    val currentPct = if (item.currentHeld > 0) (item.currentAttended.toDouble() / item.currentHeld.toDouble()) * 100.0 else 0.0
                                    val currentPctStr = if (item.currentHeld > 0) {
                                        "Now ${String.format(Locale.ENGLISH, "%.1f", currentPct)}% • ${item.currentAttended}/${item.currentHeld}"
                                    } else {
                                        "Now Not Started • 0/0"
                                    }
                                    Text(
                                        currentPctStr,
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }
                            Text(
                                "${String.format(Locale.ENGLISH, "%.1f", projPct)}%",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (projPct >= 75.0) PrimaryGreen else AlertRed
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Stepper 1: Upcoming Classes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Upcoming classes", fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepperButton(icon = Icons.Outlined.Remove) {
                                    viewModel.updatePlannerUpcoming(item.code, -1)
                                }
                                Text(
                                    "${item.upcomingClasses}",
                                    modifier = Modifier.width(36.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                StepperButton(icon = Icons.Outlined.Add) {
                                    viewModel.updatePlannerUpcoming(item.code, 1)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Stepper 2: Attend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Attend", fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                if (missedUpcoming > 0) {
                                    Text("Miss $missedUpcoming", fontSize = 10.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepperButton(icon = Icons.Outlined.Remove) {
                                    viewModel.updatePlannerAttend(item.code, -1)
                                }
                                Text(
                                    "${item.plannedAttend}",
                                    modifier = Modifier.width(36.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                StepperButton(icon = Icons.Outlined.Add) {
                                    viewModel.updatePlannerAttend(item.code, 1)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Live Advice Rule
                        val advice = viewModel.getBunkAdvice(totalAttended, totalHeld, 75.0)
                        val adviceText = when (advice) {
                            is BunkAdvice.Safe -> if (advice.classesCanMiss > 0)
                                "Then: You can miss ${advice.classesCanMiss} classes and still meet 75%"
                            else "Then: You will be exactly at the 75% limit"
                            is BunkAdvice.AtRisk -> "Then: You will need ${advice.classesNeeded} more classes to reach 75%"
                            BunkAdvice.NoClasses -> "No classes held"
                        }

                        Text(
                            adviceText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (projPct >= 75.0) PrimaryGreen else AlertRed
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFFF1F5F9),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(30.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
        }
    }
}
