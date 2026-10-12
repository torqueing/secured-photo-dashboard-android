package com.photodashboard.app.exif

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure formatting of EXIF tag values, kept free of Android types so it is unit-testable. */
object ExifFormat {
    /** Seconds are stored with this denominator: 1e-4 s is about 3e-9 degrees. */
    private const val SECONDS_DENOMINATOR = 10_000L
    private const val DECIMAL_DENOMINATOR = 1_000L

    /** Decimal degrees to an EXIF DMS rational triple, e.g. "51/1,30/1,486000/10000". */
    fun toDmsRational(value: Double): String {
        val totalSecondsScaled = (abs(value) * 3600.0 * SECONDS_DENOMINATOR).roundToLong()
        val perDegree = 3600L * SECONDS_DENOMINATOR
        val perMinute = 60L * SECONDS_DENOMINATOR
        val degrees = totalSecondsScaled / perDegree
        val minutes = (totalSecondsScaled % perDegree) / perMinute
        val seconds = totalSecondsScaled % perMinute
        return "$degrees/1,$minutes/1,$seconds/$SECONDS_DENOMINATOR"
    }

    /** Inverse of [toDmsRational], used by tests and when reading tags back. */
    fun fromDmsRational(dms: String): Double {
        val parts = dms.split(",").map { r ->
            val (n, d) = r.split("/").map { it.trim().toDouble() }
            if (d == 0.0) 0.0 else n / d
        }
        return parts[0] + parts[1] / 60.0 + parts[2] / 3600.0
    }

    fun latitudeRef(lat: Double) = if (lat < 0) "S" else "N"

    fun longitudeRef(lon: Double) = if (lon < 0) "W" else "E"

    /** Non-negative decimal as a rational, e.g. 21.4 -> "21400/1000". */
    fun toRational(value: Double): String = "${(abs(value) * DECIMAL_DENOMINATOR).roundToLong()}/$DECIMAL_DENOMINATOR"

    /** EXIF GPSAltitudeRef: "0" above sea level, "1" below. */
    fun altitudeRef(altitude: Double) = if (altitude < 0) "1" else "0"

    /** GPSSpeed is written in km/h with GPSSpeedRef "K". */
    fun metresPerSecondToKmh(mps: Float): Double = mps * 3.6

    private val dateTime = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val gpsDate = DateTimeFormatter.ofPattern("yyyy:MM:dd")

    /** Local wall-clock time for DateTimeOriginal. */
    fun dateTimeOriginal(epochMillis: Long, offset: ZoneOffset): String =
        dateTime.format(Instant.ofEpochMilli(epochMillis).atOffset(offset))

    /** OffsetTimeOriginal, e.g. "+01:00". */
    fun offsetTime(offset: ZoneOffset): String = if (offset.totalSeconds == 0) "+00:00" else offset.id

    /** GPSDateStamp (UTC). */
    fun gpsDateStamp(epochMillis: Long): String = gpsDate.format(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC))

    /** GPSTimeStamp (UTC) as "HH/1,mm/1,ss/1". */
    fun gpsTimeStamp(epochMillis: Long): String {
        val t = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC)
        return "${t.hour}/1,${t.minute}/1,${t.second}/1"
    }
}
