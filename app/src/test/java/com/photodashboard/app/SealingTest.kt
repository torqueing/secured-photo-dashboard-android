package com.photodashboard.app

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.photodashboard.app.crypto.DeviceKeys
import com.photodashboard.app.crypto.PhotoSealer
import com.photodashboard.app.exif.ExifBuilder
import com.photodashboard.app.exif.GeoFix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.imageio.ImageIO

class SealingTest {
    private fun tinyJpeg(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "jpg", it)
    }.toByteArray()

    @Test
    fun exifWrittenInMemoryReadsBack() {
        val fix = GeoFix(-33.856784, 151.215297, 21.4, 4.8f, 212f, 2.5f, "fused", 1_791_794_467_000L)
        val jpeg = ExifBuilder.withExif(
            tinyJpeg(),
            ExifBuilder.Fields(1_791_794_467_000L, ZoneOffset.ofHours(11), "Google", "Pixel 10", 6, 64, 48, fix),
        )
        val md = ImageMetadataReader.readMetadata(ByteArrayInputStream(jpeg))
        val gps = md.getFirstDirectoryOfType(GpsDirectory::class.java)
        val loc = gps.geoLocation
        assertEquals(-33.856784, loc.latitude, 1e-6)
        assertEquals(151.215297, loc.longitude, 1e-6)
        assertEquals(21.4, gps.getRational(GpsDirectory.TAG_ALTITUDE).toDouble(), 1e-3)
        assertEquals(4.8, gps.getRational(GpsDirectory.TAG_H_POSITIONING_ERROR).toDouble(), 1e-3)
        assertEquals(212.0, gps.getRational(GpsDirectory.TAG_IMG_DIRECTION).toDouble(), 1e-2)
        assertEquals(9.0, gps.getRational(GpsDirectory.TAG_SPEED).toDouble(), 1e-3) // 2.5 m/s = 9 km/h
        assertEquals("2026:10:12", gps.getString(GpsDirectory.TAG_DATE_STAMP))
        val ifd0 = md.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        assertEquals("Google", ifd0.getString(ExifIFD0Directory.TAG_MAKE))
        assertEquals("Pixel 10", ifd0.getString(ExifIFD0Directory.TAG_MODEL))
        assertEquals(6, ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION))
        val sub = md.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        assertEquals("2026:10:12 19:41:07", sub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))
        assertEquals("+11:00", sub.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL))
        // The image itself is untouched.
        assertTrue(ImageIO.read(ByteArrayInputStream(jpeg)).width == 64)
    }

    @Test
    fun existingExifIsReplacedNotDuplicated() {
        val f = ExifBuilder.Fields(0, ZoneOffset.UTC, "A", "B", 1, null, null, null)
        val once = ExifBuilder.withExif(tinyJpeg(), f)
        val twice = ExifBuilder.withExif(once, f.copy(make = "C"))
        val md = ImageMetadataReader.readMetadata(ByteArrayInputStream(twice))
        assertEquals(1, md.getDirectoriesOfType(ExifIFD0Directory::class.java).count())
        assertEquals("C", md.getFirstDirectoryOfType(ExifIFD0Directory::class.java).getString(ExifIFD0Directory.TAG_MAKE))
    }

    @Test
    fun signingPayloadMatchesServerBytes() {
        // Exactly what api/app/spd.py signing_payload produces for the same values.
        assertEquals(
            "{\"device_key_id\":\"0123456789abcdef0123456789abcdef\",\"latitude\":51.50135,\"longitude\":-0.14189," +
                "\"photo_id\":\"5a1ed000-0000-4000-8000-000000000001\",\"sha256\":\"${"ab".repeat(32)}\"," +
                "\"taken_at\":\"2026-10-13T09:15:02+01:00\"}",
            PhotoSealer.signingPayload("5a1ed000-0000-4000-8000-000000000001", "ab".repeat(32), "2026-10-13T09:15:02+01:00",
                51.50135, -0.14189, "0123456789abcdef0123456789abcdef"),
        )
        assertEquals(
            "{\"device_key_id\":\"k\",\"latitude\":null,\"longitude\":null,\"photo_id\":\"p\",\"sha256\":\"${"cd".repeat(32)}\"," +
                "\"taken_at\":\"2026-10-13T08:00:00Z\"}",
            PhotoSealer.signingPayload("p", "cd".repeat(32), "2026-10-13T08:00:00Z", null, null, "k"),
        )
    }

    @Test
    fun orgWrapMatchesKmsOaepSha256() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val dek = ByteArray(32) { it.toByte() }
        val wrapped = DeviceKeys.wrapForPublicKey(dek, Base64.getEncoder().encodeToString(kp.public.encoded))
        // KMS RSAES_OAEP_SHA_256 = OAEP with SHA-256 and MGF1-SHA-256.
        val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE, kp.private, spec) }
        assertArrayEquals(dek, c.doFinal(Base64.getDecoder().decode(wrapped)))
    }
}
