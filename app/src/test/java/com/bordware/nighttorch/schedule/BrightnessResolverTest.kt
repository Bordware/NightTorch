package com.bordware.nighttorch.schedule

import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.torch.TorchCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Covers the percentage-to-level conversion at 1%, 50% and 100%, against maxLevel values of
 * 1, 5 and 100.
 *
 * Note that maxLevel 1 is expressed as [TorchCapability.BinaryOnly] rather than
 * `Variable(1, 1)`: a device reporting a maximum of 1 has no usable range, and classifying
 * it as Variable is the trap this project explicitly guards against — see
 * docs/architecture.md.
 */
class BrightnessResolverTest {

    private val defaults = AppSettings.DEFAULT

    private fun at(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

    private fun variable(maxLevel: Int) =
        TorchCapability.Variable(maxLevel = maxLevel, defaultLevel = maxLevel)

    // ------------------------------------------------------------- night / day selection

    @Test
    fun `inside the night window the night percentage is used`() {
        val decision = BrightnessResolver.resolve(at(22, 14), defaults, variable(21))
        requireNotNull(decision)
        assertTrue(decision.isNight)
        assertEquals(defaults.nightBrightnessPercent, decision.percent)
    }

    @Test
    fun `outside the night window the day percentage is used`() {
        val decision = BrightnessResolver.resolve(at(12, 0), defaults, variable(21))
        requireNotNull(decision)
        assertTrue(!decision.isNight)
        assertEquals(defaults.dayBrightnessPercent, decision.percent)
    }

    @Test
    fun `the decision follows the wrap across midnight`() {
        val capability = variable(21)
        assertTrue(BrightnessResolver.resolve(at(23, 59), defaults, capability)!!.isNight)
        assertTrue(BrightnessResolver.resolve(at(0, 0), defaults, capability)!!.isNight)
        assertTrue(BrightnessResolver.resolve(at(5, 59), defaults, capability)!!.isNight)
        assertTrue(!BrightnessResolver.resolve(at(6, 0), defaults, capability)!!.isNight)
    }

    // ------------------------------------------------------- auto-dimming master switch

    @Test
    fun `auto dimming disabled yields no opinion`() {
        val settings = defaults.copy(autoDimmingEnabled = false)
        assertNull(BrightnessResolver.resolve(at(22, 0), settings, variable(21)))
        assertNull(BrightnessResolver.resolveLevel(at(22, 0), settings, variable(21)))
        // Also at midday, so this is not accidentally a night-only behaviour.
        assertNull(BrightnessResolver.resolve(at(12, 0), settings, variable(21)))
    }

    // ----------------------------------------------------------- percentage to level

    @Test
    fun `percent to level against maxLevel 100`() {
        val capability = variable(100)
        assertEquals(1, levelFor(1, capability))
        assertEquals(50, levelFor(50, capability))
        assertEquals(100, levelFor(100, capability))
    }

    @Test
    fun `percent to level against maxLevel 5`() {
        val capability = variable(5)
        // 1% of 5 rounds to 0, which is invalid, so it clamps up to 1.
        assertEquals(1, levelFor(1, capability))
        assertEquals(3, levelFor(50, capability))
        assertEquals(5, levelFor(100, capability))
    }

    @Test
    fun `percent to level against a device whose maximum is 1`() {
        // Such a device is BinaryOnly, and every percentage collapses to the single valid
        // level. For a binary device 1 is both the minimum and the maximum.
        val capability = TorchCapability.BinaryOnly
        assertEquals(1, levelFor(1, capability))
        assertEquals(1, levelFor(50, capability))
        assertEquals(1, levelFor(100, capability))
    }

    @Test
    fun `unsupported hardware still resolves to a valid level`() {
        // Nothing will be lit, but the resolver must not emit an invalid level.
        assertEquals(1, levelFor(50, TorchCapability.Unsupported))
    }

    // ------------------------------------------------------------------------ clamping

    @Test
    fun `resolved level is always within 1 to maxLevel`() {
        for (maxLevel in listOf(2, 5, 21, 100)) {
            val capability = variable(maxLevel)
            for (percent in 0..100) {
                val settings = defaults.copy(nightBrightnessPercent = percent)
                val level = BrightnessResolver.resolveLevel(at(22, 0), settings, capability)!!
                assertTrue(
                    "percent=$percent maxLevel=$maxLevel produced level=$level",
                    level in 1..maxLevel,
                )
            }
        }
    }

    @Test
    fun `the default night setting resolves to the dimmest level`() {
        // The point of the app: 1% at night must be level 1, not a dazzling torch at 3am.
        val decision = BrightnessResolver.resolve(at(3, 0), defaults, variable(21))
        requireNotNull(decision)
        assertTrue(decision.isNight)
        assertEquals(1, decision.level)
    }

    @Test
    fun `the default day setting resolves to full power`() {
        val decision = BrightnessResolver.resolve(at(13, 0), defaults, variable(21))
        requireNotNull(decision)
        assertEquals(21, decision.level)
    }

    private fun levelFor(percent: Int, capability: TorchCapability): Int {
        val settings = defaults.copy(nightBrightnessPercent = percent)
        return BrightnessResolver.resolveLevel(at(22, 0), settings, capability)!!
    }
}
