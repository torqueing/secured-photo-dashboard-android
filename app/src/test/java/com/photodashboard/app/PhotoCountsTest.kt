package com.photodashboard.app

import com.photodashboard.app.data.MyStats
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.ui.PhotoCounts
import com.photodashboard.app.upload.UploadState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PhotoCountsTest {
    private val zone = ZoneId.of("Europe/London")

    private fun photo(daysAgo: Long, lat: Double? = 51.5) = PhotoEntity(
        localId = "p$daysAgo-$lat", userId = 1, filePath = null, mediaStoreUri = null,
        capturedAt = LocalDate.now(zone).minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        utcOffsetMinutes = 0, latitude = lat, longitude = lat, altitudeM = null, accuracyM = null, bearingDeg = null,
        speedMps = null, locationProvider = null, noLocation = lat == null, width = null, height = null, sizeBytes = null,
        state = UploadState.UPLOADED,
    )

    @Test
    fun serverNumbersWinWhenKnown() {
        val c = PhotoCounts.of(MyStats(photosToday = 12, photosWeek = 64, photosAll = 1284, geotagged = 900), listOf(photo(0)), zone)
        assertEquals(PhotoCounts(12, 64, 1284, 900), c)
    }

    @Test
    fun offlineCountsComeFromThisPhone() {
        val c = PhotoCounts.of(null, listOf(photo(0), photo(0, lat = null), photo(40)), zone)
        assertEquals(2, c.today)
        assertEquals(3, c.all)
        assertEquals(2, c.geotagged)
        // "This week" starts on Monday: today's two always count, the 40-day-old one never does.
        assertEquals(2, c.week)
    }
}
