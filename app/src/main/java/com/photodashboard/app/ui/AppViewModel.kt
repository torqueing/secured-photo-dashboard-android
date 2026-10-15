package com.photodashboard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photodashboard.app.BuildConfig
import com.photodashboard.app.PhotoDashboardApp
import com.photodashboard.app.data.ChangePasswordRequest
import com.photodashboard.app.data.LoginRequest
import com.photodashboard.app.data.AuthResponse
import com.photodashboard.app.data.EmailVerifyRequest
import com.photodashboard.app.data.MfaTokenRequest
import com.photodashboard.app.data.MfaVerifyRequest
import com.photodashboard.app.data.MyStats
import com.photodashboard.app.data.UserDto
import com.photodashboard.app.data.VerifyTokenRequest
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.data.RegisterRequest
import com.photodashboard.app.data.Session
import com.photodashboard.app.data.apiMessage
import com.photodashboard.app.exif.GeoFix
import com.photodashboard.app.upload.CaptureProgress
import com.photodashboard.app.upload.CaptureStatus
import com.photodashboard.app.upload.CaptureStep
import com.photodashboard.app.upload.UploadWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class CaptureTracking(val localId: String?)

/** A code the user must enter before they are signed in. */
data class AuthChallenge(
    val kind: Kind,
    val token: String,
    /** "totp" and/or "email". */
    val methods: List<String>,
    /** Masked address, e.g. g•••@gmail.com. */
    val email: String?,
    val emailSent: Boolean = false,
    val sentAt: Long = if (emailSent) System.currentTimeMillis() else 0L,
) {
    enum class Kind { SECOND_STEP, VERIFY_EMAIL }
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PhotoDashboardApp).container
    private val repo = container.repository

    val apiConfigured = BuildConfig.API_BASE_URL.isNotBlank()

    /** The splash plays once per app launch and again after each sign-in. */
    var splashPending = true

    val session: StateFlow<SessionState> = MutableStateFlow<SessionState>(SessionState.Loading).also { state ->
        viewModelScope.launch {
            container.session.session.collect { s ->
                state.value = if (s == null) SessionState.SignedOut else SessionState.SignedIn(s)
            }
        }
    }.asStateFlow()

    val photos: StateFlow<List<PhotoEntity>> = container.session.session
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else container.db.photos().observe(s.userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wifiOnly = container.session.wifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: Exception) {
            _message.value = e.apiMessage()
        } finally {
            _busy.value = false
        }
    }

    /** Set while sign-in waits for a code: a second step, or confirming a new account's email. */
    private val _challenge = MutableStateFlow<AuthChallenge?>(null)
    val challenge: StateFlow<AuthChallenge?> = _challenge.asStateFlow()

    /** A session, or the next step the server asks for. */
    private suspend fun handleAuth(res: AuthResponse) {
        when {
            res.verificationRequired -> _challenge.value = AuthChallenge(AuthChallenge.Kind.VERIFY_EMAIL, res.verifyToken!!, listOf("email"), res.email, emailSent = true)
            res.mfaRequired -> _challenge.value = AuthChallenge(AuthChallenge.Kind.SECOND_STEP, res.mfaToken!!, res.methods ?: listOf("totp"), res.email)
            else -> {
                _challenge.value = null
                container.session.save(res)
                afterSignIn()
            }
        }
    }

    fun signIn(email: String, password: String) = launchBusy {
        handleAuth(container.api.login(LoginRequest(email.trim(), password, android.os.Build.MODEL)))
    }

    /** Ask for an emailed code (second step), or a new one (email confirmation). */
    fun sendEmailCode() = launchBusy {
        val c = _challenge.value ?: return@launchBusy
        val sent = if (c.kind == AuthChallenge.Kind.VERIFY_EMAIL) container.api.resendVerification(VerifyTokenRequest(c.token))
        else container.api.sendMfaEmailCode(MfaTokenRequest(c.token))
        _challenge.value = c.copy(emailSent = true, email = sent.email ?: c.email, sentAt = System.currentTimeMillis())
    }

    fun answerChallenge(code: String, method: String) = launchBusy {
        val c = _challenge.value ?: return@launchBusy
        try {
            handleAuth(
                if (c.kind == AuthChallenge.Kind.VERIFY_EMAIL) container.api.verifyEmail(EmailVerifyRequest(c.token, code.trim(), android.os.Build.MODEL))
                else container.api.verifyMfa(MfaVerifyRequest(c.token, code.trim(), android.os.Build.MODEL, method)),
            )
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 && e.apiMessage().contains("expired")) _challenge.value = null
            throw e
        }
    }

    fun cancelChallenge() {
        _challenge.value = null
    }

    fun register(form: RegisterForm) = launchBusy {
        require(form.password.length >= RegisterForm.MIN_PASSWORD) { "Password must be at least ${RegisterForm.MIN_PASSWORD} characters" }
        require(RegisterForm.USERNAME.matches(form.username.trim())) {
            "Username must be 3-30 characters: letters, numbers, dots, dashes or underscores"
        }
        val auth = container.api.register(
            RegisterRequest(
                email = form.email.trim(),
                password = form.password,
                username = form.username.trim().lowercase(),
                firstName = form.firstName.trim(),
                lastName = form.lastName.trim(),
            ),
        )
        handleAuth(auth)
    }

    private suspend fun afterSignIn() {
        val s = container.session.current()
        if (s?.mustChangePassword == true || s?.mfaSetupRequired == true) return
        repo.refreshOrgKey()
        runCatching { repo.ensureDeviceKeyRegistered() }
        repo.resumePending()
        runCatching { repo.refreshFromServer() }
    }

    /** AND-3: revoke the token, stop pending uploads, keep local photos. */
    fun signOut() = viewModelScope.launch {
        splashPending = true
        _capture.value = null
        UploadWorker.cancelAll(getApplication())
        runCatching { container.api.logout() }
        container.session.clear()
    }

    /** Account details and the user's numbers from the server (home and Account Settings). */
    private val _account = MutableStateFlow<UserDto?>(null)
    val account: StateFlow<UserDto?> = _account.asStateFlow()
    private val _stats = MutableStateFlow<MyStats?>(null)
    val stats: StateFlow<MyStats?> = _stats.asStateFlow()

    /** Quietly refresh; offline the screens fall back to what this phone knows. */
    fun loadAccount() = viewModelScope.launch {
        runCatching { _account.value = container.api.me() }
        runCatching { _stats.value = container.api.myStats(java.time.ZoneId.systemDefault().id) }
    }

    fun refresh() = launchBusy {
        repo.refreshOrgKey()
        repo.refreshFromServer()
    }

    /** Returns null on success, else the error to show in the dialog. */
    suspend fun changePassword(current: String, new: String): String? = try {
        require(new.length >= RegisterForm.MIN_PASSWORD) { "New password must be at least ${RegisterForm.MIN_PASSWORD} characters" }
        container.api.changePassword(ChangePasswordRequest(current, new))
        val wasForced = container.session.current()?.mustChangePassword == true
        container.session.setMustChangePassword(false)
        if (wasForced) afterSignIn()
        _message.value = "Password changed"
        null
    } catch (e: Exception) {
        e.apiMessage()
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch { container.session.setWifiOnly(value) }

    /** The gallery is locked until fingerprint/PIN unlocks the storage key (decision D2). */
    private val _unlockRequired = MutableStateFlow(false)
    val unlockRequired = _unlockRequired.asStateFlow()

    /** Bumped after an unlock so locked images retry. */
    private val _unlockVersion = MutableStateFlow(0)
    val unlockVersion = _unlockVersion.asStateFlow()
    private val thumbCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(200)

    fun onUnlocked() {
        _unlockRequired.value = false
        _unlockVersion.value += 1
    }

    fun onUnlockCancelled() {
        _unlockRequired.value = false
    }

    /** Decrypt and decode a sealed image; null while the gallery is locked (an unlock is requested). */
    suspend fun sealedImage(photo: PhotoEntity, thumbnail: Boolean): androidx.compose.ui.graphics.ImageBitmap? {
        if (thumbnail) thumbCache.get(photo.localId)?.let { return it }
        return try {
            val bytes = repo.decrypt(photo, thumbnail)
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.photodashboard.app.ui.SealedImages.decode(bytes)
            }
            java.util.Arrays.fill(bytes, 0)
            bitmap?.also { if (thumbnail) thumbCache.put(photo.localId, it) }
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            _unlockRequired.value = true
            null
        } catch (e: java.security.InvalidKeyException) {
            // Some versions report a locked key this way.
            _unlockRequired.value = true
            null
        }
    }

    suspend fun locationFix(): GeoFix? = container.location.fixForCapture()

    /** The capture whose progress the camera screen shows; null before the first shot. */
    private val _capture = MutableStateFlow<CaptureTracking?>(null)

    val captureStatus: StateFlow<CaptureStatus?> = _capture
        .flatMapLatest { c ->
            when {
                c == null -> flowOf(null)
                c.localId == null -> flowOf(CaptureStatus(CaptureStep.TAKEN))
                else -> container.db.photos().observeOne(c.localId).map { p ->
                    CaptureProgress.status(p != null, p?.state, p?.stage, p?.progress, p?.lastError)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val capturedLocalId: String? get() = _capture.value?.localId

    fun onPhotoTaken() {
        _capture.value = CaptureTracking(localId = null)
    }

    fun clearCapture() {
        _capture.value = null
    }

    fun onCaptured(jpeg: ByteArray, rotation: Int, capturedAt: Long, fix: GeoFix?) = viewModelScope.launch {
        try {
            val photo = repo.onCaptured(jpeg, rotation, capturedAt, fix)
            _capture.value = CaptureTracking(localId = photo.localId)
            if (fix == null) _message.value = "Saved without location"
        } catch (e: Exception) {
            _capture.value = null
            _message.value = "Could not save photo: ${e.apiMessage()}"
        }
    }

    fun photo(localId: String): Flow<PhotoEntity?> = container.db.photos().observeOne(localId)

    fun retry(localId: String) = viewModelScope.launch { repo.retry(localId) }

    fun delete(localId: String, onDone: () -> Unit) = launchBusy {
        repo.delete(localId)
        onDone()
    }
}
