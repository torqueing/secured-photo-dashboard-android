package com.photodashboard.app

import com.photodashboard.app.ui.DisplayFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DisplayFormatTest {
    @Test
    fun ukDayMonthYearAnd24Hour() {
        val t = OffsetDateTime.of(2026, 10, 2, 17, 5, 9, 0, ZoneOffset.ofHours(1))
        assertEquals("02/10/2026", DisplayFormat.date.format(t))
        assertEquals("02/10/2026 17:05:09", DisplayFormat.dateTime.format(t))
    }
}
