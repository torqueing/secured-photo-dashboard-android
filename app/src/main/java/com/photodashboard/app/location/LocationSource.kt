package com.photodashboard.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.photodashboard.app.exif.GeoFix
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Fresh fix on shutter press, 5 s timeout, else last known fix under 2 minutes old (AND-6). */
class LocationSource(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // callers check permission first
    suspend fun fixForCapture(): GeoFix? {
        val cts = CancellationTokenSource()
        val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MS) {
            runCatching {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .setDurationMillis(FRESH_TIMEOUT_MS)
                        .build(),
                    cts.token,
                ).await()
            }.getOrNull()
        }
        if (fresh == null) cts.cancel()
        val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()?.takeIf {
            val ageMs = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
            ageMs in 0..MAX_LAST_KNOWN_AGE_MS
        } ?: return null
        return GeoFix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = if (location.hasAltitude()) location.altitude else null,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            bearingDeg = if (location.hasBearing()) location.bearing else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            provider = location.provider ?: "fused",
            timeMillis = location.time,
        ).also {
            // Mock locations are allowed in v1 (useful for testing) but noted in the provider name.
            if (Build.VERSION.SDK_INT >= 31 && location.isMock) return it.copy(provider = "${it.provider}-mock")
        }
    }

    companion object {
        const val FRESH_TIMEOUT_MS = 5_000L
        const val MAX_LAST_KNOWN_AGE_MS = 2 * 60_000L
    }
}
