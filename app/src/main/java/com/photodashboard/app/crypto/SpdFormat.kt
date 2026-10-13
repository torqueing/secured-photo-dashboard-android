package com.photodashboard.app.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The .spd sealed-photo format (spec 4.4), using Tink's AES256_GCM_HKDF_4KB streaming AEAD.
 * Byte-compatible with the server (api/app/spd.py) and the dashboard (web/src/spd.js);
 * checked against shared test vectors in SpdFormatTest.
 */
object SpdFormat {
    private val MAGIC = byteArrayOf(0x53, 0x50, 0x44, 0x31) // "SPD1"
    const val ALG = "AES256_GCM_HKDF_4KB"
    const val WRAP_KMS = "aws-kms-rsa-oaep-sha256"
    const val WRAP_RECOVERY = "recovery-rsa-oaep-sha256"
    private const val SEGMENT_SIZE = 4096

    @JsonClass(generateAdapter = true)
    data class WrappedKey(val type: String, val key_arn: String? = null, val key_id: String? = null, val wrapped: String)

    @JsonClass(generateAdapter = true)
    data class Signature(val alg: String, val device_key_id: String, val value: String)

    @JsonClass(generateAdapter = true)
    data class Header(
        val v: Int = 1,
        val photo_id: String,
        val kind: String,
        val content_type: String = "image/jpeg",
        val alg: String = ALG,
        val wrapped_keys: List<WrappedKey>,
        val sha256_plaintext: String,
        val signature: Signature? = null,
        val taken_at: String,
    )

    private val headerAdapter = Moshi.Builder().build().adapter(Header::class.java)

    private fun aead(dek: ByteArray) = AesGcmHkdfStreaming(dek, "HmacSha256", 32, SEGMENT_SIZE, 0)

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun associatedData(headerBytes: ByteArray): ByteArray =
        ByteBuffer.allocate(6 + headerBytes.size).put(MAGIC).putShort(headerBytes.size.toShort()).put(headerBytes).array()

    /** Encrypt [plaintext] into a complete .spd file. */
    fun seal(dek: ByteArray, plaintext: ByteArray, header: Header): ByteArray {
        val headerBytes = headerAdapter.toJson(header).toByteArray(Charsets.UTF_8)
        require(headerBytes.size <= 0xFFFF) { "header too large" }
        val ad = associatedData(headerBytes)
        val out = ByteArrayOutputStream(plaintext.size + plaintext.size / 200 + 512)
        out.write(ad)
        aead(dek).newEncryptingStream(out, ad).use { it.write(plaintext) }
        return out.toByteArray()
    }

    data class Parsed(val header: Header, val associatedData: ByteArray, val ciphertextOffset: Int)

    fun parse(data: ByteArray): Parsed {
        require(data.size >= 6 && data.copyOfRange(0, 4).contentEquals(MAGIC)) { "not an .spd file" }
        val n = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        require(data.size >= 6 + n) { "truncated header" }
        val header = requireNotNull(headerAdapter.fromJson(String(data, 6, n, Charsets.UTF_8))) { "bad header" }
        require(header.v == 1 && header.alg == ALG) { "unsupported .spd version" }
        return Parsed(header, data.copyOfRange(0, 6 + n), 6 + n)
    }

    /** Decrypt an .spd file; throws if the key is wrong, the file changed or the fingerprint differs. */
    fun open(data: ByteArray, dek: ByteArray): Pair<Header, ByteArray> {
        val parsed = parse(data)
        val input = ByteArrayInputStream(data, parsed.ciphertextOffset, data.size - parsed.ciphertextOffset)
        val plaintext = aead(dek).newDecryptingStream(input, parsed.associatedData).use { it.readBytes() }
        check(sha256Hex(plaintext) == parsed.header.sha256_plaintext) { "fingerprint mismatch" }
        return parsed.header to plaintext
    }
}
