package com.bordware.nighttorch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * Tests the minutes-since-midnight encoding used to persist times.
 *
 * Storing a formatted string instead would be locale-dependent and need parsing on every
 * read; these tests pin the integer contract that avoids that.
 */
class AppSettingsTest {

    @Test
    fun `defaults match the documented values`() {
        val default = AppSettings.DEFAULT
        assertEquals(LocalTime.of(21, 0), default.nightStart)
        assertEquals(LocalTime.of(6, 0), default.nightEnd)
        assertEquals(1, default.nightBrightnessPercent)
        assertEquals(100, default.dayBrightnessPercent)
        assertEquals(true, default.autoDimmingEnabled)
        assertEquals(false, default.onboardingComplete)
    }

    @Test
    fun `minutes encoding round trips for every minute of the day`() {
        for (minuteOfDay in 0 until 24 * 60) {
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            assertEquals(minuteOfDay, time.toMinutes())
            assertEquals(time, minuteOfDay.toLocalTimeOrNull())
        }
    }

    @Test
    fun `boundary times encode as expected`() {
        assertEquals(0, LocalTime.of(0, 0).toMinutes())
        assertEquals(1439, LocalTime.of(23, 59).toMinutes())
        assertEquals(21 * 60, LocalTime.of(21, 0).toMinutes())
        assertEquals(6 * 60, LocalTime.of(6, 0).toMinutes())
    }

    @Test
    fun `seconds and nanos are discarded rather than rounding the minute`() {
        assertEquals(LocalTime.of(21, 0).toMinutes(), LocalTime.of(21, 0, 59, 999).toMinutes())
    }

    @Test
    fun `out of range stored values decode to null instead of throwing`() {
        // A corrupt or hand-edited preferences file must not crash the service.
        assertNull((-1).toLocalTimeOrNull())
        assertNull((24 * 60).toLocalTimeOrNull())
        assertNull(Int.MAX_VALUE.toLocalTimeOrNull())
        assertNull(Int.MIN_VALUE.toLocalTimeOrNull())
    }

    @Test
    fun `the last valid minute decodes`() {
        assertEquals(LocalTime.of(23, 59), (24 * 60 - 1).toLocalTimeOrNull())
    }
}
