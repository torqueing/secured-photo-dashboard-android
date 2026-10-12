package com.photodashboard.app.upload

/** The five steps shown after a capture. */
enum class CaptureStep(val label: String) {
    TAKEN("Photo taken"),
    SAVED("Saved to device"),
    CONNECTING("Connecting"),
    UPLOADING("Uploading"),
    FINISHED("Finished upload"),
}

/** What the capture status bar shows: the step reached, plus detail for the current step. */
data class CaptureStatus(
    val step: CaptureStep,
    val uploadPercent: Int? = null,
    val waitingForNetwork: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null,
) {
    val finished get() = step == CaptureStep.FINISHED
}

object CaptureProgress {
    /**
     * Derive the status from what is known locally: whether the file has been saved
     * (Room row exists) and the row's upload state and stage.
     */
    fun status(saved: Boolean, state: UploadState?, stage: UploadStage?, progress: Int?, lastError: String?): CaptureStatus {
        if (!saved || state == null) return CaptureStatus(CaptureStep.TAKEN)
        return when (state) {
            UploadState.UPLOADED, UploadState.REMOTE -> CaptureStatus(CaptureStep.FINISHED, uploadPercent = 100)
            UploadState.FAILED -> CaptureStatus(
                if (stage == UploadStage.UPLOADING) CaptureStep.UPLOADING else CaptureStep.CONNECTING,
                failed = true, error = lastError,
            )
            UploadState.UPLOADING -> when (stage) {
                UploadStage.UPLOADING -> CaptureStatus(CaptureStep.UPLOADING, uploadPercent = progress)
                else -> CaptureStatus(CaptureStep.CONNECTING)
            }
            // Saved and queued; the job has not started yet (no network, Wi-Fi-only, or a retry backoff).
            UploadState.PENDING -> if (lastError == null && stage == null) {
                CaptureStatus(CaptureStep.SAVED, waitingForNetwork = true)
            } else {
                CaptureStatus(CaptureStep.CONNECTING, waitingForNetwork = true, error = lastError)
            }
        }
    }
}
