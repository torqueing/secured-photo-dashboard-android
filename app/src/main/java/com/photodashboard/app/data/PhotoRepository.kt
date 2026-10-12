package com.photodashboard.app.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.photodashboard.app.exif.ExifWriter
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
) {
    private val dao get() = db.photos()

    fun photosDir(): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newCaptureFile(): File = File(photosDir(), "${UUID.randomUUID()}.jpg")

    /** After CameraX saved [file]: write EXIF, record it, copy to MediaStore, queue upload (AND-7..9). */
    suspend fun onCaptured(file: File, capturedAt: Long, fix: GeoFix?): PhotoEntity = withContext(Dispatchers.IO) {
        val user = requireNotNull(session.current()) { "not signed in" }
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(capturedAt))
        ExifWriter.write(file, capturedAt, offset, fix)
        val dims = ExifWriter.dimensions(file)
        val photo = PhotoEntity(
            localId = file.nameWithoutExtension,
            userId = user.userId,
            filePath = file.absolutePath,
            mediaStoreUri = runCatching { copyToMediaStore(file) }.onFailure { Log.w(TAG, "MediaStore copy failed", it) }.getOrNull(),
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
            width = dims.width,
            height = dims.height,
            sizeBytes = file.length(),
            state = UploadStateMachine.next(UploadState.PENDING, UploadStateMachine.Event.Enqueued),
        )
        // EXIF is on disk before the row exists, so the uploaded object always carries location (AND-8).
        dao.upsert(photo)
        UploadWorker.enqueue(context, photo.localId, session.wifiOnly.first())
        photo
    }

    private fun copyToMediaStore(file: File): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SecuredPhotoDashboard")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri.toString()
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
        photo.mediaStoreUri?.let { runCatching { context.contentResolver.delete(android.net.Uri.parse(it), null, null) } }
        dao.delete(localId)
    }

    companion object {
        private const val TAG = "PhotoRepository"
    }
}
