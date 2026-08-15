package com.bordware.nighttorch.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Covers the night-window cases described in docs/architecture.md.
 *
 * The midnight-wrap window is the whole reason [NightScheduleEvaluator] is a separate class,
 * so it gets the most attention here.
 */
class NightScheduleEvaluatorTest {

    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    private fun isNight(now: LocalTime, start: LocalTime, end: LocalTime) =
        NightScheduleEvaluator.isNight(now, start, end)

    // ---------------------------------------------------------------- wrapping window

    /** The default window, and the one every real user will have. */
    private val wrapStart = at(21, 0)
    private val wrapEnd = at(6, 0)

    @Test
    fun `wrapping window includes the start instant exactly`() {
        assertTrue(isNight(at(21, 0), wrapStart, wrapEnd))
    }

    @Test
    fun `wrapping window excludes the end instant exactly`() {
        // Half-open: 06:00 is morning, not night.
        assertFalse(isNight(at(6, 0), wrapStart, wrapEnd))
    }

    @Test
    fun `wrapping window one minute either side of the start`() {
        assertFalse(isNight(at(20, 59), wrapStart, wrapEnd))
        assertTrue(isNight(at(21, 1), wrapStart, wrapEnd))
    }

    @Test
    fun `wrapping window one minute either side of the end`() {
        assertTrue(isNight(at(5, 59), wrapStart, wrapEnd))
        assertFalse(isNight(at(6, 1), wrapStart, wrapEnd))
    }

    @Test
    fun `wrapping window spans midnight itself`() {
        // The three instants a naive start-to-end comparison gets wrong.
        assertTrue("23:59 must be night", isNight(at(23, 59), wrapStart, wrapEnd))
        assertTrue("00:00 must be night", isNight(at(0, 0), wrapStart, wrapEnd))
        assertTrue("00:01 must be night", isNight(at(0, 1), wrapStart, wrapEnd))
    }

    @Test
    fun `wrapping window excludes the middle of the day`() {
        assertFalse(isNight(at(12, 0), wrapStart, wrapEnd))
        assertFalse(isNight(at(9, 30), wrapStart, wrapEnd))
        assertFalse(isNight(at(18, 0), wrapStart, wrapEnd))
    }

    // ------------------------------------------------------- sub-minute boundary precision

    /**
     * The stored window is always whole minutes, but `LocalTime.now()` carries seconds and
     * nanoseconds, so the real comparison at runtime is sub-minute. These pin the exact
     * instant night becomes day: 05:59:59 is still night, 06:00:00 is day.
     */
    @Test
    fun `night runs to the last instant before the end time`() {
        assertTrue("05:59:59 must be night", isNight(LocalTime.of(5, 59, 59), wrapStart, wrapEnd))
        assertTrue(
            "the last nanosecond before 06:00 must be night",
            isNight(LocalTime.of(5, 59, 59, 999_999_999), wrapStart, wrapEnd),
        )
    }

    @Test
    fun `day begins exactly on the end time`() {
        assertFalse("06:00:00 must be day", isNight(LocalTime.of(6, 0, 0), wrapStart, wrapEnd))
        assertFalse(
            "one nanosecond after 06:00 must be day",
            isNight(LocalTime.of(6, 0, 0, 1), wrapStart, wrapEnd),
        )
    }

    @Test
    fun `night begins exactly on the start time, not a moment before`() {
        assertFalse(
            "the last nanosecond before 21:00 must be day",
            isNight(LocalTime.of(20, 59, 59, 999_999_999), wrapStart, wrapEnd),
        )
        assertTrue("21:00:00 must be night", isNight(LocalTime.of(21, 0, 0), wrapStart, wrapEnd))
        assertTrue(
            "one nanosecond after 21:00 must be night",
            isNight(LocalTime.of(21, 0, 0, 1), wrapStart, wrapEnd),
        )
    }

    @Test
    fun `sub-minute precision holds for a non-wrapping window too`() {
        assertTrue(isNight(LocalTime.of(4, 59, 59), plainStart, plainEnd))
        assertFalse(isNight(LocalTime.of(5, 0, 0), plainStart, plainEnd))
        assertFalse(isNight(LocalTime.of(0, 59, 59), plainStart, plainEnd))
        assertTrue(isNight(LocalTime.of(1, 0, 0), plainStart, plainEnd))
    }

