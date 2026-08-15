package com.bordware.nighttorch.torch

/**
 * Observable snapshot of the torch.
 *
 * [isOn] is never set from a local guess — it is driven entirely by
 * `CameraManager.TorchCallback`, so it stays correct when the torch is changed by the
 * Quick Settings tile, another app, or the system.
 *
 * @param isOn whether the torch is currently lit, as last reported by the system.
 * @param level the current device torch level in `1..maxLevel`, or `null` when unknown or
 *   when the capability is not [TorchCapability.Variable]. Never `0` — level 0 is invalid
 *   rather than meaning "off"; use [isOn] for that.
 * @param capability what the hardware can do, resolved once at construction.
 * @param isAvailable false while the system reports the torch as unavailable, which
 *   happens when another app holds the camera. Calls will fail until it returns to true.
 * @param error the most recent failure, or `null` if the last operation succeeded. Held as
 *   a typed value rather than a message so the UI can map it to a string resource.
 */
data class TorchState(
    val isOn: Boolean = false,
    val level: Int? = null,
    val capability: TorchCapability = TorchCapability.Unsupported,
    val isAvailable: Boolean = true,
    val error: TorchError? = null,
) {
    /** True when the torch can be operated right now. */
    val isOperable: Boolean
        get() = capability != TorchCapability.Unsupported && isAvailable

    /** True when the UI should offer a brightness control. */
    val supportsBrightness: Boolean
        get() = capability is TorchCapability.Variable
}

/**
 * A torch failure worth showing the user.
 *
 * Typed rather than a string so composables can resolve it against `strings.xml` — the
 * project forbids hardcoded user-facing text.
 */
enum class TorchError {
    /**
     * Another app holds the camera, so the torch cannot be controlled. Corresponds to
     * `CameraAccessException` with reason `CAMERA_IN_USE`, and to
     * `onTorchModeUnavailable`. Usually resolves on its own once the other app releases
     * the camera.
     */
    CameraInUse,

    /**
     * The camera subsystem rejected the call for a reason other than contention —
     * disconnected, disabled by policy, or a general camera error. Corresponds to the
     * remaining `CameraAccessException` reasons.
     */
    CameraUnavailable,

    /**
     * A torch level outside `1..maxLevel` reached the framework. Corresponds to
     * `IllegalArgumentException` from `turnOnTorchWithStrengthLevel`. Indicates a bug —
     * levels are clamped before every call — but is surfaced rather than crashing.
     */
    InvalidLevel,

    /** No camera on this device has a flash unit. */
    NoFlashUnit,
}
