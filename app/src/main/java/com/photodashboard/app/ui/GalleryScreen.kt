package com.photodashboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.upload.UploadState

/** AND-10: the user's own photos, newest first, with an upload badge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    displayName: String,
    photos: List<PhotoEntity>,
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    onOpen: (PhotoEntity) -> Unit,
    onCamera: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    sealedLoader: (PhotoEntity, Boolean) -> (suspend () -> androidx.compose.ui.graphics.ImageBitmap?)? = { _, _ -> null },
    unlockVersion: Int = 0,
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("My photos"); Text(displayName, style = MaterialTheme.typography.labelMedium) } },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Upload on Wi-Fi only")
                                    Switch(checked = wifiOnly, onCheckedChange = null)
                                }
                            },
                            onClick = { onWifiOnly(!wifiOnly) },
                        )
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; onSignOut() })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCamera, icon = { Icon(Icons.Filled.CameraAlt, null) }, text = { Text("Camera") })
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No photos yet. Tap Camera to take one.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(photos, key = { it.localId }) { photo ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { onOpen(photo) }) {
                        if (photo.sealed) SealedImage(
                            loadKey = photo.localId to unlockVersion,
                            load = sealedLoader(photo, true),
                            modifier = Modifier.fillMaxSize(),
                        ) else AsyncImage(
                            model = photo.filePath ?: photo.publicUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        StateBadge(photo.state, Modifier.align(Alignment.BottomStart).padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StateBadge(state: UploadState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        UploadState.PENDING -> "pending" to Color(0xFF6B7280)
        UploadState.UPLOADING -> "uploading" to Color(0xFF2563EB)
        UploadState.UPLOADED, UploadState.REMOTE -> "uploaded" to Color(0xFF15803D)
        UploadState.FAILED -> "failed" to Color(0xFFB91C1C)
    }
    Surface(modifier, color = color, shape = RoundedCornerShape(4.dp)) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
