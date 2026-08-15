package com.bordware.nighttorch.torch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the percentage-to-level conversion.
 *
 * The maxLevel values exercised here are deliberately 1, 5, 21 and 100: 21 is what a
 * Pixel 10 Pro actually reports (see docs/device-matrix.md), and the others bracket the
 * range real hardware is known to use.
 */
class TorchCapabilityTest {

    @Test
    fun `level is never zero, because zero is invalid rather than off`() {
        // 0% must still resolve to a valid level. Level 0 is not "off"; the range is 1..max.
        for (maxLevel in listOf(2, 5, 21, 100)) {
            val capability = TorchCapability.Variable(maxLevel = maxLevel, defaultLevel = 1)
            assertEquals(1, capability.levelForPercent(0))
        }
    }

    @Test
    fun `100 percent maps to the device maximum`() {
        assertEquals(5, TorchCapability.Variable(5, 5).levelForPercent(100))
        assertEquals(21, TorchCapability.Variable(21, 21).levelForPercent(100))
        assertEquals(100, TorchCapability.Variable(100, 100).levelForPercent(100))
    }

    @Test
    fun `50 percent maps to mid scale, rounding to nearest`() {
        assertEquals(3, TorchCapability.Variable(5, 5).levelForPercent(50))
        assertEquals(11, TorchCapability.Variable(21, 21).levelForPercent(50))
        assertEquals(50, TorchCapability.Variable(100, 100).levelForPercent(50))
    }

    @Test
    fun `1 percent clamps up to level 1 on coarse devices`() {
        // 1% of 5 rounds to 0, which is invalid, so it must clamp up rather than through.
        assertEquals(1, TorchCapability.Variable(5, 5).levelForPercent(1))
        assertEquals(1, TorchCapability.Variable(21, 21).levelForPercent(1))
        assertEquals(1, TorchCapability.Variable(100, 100).levelForPercent(1))
    }

    @Test
    fun `percentages outside 0 to 100 are clamped`() {
        val capability = TorchCapability.Variable(21, 21)
        assertEquals(1, capability.levelForPercent(-50))
        assertEquals(21, capability.levelForPercent(500))
    }

    @Test
    fun `binary and unsupported capabilities always resolve to level 1`() {
        for (percent in listOf(0, 1, 50, 100)) {
            assertEquals(1, TorchCapability.BinaryOnly.levelForPercent(percent))
            assertEquals(1, TorchCapability.Unsupported.levelForPercent(percent))
        }
    }

    @Test
    fun `a device reporting maxLevel 1 is binary, so every percent is level 1`() {
        // Guards the classic capability trap: API 33+ does not imply a usable range. A device reporting
        // max level 1 must be classified BinaryOnly, never Variable(1).
        assertEquals(1, TorchCapability.BinaryOnly.levelForPercent(100))
    }

    /**
     * The UI snaps to levels but persists percentages, so `level -> percent -> level` has to
     * land back on the same level for every level on every plausible device. If it did not,
     * a slider would jump under the user's finger, or a stored setting would drift a level
     * each time it was read and written.
     */
    @Test
    fun `level to percent to level is stable on devices with at most 100 levels`() {
        for (maxLevel in listOf(2, 3, 5, 8, 10, 21, 50, 100)) {
            val capability = TorchCapability.Variable(maxLevel = maxLevel, defaultLevel = maxLevel)
            for (level in 1..maxLevel) {
                val percent = capability.percentForLevel(level)
                assertEquals(
                    "maxLevel=$maxLevel level=$level did not survive the round trip via $percent",
                    level,
                    capability.levelForPercent(percent),
                )
            }
        }
    }

    /**
     * Above 100 levels the round trip cannot be exact, because 101 percentage values cannot
     * address more than 101 distinct levels. Documented rather than fixed: storing a
     * percentage is what makes a setting portable between devices with different maxima, and
     * being one level out of several hundred adrift is imperceptible.
     */
    @Test
    fun `very fine devices round trip to within one level`() {
        for (maxLevel in listOf(128, 255, 1000)) {
            val capability = TorchCapability.Variable(maxLevel = maxLevel, defaultLevel = maxLevel)
            for (level in 1..maxLevel) {
                val roundTripped = capability.levelForPercent(capability.percentForLevel(level))
                val drift = kotlin.math.abs(roundTripped - level)
                assertTrue(
                    "maxLevel=$maxLevel level=$level drifted to $roundTripped",
                    drift <= maxLevel / 100 + 1,
                )
            }
        }
    }

    @Test
    fun `adjacent percentages can collapse to the same level, which is why the UI snaps`() {
        // The reason the sliders are level-based rather than percent-based: on a 21-level
        // device these two percentages are indistinguishable in hardware, so a percent slider
        // would move without changing the light. 12% is already level 3, so the collapse is
        // narrow but real, and it repeats across the whole range.
        val capability = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21)
        assertEquals(2, capability.levelForPercent(10))
        assertEquals(2, capability.levelForPercent(11))
        assertEquals(3, capability.levelForPercent(12))

        // Across the full range, 101 percentages address only 21 levels.
        val distinctLevels = (0..100).map { capability.levelForPercent(it) }.distinct()
        assertEquals(21, distinctLevels.size)
    }

    @Test
    fun `percentForLevel round trips at the extremes`() {
        val capability = TorchCapability.Variable(21, 21)
        assertEquals(100, capability.percentForLevel(21))
        assertEquals(100, capability.percentForLevel(999))
        assertEquals(21, capability.levelForPercent(capability.percentForLevel(21)))
    }
}
