package com.photodashboard.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TAGLINE = "Cryptographically sealed at point of capture"
private const val SPLASH_MS = 2_600L

/** Animated splash after sign-in: logo pops in, a seal ring draws round it, the tagline rises. Tap to skip. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(400)) }
            launch { logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { delay(350); ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { delay(700); titleAlpha.animateTo(1f, tween(500)) }
            launch { delay(1_100); tagline.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        }
        delay(SPLASH_MS - 1_800)
        done()
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF1E3A8A))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(150.dp).scale(logoScale.value * if (ring.value >= 1f) pulse else 1f).alpha(logoAlpha.value), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(150.dp)) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = Color(0xFF60A5FA),
                        startAngle = -90f,
                        sweepAngle = 360f * ring.value,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    )
                }
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                Icon(
                    Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF93C5FD),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(30.dp).alpha(ring.value),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Secured Photo Dashboard",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(titleAlpha.value),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                TAGLINE,
                color = Color(0xFFBFDBFE),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 24.dp.toPx()
                },
            )
        }
        Text(
            "Tap to continue",
            color = Color(0x80FFFFFF),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp).alpha(tagline.value),
        )
    }
}
