package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Admins must turn on two-factor before using the app; setup (with its QR code) is on the web dashboard. */
@Composable
fun MfaSetupRequiredScreen(onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Two-factor authentication required", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Administrators must sign in with an authenticator app. Sign in to the web dashboard on a computer, " +
                "follow the steps to scan the QR code with an authenticator app on this phone, then sign in here again.",
        )
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}
