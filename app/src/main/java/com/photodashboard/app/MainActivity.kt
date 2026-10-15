package com.photodashboard.app

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photodashboard.app.camera.CameraScreen
import com.photodashboard.app.ui.AppViewModel
import com.photodashboard.app.ui.AuthScreen
import com.photodashboard.app.ui.ForcedPasswordScreen
import com.photodashboard.app.ui.GalleryScreen
import com.photodashboard.app.ui.HomeScreen
import com.photodashboard.app.ui.MfaSetupRequiredScreen
import com.photodashboard.app.ui.SplashScreen
import com.photodashboard.app.ui.PhotoDetailScreen
import com.photodashboard.app.ui.SessionState

sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Gallery : Screen
    data object Camera : Screen
    data class Detail(val localId: String) : Screen
}

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No screenshots or recent-apps previews of photos (spec 8.3).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF60A5FA)) else lightColorScheme(primary = Color(0xFF2563EB))
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = session) {
            SessionState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            SessionState.SignedOut -> {
                val challenge by vm.challenge.collectAsStateWithLifecycle()
                AuthScreen(
                    busy, vm.apiConfigured, vm::signIn, vm::register,
                    challenge = challenge, onAnswer = vm::answerChallenge,
                    onSendEmailCode = vm::sendEmailCode, onCancelChallenge = vm::cancelChallenge,
                )
            }
            is SessionState.SignedIn ->
                when {
                    s.session.mustChangePassword -> ForcedPasswordScreen(vm::changePassword, onSignOut = { vm.signOut() })
                    s.session.mfaSetupRequired -> MfaSetupRequiredScreen(onSignOut = { vm.signOut() })
                    else -> SignedIn(vm, s.session.displayName)
                }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SignedIn(vm: AppViewModel, displayName: String) {
    val stack = remember { mutableStateListOf(if (vm.splashPending) Screen.Splash else Screen.Home) }
    val unlockVersion by vm.unlockVersion.collectAsStateWithLifecycle()
    GalleryUnlockPrompt(vm)
    // Local sealed photos decrypt with this phone's key; remote-only ones show a lock.
    val sealedLoader: (com.photodashboard.app.data.PhotoEntity, Boolean) -> (suspend () -> androidx.compose.ui.graphics.ImageBitmap?)? =
        { photo, thumb -> if ((if (thumb) photo.thumbPath else photo.filePath) == null) null else ({ vm.sealedImage(photo, thumb) }) }
    val captureStatus by vm.captureStatus.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    // Predictive back: the system animation is driven by OnBackPressedDispatcher.
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    fun goTo(vararg screens: Screen) {
        stack.clear()
        stack.addAll(screens)
    }

    when (val screen = stack.last()) {
        Screen.Splash -> SplashScreen(onDone = {
            vm.splashPending = false
            goTo(Screen.Home)
        })
        Screen.Home -> HomeScreen(
            displayName = displayName,
            photos = photos,
            onCamera = { vm.clearCapture(); stack.add(Screen.Camera) },
            onGallery = { stack.add(Screen.Gallery) },
            onSignOut = { vm.signOut() },
            onChangePassword = vm::changePassword,
        )
        Screen.Gallery -> GalleryScreen(
            displayName = displayName,
            photos = photos,
            wifiOnly = wifiOnly,
            onWifiOnly = vm::setWifiOnly,
            onOpen = { stack.add(Screen.Detail(it.localId)) },
            onCamera = { vm.clearCapture(); stack.add(Screen.Camera) },
            onRefresh = { vm.refresh() },
            onSignOut = { vm.signOut() },
            sealedLoader = sealedLoader,
            unlockVersion = unlockVersion,
        )
        Screen.Camera -> CameraScreen(
            locationFix = vm::locationFix,
            onTaken = vm::onPhotoTaken,
            onCaptured = { jpeg, rotation, at, fix -> vm.onCaptured(jpeg, rotation, at, fix) },
            onBack = { stack.removeAt(stack.lastIndex) },
            captureStatus = captureStatus,
            onGallery = { goTo(Screen.Home, Screen.Gallery) },
            onRetry = { vm.capturedLocalId?.let(vm::retry) },
        )
        is Screen.Detail -> {
            val photoFlow = remember(screen.localId) { vm.photo(screen.localId) }
            val photo by photoFlow.collectAsState(initial = null)
            PhotoDetailScreen(
                photo = photo,
                onBack = { stack.removeAt(stack.lastIndex) },
                onRetry = { vm.retry(screen.localId) },
                onDelete = { vm.delete(screen.localId) { stack.removeAt(stack.lastIndex) } },
                sealedLoader = sealedLoader,
                unlockVersion = unlockVersion,
            )
        }
    }
}

/** Fingerprint/PIN prompt that unlocks the photo storage key for 5 minutes (decision D2). */
@Composable
private fun GalleryUnlockPrompt(vm: AppViewModel) {
    val needed by vm.unlockRequired.collectAsStateWithLifecycle()
    val activity = androidx.activity.compose.LocalActivity.current as FragmentActivity
    LaunchedEffect(needed) {
        if (!needed) return@LaunchedEffect
        val authenticators = if (android.os.Build.VERSION.SDK_INT >= 30) {
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock your photos")
            .setSubtitle("Photos are encrypted on this phone")
            .setAllowedAuthenticators(authenticators)
            .build()
        androidx.biometric.BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = vm.onUnlocked()
                override fun onAuthenticationError(code: Int, msg: CharSequence) = vm.onUnlockCancelled()
            },
        ).authenticate(info)
    }
}
