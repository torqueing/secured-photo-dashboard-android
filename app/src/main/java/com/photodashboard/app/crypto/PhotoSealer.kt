package com.photodashboard.app.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import com.photodashboard.app.exif.ExifBuilder
import com.photodashboard.app.exif.GeoFix
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.Locale

/** The organisation key photos are wrapped for (from GET /keys/public), cached on the phone. */
data class OrgKey(val keyArn: String, val publicKeySpkiB64: String, val recoveryKeyId: String? = null, val recoveryPublicKeyB64: String? = null)

/** What sealing produced: two .spd files on disk plus the metadata the server needs. */
data class SealedPhoto(
    val photoId: String,
    val photoFile: File,
    val thumbFile: File,
    val takenAt: String,
    val sha256: String,
    val signedPayload: String,
    val signature: String,
    val deviceKeyId: String,
    val keyArn: String,
    val orgWrappedKey: String,
    val deviceWrappedKey: String,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long,
)

/**
 * Spec 4.3: everything happens in memory; only the encrypted .spd files touch storage.
 * EXIF -> thumbnail -> fingerprint + signature -> encrypt (Tink) -> wrap key twice -> wipe.
 */
class PhotoSealer(private val keys: DeviceKeys, private val dir: File) {
    private val random = SecureRandom()
    private val isoSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)

    fun seal(photoId: String, jpeg: ByteArray, rotationDegrees: Int, capturedAt: Long, fix: GeoFix?, org: OrgKey): SealedPhoto {
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(capturedAt))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            .also { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, it) }
        val withExif = ExifBuilder.withExif(
            jpeg,
            ExifBuilder.Fields(
                capturedAt, offset, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL,
                ExifBuilder.orientationFor(rotationDegrees), bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 },
                fix,
            ),
        )
        val thumb = thumbnail(jpeg, rotationDegrees)
        val takenAt = isoSeconds.format(Instant.ofEpochMilli(capturedAt).atOffset(offset))
        val sha = SpdFormat.sha256Hex(withExif)
        val deviceKeyId = keys.deviceKeyId()
        val payload = signingPayload(photoId, sha, takenAt, fix?.latitude, fix?.longitude, deviceKeyId)
        val signature = keys.sign(payload.toByteArray(Charsets.UTF_8))

        val dek = ByteArray(32).also { random.nextBytes(it) }
        try {
            val orgWrapped = DeviceKeys.wrapForPublicKey(dek, org.publicKeySpkiB64)
            val wrapped = buildList {
                add(SpdFormat.WrappedKey(SpdFormat.WRAP_KMS, key_arn = org.keyArn, wrapped = orgWrapped))
                if (org.recoveryPublicKeyB64 != null) {
                    add(SpdFormat.WrappedKey(SpdFormat.WRAP_RECOVERY, key_id = org.recoveryKeyId,
                        wrapped = DeviceKeys.wrapForPublicKey(dek, org.recoveryPublicKeyB64)))
                }
            }
            val sig = SpdFormat.Signature("ES256", deviceKeyId, signature)
            fun header(kind: String, plain: ByteArray) = SpdFormat.Header(
                photo_id = photoId, kind = kind, wrapped_keys = wrapped, sha256_plaintext = SpdFormat.sha256Hex(plain),
                signature = sig, taken_at = takenAt,
            )
            dir.mkdirs()
            val photoFile = File(dir, "$photoId.spd")
            val thumbFile = File(dir, "$photoId.thumb.spd")
            photoFile.writeBytes(SpdFormat.seal(dek, withExif, header("photo", withExif)))
            thumbFile.writeBytes(SpdFormat.seal(dek, thumb, header("thumbnail", thumb)))
            return SealedPhoto(
                photoId, photoFile, thumbFile, takenAt, sha, payload, signature, deviceKeyId, org.keyArn, orgWrapped,
                keys.wrapForDevice(dek), bounds.outWidth.takeIf { it > 0 }, bounds.outHeight.takeIf { it > 0 },
                photoFile.length(),
            )
        } finally {
            // Best effort: the JVM may hold copies, which is why plaintext never goes to disk.
            Arrays.fill(dek, 0)
            Arrays.fill(withExif, 0)
            Arrays.fill(thumb, 0)
            Arrays.fill(jpeg, 0)
        }
    }

    private fun thumbnail(jpeg: ByteArray, rotationDegrees: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val scale = THUMB_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply { if (scale < 1f) postScale(scale, scale); postRotate(rotationDegrees.toFloat()) }
        val thumb = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (thumb !== decoded) thumb.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    companion object {
        const val THUMB_EDGE = 320

        /** Canonical JSON the phone signs; byte-identical to api/app/spd.py signing_payload. */
        fun signingPayload(photoId: String, sha256: String, takenAt: String, lat: Double?, lon: Double?, deviceKeyId: String): String =
            "{\"device_key_id\":\"$deviceKeyId\",\"latitude\":${lat ?: "null"},\"longitude\":${lon ?: "null"}," +
                "\"photo_id\":\"$photoId\",\"sha256\":\"$sha256\",\"taken_at\":\"$takenAt\"}"
    }
}
