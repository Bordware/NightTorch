package com.bordware.nighttorch.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.bordware.nighttorch.appContainer
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.schedule.BrightnessResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Toggles the torch when both volume keys are pressed together.
 *
 * An accessibility service is the only way an Android app can see key events while another
 * app is in front, or while the device is locked. That is the entire reason this app needs
 * one — see the user-facing justification in `accessibility_service_config.xml`.
 *
 * The service requests no accessibility events beyond the narrowest workable set and reads
 * no screen content. It holds no foreground notification, which an accessibility service
 * does not need.
 */
class FlashlightAccessibilityService : AccessibilityService() {

    /**
     * Latest settings, refreshed by a coroutine rather than read on demand.
     *
     * `onKeyEvent` runs on the main thread and must answer synchronously, so it cannot touch
     * DataStore — `runBlocking { dataStore.data.first() }` there would be disk I/O on the
     * main thread and would ANR on a slow device. Reading this volatile field costs nothing.
     * See docs/architecture.md.
     */
    @Volatile
    private var cachedSettings: AppSettings = AppSettings.DEFAULT

    private val detector = VolumeComboDetector()

    private var audioManager: AudioManager? = null

    private var haptics: HapticFeedback? = null

    /**
     * Media volume as it stood before the current gesture began, or null when no gesture is
     * in progress. See [snapshotVolumeIfGestureStarting].
     */
    private var volumeBeforeGesture: Int? = null

    /** Whether the current gesture actually fired the combo, as opposed to a lone press. */
    private var comboFiredThisGesture = false

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRestore: Runnable? = null

    private var scope: CoroutineScope? = null

    /**
     * Setup belongs here rather than in `onCreate`: the system kills and restarts the
     * service whenever the user toggles it in Settings, and only this callback marks the
     * point at which it is actually connected.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        // Key events from before a restart say nothing about what is held now.
        detector.reset()
        volumeBeforeGesture = null
        comboFiredThisGesture = false
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        haptics = HapticFeedback(this)

        scope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        newScope.launch {
            appContainer.settingsRepository.settings.collect { cachedSettings = it }
        }
    }

    /**
     * Must return synchronously: true swallows the event, false passes it to the system.
     *
     * The combo cannot be recognised without deciding the first key's fate before the second
     * arrives, so the first press is always passed through and the media volume is restored
     * afterwards if the gesture completes. See docs/architecture.md.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        snapshotVolumeIfGestureStarting(event)

        val result = detector.onKeyEvent(
            keyCode = event.keyCode,
            action = event.action,
            eventTime = event.eventTime,
            repeatCount = event.repeatCount,
        )

        if (result == ComboResult.TriggerAndConsume) {
            comboFiredThisGesture = true
            // Buzz first: it is the user's confirmation that the gesture registered, and it
            // should not wait on the camera. HapticFeedback still honours the system-wide
            // touch feedback setting, which is the switch that should govern this.
            haptics?.confirm()
            toggleTorch(cachedSettings)
        }

        if (event.action == KeyEvent.ACTION_UP && !detector.isAnyVolumeKeyHeld()) {
            onGestureEnded()
        }

        return result != ComboResult.Ignore
    }

    /** Required override. Deliberately empty — this service reads no screen content. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /**
     * Captures the media volume at the instant a gesture starts.
     *
     * It has to happen on the *first* key of the gesture. By the time the second key
     * arrives, roughly 10–18 ms later on the measured device, the system has already acted
     * on the first press, so reading the volume then would capture an already-nudged value
     * and restore one step off.
     */
    private fun snapshotVolumeIfGestureStarting(event: KeyEvent) {
        val isFreshVolumeDown = event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)

        if (isFreshVolumeDown && !detector.isAnyVolumeKeyHeld()) {
            volumeBeforeGesture = currentMediaVolume()
        }
    }

    /**
     * Called once both volume keys are back up.
     *
     * The restore has to wait until here. Restoring at trigger time is useless: the system
     * applies the passed-through key asynchronously, so the volume has not moved yet — a
     * device log showed the restore writing back the value that was already live, after
     * which the system's own change landed on top.
     */
    private fun onGestureEnded() {
        val snapshot = volumeBeforeGesture
        val fired = comboFiredThisGesture

        comboFiredThisGesture = false
        volumeBeforeGesture = null

        if (fired && snapshot != null) {
            scheduleVolumeRestore(snapshot)
        }
    }

    /**
     * Posts the restore a short time after the gesture, giving the system's own volume
     * change time to land first so it is not overwritten by ours.
     */
    private fun scheduleVolumeRestore(snapshot: Int) {
        pendingRestore?.let(handler::removeCallbacks)
        val runnable = Runnable {
            pendingRestore = null
            restoreVolume(snapshot)
        }
        pendingRestore = runnable
        handler.postDelayed(runnable, VOLUME_RESTORE_DELAY_MILLIS)
    }

    private fun toggleTorch(settings: AppSettings) {
        val torchController = appContainer.torchController
        val level = BrightnessResolver.resolveLevel(
            now = LocalTime.now(),
            settings = settings,
            capability = torchController.state.value.capability,
        )

        // A null level means auto-dimming is off, so the schedule has no opinion and the
        // manually chosen brightness stands.
        if (level != null) torchController.toggle(level) else torchController.toggle()
    }

    /**
     * Puts the media volume back to where it was before the gesture.
     *
     * `FLAG_REMOVE_SOUND_AND_VIBRATE` suppresses the feedback the adjustment would otherwise
     * make, so restoring is silent — the point is for the user never to notice the volume
     * moved at all.
     */
    private fun restoreVolume(snapshot: Int) {
        val manager = audioManager ?: return
        try {
            manager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                snapshot,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE,
            )
        } catch (e: SecurityException) {
            // Some OEM builds restrict volume changes under a notification policy. Degrade to
            // docs/architecture.md, the volume just
            // keeps the one step it moved — rather than taking the service down.
            Log.w(TAG, "Could not restore media volume", e)
        }
    }

    private fun currentMediaVolume(): Int? = try {
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not read media volume", e)
        null
    }

    private fun teardown() {
        scope?.cancel()
        scope = null
        pendingRestore?.let(handler::removeCallbacks)
        pendingRestore = null
        detector.reset()
        volumeBeforeGesture = null
        comboFiredThisGesture = false
    }

    private companion object {
        const val TAG = "NightTorchService"

        /**
         * How long to wait after the gesture before putting the volume back.
         *
         * Long enough for the system to have applied the passed-through key, short enough
         * that the momentary change is barely noticeable.
         */
        const val VOLUME_RESTORE_DELAY_MILLIS = 150L
    }
}
