package com.bordware.nighttorch.service

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven tests for the combo state machine, per docs/architecture.md.
 *
 * Timings here are grounded in the Phase 1 measurements in `docs/device-matrix.md`: a real
 * combo on a Pixel 10 Pro had a 10–18 ms gap between the two `ACTION_DOWN`s, and a single
 * press lasted 113–162 ms from down to up.
 */
class VolumeComboDetectorTest {

    private val detector = VolumeComboDetector()

    private fun down(keyCode: Int, at: Long, repeatCount: Int = 0) =
        detector.onKeyEvent(keyCode, VolumeComboDetector.ACTION_DOWN, at, repeatCount)

    private fun up(keyCode: Int, at: Long) =
        detector.onKeyEvent(keyCode, VolumeComboDetector.ACTION_UP, at, 0)

    private val volUp = VolumeComboDetector.KEYCODE_VOLUME_UP
    private val volDown = VolumeComboDetector.KEYCODE_VOLUME_DOWN

    // ------------------------------------------------- the constants must match the platform

    @Test
    fun `the detector's keycodes match the platform constants`() {
        // The detector is deliberately free of Android imports, so it declares its own
        // copies. These are compile-time constants, so this comparison is resolved when the
        // test compiles and fails loudly if the copies ever drift.
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, VolumeComboDetector.KEYCODE_VOLUME_UP)
        assertEquals(KeyEvent.KEYCODE_VOLUME_DOWN, VolumeComboDetector.KEYCODE_VOLUME_DOWN)
        assertEquals(KeyEvent.ACTION_DOWN, VolumeComboDetector.ACTION_DOWN)
        assertEquals(KeyEvent.ACTION_UP, VolumeComboDetector.ACTION_UP)
    }

    // ------------------------------------------------------------------- both key orders

    @Test
    fun `volume up then volume down triggers`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1018))
    }

    @Test
    fun `volume down then volume up triggers`() {
        assertEquals(ComboResult.Ignore, down(volDown, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volUp, 1010))
    }

    @Test
    fun `simultaneous downs at the same event time trigger`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1000))
    }

    // --------------------------------------------------------------------- window edges

    @Test
    fun `exactly at the 150ms window triggers`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1150))
    }

    @Test
    fun `one millisecond past the window does not trigger`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.Ignore, down(volDown, 1151))
    }

    @Test
    fun `far outside the window does not trigger`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.Ignore, down(volDown, 60_000))
    }

    // ------------------------------------------------------- keys must overlap in time

    @Test
    fun `releasing the first key before pressing the second does not trigger`() {
        // Deliberately stricter than "pressed recently". A user correcting an overshoot — nudge up,
        // release, nudge down — can easily fit inside 150ms, and must not light the torch.
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.Ignore, up(volUp, 1113))
        assertEquals(ComboResult.Ignore, down(volDown, 1130))
        assertEquals(ComboResult.Ignore, up(volDown, 1240))
    }

    @Test
    fun `a genuine combo overlaps, so it still triggers after the strengthening`() {
        // Measured shape from docs/device-matrix.md: 10ms apart, both released together.
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        // volUp's DOWN was passed through, so its UP must be too.
        assertEquals(ComboResult.Ignore, up(volUp, 1206))
        assertEquals(ComboResult.Consume, up(volDown, 1209))
    }

    // ------------------------------------------------- key-ups must balance key-downs

    /**
     * Regression test for a bug found on real hardware.
     *
     * docs/architecture.md. Doing
     * that leaves the system believing the first key — whose press it *did* see — is still
     * held, and it keeps applying that key's effect. On a Pixel 10 Pro this ramped the media
     * volume from 12 to the maximum of 25 and left it there.
     */
    @Test
    fun `the key whose press was passed through also has its release passed through`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))

        // The system saw volUp go down, so it must see it come up.
        assertEquals(ComboResult.Ignore, up(volUp, 1200))
        // It never saw volDown go down, so its release must stay swallowed.
        assertEquals(ComboResult.Consume, up(volDown, 1205))
    }

    @Test
    fun `the same holds when the keys are pressed in the other order`() {
        assertEquals(ComboResult.Ignore, down(volDown, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volUp, 1010))

        assertEquals(ComboResult.Ignore, up(volDown, 1200))
        assertEquals(ComboResult.Consume, up(volUp, 1205))
    }

    @Test
    fun `a re-pressed key that was consumed has its release consumed too`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        assertEquals(ComboResult.Ignore, up(volUp, 1200))
        // Re-pressed while still armed, so the system never sees this press...
        assertEquals(ComboResult.Consume, down(volUp, 1210))
        // ...and must not see its release either.
        assertEquals(ComboResult.Consume, up(volUp, 1300))
    }

    // ---------------------------------------------------------------- single press only

    @Test
    fun `a single volume press passes through untouched`() {
        assertEquals(ComboResult.Ignore, down(volDown, 1000))
        assertEquals(ComboResult.Ignore, up(volDown, 1162))
    }

    @Test
    fun `repeated single presses of the same key never trigger`() {
        var now = 1000L
        repeat(5) {
            assertEquals(ComboResult.Ignore, down(volDown, now))
            assertEquals(ComboResult.Ignore, up(volDown, now + 120))
            now += 200
        }
    }

    // -------------------------------------------------------------------- held-key repeats

    @Test
    fun `a long press of one key never triggers, whatever its repeat count`() {
        assertEquals(ComboResult.Ignore, down(volDown, 1000))
        // Repeats are not delivered on the measured device, but other OEMs may send them.
        for (repeat in 1..10) {
            assertEquals(ComboResult.Ignore, down(volDown, 1000 + repeat * 50L, repeatCount = repeat))
        }
        assertEquals(ComboResult.Ignore, up(volDown, 2000))
    }

    @Test
    fun `a repeat of the partner key does not arm the combo`() {
        // Only a fresh press counts as half a combo; a repeat must not stand in for one.
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.Ignore, down(volDown, 1050, repeatCount = 3))
    }

    @Test
    fun `repeats after a trigger are consumed`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        assertEquals(ComboResult.Consume, down(volUp, 1060, repeatCount = 1))
        assertEquals(ComboResult.Consume, down(volDown, 1070, repeatCount = 1))
    }

    // ------------------------------------------------------------ re-trigger suppression

    @Test
    fun `a held combo fires exactly once`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        // Everything until both keys are released is swallowed, and nothing re-fires.
        assertEquals(ComboResult.Consume, down(volUp, 1100, repeatCount = 1))
        assertEquals(ComboResult.Consume, down(volDown, 1110, repeatCount = 1))
        assertEquals(ComboResult.Ignore, up(volUp, 1200))
        assertEquals(ComboResult.Consume, up(volDown, 1205))
    }

    @Test
    fun `a second combo after a full release triggers again`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        assertEquals(ComboResult.Ignore, up(volUp, 1200))
        assertEquals(ComboResult.Consume, up(volDown, 1205))

        assertEquals(ComboResult.Ignore, down(volUp, 2000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 2010))
    }

    @Test
    fun `releasing only one key does not re-arm the detector`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        assertEquals(ComboResult.Ignore, up(volUp, 1200))
        // Still armed: the other key is down, so this press must not fire a second time.
        assertEquals(ComboResult.Consume, down(volUp, 1210))
    }

    // ----------------------------------------------------------------- non-volume keys

    @Test
    fun `non-volume keys are always ignored`() {
        for (keyCode in listOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_HOME)) {
            assertEquals(ComboResult.Ignore, down(keyCode, 1000))
            assertEquals(ComboResult.Ignore, up(keyCode, 1100))
        }
    }

    @Test
    fun `a power key between the two volume presses does not break the combo`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.Ignore, down(KeyEvent.KEYCODE_POWER, 1005))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
    }

    @Test
    fun `non-volume keys are still ignored while a combo is armed`() {
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        // The service must never swallow the power key; that would trap the user.
        assertEquals(ComboResult.Ignore, down(KeyEvent.KEYCODE_POWER, 1100))
    }

    // ------------------------------------------------------------------ held-key query

    @Test
    fun `isAnyVolumeKeyHeld tracks press and release`() {
        assertFalse(detector.isAnyVolumeKeyHeld())
        down(volUp, 1000)
        assertTrue(detector.isAnyVolumeKeyHeld())
        down(volDown, 1010)
        assertTrue(detector.isAnyVolumeKeyHeld())
        up(volUp, 1200)
        assertTrue(detector.isAnyVolumeKeyHeld())
        up(volDown, 1205)
        assertFalse(detector.isAnyVolumeKeyHeld())
    }

    @Test
    fun `isAnyVolumeKeyHeld ignores non-volume keys`() {
        down(KeyEvent.KEYCODE_POWER, 1000)
        assertFalse(detector.isAnyVolumeKeyHeld())
    }

    // ------------------------------------------------------------------------ watchdog

    @Test
    fun `a lost key-up cannot wedge the detector forever`() {
        val short = VolumeComboDetector(staleGestureMillis = 5_000)
        assertEquals(
            ComboResult.Ignore,
            short.onKeyEvent(volUp, VolumeComboDetector.ACTION_DOWN, 1000, 0),
        )
        assertEquals(
            ComboResult.TriggerAndConsume,
            short.onKeyEvent(volDown, VolumeComboDetector.ACTION_DOWN, 1010, 0),
        )
        // Both key-ups never arrive. Without a watchdog the detector would consume every
        // volume event from here on and the volume keys would be dead until the service
        // restarted — the exact outcome the design exists to avoid.
        assertEquals(
            ComboResult.Ignore,
            short.onKeyEvent(volDown, VolumeComboDetector.ACTION_DOWN, 30_000, 0),
        )
        assertEquals(
            ComboResult.Ignore,
            short.onKeyEvent(volDown, VolumeComboDetector.ACTION_UP, 30_100, 0),
        )
    }

    @Test
    fun `the watchdog does not fire during a normal long hold`() {
        // Phase 1 measured a deliberate 5.3s hold, so the timeout must sit well above that.
        assertEquals(ComboResult.Ignore, down(volUp, 1000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 1010))
        assertEquals(ComboResult.Ignore, up(volUp, 7000))
        assertEquals(ComboResult.Consume, up(volDown, 7005))
    }

    // --------------------------------------------------------------------------- reset

    @Test
    fun `reset clears all state`() {
        down(volUp, 1000)
        down(volDown, 1010)
        detector.reset()
        assertFalse(detector.isAnyVolumeKeyHeld())
        // A fresh gesture behaves as though nothing came before.
        assertEquals(ComboResult.Ignore, down(volUp, 2000))
        assertEquals(ComboResult.TriggerAndConsume, down(volDown, 2010))
    }

    // ------------------------------------------------------------------- custom window

    @Test
    fun `a custom window is honoured at both edges`() {
        val wide = VolumeComboDetector(windowMillis = 400)
        assertEquals(
            ComboResult.Ignore,
            wide.onKeyEvent(volUp, VolumeComboDetector.ACTION_DOWN, 1000, 0),
        )
        assertEquals(
            ComboResult.TriggerAndConsume,
            wide.onKeyEvent(volDown, VolumeComboDetector.ACTION_DOWN, 1400, 0),
        )
    }
}
