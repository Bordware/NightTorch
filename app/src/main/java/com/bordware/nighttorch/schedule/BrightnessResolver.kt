package com.bordware.nighttorch.schedule

import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.torch.TorchCapability
import java.time.LocalTime

/**
 * What the schedule decided, and why.
 *
 * Carries [isNight] and [percent] alongside [level] so the UI can render the live preview
 * line the design calls for — "Now 22:14 → Night → level 1 of 21" — without re-deriving
 * the decision and risking a different answer than the one actually applied.
 *
 * @param isNight whether [LocalTime] fell inside the night window.
 * @param percent the user-facing brightness percentage the schedule selected.
 * @param level the device torch level [percent] resolves to, always in `1..maxLevel`.
 */
data class BrightnessDecision(
    val isNight: Boolean,
    val percent: Int,
    val level: Int,
)

/**
 * Maps a time, the user's settings, and the hardware capability to a torch level.
 *
 * Pure Kotlin with no Android imports, so it is unit testable on the JVM.
 */
object BrightnessResolver {

    /**
     * Resolves the brightness to use for a torch activation at [now].
     *
     * Returns **null when [AppSettings.autoDimmingEnabled] is false**, meaning "the schedule
     * has no opinion — use whatever brightness was last set manually".
     *
     * This is a deliberate refinement of the signature in docs/architecture.md, which returns a plain
     * `Int`. A total function would have to invent a brightness when auto-dimming is
     * switched off, and every candidate is wrong: returning the day percentage makes a
     * slider labelled "day brightness" secretly govern a disabled feature, and returning
     * full power overrides whatever the user just chose on the manual slider. Null lets the
     * caller fall through to `TorchController.toggle()`, which uses the manual level, so
     * turning auto-dimming off does exactly what the switch says.
     *
     * @param now the time to evaluate, typically `LocalTime.now()`.
     * @param settings the current user settings.
     * @param capability the hardware capability, which determines the level range.
     * @return the decision, or null if auto-dimming is disabled.
     */
    fun resolve(
        now: LocalTime,
        settings: AppSettings,
        capability: TorchCapability,
    ): BrightnessDecision? {
        if (!settings.autoDimmingEnabled) return null

        val isNight = NightScheduleEvaluator.isNight(
            now = now,
            start = settings.nightStart,
            end = settings.nightEnd,
        )
        val percent = if (isNight) {
            settings.nightBrightnessPercent
        } else {
            settings.dayBrightnessPercent
        }

        return BrightnessDecision(
            isNight = isNight,
            percent = percent,
            // levelForPercent clamps into 1..maxLevel for Variable, and returns 1 for
            // BinaryOnly and Unsupported. For a binary device 1 *is* the maximum — it is the
            // only valid level — and the value is ignored anyway, since TorchController
            // falls back to setTorchMode there.
            level = capability.levelForPercent(percent),
        )
    }

    /**
     * Convenience for callers that only need the level, such as the accessibility service.
     *
     * @return the device level, or null when auto-dimming is disabled.
     */
    fun resolveLevel(
        now: LocalTime,
        settings: AppSettings,
        capability: TorchCapability,
    ): Int? = resolve(now, settings, capability)?.level
}
