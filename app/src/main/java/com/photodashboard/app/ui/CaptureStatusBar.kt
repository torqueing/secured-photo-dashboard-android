package com.photodashboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photodashboard.app.upload.CaptureStatus
import com.photodashboard.app.upload.CaptureStep

private val Done = Color(0xFF16A34A)
private val Active = Color(0xFF2563EB)
private val Waiting = Color(0x66FFFFFF)
private val Failed = Color(0xFFDC2626)

/** Five-step status after a capture: taken, saved, connecting, uploading, finished. */
@Composable
fun CaptureStatusBar(status: CaptureStatus, onGallery: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val current = status.step.ordinal
    val label = when {
        status.failed -> "Upload failed${status.error?.let { ": $it" } ?: ""}"
        status.finished -> "Finished upload"
        status.step == CaptureStep.UPLOADING && status.uploadPercent != null -> "Uploading ${status.uploadPercent}%"
        status.waitingForNetwork -> "${status.step.label}: waiting for network, will upload automatically"
        else -> status.step.label
    }
    Surface(
        modifier = modifier.semantics { contentDescription = "Capture status: $label" },
        color = Color(0xE6111827),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CaptureStep.entries.forEach { step ->
                    val state = when {
                        status.failed && step.ordinal == current -> StepState.FAILED
                        step.ordinal < current || (status.finished && step == CaptureStep.FINISHED) -> StepState.DONE
                        step.ordinal == current -> StepState.ACTIVE
                        else -> StepState.TODO
                    }
                    StepDot(step, state, Modifier.weight(1f))
                }
            }
            val target = if (status.finished) 1f else (current + 0.5f) / CaptureStep.entries.size
            val fraction by animateFloatAsState(target, label = "captureProgress")
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (status.failed) Failed else if (status.finished) Done else Active,
                trackColor = Color(0x33FFFFFF),
            )
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (status.step == CaptureStep.UPLOADING && status.uploadPercent != null && !status.failed) {
                LinearProgressIndicator(
                    progress = { status.uploadPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color(0x33FFFFFF),
                )
            }
            if (status.failed) {
                OutlinedButton(onClick = onRetry) { Text("Retry upload", color = Color.White) }
            }
            AnimatedVisibility(status.finished, enter = fadeIn() + expandVertically()) {
                Button(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text("Take me to the gallery") }
            }
        }
    }
}

private enum class StepState { TODO, ACTIVE, DONE, FAILED }

@Composable
private fun StepDot(step: CaptureStep, state: StepState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when (state) {
            StepState.DONE -> Done
            StepState.ACTIVE -> Active
            StepState.FAILED -> Failed
            StepState.TODO -> Waiting
        },
        label = "stepColor",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(26.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.DONE -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                StepState.FAILED -> Icon(Icons.Filled.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
                StepState.ACTIVE -> CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                StepState.TODO -> Text("${step.ordinal + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            step.label,
            color = if (state == StepState.TODO) Color(0x99FFFFFF) else Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
