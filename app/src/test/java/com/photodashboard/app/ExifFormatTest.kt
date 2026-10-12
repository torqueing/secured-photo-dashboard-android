package com.photodashboard.app

import com.photodashboard.app.exif.ExifFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ExifFormatTest {
    @Test
    fun dmsRoundTripIsWithinOneMicrodegree() {
        for (v in listOf(51.50135, -0.14189, -33.856784, 151.215297, 0.0, 89.999999, -179.999999)) {
            val back = ExifFormat.fromDmsRational(ExifFormat.toDmsRational(v))
            assertEquals("value $v", kotlin.math.abs(v), back, 1e-7)
        }
    }

    @Test
    fun dmsFormat() {
        assertEquals("51/1,30/1,486000/10000", ExifFormat.toDmsRational(51.5135))
    }

    @Test
    fun refs() {
        assertEquals("S", ExifFormat.latitudeRef(-1.0))
        assertEquals("N", ExifFormat.latitudeRef(1.0))
        assertEquals("W", ExifFormat.longitudeRef(-0.1))
        assertEquals("E", ExifFormat.longitudeRef(0.1))
        assertEquals("1", ExifFormat.altitudeRef(-3.0))
        assertEquals("0", ExifFormat.altitudeRef(21.4))
    }

    @Test
    fun rationals() {
        assertEquals("21400/1000", ExifFormat.toRational(21.4))
        assertEquals("4800/1000", ExifFormat.toRational(4.8))
        assertEquals(36.0, ExifFormat.metresPerSecondToKmh(10f), 1e-4)
    }

    @Test
    fun timestamps() {
        val t = 1_791_794_467_000L // 2026-10-12T08:41:07Z
        val bst = ZoneOffset.ofHours(1)
        assertEquals("2026:10:12 09:41:07", ExifFormat.dateTimeOriginal(t, bst))
        assertEquals("+01:00", ExifFormat.offsetTime(bst))
        assertEquals("+00:00", ExifFormat.offsetTime(ZoneOffset.UTC))
        assertEquals("2026:10:12", ExifFormat.gpsDateStamp(t))
        assertEquals("8/1,41/1,7/1", ExifFormat.gpsTimeStamp(t))
    }
}
