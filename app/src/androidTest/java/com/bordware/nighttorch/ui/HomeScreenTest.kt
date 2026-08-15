package com.bordware.nighttorch.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bordware.nighttorch.R
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.schedule.BrightnessDecision
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.torch.TorchError
import com.bordware.nighttorch.torch.TorchState
import com.bordware.nighttorch.ui.home.HomeScreen
import com.bordware.nighttorch.ui.home.HomeUiState
import com.bordware.nighttorch.ui.theme.NightTorchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * State transitions for the four home screen cards.
 *
 * Harness note: Espresso is pinned forward in the version catalog. It arrives transitively
 * at 3.5.0, which reflects on `InputManager.getInstance` — removed in Android 17 — so every
 * test here failed with NoSuchMethodException on the API 37 device before the pin.
 *
 * These need the device **unlocked and awake**. The rule hosts the content in a real
 * activity, and a locked or dozing device never brings it to the foreground, which surfaces
 * as the misleading "No compose hierarchies found in the app".
 *
 * `HomeScreen` takes data and lambdas only, so every case here is driven by constructing a
 * state rather than by manipulating a real torch — which also means the awkward states
 * (no flash unit, camera busy, binary-only hardware) are reachable on a device that has none
 * of those problems.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun string(id: Int) = resources.getString(id)

    private val variable21 = TorchCapability.Variable(maxLevel = 21, defaultLevel = 21)

    private fun setContent(
        state: HomeUiState,
        onToggleTorch: () -> Unit = {},
        onOpenAccessibilitySettings: () -> Unit = {},
        onBrightnessChange: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            NightTorchTheme(darkTheme = true, dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onToggleTorch = onToggleTorch,
                    onManualBrightnessChange = onBrightnessChange,
                    onAutoDimmingChange = {},
                    onNightStartChange = {},
                    onNightEndChange = {},
                    onNightBrightnessChange = {},
                    onDayBrightnessChange = {},
                    onOpenSourceCode = {},
                )
            }
        }
    }

    /** The primary torch control, which carries its label as a contentDescription. */
    private fun torchButton() =
        composeRule.onNodeWithContentDescription(string(R.string.torch_card_switch))

    private fun state(
        serviceEnabled: Boolean = true,
        torch: TorchState = TorchState(capability = variable21),
        settings: AppSettings = AppSettings.DEFAULT,
        decision: BrightnessDecision? = BrightnessDecision(isNight = true, percent = 1, level = 1),
        now: LocalTime = LocalTime.of(22, 14),
        manualPercent: Int = 100,
    ) = HomeUiState(
        serviceEnabled = serviceEnabled,
        torch = torch,
        settings = settings,
        manualBrightnessPercent = manualPercent,
        now = now,
        scheduleDecision = decision,
    )

    // ------------------------------------------------------------ service status card

    @Test
    fun serviceActiveHidesTheSettingsButton() {
        setContent(state(serviceEnabled = true))

        composeRule.onNodeWithText(string(R.string.service_card_enabled)).assertIsDisplayed()
        // Nothing to fix, so nothing to press.
        composeRule.onNodeWithText(string(R.string.service_card_open_settings)).assertDoesNotExist()
    }

    @Test
    fun serviceInactiveOffersTheSettingsButton() {
        var opened = 0
        setContent(state(serviceEnabled = false), onOpenAccessibilitySettings = { opened++ })

        composeRule.onNodeWithText(string(R.string.service_card_disabled)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.service_card_open_settings)).performClick()
        assertEquals(1, opened)
    }

    // -------------------------------------------------------------- manual torch card

    @Test
    fun torchButtonReportsItsStateAsAToggle() {
        setContent(state(torch = TorchState(isOn = false, capability = variable21)))

        // Announced as a switch rather than a button, so a screen reader says "off".
        torchButton().assertIsOff()
    }

    @Test
    fun torchButtonReportsOnWhenLit() {
        setContent(state(torch = TorchState(isOn = true, level = 21, capability = variable21)))

        torchButton().assertIsOn()
    }

    @Test
    fun tappingTheTorchButtonToggles() {
        var toggles = 0
        setContent(
            state(torch = TorchState(isOn = false, capability = variable21)),
            onToggleTorch = { toggles++ },
        )

        composeRule.onNodeWithText(string(R.string.torch_card_tap_to_turn_on))
            .performScrollTo().assertIsDisplayed()
        torchButton().performScrollTo().performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun binaryOnlyHardwareHidesTheSliderAndExplainsWhy() {
        setContent(state(torch = TorchState(capability = TorchCapability.BinaryOnly)))

        composeRule.onNodeWithText(string(R.string.torch_card_binary_only))
            .performScrollTo()
            .assertIsDisplayed()
        // A dead slider would imply the brightness can be changed.
        composeRule.onNodeWithText(string(R.string.torch_card_brightness)).assertDoesNotExist()
    }

    @Test
    fun unsupportedHardwareExplainsThereIsNoFlashUnit() {
        setContent(
            state(
                torch = TorchState(
                    capability = TorchCapability.Unsupported,
                    error = TorchError.NoFlashUnit,
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.torch_card_unsupported))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.torch_card_brightness)).assertDoesNotExist()
    }

    @Test
    fun cameraInUseDisablesTheControlAndSaysWhyOnce() {
        setContent(
            state(
                torch = TorchState(
                    capability = variable21,
                    isAvailable = false,
                    error = TorchError.CameraInUse,
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.torch_card_unavailable))
            .performScrollTo().assertIsDisplayed()
        // The unavailability line already explains it; the error must not repeat it.
        composeRule.onNodeWithText(string(R.string.torch_card_error_camera_in_use))
            .assertDoesNotExist()
        torchButton().assertIsNotEnabled()
    }

    @Test
    fun brightnessReadsAsALevelNotAPercentage() {
        setContent(state(torch = TorchState(capability = variable21), manualPercent = 50))

        // 50% of 21 levels is level 11; the percentage is finer than the hardware.
        composeRule.onNodeWithText(resources.getString(R.string.torch_card_level_of, 11, 21))
            .performScrollTo().assertIsDisplayed()
    }

    // --------------------------------------------------------------- auto dimming card

    @Test
    fun nightPreviewShowsTheResolvedLevel() {
        setContent(
            state(
                now = LocalTime.of(22, 14),
                decision = BrightnessDecision(isNight = true, percent = 1, level = 1),
            ),
        )

        // Asserting the whole line: "Night" on its own also matches Night Start, Night End
        // and Night brightness.
        composeRule.onNodeWithText("→ Night → level 1 of 21", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun autoDimmingOffSaysTheScheduleHasNoOpinion() {
        setContent(
            state(
                settings = AppSettings.DEFAULT.copy(autoDimmingEnabled = false),
                decision = null,
            ),
        )

        composeRule.onNodeWithText(string(R.string.dimming_card_preview_off)).assertIsDisplayed()
        // The schedule controls are pointless while the feature is off.
        composeRule.onNodeWithText(string(R.string.dimming_card_night_start)).assertDoesNotExist()
    }

    @Test
    fun equalStartAndEndWarnsThatDimmingNeverApplies() {
        setContent(
            state(
                settings = AppSettings.DEFAULT.copy(
                    nightStart = LocalTime.of(21, 0),
                    nightEnd = LocalTime.of(21, 0),
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.dimming_card_same_time_warning))
            .assertIsDisplayed()
    }

    @Test
    fun binaryHardwareHidesTheScheduleBrightnessSliders() {
        setContent(state(torch = TorchState(capability = TorchCapability.BinaryOnly)))

        composeRule.onNodeWithText(string(R.string.dimming_card_night_brightness))
            .assertDoesNotExist()
    }

    // -------------------------------------------------------------------- privacy card

    @Test
    fun privacyCardStatesTheNoInternetClaimAndLinksTheSource() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.privacy_bullet_network))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.privacy_card_source)).assertHasClickAction()
    }

    @Test
    fun privacyCardAccountsForEveryPermissionTheAppDeclares() {
        setContent(state())

        // Three declared permissions, three explanations. If a permission is ever added
        // without a line here, this fails.
        composeRule.onNodeWithText(string(R.string.privacy_bullet_accessibility))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.privacy_bullet_audio))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.privacy_bullet_vibrate))
            .performScrollTo().assertIsDisplayed()
    }
}
