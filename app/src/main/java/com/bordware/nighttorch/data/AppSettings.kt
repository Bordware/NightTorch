package com.bordware.nighttorch.data

import java.time.LocalTime

/**
 * Immutable snapshot of every user preference.
 *
 * Pure Kotlin with no Android imports, so the schedule logic that consumes it stays unit
 * testable on the JVM. `java.time.LocalTime` is available natively from API 26, which is
 * the reason minSdk is 26 rather than 24 — see docs/architecture.md.
 *
 * @param nightStart when the night window opens. Only hours and minutes are meaningful;
 *   seconds and nanoseconds are dropped on persistence.
 * @param nightEnd when the night window closes. May be earlier in the day than
 *   [nightStart], which means the window wraps past midnight — the usual case.
 * @param nightBrightnessPercent brightness to use inside the night window, `0..100`.
 * @param dayBrightnessPercent brightness to use outside the night window, `0..100`.
 * @param autoDimmingEnabled master switch. When false, the schedule expresses no opinion
 *   and the torch uses whatever brightness was last set manually.
 * @param onboardingComplete whether first-run onboarding has been finished or skipped.
 */
data class AppSettings(
    val nightStart: LocalTime,
    val nightEnd: LocalTime,
    val nightBrightnessPercent: Int,
    val dayBrightnessPercent: Int,
    val autoDimmingEnabled: Boolean,
    val onboardingComplete: Boolean,
) {
    companion object {
        /**
         * Values used before the user has changed anything, and whenever a stored value is
         * missing or unreadable.
         *
         * Night brightness defaults to 1% deliberately: on a device with a wide range it
         * resolves to level 1, the dimmest usable setting, which is the entire point of the
         * app — a torch that does not dazzle you at 3am.
         */
        val DEFAULT = AppSettings(
            nightStart = LocalTime.of(21, 0),
            nightEnd = LocalTime.of(6, 0),
            nightBrightnessPercent = 1,
            dayBrightnessPercent = 100,
            autoDimmingEnabled = true,
            onboardingComplete = false,
        )

        /** Valid range for any stored brightness percentage. */
        val PERCENT_RANGE = 0..100
    }
}

/**
 * Minutes since midnight, `0..1439`.
 *
 * Times are persisted as an integer rather than a formatted string so the stored data is
 * independent of locale, time-zone rules and formatter patterns. A string like "21:00"
 * would need parsing on every read and would be ambiguous under a 12-hour locale.
 *
 * Seconds and nanoseconds are discarded — the UI only offers hour and minute.
 */
internal fun LocalTime.toMinutes(): Int = hour * MINUTES_PER_HOUR + minute

/**
 * Rebuilds a [LocalTime] from minutes since midnight, or null when the stored value is out
 * of range.
 *
 * Out-of-range can only happen if the preferences file was hand-edited or corrupted, but it
 * returns null rather than throwing so the caller falls back to a default instead of
 * crashing the accessibility service.
 */
internal fun Int.toLocalTimeOrNull(): LocalTime? =
    if (this in 0 until MINUTES_PER_DAY) {
        LocalTime.of(this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR)
    } else {
        null
    }

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
