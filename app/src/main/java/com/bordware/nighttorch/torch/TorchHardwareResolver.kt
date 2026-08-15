package com.bordware.nighttorch.torch

/**
 * One camera, described in plain values rather than Camera2 types.
 *
 * @param isBackFacing null when the device does not report a lens facing at all.
 */
internal data class FlashCamera(
    val id: String,
    val hasFlashUnit: Boolean,
    val isBackFacing: Boolean?,
)

/**
 * The two decisions [TorchController] makes about hardware, extracted as pure functions.
 *
 * **Why this rather than a fake `CameraManager`.** docs/architecture.md
 * can be introduced without contorting the production API. It cannot: `CameraManager` and
 * `CameraCharacteristics` are both final, `CameraCharacteristics.Key` instances cannot be
 * constructed, and faking them needs either a mocking framework that rewrites bytecode or a
 * wrapper interface mirroring the Camera2 surface. Both buy indirection rather than
 * confidence.
 *
 * What actually needed testing was never the Camera2 plumbing — it was the *decisions*: which
 * camera to drive, and how a set of reported characteristics maps to a capability. Those are
 * arithmetic over plain values, so they are pulled out here and tested directly.
 *
 * This also covers cases the hardware cannot produce. The development device reports 21
 * levels, so "API 33+ but the device reports a maximum of 1" — the classic trap, and common on
 * real hardware — is untestable on it and testable here.
 */
internal object TorchHardwareResolver {

    /** Android 13, where the flash strength APIs arrived. */
    const val API_WITH_STRENGTH_CONTROL = 33

    /**
     * Picks the camera whose flash to drive.
     *
     * Prefers a back-facing camera with a flash unit, falls back to any camera with one, and
     * returns null when none has one. Never assumes `"0"`: the ID owning the flash is not
     * guaranteed to be first, and on some devices the front camera enumerates ahead of the
     * back.
     */
    fun selectFlashCameraId(cameras: List<FlashCamera>): String? {
        val withFlash = cameras.filter { it.hasFlashUnit }
        return withFlash.firstOrNull { it.isBackFacing == true }?.id ?: withFlash.firstOrNull()?.id
    }

    /**
     * Maps reported characteristics to a [TorchCapability].
     *
     * @param sdkInt the running API level, passed in rather than read, so the API 26–32
     *   branch is testable on a JVM.
     * @param hasFlashUnit whether a camera with a flash unit was found at all.
     * @param maxLevel `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`, or null below API 33 or when the
     *   device does not report it.
     * @param defaultLevel `FLASH_INFO_STRENGTH_DEFAULT_LEVEL`, clamped into range, falling
     *   back to the maximum when absent or nonsensical.
     */
    fun resolveCapability(
        sdkInt: Int,
        hasFlashUnit: Boolean,
        maxLevel: Int?,
        defaultLevel: Int?,
    ): TorchCapability = when {
        !hasFlashUnit -> TorchCapability.Unsupported

        // The strength keys do not exist below API 33, so there is nothing to ask.
        sdkInt < API_WITH_STRENGTH_CONTROL -> TorchCapability.BinaryOnly

        // null means the device does not report a range; 1 means the range holds a single
        // value. Both are binary in practice, and both are common on API 33+. Treating either
        // as Variable is the mistake this classification exists to prevent.
        maxLevel == null || maxLevel <= 1 -> TorchCapability.BinaryOnly

        else -> TorchCapability.Variable(
            maxLevel = maxLevel,
            defaultLevel = defaultLevel?.coerceIn(1, maxLevel) ?: maxLevel,
        )
    }
}
