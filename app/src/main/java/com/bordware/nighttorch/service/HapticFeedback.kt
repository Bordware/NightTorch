package com.bordware.nighttorch.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

/**
 * Fires the short confirming buzz when the volume combo is recognised.
 *
 * Worth more here than in an ordinary app: the whole point of the gesture is that it works
 * without looking at the screen, so the vibration is often the user's *first* confirmation
 * that anything happened. Without it, a torch that fails to light is indistinguishable from
 * a gesture that was never recognised.
 *
 * API levels verified against `platforms/android-37.0/data/api-versions.xml`, because the
 * vibrator API changed shape twice inside our supported range:
 *
 * | API | What exists |
 * |---|---|
 * | 26 | `VibrationEffect.createOneShot`, `Vibrator.vibrate(VibrationEffect)` |
 * | 29 | `VibrationEffect.createPredefined` and the `EFFECT_*` constants |
 * | 31 | `VibratorManager.getDefaultVibrator()`; the old service lookup is deprecated |
 *
 * @param context any context; the application context is retained.
 */
class HapticFeedback(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = resolveVibrator()

    /**
     * Buzzes once, unless the device has no vibrator or the user has switched system-wide
     * touch feedback off.
     *
     * Deliberately checks the system setting as well as the app's own: a user who has
     * silenced haptics device-wide has already answered this question, and an app that
     * buzzes anyway is the kind that gets uninstalled.
     */
    fun confirm() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (!systemHapticsEnabled()) return

        try {
            vibrator.vibrate(confirmationEffect())
        } catch (e: SecurityException) {
            // VIBRATE is a normal permission, so this should not happen — but a service that
            // dies here would take the torch shortcut with it.
            Log.w(TAG, "Vibration rejected", e)
        }
    }

    /**
     * A single firm tick. Predefined effects are preferred where available because they are
     * tuned per device and match the feel of the rest of the system; the hand-rolled
     * duration below is only a fallback for API 26–28.
     */
    private fun confirmationEffect(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            VibrationEffect.createOneShot(FALLBACK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private fun resolveVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "No vibrator available", e)
        null
    }

    /**
     * Whether the user has haptic feedback switched on device-wide.
     *
     * `Settings.System.HAPTIC_FEEDBACK_ENABLED` is readable without permission; a missing
     * value is treated as enabled, matching the platform default.
     */
    private fun systemHapticsEnabled(): Boolean = try {
        Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not read system haptic setting", e)
        true
    }

    private companion object {
        const val TAG = "HapticFeedback"

        /** Only used on API 26–28, where predefined effects do not exist. */
        const val FALLBACK_MILLIS = 40L
    }
}
