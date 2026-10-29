package com.akshit.attendance.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    colors: List<Color> = listOf(
        Color(0xFFE2E8F0),
        Color(0xFFF1F5F9),
        Color(0xFFE2E8F0)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnimation"
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush())
    )
}

@Composable
fun DashboardHeroSkeleton(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SkeletonBox(modifier = Modifier.width(100.dp).height(24.dp), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            SkeletonBox(modifier = Modifier.size(120.dp), shape = CircleShape)
            Spacer(Modifier.height(16.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SkeletonBox(modifier = Modifier.width(80.dp).height(36.dp), shape = RoundedCornerShape(8.dp))
                SkeletonBox(modifier = Modifier.width(80.dp).height(36.dp), shape = RoundedCornerShape(8.dp))
                SkeletonBox(modifier = Modifier.width(80.dp).height(36.dp), shape = RoundedCornerShape(8.dp))
            }
        }
    }
}

@Composable
fun SubjectCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                SkeletonBox(modifier = Modifier.width(50.dp).height(24.dp), shape = RoundedCornerShape(6.dp))
            }
            Spacer(Modifier.height(14.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(8.dp), shape = RoundedCornerShape(4.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp), shape = RoundedCornerShape(6.dp))
        }
    }
}

@Composable
fun ScheduleCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
            }
            Spacer(Modifier.width(8.dp))
            SkeletonBox(modifier = Modifier.width(60.dp).height(24.dp), shape = RoundedCornerShape(12.dp))
        }
    }
}
