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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Sign in and Register (AND-1, AND-2). */
@Composable
fun AuthScreen(
    busy: Boolean,
    apiConfigured: Boolean,
    onSignIn: (String, String) -> Unit,
    onRegister: (RegisterForm) -> Unit,
    mfaPending: Boolean = false,
    onVerifyMfa: (String) -> Unit = {},
    onCancelMfa: () -> Unit = {},
) {
    if (mfaPending) {
        MfaCodeStep(busy, onVerifyMfa, onCancelMfa)
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

/** Second sign-in step: the 6-digit code from the authenticator app. */
@Composable
private fun MfaCodeStep(busy: Boolean, onVerify: (String) -> Unit, onCancel: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Two-factor authentication", style = MaterialTheme.typography.headlineSmall)
        Text("Enter the 6-digit code from your authenticator app.")
        OutlinedTextField(
            code, { v -> code = v.filter { it.isDigit() }.take(6) }, label = { Text("Code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Button(onClick = { onVerify(code) }, enabled = code.length == 6 && !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Verify")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
