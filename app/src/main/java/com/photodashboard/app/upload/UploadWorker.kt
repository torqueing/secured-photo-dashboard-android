package com.photodashboard.app.upload

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.photodashboard.app.PhotoDashboardApp
import com.photodashboard.app.data.PhotoEntity
import com.photodashboard.app.data.RegisterPhotoRequest
import com.photodashboard.app.data.RegisterSealedRequest
import com.photodashboard.app.data.SealedUploadUrlsRequest
import com.photodashboard.app.data.UploadUrlRequest
import com.photodashboard.app.data.apiMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** One job per photo: get a presigned URL, PUT the JPEG, register it (AND-13). */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container = (context.applicationContext as PhotoDashboardApp).container

    override suspend fun doWork(): Result {
        val localId = inputData.getString(KEY_LOCAL_ID) ?: return Result.failure()
        val dao = container.db.photos()
        var photo = dao.get(localId) ?: return Result.failure()
        if (photo.state == UploadState.UPLOADED || photo.state == UploadState.REMOTE) return Result.success()
        if (container.session.current() == null) return Result.failure() // signed out (AND-3)

        val attempts = runAttemptCount + 1
        photo = photo.copy(state = UploadStateMachine.next(photo.state, UploadStateMachine.Event.Started), attempts = attempts)
        dao.update(photo)
        return try {
            photo = upload(photo)
            dao.update(photo.copy(state = UploadState.UPLOADED, lastError = null, stage = null, progress = null))
            Result.success()
        } catch (e: Exception) {
            val code = (e as? HttpException)?.code()
            if (UploadStateMachine.isAuthFailure(code)) {
                // Re-queued by resumePending() after the user signs in again.
                dao.update(
                    photo.copy(
                        state = UploadStateMachine.next(photo.state, UploadStateMachine.Event.AuthLost),
                        lastError = "Waiting for sign-in", stage = null, progress = null,
                    ),
                )
                return Result.failure()
            }
            val retryable = e is IOException || UploadStateMachine.isRetryable(code)
            val next = UploadStateMachine.next(photo.state, UploadStateMachine.Event.Failed(retryable, attempts))
            Log.w(TAG, "upload $localId attempt $attempts failed: ${e.apiMessage()}")
            // A final failure keeps the stage it failed in, for the capture status bar.
            val failedIn = if (next == UploadState.FAILED) dao.get(localId)?.stage else null
            dao.update(photo.copy(state = next, lastError = e.apiMessage(), stage = failedIn, progress = null))
            if (next == UploadState.PENDING) Result.retry() else Result.failure()
        }
    }

    /** S4: upload the two sealed .spd files with signed checksums, then register the seal. */
    private suspend fun uploadSealed(start: PhotoEntity): PhotoEntity {
        val api = container.api
        val dao = container.db.photos()
        val photoFile = File(requireNotNull(start.filePath) { "sealed photo missing" })
        val thumbFile = File(requireNotNull(start.thumbPath) { "sealed thumbnail missing" })
        dao.setStage(start.localId, UploadStage.CONNECTING, null)
        container.repository.ensureDeviceKeyRegistered()
        val photoSum = sha256B64(photoFile)
        val thumbSum = sha256B64(thumbFile)
        val urls = api.sealedUploadUrls(SealedUploadUrlsRequest(start.localId, photoSum, thumbSum))
        var photo = start.copy(serverPhotoId = urls.photoId, s3Key = urls.photo.s3Key, stage = UploadStage.UPLOADING, progress = 0)
        dao.update(photo)
        if (urls.status == "uploaded") return photo

        fun put(target: String, file: File, sum: String, onProgress: ((Int) -> Unit)?) {
            val raw = file.asRequestBody(OCTET)
            val body = if (onProgress != null) ProgressRequestBody(raw, onProgress) else raw
            val req = Request.Builder().url(target).put(body)
                .header("Content-Type", "application/octet-stream")
                .header("x-amz-checksum-sha256", sum)
                .build()
            container.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("S3 PUT failed: HTTP ${resp.code}")
            }
        }
        put(urls.thumbnail.uploadUrl, thumbFile, thumbSum, null)
        put(urls.photo.uploadUrl, photoFile, photoSum) { percent ->
            kotlinx.coroutines.runBlocking { dao.setStage(start.localId, UploadStage.UPLOADING, percent) }
        }
        api.registerSealed(
            RegisterSealedRequest(
                photoId = start.localId,
                sha256 = requireNotNull(photo.sha256),
                signedPayload = requireNotNull(photo.signedPayload),
                signature = requireNotNull(photo.signature),
                deviceKeyId = requireNotNull(photo.deviceKeyId),
                keyArn = requireNotNull(photo.keyArn),
                wrappedKey = requireNotNull(photo.orgWrappedKey),
                capturedAt = requireNotNull(photo.takenAtIso),
                latitude = photo.latitude,
                longitude = photo.longitude,
                altitudeM = photo.altitudeM,
                accuracyM = photo.accuracyM,
                bearingDeg = photo.bearingDeg,
                speedMps = photo.speedMps,
                locationSource = photo.locationProvider,
                deviceMake = android.os.Build.MANUFACTURER,
                deviceModel = android.os.Build.MODEL,
                width = photo.width,
                height = photo.height,
            ),
        )
        return photo
    }

    private fun sha256B64(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return java.util.Base64.getEncoder().encodeToString(md.digest())
    }

    private suspend fun upload(start: PhotoEntity): PhotoEntity {
        if (start.sealed) return uploadSealed(start)
        val api = container.api
        val dao = container.db.photos()
        val file = File(requireNotNull(start.filePath) { "photo has no local file" })
        dao.setStage(start.localId, UploadStage.CONNECTING, null)
        // Reuse the photo_id from an earlier attempt so retries never create duplicates (AND-15).
        val url = api.uploadUrl(UploadUrlRequest(start.serverPhotoId))
        var photo = start.copy(
            serverPhotoId = url.photoId, s3Key = url.s3Key, publicUrl = url.publicUrl,
            stage = UploadStage.UPLOADING, progress = 0,
        )
        dao.update(photo)
        if (url.status == "uploaded") return photo

        val body = ProgressRequestBody(file.asRequestBody(JPEG)) { percent ->
            // Called on OkHttp's writer thread; Room's blocking write is fine there.
            kotlinx.coroutines.runBlocking { dao.setStage(start.localId, UploadStage.UPLOADING, percent) }
        }
        val put = Request.Builder()
            .url(url.uploadUrl)
            .put(body)
            .build()
        container.http.newCall(put).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("S3 PUT failed: HTTP ${resp.code}")
        }

        val offset = ZoneOffset.ofTotalSeconds(photo.utcOffsetMinutes * 60)
        api.registerPhoto(
            RegisterPhotoRequest(
                photoId = url.photoId,
                s3Key = url.s3Key,
                capturedAt = Instant.ofEpochMilli(photo.capturedAt).atOffset(offset).toString(),
                latitude = photo.latitude,
                longitude = photo.longitude,
                altitudeM = photo.altitudeM,
                accuracyM = photo.accuracyM,
                bearingDeg = photo.bearingDeg,
                speedMps = photo.speedMps,
                locationSource = photo.locationProvider,
                deviceMake = android.os.Build.MANUFACTURER,
                deviceModel = android.os.Build.MODEL,
                width = photo.width,
                height = photo.height,
                sizeBytes = file.length(),
            ),
        )
        photo = photo.copy(sizeBytes = file.length())
        return photo
    }

    companion object {
        private const val TAG = "UploadWorker"
        const val KEY_LOCAL_ID = "local_id"
        const val TAG_UPLOAD = "photo-upload"
        private val JPEG = "image/jpeg".toMediaType()
        private val OCTET = "application/octet-stream".toMediaType()

        fun uniqueName(localId: String) = "upload-$localId"

        fun enqueue(context: Context, localId: String, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_LOCAL_ID to localId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG_UPLOAD)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(localId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG_UPLOAD)
        }
    }
}
