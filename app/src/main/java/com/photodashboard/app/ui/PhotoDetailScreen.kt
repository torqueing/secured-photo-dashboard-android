package com.photodashboard.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.upload.UploadState
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** AND-11/12: image, capture time, coordinates, accuracy, map link, retry, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(photo: PhotoEntity?, onBack: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (photo != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") } },
            )
        },
    ) { padding ->
        if (photo == null) return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = photo.filePath ?: photo.publicUrl, contentDescription = null,
                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(),
            )
            StateBadge(photo.state)
            val offset = ZoneOffset.ofTotalSeconds(photo.utcOffsetMinutes * 60)
            Field("Captured", DisplayFormat.dateTime.format(Instant.ofEpochMilli(photo.capturedAt).atOffset(offset)))
            if (photo.latitude != null && photo.longitude != null) {
                Field("Coordinates", String.format(Locale.US, "%.6f, %.6f", photo.latitude, photo.longitude))
                photo.accuracyM?.let { Field("Accuracy", String.format(Locale.US, "± %.1f m", it)) }
                photo.altitudeM?.let { Field("Altitude", String.format(Locale.US, "%.1f m", it)) }
                photo.locationProvider?.let { Field("Location source", it) }
                OutlinedButton(onClick = {
                    val uri = "geo:${photo.latitude},${photo.longitude}?q=${photo.latitude},${photo.longitude}".toUri()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Text("Open in maps") }
            } else {
                Field("Location", "none (no_location)")
            }
            photo.lastError?.let { Field("Last error", it) }
            if (photo.state == UploadState.FAILED) {
                Button(onClick = onRetry) { Text("Retry upload") }
            }
        }
    }
    if (confirmDelete && photo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo?") },
            text = {
                Text(if (photo.serverPhotoId != null) "It will be removed from this phone and from the server." else "It will be removed from this phone.")
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
