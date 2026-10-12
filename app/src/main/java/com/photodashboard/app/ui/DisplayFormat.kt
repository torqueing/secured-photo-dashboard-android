package com.photodashboard.app.ui

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates are shown as DD/MM/YYYY with 24-hour times, matching the web dashboard. */
object DisplayFormat {
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK)
    val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.UK)
}
