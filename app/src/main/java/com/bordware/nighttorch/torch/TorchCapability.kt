package com.bordware.nighttorch.torch

import kotlin.math.roundToInt

/**
 * What the selected camera's flash unit can actually do.
 *
 * Deliberately pure Kotlin with no Android imports so the percentage-to-level conversion
 * stays unit testable on the JVM.
 *
 * The three cases exist because "supports variable brightness" is not the same question as
 * "runs Android 13". A device can be on API 33+ and still report no usable strength range;
 * see [BinaryOnly].
 */
sealed interface TorchCapability {

    /**
     * No camera on this device reports a flash unit, so there is nothing to control.
     *
     * Reached when no camera ID reports `FLASH_INFO_AVAILABLE == true`. The manifest
     * declares `android.hardware.camera.flash` as required, so app stores should filter
     * these devices out, but sideloads and emulators still land here.
     */
    data object Unsupported : TorchCapability

    /**
     * The torch can only be switched on and off; brightness is not adjustable.
     *
     * Reached in three distinct situations, all of which must map here:
     *  - the device runs below API 33, where the strength APIs do not exist at all;
     *  - the device runs API 33+ but reports `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL == null`;
     *  - the device runs API 33+ and reports a maximum level of 1, meaning the only valid
     *    strength is the single level it already uses.
     *
     * The last two are common on real hardware. Do not infer variable brightness from the
     * API level.
     */
    data object BinaryOnly : TorchCapability

    /**
     * The torch supports [maxLevel] discrete brightness levels, numbered `1..maxLevel`.
     *
     * Only reachable on API 33+ with a reported maximum above 1.
     *
     * @param maxLevel the device's `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`. Varies enormously
     *   between devices — a Pixel 10 Pro reports 21, other hardware reports 5 or 100+ —
     *   which is exactly why user preferences are stored as percentages rather than levels.
     * @param defaultLevel the device's `FLASH_INFO_STRENGTH_DEFAULT_LEVEL`. Note this is
     *   not necessarily mid-scale: the Pixel 10 Pro reports the same value as [maxLevel],
     *   so it means "the level used when you call `setTorchMode`", not "a comfortable
     *   default brightness".
     */
    data class Variable(val maxLevel: Int, val defaultLevel: Int) : TorchCapability

    /**
     * Converts a user-facing brightness percentage to a device torch level.
     *
     * User preferences are stored as percentages because [Variable.maxLevel] is wildly
     * device-dependent, so a stored raw level would mean different brightnesses on
     * different hardware — and would be out of range entirely when restored onto a device
     * with a smaller maximum.
     *
     * Level `0` is not "off"; it is invalid. The valid range is `1..maxLevel` inclusive, so
     * the result is always at least 1 even for 0%.
     *
     * @param percent desired brightness, clamped into `0..100` before conversion.
     * @return a level in `1..maxLevel` for [Variable]; always 1 for [BinaryOnly] and
     *   [Unsupported], where the caller ignores the level anyway.
     */
    fun levelForPercent(percent: Int): Int = when (this) {
        is Variable -> {
            val clampedPercent = percent.coerceIn(0, 100)
            val scaled = (clampedPercent / 100f * maxLevel).roundToInt()
            scaled.coerceIn(1, maxLevel)
        }

        BinaryOnly, Unsupported -> 1
    }

    /**
     * Converts a device torch level back to a percentage, for showing a stored level in the
     * UI. Inverse of [levelForPercent], subject to rounding at coarse [Variable.maxLevel]
     * values.
     */
    fun percentForLevel(level: Int): Int = when (this) {
        is Variable -> (level.coerceIn(1, maxLevel) / maxLevel.toFloat() * 100f).roundToInt()
        BinaryOnly, Unsupported -> 100
    }
}
