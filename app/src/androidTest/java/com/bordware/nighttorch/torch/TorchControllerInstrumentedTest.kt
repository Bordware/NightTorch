package com.bordware.nighttorch.torch

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of [TorchController] against real camera hardware.
 *
 * Assertions are written as invariants rather than against this specific device, so the
 * suite stays meaningful on hardware with a different capability. The measured values are
 * logged under the `TorchSpec` tag so a run doubles as a way to fill in
 * `docs/device-matrix.md` for a new device.
 *
 * These tests drive the real torch, so it will visibly flash during the run.
 */
@RunWith(AndroidJUnit4::class)
class TorchControllerInstrumentedTest {

    private lateinit var controller: TorchController
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        controller = TorchController(context)
    }

    @After
    fun tearDown() {
        controller.turnOff()
        settle()
        controller.close()
    }

    @Test
    fun reportsCapabilityConsistentWithItsOwnInvariants() {
        val capability = controller.state.value.capability
        Log.i(TAG, "capability=$capability")

        when (capability) {
            is TorchCapability.Variable -> {
                // Variable must never be constructed with a degenerate range; a device
                // reporting max level 1 belongs in BinaryOnly.
                assertTrue(
                    "Variable must report maxLevel > 1, got ${capability.maxLevel}",
                    capability.maxLevel > 1,
                )
                assertTrue(
                    "defaultLevel ${capability.defaultLevel} outside 1..${capability.maxLevel}",
                    capability.defaultLevel in 1..capability.maxLevel,
                )
            }

            TorchCapability.BinaryOnly, TorchCapability.Unsupported -> Unit
        }
    }

    @Test
    fun stateStartsWithoutAnErrorOnHardwareWithAFlashUnit() {
        val state = controller.state.value
        if (state.capability == TorchCapability.Unsupported) {
            assertEquals(TorchError.NoFlashUnit, state.error)
        } else {
            assertNull("Unexpected startup error: ${state.error}", state.error)
        }
    }

    @Test
    fun turningOnAndOffIsReflectedInStateViaTheTorchCallback() {
        if (!controller.state.value.isOperable) return

        controller.turnOn()
        settle()
        assertTrue("Torch did not report on", controller.state.value.isOn)
        assertNull(controller.state.value.error)

        controller.turnOff()
        settle()
        assertTrue("Torch did not report off", !controller.state.value.isOn)
    }

    @Test
    fun toggleFlipsWhicheverWayTheTorchIsCurrentlyFacing() {
        if (!controller.state.value.isOperable) return

        val initial = controller.state.value.isOn
        controller.toggle()
        settle()
        assertEquals(!initial, controller.state.value.isOn)

        controller.toggle()
        settle()
        assertEquals(initial, controller.state.value.isOn)
    }

    @Test
    fun brightnessLevelsAreClampedIntoTheValidRange() {
        val capability = controller.state.value.capability
        if (capability !is TorchCapability.Variable) return

        controller.turnOn()
        settle()

        // 0 is not "off" — it is invalid and must clamp up to 1.
        controller.setLevel(0)
        settle()
        assertLevelWithin(1, capability.maxLevel)

        controller.setLevel(Int.MAX_VALUE)
        settle()
        assertLevelWithin(1, capability.maxLevel)

        controller.turnOff()
        settle()
    }

    @Test
    fun settingALevelWhileOffDoesNotLightTheTorch() {
        val capability = controller.state.value.capability
        if (capability !is TorchCapability.Variable) return

        controller.turnOff()
        settle()

        controller.setLevel(capability.maxLevel)
        settle()

        assertTrue(
            "setLevel switched the torch on; a brightness slider must be draggable while off",
            !controller.state.value.isOn,
        )
    }

    /**
     * The requirement that torch state is never a local boolean.
     *
     * Changes the torch through a *separate* `CameraManager`, bypassing [TorchController]
     * entirely — which is exactly what the Quick Settings tile, another torch app, or the
     * system does. If `state` were maintained by the controller's own methods rather than by
     * `CameraManager.TorchCallback`, it would not move here and the UI would desync.
     */
    @Test
    fun stateFollowsATorchChangeMadeOutsideTheController() {
        if (!controller.state.value.isOperable) return

        val externalManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = externalManager.firstFlashCameraId() ?: return

        controller.turnOff()
        settle()

        externalManager.setTorchMode(cameraId, true)
        settle()
        assertTrue(
            "State did not follow an external switch-on; it is not callback-driven",
            controller.state.value.isOn,
        )

        externalManager.setTorchMode(cameraId, false)
        settle()
        assertTrue(
            "State did not follow an external switch-off",
            !controller.state.value.isOn,
        )
    }

    private fun CameraManager.firstFlashCameraId(): String? = cameraIdList.firstOrNull { id ->
        getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    @Test
    fun logsTheDeviceCapabilityForTheDeviceMatrix() {
        val state = controller.state.value
        Log.i(TAG, "--- device matrix values ---")
        Log.i(TAG, "capability=${state.capability}")
        Log.i(TAG, "isOn=${state.isOn} level=${state.level} available=${state.isAvailable}")
        (state.capability as? TorchCapability.Variable)?.let {
            Log.i(TAG, "levelForPercent: 1%=${it.levelForPercent(1)} " +
                "50%=${it.levelForPercent(50)} 100%=${it.levelForPercent(100)}")
        }
        assertNotNull(state.capability)
    }

    private fun assertLevelWithin(min: Int, max: Int) {
        val level = controller.state.value.level ?: return
        assertTrue("level $level outside $min..$max", level in min..max)
    }

    /**
     * The torch callback is delivered on the main looper, so state updates land
     * asynchronously relative to the calling test thread.
     */
    private fun settle() = Thread.sleep(SETTLE_MILLIS)

    private companion object {
        const val TAG = "TorchSpec"
        const val SETTLE_MILLIS = 400L
    }
}
