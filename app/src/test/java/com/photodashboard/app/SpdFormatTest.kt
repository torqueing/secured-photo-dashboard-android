package com.photodashboard.app

import com.photodashboard.app.crypto.SpdFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Tink must read the vectors written by the server's Python implementation, and vice versa. */
class SpdFormatTest {
    private val json = requireNotNull(javaClass.classLoader).getResource("spd-vectors.json").readText()
    private val dek = Base64.getDecoder().decode(Regex("\"dek_b64\": \"([^\"]+)\"").find(json)!!.groupValues[1])
    private val vectors = Regex("\"plaintext_b64\": \"([^\"]*)\".*?\"spd_b64\": \"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
        .findAll(json).map { Base64.getDecoder().decode(it.groupValues[1]) to Base64.getDecoder().decode(it.groupValues[2]) }
        .toList()

    @Test
    fun tinkDecryptsServerVectors() {
        assertEquals(8, vectors.size)
        for ((plain, spd) in vectors) {
            val (header, out) = SpdFormat.open(spd, dek)
            assertArrayEquals(plain, out)
            assertEquals("photo", header.kind)
        }
    }

    @Test
    fun sealOpenRoundTripAndTamper() {
        val plain = ByteArray(300_000) { (it * 13).toByte() }
        val header = SpdFormat.Header(
            photo_id = "p1", kind = "photo", wrapped_keys = emptyList(),
            sha256_plaintext = SpdFormat.sha256Hex(plain), taken_at = "2026-10-13T09:15:02Z",
        )
        val sealed = SpdFormat.seal(dek, plain, header)
        assertArrayEquals(plain, SpdFormat.open(sealed, dek).second)
        val tampered = sealed.copyOf().also { it[it.size - 10] = (it[it.size - 10].toInt() xor 1).toByte() }
        assertTrue(runCatching { SpdFormat.open(tampered, dek) }.isFailure)
        assertTrue(runCatching { SpdFormat.open(sealed, ByteArray(32)) }.isFailure)
    }
}
