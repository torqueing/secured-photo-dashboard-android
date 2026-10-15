package com.photodashboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photodashboard.app.data.MyStats
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.data.UserDto
import java.time.OffsetDateTime
import java.time.ZoneId

/** Account Settings: details, statistics and security (change password), opened from home. */
@Composable
fun AccountScreen(
    displayName: String,
    email: String,
    account: UserDto?,
    stats: MyStats?,
    photos: List<PhotoEntity>,
    onBack: () -> Unit,
    onChangePassword: suspend (String, String) -> String?,
    onSignOut: () -> Unit,
) {
    var changingPassword by remember { mutableStateOf(false) }
    if (changingPassword) ChangePasswordDialog(onSubmit = onChangePassword, onDismiss = { changingPassword = false })
    val counts = PhotoCounts.of(stats, photos)
    val role = (account?.role ?: "").replace('_', ' ').replaceFirstChar { it.uppercase() }
    GlassBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Account Settings", color = Glass.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 14.dp))
            }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialsAvatar(displayName, size = 48.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(displayName, color = Glass.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(account?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }, role.ifBlank { null }).joinToString(" · "),
                        color = Glass.muted, fontSize = 14.sp)
                }
            }
            Panel("Details") {
                Detail("Email", account?.email ?: email)
                Detail("Organisation", account?.orgName ?: "None")
                Detail("Member since", account?.createdAt?.let { formatDate(it) } ?: "—")
                Detail("This phone", "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", last = true)
            }
            Panel("Statistics") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("%,d".format(counts.today), "Photos today", Modifier.weight(1f))
                    Tile("%,d".format(counts.week), "This week", Modifier.weight(1f))
                    Tile("%,d".format(counts.all), "All time", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Tile(stats?.videos?.let { "%,d".format(it) } ?: "Soon", "Videos", Modifier.weight(1f))
                    Tile(stats?.let { formatBytes(it.storageBytes) } ?: "—", "Storage", Modifier.weight(1f))
                    Tile(stats?.lastUploadAt?.let { formatTime(it) } ?: "—", "Last upload", Modifier.weight(1f))
                }
            }
            Panel("Security") {
                Action(Icons.Outlined.Lock, "Change password", "Signs out your other devices") { changingPassword = true }
                HorizontalDivider(color = Color(0x1AFFFFFF))
                Action(
                    Icons.Outlined.VerifiedUser, "Two-factor",
                    if (account?.totpEnabled == true) "Authenticator app · On" else "Emailed codes",
                )
                HorizontalDivider(color = Color(0x1AFFFFFF))
                Action(Icons.Outlined.PhoneAndroid, "Signed-in devices", stats?.let { "${it.sessionsActive} active" } ?: "—")
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x24FF5A1F))
                    .border(1.dp, Color(0x73FF5A1F), RoundedCornerShape(22.dp)).clickable(onClick = onSignOut).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Sign out", color = Color(0xFFFFB4A0), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Text(
                "Signed-in devices can be signed out on the web dashboard under Account.",
                color = Glass.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title.uppercase(), color = Glass.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun Detail(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Glass.muted, fontSize = 15.sp)
        Text(value, color = Glass.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    if (!last) HorizontalDivider(color = Color(0x1AFFFFFF))
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x0FFFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, color = Glass.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = Glass.muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Glass.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Glass.muted, fontSize = 13.sp)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Glass.muted)
    }
}

private fun parse(iso: String) = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

private fun formatDate(iso: String) = parse(iso)?.format(DisplayFormat.date) ?: "—"

private fun formatTime(iso: String): String {
    val t = parse(iso) ?: return "—"
    return if (t.toLocalDate() == java.time.LocalDate.now()) t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    else t.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0 || v >= 10) "${v.toInt()} ${units[i]}" else "%.1f %s".format(v, units[i])
}
