package com.bordware.nighttorch.service

/**
 * What the caller should do with a key event.
 *
 * `AccessibilityService.onKeyEvent` must answer synchronously, so these map directly onto
 * its boolean return value.
 */
sealed interface ComboResult {
    /** Pass the event to the system — `onKeyEvent` returns false. */
    data object Ignore : ComboResult

    /** Swallow the event — `onKeyEvent` returns true. */
    data object Consume : ComboResult

    /** Fire the torch toggle, and swallow the event. */
    data object TriggerAndConsume : ComboResult
}

/**
 * Recognises "both volume keys pressed together" from a stream of key events.
 *
 * Pure Kotlin: no Android imports, no clock, no I/O. Every decision is a function of the
 * event primitives handed in, which is what makes the timing behaviour testable in
 * milliseconds on the JVM rather than by pressing buttons on a phone.
 *
 * **Not thread safe.** It is only ever driven from `onKeyEvent`, which the framework calls
 * on the main thread.
 *
 * Timings default to the values validated in `docs/device-matrix.md`: a real combo on the
 * target device had a 10–18 ms gap between the two `ACTION_DOWN`s, against a 150 ms window.
 *
 * @param windowMillis how far apart the two presses may be and still count as one gesture,
 *   compared inclusively. Kept generous rather than tightened to the measured 18 ms,
 *   because a forgiving window suits users with reduced motor control — the accessibility
 *   justification this whole feature rests on.
 * @param staleGestureMillis safety valve; see [onKeyEvent].
 */
