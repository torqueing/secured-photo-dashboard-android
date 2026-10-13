package com.photodashboard.app.data

import android.content.Context
import android.util.Base64
import com.photodashboard.app.crypto.DeviceKeys
import com.photodashboard.app.crypto.OrgKey
import com.photodashboard.app.crypto.PhotoSealer
import com.photodashboard.app.crypto.SpdFormat
import android.util.Log
import com.photodashboard.app.exif.GeoFix
import com.photodashboard.app.upload.UploadState
import com.photodashboard.app.upload.UploadStateMachine
import com.photodashboard.app.upload.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class PhotoRepository(
    private val context: Context,
    private val db: PhotoDb,
    private val api: PhotoApi,
    private val session: SessionStore,
    private val deviceKeys: DeviceKeys,
) {
    private val dao get() = db.photos()

    private val sealer by lazy { PhotoSealer(deviceKeys, File(context.filesDir, "sealed")) }

    /** Fetch and cache the organisation public key photos are wrapped for (needs the network). */
    suspend fun refreshOrgKey(): OrgKey? = withContext(Dispatchers.IO) {
        runCatching { session.saveOrgKey(api.orgKey()) }.onFailure { Log.w(TAG, "org key refresh failed", it) }
        session.orgKey()
    }

    /** Register this phone's signing key with the server once (spec 8.3 key attestation). */
    suspend fun ensureDeviceKeyRegistered(): String = withContext(Dispatchers.IO) {
        val id = deviceKeys.deviceKeyId()
        if (session.registeredDeviceKey() != id) {
            val res = api.registerDeviceKey(
                DeviceKeyRequest(Base64.encodeToString(deviceKeys.signingPublicKeySpki(), Base64.NO_WRAP), deviceKeys.attestationChainB64()),
            )
            check(res.deviceKeyId == id) { "server derived a different device key id" }
            session.setRegisteredDeviceKey(id)
        }
        id
    }

    /**
     * Spec 4.3: seal the in-memory JPEG (EXIF, thumbnail, signature, encryption) and queue the
     * upload. Only the encrypted .spd files are written; nothing goes to the shared gallery.
     */
    suspend fun onCaptured(jpeg: ByteArray, rotationDegrees: Int, capturedAt: Long, fix: GeoFix?): PhotoEntity =
        withContext(Dispatchers.Default) {
            val user = requireNotNull(session.current()) { "not signed in" }
            val org = session.orgKey() ?: refreshOrgKey()
                ?: error("The encryption key hasn't been downloaded yet; connect to the internet once and try again")
            val photoId = UUID.randomUUID().toString()
            val sealed = sealer.seal(photoId, jpeg, rotationDegrees, capturedAt, fix, org)
            val offset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(capturedAt))
            val photo = PhotoEntity(
                localId = photoId,
                userId = user.userId,
                filePath = sealed.photoFile.absolutePath,
                mediaStoreUri = null,
                capturedAt = capturedAt,
                utcOffsetMinutes = offset.totalSeconds / 60,
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                altitudeM = fix?.altitudeM,
                accuracyM = fix?.accuracyM,
                bearingDeg = fix?.bearingDeg,
                speedMps = fix?.speedMps,
                locationProvider = fix?.provider,
                noLocation = fix == null,
                width = sealed.width,
                height = sealed.height,
                sizeBytes = sealed.sizeBytes,
                state = UploadStateMachine.next(UploadState.PENDING, UploadStateMachine.Event.Enqueued),
                serverPhotoId = photoId,
                sealed = true,
                thumbPath = sealed.thumbFile.absolutePath,
                takenAtIso = sealed.takenAt,
                sha256 = sealed.sha256,
                signedPayload = sealed.signedPayload,
                signature = sealed.signature,
                deviceKeyId = sealed.deviceKeyId,
                keyArn = sealed.keyArn,
                orgWrappedKey = sealed.orgWrappedKey,
                deviceWrappedKey = sealed.deviceWrappedKey,
            )
            dao.upsert(photo)
            UploadWorker.enqueue(context, photo.localId, session.wifiOnly.first())
            photo
        }

    /** Decrypt a sealed photo or its thumbnail in memory. Throws UserNotAuthenticatedException when locked. */
    suspend fun decrypt(photo: PhotoEntity, thumbnail: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val path = requireNotNull(if (thumbnail) photo.thumbPath else photo.filePath) { "no local copy" }
        val dek = deviceKeys.unwrapForDevice(requireNotNull(photo.deviceWrappedKey))
        try {
            SpdFormat.open(File(path).readBytes(), dek).second
        } finally {
            java.util.Arrays.fill(dek, 0)
        }
    }

    /** Merge the server's list into Room so photos uploaded elsewhere show too (AND-10). */
    suspend fun refreshFromServer() = withContext(Dispatchers.IO) {
        val user = session.current() ?: return@withContext
        var cursor: String? = null
        do {
            val page = api.photos(cursor = cursor, limit = 200)
            for (p in page.items) {
                val local = dao.byServerId(p.id)
                if (local != null) {
                    if (local.state != UploadState.UPLOADED) dao.update(local.copy(state = UploadState.UPLOADED, publicUrl = p.publicUrl))
                    continue
                }
                val captured = p.capturedAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                dao.insertIfAbsent(
                    PhotoEntity(
                        localId = "remote-${p.id}", userId = user.userId, filePath = null, mediaStoreUri = null,
                        capturedAt = captured?.toInstant()?.toEpochMilli() ?: 0L,
                        utcOffsetMinutes = (captured?.offset?.totalSeconds ?: 0) / 60,
                        latitude = p.latitude, longitude = p.longitude, altitudeM = p.altitudeM,
                        accuracyM = p.accuracyM?.toFloat(), bearingDeg = null, speedMps = null, locationProvider = null,
                        noLocation = p.latitude == null, width = null, height = null, sizeBytes = null,
                        state = UploadState.REMOTE, serverPhotoId = p.id, s3Key = p.s3Key, publicUrl = p.publicUrl,
                        sealed = p.encVersion != null,
                    ),
                )
            }
            cursor = page.nextCursor
        } while (cursor != null)
    }

    suspend fun retry(localId: String) {
        val photo = dao.get(localId) ?: return
        val next = UploadStateMachine.next(photo.state, UploadStateMachine.Event.RetryRequested)
        dao.update(photo.copy(state = next, attempts = 0, lastError = null))
        if (next == UploadState.PENDING) UploadWorker.enqueue(context, localId, session.wifiOnly.first())
    }

    /** Re-queue anything left unfinished, e.g. after signing back in. */
    suspend fun resumePending() {
        val user = session.current() ?: return
        val wifiOnly = session.wifiOnly.first()
        dao.unfinished(user.userId).filter { it.state != UploadState.FAILED }.forEach {
            UploadWorker.enqueue(context, it.localId, wifiOnly)
        }
    }

    /** AND-12: delete locally, and on the server when it was uploaded. */
    suspend fun delete(localId: String) = withContext(Dispatchers.IO) {
        val photo = dao.get(localId) ?: return@withContext
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(UploadWorker.uniqueName(localId))
        photo.serverPhotoId?.let { id ->
            if (photo.state == UploadState.UPLOADED || photo.state == UploadState.REMOTE) {
                try {
                    api.deletePhoto(id)
                } catch (e: HttpException) {
                    if (e.code() != 404) throw e
                }
            }
        }
        photo.filePath?.let { File(it).delete() }
        photo.thumbPath?.let { File(it).delete() }
        photo.mediaStoreUri?.let { runCatching { context.contentResolver.delete(android.net.Uri.parse(it), null, null) } }
        dao.delete(localId)
    }

    companion object {
        private const val TAG = "PhotoRepository"
    }
}
