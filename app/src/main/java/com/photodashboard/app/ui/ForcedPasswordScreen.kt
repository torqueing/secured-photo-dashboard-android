package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shown instead of the app until a forced password change is done. */
@Composable
fun ForcedPasswordScreen(onSubmit: suspend (String, String) -> String?, onSignOut: () -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val transform = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Choose a new password", style = MaterialTheme.typography.headlineSmall)
        Text("Your organisation admin asked you to change your password. Your previous password can't be reused.")
        OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true,
            visualTransformation = transform, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true,
            visualTransformation = transform, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("At least 8 characters") })
        OutlinedTextField(repeat, { repeat = it }, label = { Text("Repeat new password") }, singleLine = true,
            visualTransformation = transform, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().toggleable(value = show, role = Role.Checkbox, onValueChange = { show = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = show, onCheckedChange = null)
            Text("Show password", Modifier.padding(start = 8.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && current.isNotEmpty() && new.length >= 8,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (new != repeat) {
                    error = "New passwords do not match"
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    error = onSubmit(current, new)
                    busy = false
                }
            },
        ) { Text(if (busy) "Saving…" else "Set new password") }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}
