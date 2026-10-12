package com.photodashboard.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.photodashboard.app.exif.GeoFix
import com.photodashboard.app.ui.CaptureStatusBar
import com.photodashboard.app.upload.CaptureStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** CameraX capture with lens switch and flash (AND-4); location requested on shutter (AND-5, AND-6). */
@Composable
fun CameraScreen(
    newFile: () -> File,
    locationFix: suspend () -> GeoFix?,
    onTaken: () -> Unit,
    onCaptured: (File, Long, GeoFix?) -> Unit,
    onBack: () -> Unit,
    captureStatus: CaptureStatus?,
    onGallery: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var locationGranted by remember {
        mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted || !locationGranted) launcher.launch(PERMISSIONS)
    }

    if (!cameraGranted) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is needed to take photos.")
            Button(onClick = { launcher.launch(PERMISSIONS) }) { Text("Grant permission") }
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember(lensFacing) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(90)
            .build()
    }
    imageCapture.flashMode = flashMode

    DisposableEffect(lensFacing, lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraScreen", "bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { providerFuture.get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            if (!locationGranted) {
                Text(
                    "No location permission: photos are flagged no_location",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically).weight(1f).padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                    else -> ImageCapture.FLASH_MODE_AUTO
                }
            }) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                    ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                    else -> Icons.Filled.FlashAuto
                }
                Icon(icon, "Flash mode", tint = Color.White)
            }
        }
        if (captureStatus != null) {
            CaptureStatusBar(
                status = captureStatus,
                onGallery = onGallery,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp, start = 12.dp, end = 12.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp))
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(if (capturing) Color.Gray else Color.White, CircleShape),
            ) {
                IconButton(
                    onClick = {
                        if (capturing) return@IconButton
                        capturing = true
                        scope.launch {
                            try {
                                val fix = async { if (locationGranted) runCatching { locationFix() }.getOrNull() else null }
                                val file = newFile()
                                val takenAt = System.currentTimeMillis()
                                imageCapture.takePictureTo(file, context)
                                onTaken()
                                onCaptured(file, takenAt, fix.await())
                            } catch (e: Exception) {
                                Log.e("CameraScreen", "capture failed", e)
                            } finally {
                                capturing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
            IconButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                else CameraSelector.LENS_FACING_BACK
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Cameraswitch, "Switch camera", tint = Color.White)
            }
        }
    }
}

private suspend fun ImageCapture.takePictureTo(file: File, context: android.content.Context) =
    suspendCancellableCoroutine { cont ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Unit)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            },
        )
    }