    // ------------------------------------------------------------ non-wrapping window

    private val plainStart = at(1, 0)
    private val plainEnd = at(5, 0)

    @Test
    fun `non-wrapping window includes the start and excludes the end`() {
        assertTrue(isNight(at(1, 0), plainStart, plainEnd))
        assertFalse(isNight(at(5, 0), plainStart, plainEnd))
    }

    @Test
    fun `non-wrapping window one minute either side of each edge`() {
        assertFalse(isNight(at(0, 59), plainStart, plainEnd))
        assertTrue(isNight(at(1, 1), plainStart, plainEnd))
        assertTrue(isNight(at(4, 59), plainStart, plainEnd))
        assertFalse(isNight(at(5, 1), plainStart, plainEnd))
    }

    @Test
    fun `non-wrapping window does not wrap past midnight`() {
        // The mirror of the wrap test: these must NOT be night for a 01:00-05:00 window.
        assertFalse("23:59 must not be night", isNight(at(23, 59), plainStart, plainEnd))
        assertFalse("00:00 must not be night", isNight(at(0, 0), plainStart, plainEnd))
        assertFalse("00:01 must not be night", isNight(at(0, 1), plainStart, plainEnd))
    }

    // -------------------------------------------------------------- degenerate window

    @Test
    fun `equal start and end is never night`() {
        // Documented choice: "never", not "always". A user who sets both ends the same gets
        // an ordinary torch rather than one permanently stuck dim.
        val same = at(21, 0)
        assertFalse(isNight(at(21, 0), same, same))
        assertFalse(isNight(at(0, 0), same, same))
        assertFalse(isNight(at(12, 0), same, same))
        assertFalse(isNight(at(20, 59), same, same))
        assertFalse(isNight(at(21, 1), same, same))
    }

    @Test
    fun `equal start and end at midnight is also never night`() {
        val midnight = at(0, 0)
        assertFalse(isNight(at(0, 0), midnight, midnight))
        assertFalse(isNight(at(23, 59), midnight, midnight))
    }

    // ------------------------------------------------------------------- edge windows

    @Test
    fun `window starting at midnight does not wrap`() {
        // start 00:00 < end 06:00, so this is the ordinary case, not the wrapping one.
        assertTrue(isNight(at(0, 0), at(0, 0), at(6, 0)))
        assertTrue(isNight(at(5, 59), at(0, 0), at(6, 0)))
        assertFalse(isNight(at(6, 0), at(0, 0), at(6, 0)))
        assertFalse(isNight(at(23, 59), at(0, 0), at(6, 0)))
    }

    @Test
    fun `window ending at midnight covers the whole evening`() {
        // start 21:00 > end 00:00, so this wraps — but the wrapped portion is empty.
        assertTrue(isNight(at(21, 0), at(21, 0), at(0, 0)))
        assertTrue(isNight(at(23, 59), at(21, 0), at(0, 0)))
        assertFalse(isNight(at(0, 0), at(21, 0), at(0, 0)))
        assertFalse(isNight(at(20, 59), at(21, 0), at(0, 0)))
    }

    @Test
    fun `one minute window contains exactly its start`() {
        assertTrue(isNight(at(21, 0), at(21, 0), at(21, 1)))
        assertFalse(isNight(at(21, 1), at(21, 0), at(21, 1)))
        assertFalse(isNight(at(20, 59), at(21, 0), at(21, 1)))
    }

    @Test
    fun `every minute of the day is classified consistently for the default window`() {
        // Exhaustive sweep: exactly the 540 minutes from 21:00 to 06:00 must be night.
        var nightMinutes = 0
        for (minuteOfDay in 0 until 24 * 60) {
            val now = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            if (isNight(now, wrapStart, wrapEnd)) nightMinutes++
        }
        // 21:00-24:00 is 180 minutes, 00:00-06:00 is 360 minutes.
        org.junit.Assert.assertEquals(180 + 360, nightMinutes)
    }
}
