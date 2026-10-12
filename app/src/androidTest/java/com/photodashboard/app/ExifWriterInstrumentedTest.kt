package com.photodashboard.app

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photodashboard.app.exif.ExifWriter
import com.photodashboard.app.exif.GeoFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneOffset

/** Writes EXIF for a mocked fix into a real JPEG on device and reads it back (spec: instrumented EXIF test). */
@RunWith(AndroidJUnit4::class)
class ExifWriterInstrumentedTest {
    @Test
    fun writesLocationTimeAndAccuracy() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.cacheDir, "exif-test.jpg")
        Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
        val fix = GeoFix(-33.856784, 151.215297, 21.4, 4.8f, 212f, 1.5f, "fused", 1_791_794_467_000L)

        ExifWriter.write(file, 1_791_794_467_000L, ZoneOffset.ofHours(11), fix)

        val exif = ExifInterface(file)
        val latLong = exif.latLong
        assertNotNull(latLong)
        latLong!!
        assertEquals(-33.856784, latLong[0], 1e-6)
        assertEquals(151.215297, latLong[1], 1e-6)
        assertEquals(21.4, exif.getAltitude(0.0), 1e-3)
        assertEquals(4.8, exif.getAttributeDouble(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, 0.0), 1e-3)
        assertEquals("2026:10:12 19:41:07", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+11:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals("2026:10:12", exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP))
    }
}
