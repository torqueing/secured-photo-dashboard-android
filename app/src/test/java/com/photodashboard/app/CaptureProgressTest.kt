package com.photodashboard.app

import com.photodashboard.app.upload.CaptureProgress
import com.photodashboard.app.upload.CaptureStep
import com.photodashboard.app.upload.UploadStage
import com.photodashboard.app.upload.UploadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureProgressTest {
    private fun status(saved: Boolean, state: UploadState?, stage: UploadStage? = null, progress: Int? = null, error: String? = null) =
        CaptureProgress.status(saved, state, stage, progress, error)

    @Test
    fun walksThroughTheFiveSteps() {
        assertEquals(CaptureStep.TAKEN, status(false, null).step)
        assertEquals(CaptureStep.SAVED, status(true, UploadState.PENDING).step)
        assertEquals(CaptureStep.CONNECTING, status(true, UploadState.UPLOADING, UploadStage.CONNECTING).step)
        val up = status(true, UploadState.UPLOADING, UploadStage.UPLOADING, 40)
        assertEquals(CaptureStep.UPLOADING, up.step)
        assertEquals(40, up.uploadPercent)
        val done = status(true, UploadState.UPLOADED)
        assertEquals(CaptureStep.FINISHED, done.step)
        assertTrue(done.finished)
    }

    @Test
    fun offlineWaitsAtSavedThenConnectingAfterARetry() {
        assertTrue(status(true, UploadState.PENDING).waitingForNetwork)
        val retrying = status(true, UploadState.PENDING, error = "timeout")
        assertEquals(CaptureStep.CONNECTING, retrying.step)
        assertTrue(retrying.waitingForNetwork)
    }

    @Test
    fun failureKeepsTheStepItFailedOn() {
        val f = status(true, UploadState.FAILED, UploadStage.UPLOADING, error = "HTTP 403")
        assertEquals(CaptureStep.UPLOADING, f.step)
        assertTrue(f.failed)
        assertEquals(CaptureStep.CONNECTING, status(true, UploadState.FAILED, error = "x").step)
    }
}
