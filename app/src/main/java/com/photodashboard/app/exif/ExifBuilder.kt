package com.photodashboard.app.exif

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Writes the EXIF tags of AND-7 into a JPEG held in memory (AndroidX ExifInterface can only
 * edit files). Produces a big-endian TIFF block in an APP1 "Exif" segment that replaces any
 * EXIF segment already in the JPEG.
 */
object ExifBuilder {
    private const val ASCII = 2
    private const val SHORT = 3
    private const val LONG = 4
    private const val RATIONAL = 5
    private const val BYTE = 1
    private const val UNDEFINED = 7

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    private fun ascii(tag: Int, s: String) = (s + "\u0000").toByteArray(Charsets.US_ASCII).let { Entry(tag, ASCII, it.size, it) }
    private fun short(tag: Int, v: Int) = Entry(tag, SHORT, 1, ByteBuffer.allocate(2).putShort(v.toShort()).array())
    private fun long(tag: Int, v: Long) = Entry(tag, LONG, 1, ByteBuffer.allocate(4).putInt(v.toInt()).array())
    private fun byte(tag: Int, vararg v: Int) = Entry(tag, BYTE, v.size, ByteArray(v.size) { v[it].toByte() })
    private fun rationals(tag: Int, vararg r: Pair<Long, Long>) = Entry(
        tag, RATIONAL, r.size,
        ByteBuffer.allocate(8 * r.size).apply { r.forEach { (n, d) -> putInt(n.toInt()); putInt(d.toInt()) } }.array(),
    )

    private fun dms(value: Double): Array<Pair<Long, Long>> {
        val totalScaled = (abs(value) * 3600.0 * 10_000).roundToLong()
        val deg = totalScaled / (3600L * 10_000)
        val min = (totalScaled % (3600L * 10_000)) / (60L * 10_000)
        val sec = totalScaled % (60L * 10_000)
        return arrayOf(deg to 1L, min to 1L, sec to 10_000L)
    }

    private fun decimal(v: Double, denominator: Long = 1000) = (abs(v) * denominator).roundToLong() to denominator

    /** EXIF orientation value for a CameraX rotation (0/90/180/270). */
    fun orientationFor(rotationDegrees: Int) = when ((rotationDegrees % 360 + 360) % 360) {
        90 -> 6
        180 -> 3
        270 -> 8
        else -> 1
    }

    data class Fields(
        val capturedAtMillis: Long,
        val offset: ZoneOffset,
        val make: String,
        val model: String,
        val orientation: Int,
        val width: Int?,
        val height: Int?,
        val fix: GeoFix?,
    )

