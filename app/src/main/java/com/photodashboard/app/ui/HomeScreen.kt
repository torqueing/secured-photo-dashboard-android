package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.upload.UploadState

/** Overview after the splash: app name, what it does, quick stats, Camera and Gallery buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    displayName: String,
    photos: List<PhotoEntity>,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: suspend (String, String) -> String?,
) {
    var changingPassword by remember { mutableStateOf(false) }
    if (changingPassword) ChangePasswordDialog(onSubmit = onChangePassword, onDismiss = { changingPassword = false })
    val uploaded = photos.count { it.state == UploadState.UPLOADED || it.state == UploadState.REMOTE }
    val waiting = photos.count { it.state == UploadState.PENDING || it.state == UploadState.UPLOADING }
    val failed = photos.count { it.state == UploadState.FAILED }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = { IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Secured Photo Dashboard", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(TAGLINE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Signed in as $displayName. Photos you take are stamped with time and location in their EXIF data, " +
                    "kept on this phone and uploaded in the background, even if you are offline when you take them.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Stat("Photos", photos.size)
                    Stat("Uploaded", uploaded)
                    Stat("Waiting", waiting)
                    if (failed > 0) Stat("Failed", failed)
                }
            }
            Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.size(24.dp))
                Text("  Camera", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(24.dp))
                Text("  Gallery", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { changingPassword = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Key, null, Modifier.size(18.dp))
                Text("  Change password")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
