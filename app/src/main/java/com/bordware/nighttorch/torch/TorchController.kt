package com.bordware.nighttorch.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the device torch and publishes its state.
 *
 * Requires **no permission**. `CameraManager.setTorchMode` and
 * `turnOnTorchWithStrengthLevel` sit deliberately outside the camera permission model, so
 * this class must never cause `android.permission.CAMERA` to be declared.
 *
 * Must be a process-wide singleton — see `AppContainer`. The accessibility service and the
 * UI have to observe the same [state] object, and each instance registers its own system
 * callback, so constructing more than one both desynchronises the UI and leaks callbacks.
 *
 * All public methods are safe to call from the main thread, including from
 * `AccessibilityService.onKeyEvent`: none of them perform disk I/O or block.
 *
 * @param context any context; the application context is retained internally.
 */
class TorchController(context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * The camera whose flash unit is driven, or null when the device has none.
     *
     * Chosen by iterating [CameraManager.getCameraIdList] and preferring a back-facing
     * camera that reports a flash unit. Never hardcoded to `"0"` — the ID that owns the
     * flash is not guaranteed to be first, and on some devices the front camera enumerates
     * ahead of the back.
     */
    private val cameraId: String? = selectFlashCameraId()

    /**
     * Resolved once at construction. The flash unit's capability cannot change while the
     * process is alive, and re-reading characteristics on every call would be wasteful.
     */
    private val capability: TorchCapability = detectCapability(cameraId)

    private val _state = MutableStateFlow(
        TorchState(
            capability = capability,
            error = if (cameraId == null) TorchError.NoFlashUnit else null,
        ),
    )

    /**
     * The current torch state.
     *
     * Driven by [CameraManager.TorchCallback], never by assuming a call succeeded, so it
     * reflects changes made by the Quick Settings tile, other apps, and the system.
     */
    val state: StateFlow<TorchState> = _state.asStateFlow()

    /**
     * Level to use the next time the torch is switched on without an explicit level.
     *
     * Volatile because it is written from the UI thread and read from
     * `AccessibilityService.onKeyEvent`.
     */
    @Volatile
    private var desiredLevel: Int = (capability as? TorchCapability.Variable)?.defaultLevel ?: 1

    private val torchCallback = object : CameraManager.TorchCallback() {

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != this@TorchController.cameraId) return
            _state.update { it.copy(isOn = enabled, isAvailable = true) }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != this@TorchController.cameraId) return
            // Another app has taken the camera. Report not-on rather than guessing.
            _state.update {
                it.copy(isOn = false, isAvailable = false, error = TorchError.CameraInUse)
            }
        }

        /**
         * API 33+ only. Never invoked below that, because the framework has no notion of
         * torch strength there — the override is simply unused on older devices.
         */
        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            if (cameraId != this@TorchController.cameraId) return
            _state.update { it.copy(level = newStrengthLevel) }
        }
    }

    init {
        // The Handler overload is used rather than the Executor overload because
        // registerTorchCallback(Executor, TorchCallback) is API 28 and minSdk is 26.
        // Registration immediately delivers the current mode for every camera, which is
        // what seeds the initial state.
        cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        readInitialStrengthLevel()
    }

    /**
     * Switches the torch off if it is on, otherwise on at the last requested level.
     *
     * Prefer [toggle] with an explicit level from callers that resolve a
     * schedule-appropriate brightness.
     */
    fun toggle() = toggle(desiredLevel)

    /**
     * Switches the torch off if it is on, otherwise on at [level].
     *
     * Reading [state] and then calling [turnOn] or [turnOff] separately would race with
     * the torch callback; this decides and acts in one step.
     *
     * @param level device level, clamped into `1..maxLevel`. Ignored when the capability is
     *   not [TorchCapability.Variable].
     */
    fun toggle(level: Int) {
        if (_state.value.isOn) turnOff() else turnOn(level)
    }

    /**
     * Switches the torch on at [level].
     *
     * On API 33+ with [TorchCapability.Variable] this uses
     * `turnOnTorchWithStrengthLevel`; otherwise it falls back to `setTorchMode`, where
     * [level] has no effect.
     *
     * @param level device level. Clamped into `1..maxLevel`, so callers cannot pass an
     *   out-of-range value; 0 is not "off" and is clamped up to 1.
     */
    fun turnOn(level: Int = desiredLevel) {
        val id = cameraId ?: return failNoFlashUnit()
        val clamped = clampLevel(level, capability)
        desiredLevel = clamped

        runCatchingTorch {
            if (capability is TorchCapability.Variable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                cameraManager.turnOnTorchWithStrengthLevel(id, clamped)
            } else {
                cameraManager.setTorchMode(id, true)
            }
        }
    }

    /**
     * Switches the torch off.
     *
     * Always uses `setTorchMode(id, false)` regardless of API level and capability —
     * there is no "off" strength level, and level 0 is invalid rather than dark.
     */
    fun turnOff() {
        val id = cameraId ?: return failNoFlashUnit()
        runCatchingTorch { cameraManager.setTorchMode(id, false) }
    }

    /**
     * Sets the brightness [level], applying it immediately if the torch is already lit.
     *
     * While lit, this re-calls `turnOnTorchWithStrengthLevel` with the new level rather
     * than switching off and on again, which would produce a visible flicker.
     *
     * While unlit, the level is only remembered — this never switches the torch on, so a
     * brightness slider can be dragged with the torch off without lighting it.
     *
     * No-op for capabilities other than [TorchCapability.Variable].
     */
    fun setLevel(level: Int) {
        if (capability !is TorchCapability.Variable) return
        val id = cameraId ?: return failNoFlashUnit()

        val clamped = clampLevel(level, capability)
        desiredLevel = clamped
        if (!_state.value.isOn) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatchingTorch { cameraManager.turnOnTorchWithStrengthLevel(id, clamped) }
        }
    }

    /**
     * Sets brightness from a user-facing percentage, converting to a device level first.
     *
     * @see TorchCapability.levelForPercent
     */
    fun setPercent(percent: Int) = setLevel(capability.levelForPercent(percent))

    /**
     * Unregisters the system torch callback.
     *
     * The singleton normally lives for the whole process, so this exists for tests and for
     * completeness rather than for routine use.
     */
    fun close() = cameraManager.unregisterTorchCallback(torchCallback)

    /**
     * Reads the camera list into plain values and lets [TorchHardwareResolver] choose.
     *
     * Only the Camera2 reads live here; the choice itself is pure and unit tested.
     */
    private fun selectFlashCameraId(): String? = try {
        TorchHardwareResolver.selectFlashCameraId(
            cameraManager.cameraIdList.map { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                FlashCamera(
                    id = id,
                    hasFlashUnit =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    isBackFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                        ?.let { it == CameraCharacteristics.LENS_FACING_BACK },
                )
            },
        )
    } catch (e: CameraAccessException) {
        Log.w(TAG, "Could not enumerate cameras", e)
        null
    }

    /**
     * Reads the strength characteristics and lets [TorchHardwareResolver] classify them.
     *
     * A read failure is reported as no flash unit rather than guessed at: claiming binary
     * support the device may not have would leave the UI offering a control that does nothing.
     */
    private fun detectCapability(id: String?): TorchCapability {
        if (id == null) return TorchCapability.Unsupported

        val strengthSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            TorchHardwareResolver.resolveCapability(
                sdkInt = Build.VERSION.SDK_INT,
                hasFlashUnit = true,
                maxLevel = if (strengthSupported) {
                    characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                } else {
                    null
                },
                defaultLevel = if (strengthSupported) {
                    characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
                } else {
                    null
                },
            )
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Could not read flash characteristics", e)
            TorchCapability.BinaryOnly
        }
    }

    private fun readInitialStrengthLevel() {
        val id = cameraId ?: return
        if (capability !is TorchCapability.Variable) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        try {
            val level = cameraManager.getTorchStrengthLevel(id)
            _state.update { it.copy(level = level) }
            desiredLevel = level
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Could not read initial torch strength", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Device rejected getTorchStrengthLevel", e)
        }
    }

    private fun clampLevel(level: Int, capability: TorchCapability): Int =
        if (capability is TorchCapability.Variable) level.coerceIn(1, capability.maxLevel) else 1

    /**
     * Runs a camera call, converting the two documented failure modes into [TorchState.error]
     * instead of letting them reach the caller. A torch app must never crash on a torch call.
     */
    private inline fun runCatchingTorch(block: () -> Unit) {
        try {
            block()
            _state.update { it.copy(error = null) }
        } catch (e: CameraAccessException) {
            val error = when (e.reason) {
                CameraAccessException.CAMERA_IN_USE,
                CameraAccessException.MAX_CAMERAS_IN_USE,
                -> TorchError.CameraInUse

                else -> TorchError.CameraUnavailable
            }
            Log.w(TAG, "Torch call failed, reason=${e.reason}", e)
            _state.update { it.copy(error = error) }
        } catch (e: IllegalArgumentException) {
            // Should be unreachable: levels are clamped before every call. Surfaced rather
            // than crashing so a device with an unusual range cannot take the app down.
            Log.w(TAG, "Torch level rejected by the framework", e)
            _state.update { it.copy(error = TorchError.InvalidLevel) }
        }
    }

    private fun failNoFlashUnit() {
        _state.update { it.copy(error = TorchError.NoFlashUnit) }
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
