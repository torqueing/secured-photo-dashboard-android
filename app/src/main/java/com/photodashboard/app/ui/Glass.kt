package com.photodashboard.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photodashboard.app.R

/** Colours of the glass screens (home, Account Settings): always dark, over the blurred background. */
object Glass {
    val text = Color(0xFFF3F5F8)
    val muted = Color(0x94EBF0F8)
    val edge = Color(0x2EFFFFFF)
    val accent = Color(0xFFFF5A1F)
    val fill = Brush.linearGradient(listOf(Color(0x29FFFFFF), Color(0x0FFFFFFF), Color(0x0AFFFFFF)))
}

/** The out-of-focus AI / security picture behind the glass screens, with a soft top and bottom fade. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF070A12))) {
        Image(
            painterResource(R.drawable.home_background), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x73050710), 0.22f to Color.Transparent, 0.8f to Color.Transparent, 1f to Color(0x99050710),
                ),
            ),
        )
        content()
    }
}

/** A frosted-glass panel. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), onClick: (() -> Unit)? = null): Modifier =
    this.clip(shape).background(Glass.fill, shape).border(1.dp, Glass.edge, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

/** A round glass button with an icon (e.g. the "open" arrows on the home boxes). */
@Composable
fun GlassCircle(icon: ImageVector, description: String?, size: Dp = 46.dp, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(size).glass(CircleShape, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(size * 0.43f))
    }
}

/** Small rounded label, e.g. "Coming soon". */
@Composable
fun GlassPill(text: String) {
    Box(Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = Glass.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Initials in a gradient tile, standing in for a profile picture. */
@Composable
fun InitialsAvatar(name: String, size: Dp = 60.dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
    }
}
