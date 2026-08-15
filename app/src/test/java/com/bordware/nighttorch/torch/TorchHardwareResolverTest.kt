package com.bordware.nighttorch.torch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hardware decisions, tested without hardware.
 *
 * Most of these describe devices the development Pixel cannot imitate. It reports 21 levels,
 * so the cases that actually bite — API 33+ reporting a maximum of 1, or reporting nothing at
 * all — can only be covered here.
 */
class TorchHardwareResolverTest {

    private fun camera(id: String, hasFlash: Boolean = true, back: Boolean? = true) =
        FlashCamera(id = id, hasFlashUnit = hasFlash, isBackFacing = back)

    // ------------------------------------------------------------- camera selection

    @Test
    fun `prefers the back camera with a flash unit`() {
        val id = TorchHardwareResolver.selectFlashCameraId(
            listOf(
                camera("0", hasFlash = true, back = false),
                camera("1", hasFlash = true, back = true),
            ),
        )
        assertEquals("1", id)
    }

    @Test
    fun `does not assume the flash lives on camera zero`() {
        // The trap: hardcoding "0" picks a front camera with no flash here.
        val id = TorchHardwareResolver.selectFlashCameraId(
            listOf(
                camera("0", hasFlash = false, back = false),
                camera("2", hasFlash = true, back = true),
            ),
        )
        assertEquals("2", id)
    }

    @Test
    fun `falls back to any camera with a flash when none is back-facing`() {
        val id = TorchHardwareResolver.selectFlashCameraId(
            listOf(
                camera("0", hasFlash = false, back = true),
                camera("1", hasFlash = true, back = false),
            ),
        )
        assertEquals("1", id)
    }

    @Test
    fun `accepts a camera that does not report its lens facing`() {
        val id = TorchHardwareResolver.selectFlashCameraId(
            listOf(camera("7", hasFlash = true, back = null)),
        )
        assertEquals("7", id)
    }

    @Test
    fun `returns null when no camera has a flash unit`() {
        assertNull(
            TorchHardwareResolver.selectFlashCameraId(
                listOf(camera("0", hasFlash = false), camera("1", hasFlash = false)),
            ),
        )
    }

    @Test
    fun `returns null for a device with no cameras at all`() {
        assertNull(TorchHardwareResolver.selectFlashCameraId(emptyList()))
    }

    @Test
    fun `matches the measured Pixel 10 Pro layout`() {
        // docs/device-matrix.md: camera 0 is BACK with a flash, camera 1 is FRONT without.
        val id = TorchHardwareResolver.selectFlashCameraId(
            listOf(
                camera("0", hasFlash = true, back = true),
                camera("1", hasFlash = false, back = false),
            ),
        )
        assertEquals("0", id)
    }

    // ------------------------------------------------------------ capability mapping

    @Test
    fun `no flash unit is unsupported`() {
        val capability = TorchHardwareResolver.resolveCapability(
            sdkInt = 37,
            hasFlashUnit = false,
            maxLevel = 21,
            defaultLevel = 21,
        )
        assertEquals(TorchCapability.Unsupported, capability)
    }

    @Test
    fun `below API 33 is binary regardless of what is passed`() {
        for (sdkInt in listOf(26, 30, 32)) {
            val capability = TorchHardwareResolver.resolveCapability(
                sdkInt = sdkInt,
                hasFlashUnit = true,
                // Even if a level somehow arrived, the APIs to use it do not exist.
                maxLevel = 21,
                defaultLevel = 21,
            )
            assertEquals("sdkInt=$sdkInt", TorchCapability.BinaryOnly, capability)
        }
    }

    /**
     * The classic capability trap. Untestable on the development device, which reports 21.
     */
    @Test
    fun `API 33 plus with a maximum of one is binary, not variable`() {
        val capability = TorchHardwareResolver.resolveCapability(
            sdkInt = 33,
            hasFlashUnit = true,
            maxLevel = 1,
            defaultLevel = 1,
        )
        assertEquals(TorchCapability.BinaryOnly, capability)
    }

    @Test
    fun `API 33 plus reporting no maximum at all is binary`() {
        val capability = TorchHardwareResolver.resolveCapability(
            sdkInt = 33,
            hasFlashUnit = true,
            maxLevel = null,
            defaultLevel = null,
        )
        assertEquals(TorchCapability.BinaryOnly, capability)
    }

    @Test
    fun `a nonsensical zero or negative maximum is binary`() {
        for (maxLevel in listOf(0, -1)) {
            val capability = TorchHardwareResolver.resolveCapability(
                sdkInt = 37,
                hasFlashUnit = true,
                maxLevel = maxLevel,
                defaultLevel = null,
            )
            assertEquals("maxLevel=$maxLevel", TorchCapability.BinaryOnly, capability)
        }
    }

    @Test
    fun `a real range becomes variable`() {
        val capability = TorchHardwareResolver.resolveCapability(
            sdkInt = 37,
            hasFlashUnit = true,
            maxLevel = 21,
            defaultLevel = 21,
        )
        assertEquals(TorchCapability.Variable(maxLevel = 21, defaultLevel = 21), capability)
    }

    @Test
    fun `a missing default level falls back to the maximum`() {
        val capability = TorchHardwareResolver.resolveCapability(
            sdkInt = 37,
            hasFlashUnit = true,
            maxLevel = 5,
            defaultLevel = null,
        )
        assertEquals(TorchCapability.Variable(maxLevel = 5, defaultLevel = 5), capability)
    }

    @Test
    fun `an out-of-range default level is clamped rather than trusted`() {
        val tooHigh = TorchHardwareResolver.resolveCapability(
            sdkInt = 37,
            hasFlashUnit = true,
            maxLevel = 5,
            defaultLevel = 99,
        )
        assertEquals(TorchCapability.Variable(maxLevel = 5, defaultLevel = 5), tooHigh)

        // Level 0 is not "off", it is invalid, so it must clamp up to 1.
        val tooLow = TorchHardwareResolver.resolveCapability(
            sdkInt = 37,
            hasFlashUnit = true,
            maxLevel = 5,
            defaultLevel = 0,
        )
        assertEquals(TorchCapability.Variable(maxLevel = 5, defaultLevel = 1), tooLow)
    }

    @Test
    fun `API 33 exactly is the boundary where strength becomes available`() {
        assertEquals(
            TorchCapability.BinaryOnly,
            TorchHardwareResolver.resolveCapability(32, true, 21, 21),
        )
        assertEquals(
            TorchCapability.Variable(21, 21),
            TorchHardwareResolver.resolveCapability(33, true, 21, 21),
        )
    }
}
