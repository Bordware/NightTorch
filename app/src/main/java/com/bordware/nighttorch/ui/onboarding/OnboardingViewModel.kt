package com.bordware.nighttorch.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.data.SettingsRepository
import com.bordware.nighttorch.di.AppContainer
import com.bordware.nighttorch.service.AccessibilityStatusMonitor
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.torch.TorchController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * @param serviceEnabled whether the accessibility service is actually running. The flow's
 *   Next control is gated on this rather than on the user having *visited* Settings, so it
 *   cannot advance on a failed attempt.
 * @param showRestrictedSettingsHint set once the user has come back from Settings twice
 *   without the service turning on, which is the signature of the Android 13+ restricted
 *   settings block (docs/architecture.md).
 */
data class OnboardingUiState(
    val serviceEnabled: Boolean = false,
    val settings: AppSettings = AppSettings.DEFAULT,
    val capability: TorchCapability = TorchCapability.Unsupported,
    val showRestrictedSettingsHint: Boolean = false,
)

/**
 * State for the first-run flow.
 *
 * Kept separate from `HomeViewModel` despite overlapping dependencies, because it owns one
 * thing the home screen has no business knowing about: how many times the user has bounced
 * off the Settings screen without succeeding.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val accessibilityStatusMonitor: AccessibilityStatusMonitor,
    torchController: TorchController,
) : ViewModel() {

    private val statusRefreshes = MutableSharedFlow<Boolean>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * How many times the user has been sent to accessibility settings.
     *
     * Counting departures rather than returns because a return is not observable on its own —
     * `ON_RESUME` also fires for unrelated reasons.
     */
    private val settingsVisits = MutableStateFlow(0)

    val uiState: StateFlow<OnboardingUiState> = combine(
        merge(accessibilityStatusMonitor.isEnabledFlow, statusRefreshes),
        settingsRepository.settings,
        torchController.state,
        settingsVisits,
    ) { serviceEnabled, settings, torch, visits ->
        OnboardingUiState(
            serviceEnabled = serviceEnabled,
            settings = settings,
            capability = torch.capability,
            // Two failed attempts is the threshold: one failure is an ordinary misclick or a
            // change of mind, and nagging about a rare system restriction after a single
            // miss would confuse far more users than it would help.
            showRestrictedSettingsHint = !serviceEnabled && visits >= VISITS_BEFORE_HINT,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = OnboardingUiState(),
    )

    /** Call when actually launching the Settings intent, not when rendering the button. */
    fun onOpenedAccessibilitySettings() {
        settingsVisits.value += 1
    }

    /** Re-read on `ON_RESUME`, for when the process died while the user was in Settings. */
    fun refreshServiceStatus() {
        statusRefreshes.tryEmit(accessibilityStatusMonitor.isEnabled())
    }

    fun onNightStartChange(time: LocalTime) = update { settingsRepository.setNightStart(time) }

    fun onNightEndChange(time: LocalTime) = update { settingsRepository.setNightEnd(time) }

    fun onNightBrightnessChange(percent: Int) = update {
        settingsRepository.setNightBrightnessPercent(percent)
    }

    fun onDayBrightnessChange(percent: Int) = update {
        settingsRepository.setDayBrightnessPercent(percent)
    }

    fun onAutoDimmingChange(enabled: Boolean) = update {
        settingsRepository.setAutoDimmingEnabled(enabled)
    }

    /**
     * Ends onboarding, whether the user finished it or skipped.
     *
     * Skipping records completion too: someone who declined the accessibility service should
     * land on the home screen and be able to use the torch, not be asked again on every
     * launch. The status card already tells them the shortcut is inactive.
     */
    fun onOnboardingFinished() = update { settingsRepository.setOnboardingComplete(true) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return OnboardingViewModel(
                settingsRepository = container.settingsRepository,
                accessibilityStatusMonitor = container.accessibilityStatusMonitor,
                torchController = container.torchController,
            ) as T
        }
    }

    private companion object {
        const val VISITS_BEFORE_HINT = 2
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
