package com.photodashboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photodashboard.app.data.MyStats
import com.photodashboard.app.data.PhotoEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The user's photo counts: from the server when known, else from the photos on this phone. */
data class PhotoCounts(val today: Int, val week: Int, val all: Int, val geotagged: Int) {
    companion object {
        fun of(stats: MyStats?, photos: List<PhotoEntity>, zone: ZoneId = ZoneId.systemDefault()): PhotoCounts {
            if (stats != null) return PhotoCounts(stats.photosToday, stats.photosWeek, stats.photosAll, stats.geotagged)
            val today = LocalDate.now(zone)
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val days = photos.map { Instant.ofEpochMilli(it.capturedAt).atZone(zone).toLocalDate() }
            return PhotoCounts(
                today = days.count { it == today },
                week = days.count { !it.isBefore(monday) },
                all = photos.size,
                geotagged = photos.count { it.latitude != null },
            )
        }
    }
}

/** Home: four glass boxes (Photos, Videos, Gallery, Maps) and Account Settings. */
@Composable
fun HomeScreen(
    displayName: String,
    photos: List<PhotoEntity>,
    stats: MyStats?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAccount: () -> Unit,
    onMap: () -> Unit,
) {
    val counts = PhotoCounts.of(stats, photos)
    val parts = displayName.split(" ").filter { it.isNotBlank() }
    val shortName = if (parts.size > 1) "${parts.first()} ${parts.last().first()}." else displayName
    GlassBackground {
        // Fits the screen: the two rows of boxes share whatever height is left.
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                InitialsAvatar(displayName)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Welcome back", color = Glass.muted, fontSize = 15.sp)
                    Text(shortName, color = Glass.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeBox("Take Photo", "Sealed at capture", Icons.Outlined.CameraAlt, Modifier.weight(1f), onCamera, "Open the camera") {
                    StatLine("Today", counts.today)
                    StatLine("This week", counts.week)
                    StatLine("All time", counts.all, last = true)
                }
                HomeBox("Videos", "Encrypted clips", Icons.Outlined.Videocam, Modifier.weight(1f)) {
                    GlassPill("Coming soon")
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeBox("Gallery", "${counts.all} verified items", Icons.Outlined.Collections, Modifier.weight(1f), onGallery, "Open the gallery") {
                    ThumbStack(counts.all)
                }
                HomeBox("Maps", "${counts.geotagged} geotagged", Icons.Outlined.Map, Modifier.weight(1f), onMap, "Open the map") {
                    GlassPill("View pins")
                }
            }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), onAccount).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text("Account Settings", color = Glass.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Details, statistics and password", color = Glass.muted, fontSize = 14.sp)
                }
                GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open Account Settings", onClick = onAccount)
            }
        }
    }
}

@Composable
private fun HomeBox(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    openLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxHeight().glass(RoundedCornerShape(28.dp), onClick).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.weight(1f))
            if (onClick != null) GlassCircle(Icons.Outlined.OpenInFull, openLabel, size = 42.dp, onClick = onClick)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = Glass.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, color = Glass.muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun StatLine(label: String, value: Int, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, color = Glass.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("%,d".format(value), color = Glass.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
    if (!last) HorizontalDivider(color = Color(0x1FFFFFFF))
}

@Composable
private fun ThumbStack(total: Int) {
    val colours = listOf(
        listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), listOf(Color(0xFF22D3EE), Color(0xFF3B82F6)),
        listOf(Color(0xFFA3E635), Color(0xFF10B981)),
    )
    Box(Modifier.height(44.dp).width(140.dp)) {
        colours.forEachIndexed { i, c ->
            Box(
                Modifier.offset(x = (i * 32).dp).size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(c)).border(2.dp, Color(0x59FFFFFF), RoundedCornerShape(12.dp)),
            )
        }
        if (total > 3) {
            Box(Modifier.offset(x = 96.dp).size(44.dp).glass(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("+${total - 3}", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
