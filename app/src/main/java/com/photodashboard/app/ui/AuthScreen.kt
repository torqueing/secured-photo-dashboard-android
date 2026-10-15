package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Sign in and Register (AND-1, AND-2). */
@Composable
fun AuthScreen(
    busy: Boolean,
    apiConfigured: Boolean,
    onSignIn: (String, String) -> Unit,
    onRegister: (RegisterForm) -> Unit,
    challenge: AuthChallenge? = null,
    onAnswer: (code: String, method: String) -> Unit = { _, _ -> },
    onSendEmailCode: () -> Unit = {},
    onCancelChallenge: () -> Unit = {},
) {
    if (challenge != null) {
        CodeStep(busy, challenge, onAnswer, onSendEmailCode, onCancelChallenge)
        return
    }
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Secured Photo Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(if (registering) "Create an account" else "Sign in", style = MaterialTheme.typography.titleMedium)
        if (!apiConfigured) {
            Text(
                "No API URL was built into this app. Set api.base.url in local.properties and rebuild.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (registering) {
            OutlinedTextField(
                username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("3–30 letters, numbers, dots, dashes or underscores") },
            )
            OutlinedTextField(firstName, { firstName = it }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lastName, { lastName = it }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (registering) ({ Text("At least 10 characters; not a common password or your username") }) else null,
        )
        Row(
            Modifier.fillMaxWidth().toggleable(value = showPassword, role = Role.Checkbox, onValueChange = { showPassword = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = showPassword, onCheckedChange = null)
            Text("Show password", Modifier.padding(start = 8.dp))
        }
        val valid = email.contains('@') && password.isNotEmpty() && (
            !registering || (password.length >= RegisterForm.MIN_PASSWORD && username.isNotBlank() && firstName.isNotBlank() && lastName.isNotBlank())
            )
        Button(
            onClick = {
                if (registering) onRegister(RegisterForm(email, password, username, firstName, lastName))
                else onSignIn(email, password)
            },
            enabled = valid && !busy && apiConfigured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(if (registering) "Register" else "Sign in")
        }
        TextButton(onClick = { registering = !registering }, modifier = Modifier.fillMaxWidth()) {
            Text(if (registering) "Have an account? Sign in" else "New here? Register")
        }
    }
}

/**
 * The step after the password (or after registering): confirm the email address with an
 * emailed code, or the second sign-in step with the authenticator app or an emailed code.
 */
@Composable
private fun CodeStep(
    busy: Boolean,
    challenge: AuthChallenge,
    onAnswer: (String, String) -> Unit,
    onSendEmail: () -> Unit,
    onCancel: () -> Unit,
) {
    val verifying = challenge.kind == AuthChallenge.Kind.VERIFY_EMAIL
    var method by rememberSaveable(challenge.token) { mutableStateOf(if ("totp" in challenge.methods && !verifying) "totp" else "email") }
    var code by rememberSaveable(challenge.token) { mutableStateOf("") }
    // Email is the way in: make sure a code has been sent (once).
    LaunchedEffect(method, challenge.token) {
        if (method == "email" && !challenge.emailSent) onSendEmail()
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(challenge.sentAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val wait = ((challenge.sentAt + RESEND_WAIT_MS - now) / 1_000).coerceAtLeast(0)
    val to = challenge.email ?: "your email address"
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (verifying) "Confirm your email" else "Second step", style = MaterialTheme.typography.headlineSmall)
        if (!verifying && challenge.methods.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = method == "totp", onClick = { method = "totp"; code = "" }, label = { Text("Authenticator app") })
                FilterChip(selected = method == "email", onClick = { method = "email"; code = "" }, label = { Text("Email me a code") })
            }
        }
        Text(
            when {
                verifying -> "We've emailed a 6-digit code to $to. Enter it to confirm your email address."
                method == "totp" -> "Enter the 6-digit code from your authenticator app."
                challenge.emailSent -> "We've emailed a 6-digit code to $to. It expires in 10 minutes."
                else -> "Sending a code to $to…"
            },
        )
        OutlinedTextField(
            code, { v -> code = v.filter { it.isDigit() }.take(6) }, label = { Text("Code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Button(onClick = { onAnswer(code, method) }, enabled = code.length == 6 && !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(if (verifying) "Confirm" else "Verify")
        }
        if (method == "email") {
            if (wait > 0) {
                Text("Didn't get it? You can ask for another in $wait s. Check your spam folder too.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(onClick = onSendEmail, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (challenge.emailSent) "Send a new code" else "Send code")
                }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

private const val RESEND_WAIT_MS = 60_000L
