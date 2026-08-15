package com.bordware.nighttorch.schedule

import java.time.LocalTime

/**
 * Decides whether a given time falls inside the night window.
 *
 * Pure Kotlin with no Android imports, so it runs as a millisecond JVM unit test. This is a
 * separate class solely because of the midnight-wrap case: a naive
 * `now >= start && now <= end` is wrong for every sensible night window, since night windows
 * cross midnight by definition.
 */
object NightScheduleEvaluator {

    /**
     * Whether [now] falls inside the window from [start] until [end].
     *
     * The window is **half-open**: it includes [start] and excludes [end]. That makes
     * adjacent windows tile without overlapping, and means a one-minute window
     * `21:00..21:01` contains exactly 21:00.
     *
     * Three cases:
     * - `start == end` — a zero-length window. Returns false always; see below.
     * - `start < end` — an ordinary same-day window such as `01:00..05:00`. Night is
     *   `now >= start && now < end`.
     * - `start > end` — a window wrapping past midnight such as `21:00..06:00`. Night is
     *   `now >= start || now < end`, which is the union of the evening and morning halves.
     *
     * The degenerate `start == end` case is defined as **never night**, not always night.
     * Both readings are defensible for a zero-length window, but "never" is the safe one:
     * a user who accidentally sets both ends the same gets a torch that behaves normally
     * rather than one permanently stuck at 1% brightness, which would look like a bug and
     * be hard to diagnose from the UI.
     *
     * @param now the time to test, typically `LocalTime.now()`.
     * @param start when the window opens, inclusive.
     * @param end when the window closes, exclusive.
     */
    fun isNight(now: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> false
        start < end -> now >= start && now < end
        else -> now >= start || now < end
    }
}
