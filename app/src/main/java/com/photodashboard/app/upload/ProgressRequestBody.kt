package com.photodashboard.app.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Wraps a body and reports whole-percent progress (each value once) while it is written. */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var lastReported = -1
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val percent = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
                // Report every 5 % to keep database writes cheap.
                if (percent >= lastReported + 5 || percent == 100 && lastReported != 100) {
                    lastReported = percent
                    onProgress(percent)
                }
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
