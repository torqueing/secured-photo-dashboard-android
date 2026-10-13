package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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

/** Current + new + repeat, with "Show password"; other sessions are signed out by the server. */
@Composable
fun ChangePasswordDialog(onSubmit: suspend (String, String) -> String?, onDismiss: () -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val transform = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Password)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true,
                    visualTransformation = transform, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true,
                    visualTransformation = transform, keyboardOptions = keyboard, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("At least 10 characters; not a common password") })
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && current.isNotEmpty() && new.length >= RegisterForm.MIN_PASSWORD,
                onClick = {
                    if (new != repeat) {
                        error = "New passwords do not match"
                        return@TextButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        val result = onSubmit(current, new)
                        busy = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text(if (busy) "Saving…" else "Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