    /** Build the TIFF block (without the "Exif\0\0" prefix). */
    fun tiff(f: Fields): ByteArray {
        val ifd0 = mutableListOf(
            ascii(0x010F, f.make),
            ascii(0x0110, f.model),
            short(0x0112, f.orientation),
            ascii(0x0132, ExifFormat.dateTimeOriginal(f.capturedAtMillis, f.offset)),
        )
        val exif = mutableListOf(
            ascii(0x9003, ExifFormat.dateTimeOriginal(f.capturedAtMillis, f.offset)),
            ascii(0x9011, ExifFormat.offsetTime(f.offset)),
        )
        f.width?.let { exif += long(0xA002, it.toLong()) }
        f.height?.let { exif += long(0xA003, it.toLong()) }
        val gps = mutableListOf<Entry>()
        f.fix?.let { fix ->
            gps += byte(0x0000, 2, 3, 0, 0)
            gps += ascii(0x0001, ExifFormat.latitudeRef(fix.latitude))
            gps += rationals(0x0002, *dms(fix.latitude))
            gps += ascii(0x0003, ExifFormat.longitudeRef(fix.longitude))
            gps += rationals(0x0004, *dms(fix.longitude))
            fix.altitudeM?.let {
                gps += byte(0x0005, if (it < 0) 1 else 0)
                gps += rationals(0x0006, decimal(it))
            }
            val t = Instant.ofEpochMilli(fix.timeMillis).atOffset(ZoneOffset.UTC)
            gps += rationals(0x0007, t.hour.toLong() to 1L, t.minute.toLong() to 1L, t.second.toLong() to 1L)
            fix.speedMps?.let {
                gps += ascii(0x000C, "K")
                gps += rationals(0x000D, decimal(ExifFormat.metresPerSecondToKmh(it)))
            }
            fix.bearingDeg?.let {
                gps += ascii(0x0010, "T")
                gps += rationals(0x0011, decimal(it.toDouble(), 100))
            }
            fix.provider?.let {
                val bytes = "ASCII\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII) + it.toByteArray(Charsets.US_ASCII)
                gps += Entry(0x001B, UNDEFINED, bytes.size, bytes)
            }
            gps += ascii(0x001D, ExifFormat.gpsDateStamp(fix.timeMillis))
            fix.accuracyM?.let { gps += rationals(0x001F, decimal(it.toDouble())) }
        }

        // Layout: header(8) | IFD0 | Exif IFD | GPS IFD | value data; pointers fixed up below.
        ifd0 += long(0x8769, 0)
        if (gps.isNotEmpty()) ifd0 += long(0x8825, 0)
        val ifds = listOf(ifd0.sortedBy { it.tag }, exif.sortedBy { it.tag }, gps.sortedBy { it.tag }).filter { it.isNotEmpty() }
        val ifdSize = { e: List<Entry> -> 2 + 12 * e.size + 4 }
        val ifdOffsets = ifds.runningFold(8) { off, e -> off + ifdSize(e) }
        var dataOffset = ifdOffsets.last()
        val out = ByteBuffer.allocate(64 * 1024)
        out.put(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8))
        val data = ByteArrayOutputStream()
        ifds.forEachIndexed { i, entries ->
            out.putShort(entries.size.toShort())
            for (e in entries) {
                out.putShort(e.tag.toShort()).putShort(e.type.toShort()).putInt(e.count)
                val value = when (e.tag) {
                    0x8769 -> ByteBuffer.allocate(4).putInt(ifdOffsets[1]).array()
                    0x8825 -> ByteBuffer.allocate(4).putInt(ifdOffsets[2]).array()
                    else -> e.data
                }
                if (value.size <= 4) {
                    out.put(value.copyOf(4))
                } else {
                    out.putInt(dataOffset + data.size())
                    data.write(value)
                    if (data.size() % 2 == 1) data.write(0) // keep offsets word-aligned
                }
            }
            out.putInt(0) // no next IFD
            check(out.position() == ifdOffsets[i + 1])
        }
        out.put(data.toByteArray())
        return out.array().copyOf(out.position())
    }

    /** Return a copy of [jpeg] whose EXIF is exactly [f] (any previous EXIF segment is dropped). */
    fun withExif(jpeg: ByteArray, f: Fields): ByteArray {
        require(jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "not a JPEG" }
        val tiff = tiff(f)
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff
        require(payload.size + 2 <= 0xFFFF) { "EXIF too large" }
        val out = ByteArrayOutputStream(jpeg.size + payload.size + 4)
        out.write(jpeg, 0, 2) // SOI
        var pos = 2
        var wrote = false
        fun writeApp1() {
            out.write(0xFF); out.write(0xE1)
            out.write((payload.size + 2) shr 8); out.write((payload.size + 2) and 0xFF)
            out.write(payload)
            wrote = true
        }
        while (pos + 4 <= jpeg.size && jpeg[pos] == 0xFF.toByte()) {
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break // start of scan: the rest is image data
            val len = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            val isExif = marker == 0xE1 && len >= 8 &&
                String(jpeg, pos + 4, 4, Charsets.US_ASCII) == "Exif"
            if (marker == 0xE0) {
                out.write(jpeg, pos, len + 2) // JFIF APP0 stays first
            } else {
                if (!wrote) writeApp1()
                if (!isExif) out.write(jpeg, pos, len + 2)
            }
            pos += len + 2
        }
        if (!wrote) writeApp1()
        out.write(jpeg, pos, jpeg.size - pos)
        return out.toByteArray()
    }
}
