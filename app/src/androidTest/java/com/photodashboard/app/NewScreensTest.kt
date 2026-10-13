package com.photodashboard.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.ui.CaptureStatusBar
import com.photodashboard.app.ui.HomeScreen
import com.photodashboard.app.ui.SplashScreen
import com.photodashboard.app.ui.TAGLINE
import com.photodashboard.app.upload.CaptureStatus
import com.photodashboard.app.upload.CaptureStep
import com.photodashboard.app.upload.UploadState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the splash, home and capture status bar with fake data; saves screenshots for review. */
@RunWith(AndroidJUnit4::class)
class NewScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) = MaterialTheme { Surface(Modifier.fillMaxSize()) { content() } }

    @Test
    fun splashShowsTaglineAndFinishes() {
        var done = false
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed { SplashScreen(onDone = { done = true }) } }
        compose.mainClock.advanceTimeBy(1_900)
        compose.onNodeWithText(TAGLINE).assertExists()
        compose.onNodeWithText("Photo Dashboard").assertExists()
        save("1-splash")
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertTrue(done)
    }

    @Test
    fun homeHasCameraAndGalleryButtons() {
        var camera = false
        var gallery = false
        val photos = listOf(UploadState.UPLOADED, UploadState.UPLOADED, UploadState.PENDING).mapIndexed { i, s ->
            PhotoEntity("p$i", 1, null, null, 0, 60, null, null, null, null, null, null, null, true, null, null, null, s)
        }
        compose.setContent { Themed { HomeScreen("George", photos, { camera = true }, { gallery = true }, {}, { _, _ -> null }) } }
        compose.onNodeWithText("Photo Dashboard").assertExists()
        save("2-home")
        compose.onNodeWithText("Camera", substring = true).performClick()
        compose.onNodeWithText("Gallery", substring = true).performClick()
        assertTrue(camera && gallery)
    }

    @Test
    fun statusBarStepsAndGalleryButton() {
        val states = listOf(
            "3-taken" to CaptureStatus(CaptureStep.TAKEN),
            "4-saved-offline" to CaptureStatus(CaptureStep.SAVED, waitingForNetwork = true),
            "5-uploading" to CaptureStatus(CaptureStep.UPLOADING, uploadPercent = 45),
            "6-finished" to CaptureStatus(CaptureStep.FINISHED, uploadPercent = 100),
        )
        var current = states.first().second
        var gallery = false
        val holder = androidx.compose.runtime.mutableStateOf(current)
        compose.setContent {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Surface(color = Color.DarkGray) { CaptureStatusBar(holder.value, onGallery = { gallery = true }, onRetry = {}) }
            }
        }
        for ((name, status) in states) {
            holder.value = status
            compose.waitForIdle()
            save(name)
        }
        compose.onNodeWithText("Take me to the gallery").performClick()
        assertTrue(gallery)
    }
}
