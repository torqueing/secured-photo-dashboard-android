package com.photodashboard.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.photodashboard.app.data.PhotoEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.Instant
import java.time.ZoneId

/** OpenStreetMap with a pin for every geotagged photo; tapping a pin's label opens the photo. */
@Composable
fun MapScreen(photos: List<PhotoEntity>, onOpen: (PhotoEntity) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val located = remember(photos) { photos.filter { it.latitude != null && it.longitude != null } }
    val map = remember {
        Configuration.getInstance().apply {
            // OpenStreetMap's tile policy asks apps to identify themselves; tiles are cached in app storage only.
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir.resolve("osmdroid")
            osmdroidTileCache = context.cacheDir.resolve("osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 3.0
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { map },
            update = { view ->
                view.overlays.removeAll { it is Marker }
                located.forEach { p ->
                    view.overlays.add(Marker(view).apply {
                        position = GeoPoint(p.latitude!!, p.longitude!!)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = Instant.ofEpochMilli(p.capturedAt).atZone(ZoneId.systemDefault()).format(DisplayFormat.dateTime)
                        snippet = "%.5f, %.5f · tap to open".format(p.latitude, p.longitude)
                        setOnMarkerClickListener { m, _ ->
                            if (m.isInfoWindowShown) onOpen(p) else m.showInfoWindow()
                            true
                        }
                    })
                }
                when {
                    located.size == 1 -> { view.controller.setZoom(16.0); view.controller.setCenter(GeoPoint(located[0].latitude!!, located[0].longitude!!)) }
                    located.size > 1 -> view.post {
                        val box = BoundingBox.fromGeoPointsSafe(located.map { GeoPoint(it.latitude!!, it.longitude!!) })
                        view.zoomToBoundingBox(box.increaseByScale(1.3f), false)
                    }
                    else -> { view.controller.setZoom(6.0); view.controller.setCenter(GeoPoint(54.0, -2.5)) }
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.safeDrawingPadding().padding(14.dp)) {
            GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(14.dp).fillMaxWidth()
                .glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                if (located.isEmpty()) "No geotagged photos yet" else "${located.size} geotagged photo${if (located.size == 1) "" else "s"} · tap a pin, then its label to open",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
        Text("© OpenStreetMap contributors", color = Color(0xFF333333), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(end = 8.dp, top = 4.dp))
    }
}
