package com.akshit.attendance.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shape parameters for the 4-fold symmetric polygon.
 * [rOuter] is radial distance at multiples of 90 degrees (0, 90, 180, 270).
 * [rInner] is radial distance at odd 45-degree points (45, 135, 225, 315).
 * [kappaOuter] and [kappaInner] define the tangent handle length (curvature).
 */
private data class MorphShapeParams(
    val rOuter: Float,
    val rInner: Float,
    val kappaOuter: Float,
    val kappaInner: Float,
)

// Precomputed normalized geometry for Material 3 Expressive shapes:
// 1. Circle: smooth uniform curve
private val CircleGeometry = MorphShapeParams(1.0f, 1.0f, 0.276f, 0.276f)
// 2. Rounded Squircle: square with rounded corners
private val SquircleGeometry = MorphShapeParams(1.08f, 1.02f, 0.22f, 0.32f)
// 3. 4-Lobed Flower / Clover: organic distinct lobes
private val CloverGeometry = MorphShapeParams(1.22f, 0.68f, 0.40f, 0.16f)
// 4. 4-Point Scalloped Star: dynamic pointed star with soft valleys
private val StarGeometry = MorphShapeParams(1.28f, 0.50f, 0.16f, 0.20f)

private fun interpolateShape(a: MorphShapeParams, b: MorphShapeParams, fraction: Float): MorphShapeParams {
    return MorphShapeParams(
        rOuter = lerp(a.rOuter, b.rOuter, fraction),
        rInner = lerp(a.rInner, b.rInner, fraction),
        kappaOuter = lerp(a.kappaOuter, b.kappaOuter, fraction),
        kappaInner = lerp(a.kappaInner, b.kappaInner, fraction),
    )
}

/**
 * Material 3 Expressive LoadingIndicator.
 *
 * Implements Google's Material 3 Expressive shape-morphing animation
 * (Circle -> Squircle -> 4-Lobe Clover -> 4-Point Star -> Circle) with continuous rotation
 * and subtle scale pulsing.
 *
 * @param progress Optional lambda providing 0.0..1.0 for determinate gesture pull-down.
 *                 When null, the indicator runs an infinite indeterminate morph loop.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 28.dp,
    progress: (() -> Float)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ExpressiveLoadingTransition")

    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )

    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    val currentPhase = if (progress != null) {
        val p = progress().coerceIn(0f, 1f)
        p * 2f // Morph from Circle to Clover during pull down
    } else {
        animatedPhase
    }

    val currentRotation = if (progress != null) {
        val p = progress().coerceIn(0f, 1f)
        p * 180f
    } else {
        animatedPhase * 90f
    }

    val currentScale = if (progress != null) {
        val p = progress().coerceIn(0f, 1f)
        0.35f + p * 0.65f
    } else {
        scalePulse
    }

    val baseRadius = with(LocalDensity.current) { (size / 2).toPx() } * 0.72f

    val phaseInt = currentPhase.toInt().coerceIn(0, 3)
    val rawFraction = (currentPhase - phaseInt).coerceIn(0f, 1f)
    val easedFraction = FastOutSlowInEasing.transform(rawFraction)

    val shape = when (phaseInt) {
        0 -> interpolateShape(CircleGeometry, SquircleGeometry, easedFraction)
        1 -> interpolateShape(SquircleGeometry, CloverGeometry, easedFraction)
        2 -> interpolateShape(CloverGeometry, StarGeometry, easedFraction)
        else -> interpolateShape(StarGeometry, CircleGeometry, easedFraction)
    }

    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = currentRotation
                scaleX = currentScale
                scaleY = currentScale
            }
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        path.reset()

        val rOut = baseRadius * shape.rOuter
        val rIn = baseRadius * shape.rInner
        val kOut = shape.kappaOuter
        val kIn = shape.kappaInner

        for (k in 0 until 8) {
            val angle = (k * Math.PI / 4.0).toFloat()
            val r = if (k % 2 == 0) rOut else rIn
            val kappa = if (k % 2 == 0) kOut else kIn

            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)

            val tx = -sin(angle) * r * kappa
            val ty = cos(angle) * r * kappa

            val nextK = (k + 1) % 8
            val nextAngle = (nextK * Math.PI / 4.0).toFloat()
            val nextR = if (nextK % 2 == 0) rOut else rIn
            val nextKappa = if (nextK % 2 == 0) kOut else kIn

            val nextPx = cx + nextR * cos(nextAngle)
            val nextPy = cy + nextR * sin(nextAngle)

            val nextTx = -sin(nextAngle) * nextR * nextKappa
            val nextTy = cos(nextAngle) * nextR * nextKappa

            if (k == 0) {
                path.moveTo(px, py)
            }

            path.cubicTo(
                px + tx, py + ty,
                nextPx - nextTx, nextPy - nextTy,
                nextPx, nextPy
            )
        }
        path.close()

        drawPath(path = path, color = color)
    }
}

/**
 * Adapter for Material 3 [PullToRefreshContainer] indicator slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshExpressiveIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val progressLambda: (() -> Float)? = if (state.isRefreshing) {
        null
    } else {
        { state.progress }
    }

    Box(
        modifier = modifier
            .size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveLoadingIndicator(
            progress = progressLambda,
            color = color,
            size = 26.dp
        )
    }
}

/**
 * Contained variant with an elevated circular surface container.
 */
@Composable
fun ContainedExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    containerSize: Dp = 48.dp,
    indicatorSize: Dp = 26.dp,
    progress: (() -> Float)? = null,
) {
    Surface(
        modifier = modifier.size(containerSize),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveLoadingIndicator(
                progress = progress,
                color = indicatorColor,
                size = indicatorSize
            )
        }
    }
}
