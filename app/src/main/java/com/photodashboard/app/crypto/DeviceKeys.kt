package com.photodashboard.app.crypto

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Hardware-backed keys in the Android Keystore (spec 4.1):
 *  - signing keys: EC P-256 with key attestation, one per account signed in on this phone (the
 *    server ties each key to one user); signs each photo's fingerprint at capture.
 *  - storage key: RSA-2048 that wraps each photo key for this phone. Wrapping uses the public
 *    half (no unlock needed at capture); unwrapping needs fingerprint/PIN within 5 minutes,
 *    which is the gallery lock (decision D2).
 *  - token key: AES-256-GCM for encrypting sign-in tokens at rest.
 */
class DeviceKeys(private val context: Context) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun hasStrongBox() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    /** Generate with StrongBox when the phone has it, else the regular secure hardware. */
    private fun <T> withStrongBoxFallback(build: (strongBox: Boolean) -> T): T =
        if (hasStrongBox()) {
            try {
                build(true)
            } catch (_: StrongBoxUnavailableException) {
                build(false)
            }
        } else {
            build(false)
        }

    // ---------------------------------------------------------------- signing key
    private fun signingAlias(userId: Long) = "$SIGNING-u$userId"

    private fun ensureSigningKey(userId: Long): String {
        val alias = signingAlias(userId)
        if (keyStore.containsAlias(alias)) return alias
        withStrongBoxFallback { strongBox ->
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(ByteArray(32).also { SecureRandom().nextBytes(it) })
                .setIsStrongBoxBacked(strongBox)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply { initialize(spec) }
                .generateKeyPair()
        }
        return alias
    }

    fun signingPublicKeySpki(userId: Long): ByteArray =
        keyStore.getCertificate(ensureSigningKey(userId)).publicKey.encoded

    /** First 32 hex characters of SHA-256(SPKI): the id the server also derives. */
    fun deviceKeyId(userId: Long): String = MessageDigest.getInstance("SHA-256").digest(signingPublicKeySpki(userId))
        .joinToString("") { "%02x".format(it) }.take(32)

    fun attestationChainB64(userId: Long): List<String> =
        keyStore.getCertificateChain(ensureSigningKey(userId)).orEmpty().map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }

    /** ES256 signature (DER) over [payload], base64. */
    fun sign(userId: Long, payload: ByteArray): String {
        val key = keyStore.getKey(ensureSigningKey(userId), null) as PrivateKey
        val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(payload) }.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    // ---------------------------------------------------------------- storage key (gallery)
    /** True when unwrapping photo keys requires fingerprint/PIN (needs a secure lock screen). */
    val galleryLockAvailable: Boolean
        get() = (context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure) == true

    private fun ensureStorageKey() {
        if (keyStore.containsAlias(STORAGE)) return
        val requireAuth = galleryLockAvailable
        withStrongBoxFallback { strongBox ->
            val builder = KeyGenParameterSpec.Builder(STORAGE, KeyProperties.PURPOSE_DECRYPT)
                .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setIsStrongBoxBacked(strongBox)
            if (requireAuth) {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                }
            }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                .apply { initialize(builder.build()) }.generateKeyPair()
        }
    }

    // Keystore RSA-OAEP fixes MGF1 to SHA-1 on older Android versions; use that for the local copy.
    private val deviceOaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    /** Wrap a photo key for this phone. Works while locked: only the public key is used. */
    fun wrapForDevice(dek: ByteArray): String {
        ensureStorageKey()
        val pub: PublicKey = keyStore.getCertificate(STORAGE).publicKey
        // Re-encode so the software provider (not Keystore) does the public-key operation.
        val softPub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pub.encoded))
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.ENCRYPT_MODE, softPub, deviceOaep) }
        return Base64.encodeToString(c.doFinal(dek), Base64.NO_WRAP)
    }

    /** Unwrap a photo key. Throws UserNotAuthenticatedException when the gallery is locked. */
    fun unwrapForDevice(wrappedB64: String): ByteArray {
        ensureStorageKey()
        val key = keyStore.getKey(STORAGE, null) as PrivateKey
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE, key, deviceOaep) }
        return c.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
    }

    // ---------------------------------------------------------------- tokens
    private fun tokenKey(): SecretKey {
        (keyStore.getKey(TOKENS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(TOKENS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    fun encryptToken(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, tokenKey()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decryptToken(enc: String): String? = runCatching {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, tokenKey(), GCMParameterSpec(128, raw, 0, 12)) }
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNING = "spd-signing-v2"
        private const val STORAGE = "spd-storage-v1"
        private const val TOKENS = "session-tokens-v1"
        const val AUTH_VALIDITY_SECONDS = 300

        private val orgOaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

        /** RSA-OAEP-SHA256 (MGF1-SHA256), matching KMS RSAES_OAEP_SHA_256. */
        fun wrapForPublicKey(dek: ByteArray, spkiB64: String): String {
            val pub = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(java.util.Base64.getDecoder().decode(spkiB64)))
            val c = Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.ENCRYPT_MODE, pub, orgOaep) }
            return java.util.Base64.getEncoder().encodeToString(c.doFinal(dek))
        }
    }
}