class VolumeComboDetector(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val staleGestureMillis: Long = DEFAULT_STALE_GESTURE_MILLIS,
) {

    private var volumeUpDownAt: Long? = null
    private var volumeDownDownAt: Long? = null
    private var volumeUpHeld = false
    private var volumeDownHeld = false

    /**
     * Whether the system was allowed to see each key's press.
     *
     * A key whose `ACTION_DOWN` was passed through must have its `ACTION_UP` passed through
     * as well, or the system is left believing the key is still held. See [onUp].
     */
    private var volumeUpPassedThrough = false
    private var volumeDownPassedThrough = false

    /** True from the moment the combo fires until both keys have been released. */
    private var gestureArmed = false
    private var gestureArmedAt = 0L

    /**
     * Feeds one key event through the state machine.
     *
     * The contract, per docs/architecture.md
     * - Anything other than the two volume keys is ignored outright, including while a
     *   gesture is armed. Swallowing the power key would trap the user.
     * - A fresh press fires the combo when the *other* volume key is **still held** and its
     *   press landed within [windowMillis].
     * - Once fired, every further volume event is swallowed until both keys are released,
     *   so one gesture produces exactly one toggle.
     * - `repeatCount > 0` never fires the combo, so holding a single key cannot trigger it.
     * - A lone press that never gains a partner is ignored throughout, so ordinary volume
     *   control keeps working.
     *
     * **Requiring the other key to still be held strengthens the documented contract**, which asks
     * only that its press was recent. Measured presses last 113–162 ms while a genuine combo
     * has a 10–18 ms gap, so a user correcting an overshoot — nudge up, release, nudge down —
     * fits inside 150 ms comfortably and would otherwise light the torch by accident.
     *
     * The [staleGestureMillis] watchdog covers a key-up that never arrives, which can happen
     * if the system takes input away mid-gesture. Without it the detector would swallow every
     * volume event from then on, leaving the volume keys dead until the service restarted —
     * precisely the outcome the design exists to avoid. The default sits far above any deliberate hold;
     * a deliberate hold was measured at 5.3 s.
     *
     * @param keyCode the platform key code, e.g. [KEYCODE_VOLUME_UP].
     * @param action [ACTION_DOWN] or [ACTION_UP].
     * @param eventTime the event's `KeyEvent.getEventTime()`. This is the monotonic
     *   `uptimeMillis` timebase, so it is immune to the wall clock changing under us —
     *   never pass `System.currentTimeMillis()`.
     * @param repeatCount the event's `KeyEvent.getRepeatCount()`.
     */
    fun onKeyEvent(keyCode: Int, action: Int, eventTime: Long, repeatCount: Int): ComboResult {
        if (!isVolumeKey(keyCode)) return ComboResult.Ignore

        if (gestureArmed && eventTime - gestureArmedAt > staleGestureMillis) reset()

        return when (action) {
            ACTION_DOWN -> onDown(keyCode, eventTime, repeatCount)
            ACTION_UP -> onUp(keyCode)
            else -> ComboResult.Ignore
        }
    }

    /**
     * Whether either volume key is currently held.
     *
     * The service uses this to decide when a gesture begins, so it can snapshot the media
     * volume before the system has had a chance to change it.
     */
    fun isAnyVolumeKeyHeld(): Boolean = volumeUpHeld || volumeDownHeld

    /**
     * Drops all state.
     *
     * Called when the service connects or disconnects, since key events that arrived before
     * a restart say nothing about what is held now.
     */
    fun reset() {
        volumeUpDownAt = null
        volumeDownDownAt = null
        volumeUpHeld = false
        volumeDownHeld = false
        volumeUpPassedThrough = false
        volumeDownPassedThrough = false
        gestureArmed = false
        gestureArmedAt = 0L
    }

    private fun onDown(keyCode: Int, eventTime: Long, repeatCount: Int): ComboResult {
        // A repeat is not a fresh press: it must neither arm the combo nor stand in for the
        // partner press. Recording its time would let a long press pair with a later tap.
        if (repeatCount > 0) {
            return if (gestureArmed) ComboResult.Consume else ComboResult.Ignore
        }

        val partnerDownAt = partnerDownAt(keyCode)
        val partnerHeld = isPartnerHeld(keyCode)

        setHeld(keyCode, held = true)
        setDownAt(keyCode, eventTime)

        if (gestureArmed) {
            setPassedThrough(keyCode, false)
            return ComboResult.Consume
        }

        val withinWindow = partnerDownAt != null && eventTime - partnerDownAt <= windowMillis
        return if (partnerHeld && withinWindow) {
            gestureArmed = true
            gestureArmedAt = eventTime
            setPassedThrough(keyCode, false)
            ComboResult.TriggerAndConsume
        } else {
            // The system is about to act on this press, and must later see it released.
            setPassedThrough(keyCode, true)
            ComboResult.Ignore
        }
    }

    private fun onUp(keyCode: Int): ComboResult {
        val wasPassedThrough = passedThrough(keyCode)
        val wasArmed = gestureArmed

        setHeld(keyCode, held = false)
        setDownAt(keyCode, null)
        setPassedThrough(keyCode, false)

        // Stay armed until both keys are up, so releasing one and pressing it again cannot
        // fire a second toggle within the same gesture.
        if (gestureArmed && !isAnyVolumeKeyHeld()) gestureArmed = false

        return when {
            // A release must always follow the press the system saw. Swallowing it strands
            // the system believing the key is still down, and it then keeps applying that
            // key's effect: measured on a Pixel 10 Pro, consuming the first key's ACTION_UP
            // ramped the media volume to maximum and left it there. This deliberately
            // departs from docs/architecture.md, which says to consume both key-ups once armed.
            wasPassedThrough -> ComboResult.Ignore
            wasArmed -> ComboResult.Consume
            else -> ComboResult.Ignore
        }
    }

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KEYCODE_VOLUME_UP || keyCode == KEYCODE_VOLUME_DOWN

    private fun partnerDownAt(keyCode: Int): Long? =
        if (keyCode == KEYCODE_VOLUME_UP) volumeDownDownAt else volumeUpDownAt

    private fun isPartnerHeld(keyCode: Int): Boolean =
        if (keyCode == KEYCODE_VOLUME_UP) volumeDownHeld else volumeUpHeld

    private fun setHeld(keyCode: Int, held: Boolean) {
        if (keyCode == KEYCODE_VOLUME_UP) volumeUpHeld = held else volumeDownHeld = held
    }

    private fun setDownAt(keyCode: Int, at: Long?) {
        if (keyCode == KEYCODE_VOLUME_UP) volumeUpDownAt = at else volumeDownDownAt = at
    }

    private fun passedThrough(keyCode: Int): Boolean =
        if (keyCode == KEYCODE_VOLUME_UP) volumeUpPassedThrough else volumeDownPassedThrough

    private fun setPassedThrough(keyCode: Int, passed: Boolean) {
        if (keyCode == KEYCODE_VOLUME_UP) {
            volumeUpPassedThrough = passed
        } else {
            volumeDownPassedThrough = passed
        }
    }

    companion object {
        /**
         * Copies of the platform constants, so this class stays free of Android imports and
         * runs as a plain JVM unit test.
         *
         * `VolumeComboDetectorTest` asserts these equal the real `KeyEvent` values, which
         * catches any drift when the test compiles.
         */
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1

        /** See [VolumeComboDetector.windowMillis]. */
        const val DEFAULT_WINDOW_MILLIS = 150L

        /** Generous enough that no deliberate hold can reach it. */
        const val DEFAULT_STALE_GESTURE_MILLIS = 15_000L
    }
}
