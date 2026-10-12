package com.photodashboard.app

import com.photodashboard.app.upload.UploadState.FAILED
import com.photodashboard.app.upload.UploadState.PENDING
import com.photodashboard.app.upload.UploadState.REMOTE
import com.photodashboard.app.upload.UploadState.UPLOADED
import com.photodashboard.app.upload.UploadState.UPLOADING
import com.photodashboard.app.upload.UploadStateMachine
import com.photodashboard.app.upload.UploadStateMachine.Event
import com.photodashboard.app.upload.UploadStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadStateMachineTest {
    private val sm = UploadStateMachine

    @Test
    fun happyPath() {
        var s = sm.next(PENDING, Event.Enqueued)
        assertEquals(PENDING, s)
        s = sm.next(s, Event.Started)
        assertEquals(UPLOADING, s)
        s = sm.next(s, Event.Succeeded)
        assertEquals(UPLOADED, s)
    }

    @Test
    fun retryableFailureGoesBackToPendingUntilMaxAttempts() {
        assertEquals(PENDING, sm.next(UPLOADING, Event.Failed(retryable = true, attempts = 1)))
        assertEquals(PENDING, sm.next(UPLOADING, Event.Failed(retryable = true, attempts = 9)))
        assertEquals(FAILED, sm.next(UPLOADING, Event.Failed(retryable = true, attempts = 10)))
        assertEquals(FAILED, sm.next(UPLOADING, Event.Failed(retryable = false, attempts = 1)))
    }

    @Test
    fun manualRetryOnlyFromFailed() {
        assertEquals(PENDING, sm.next(FAILED, Event.RetryRequested))
        assertEquals(UPLOADED, sm.next(UPLOADED, Event.RetryRequested))
        assertEquals(UPLOADING, sm.next(UPLOADING, Event.RetryRequested))
    }

    @Test
    fun uploadedAndRemoteAreTerminal() {
        for (terminal in listOf(UPLOADED, REMOTE)) {
            assertEquals(terminal, sm.next(terminal, Event.Started))
            assertEquals(terminal, sm.next(terminal, Event.Enqueued))
            assertEquals(terminal, sm.next(terminal, Event.Failed(true, 1)))
        }
    }

    @Test
    fun resumeReusesPhotoId() {
        assertEquals(UploadStep.REQUEST_URL, sm.resumeFrom(null, objectUploaded = false, registered = false))
        assertEquals(UploadStep.PUT_OBJECT, sm.resumeFrom("id", objectUploaded = false, registered = false))
        assertEquals(UploadStep.REGISTER, sm.resumeFrom("id", objectUploaded = true, registered = false))
        assertEquals(UploadStep.DONE, sm.resumeFrom("id", objectUploaded = true, registered = true))
    }

    @Test
    fun authLossWaitsForSignInInsteadOfFailing() {
        assertEquals(PENDING, sm.next(UPLOADING, Event.AuthLost))
        assertEquals(PENDING, sm.next(FAILED, Event.AuthLost))
        assertEquals(UPLOADED, sm.next(UPLOADED, Event.AuthLost))
        assertTrue(sm.isAuthFailure(401))
        assertTrue(sm.isAuthFailure(403))
        assertFalse(sm.isAuthFailure(500))
    }

    @Test
    fun retryableCodes() {
        assertTrue(sm.isRetryable(null))
        assertTrue(sm.isRetryable(503))
        assertTrue(sm.isRetryable(409))
        assertFalse(sm.isRetryable(400))
        assertFalse(sm.isRetryable(401))
        assertFalse(sm.isRetryable(403))
    }
}
