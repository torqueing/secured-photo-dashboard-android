package com.photodashboard.app.exif

import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.ZoneOffset

/** A location fix in the shape we write to EXIF. */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float?,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val provider: String?,
    val timeMillis: Long,
)

/** Writes location, time and device tags into a JPEG in place (AND-7). */
object ExifWriter {
    fun write(file: File, capturedAtMillis: Long, offset: ZoneOffset, fix: GeoFix?) {
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, ExifFormat.dateTimeOriginal(capturedAtMillis, offset))
        exif.setAttribute(ExifInterface.TAG_DATETIME, ExifFormat.dateTimeOriginal(capturedAtMillis, offset))
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifFormat.offsetTime(offset))
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, ExifFormat.offsetTime(offset))
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        if (fix != null) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, ExifFormat.toDmsRational(fix.latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, ExifFormat.latitudeRef(fix.latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, ExifFormat.toDmsRational(fix.longitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, ExifFormat.longitudeRef(fix.longitude))
            fix.altitudeM?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, ExifFormat.toRational(it))
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, ExifFormat.altitudeRef(it))
            }
            fix.accuracyM?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifFormat.toRational(it.toDouble()))
            }
            fix.bearingDeg?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, ExifFormat.toRational(it.toDouble()))
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.GPS_DIRECTION_TRUE)
            }
            fix.speedMps?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED, ExifFormat.toRational(ExifFormat.metresPerSecondToKmh(it)))
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.GPS_SPEED_KILOMETERS_PER_HOUR)
            }
            fix.provider?.let { exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, it) }
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, ExifFormat.gpsDateStamp(fix.timeMillis))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, ExifFormat.gpsTimeStamp(fix.timeMillis))
        }
        exif.saveAttributes()
    }

    data class Dimensions(val width: Int?, val height: Int?)

    fun dimensions(file: File): Dimensions {
        val exif = ExifInterface(file)
        val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
        val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
        return Dimensions(w, h)
    }
}
