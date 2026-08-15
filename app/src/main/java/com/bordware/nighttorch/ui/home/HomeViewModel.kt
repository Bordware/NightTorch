package com.bordware.nighttorch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bordware.nighttorch.data.AppSettings
import com.bordware.nighttorch.data.SettingsRepository
import com.bordware.nighttorch.di.AppContainer
import com.bordware.nighttorch.schedule.BrightnessDecision
import com.bordware.nighttorch.schedule.BrightnessResolver
import com.bordware.nighttorch.service.AccessibilityStatusMonitor
import com.bordware.nighttorch.torch.TorchCapability
import com.bordware.nighttorch.torch.TorchController
import com.bordware.nighttorch.torch.TorchState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Everything the home screen renders, in one immutable snapshot.
 *
 * @param serviceEnabled whether the accessibility service is switched on.
 * @param torch live torch state, driven by the system torch callback.
 * @param settings the persisted user preferences.
 * @param manualBrightnessPercent the manual slider position. Session state rather than a
 *   stored preference: it mirrors the torch's own working level, which lives for as long
 *   as the process does.
 * @param now the current time, refreshed once a minute so the schedule preview stays honest
 *   while the screen is open.
 * @param scheduleDecision what the schedule would do at [now], or null when auto-dimming is
 *   switched off and therefore has no opinion.
 */
data class HomeUiState(
    val serviceEnabled: Boolean = false,
    val torch: TorchState = TorchState(),
    val settings: AppSettings = AppSettings.DEFAULT,
    val manualBrightnessPercent: Int = DEFAULT_MANUAL_PERCENT,
    val now: LocalTime = LocalTime.MIDNIGHT,
    val scheduleDecision: BrightnessDecision? = null,
)

private const val DEFAULT_MANUAL_PERCENT = 100

/**
 * Holds all home screen state, so the composables can be pure functions of data plus
 * lambdas.
 *
 * A plain [ViewModel] built by [Factory] from the [AppContainer]; there is no Hilt in this
 * project (docs/architecture.md).
 */
class HomeViewModel(
    private val torchController: TorchController,
    private val settingsRepository: SettingsRepository,
    private val accessibilityStatusMonitor: AccessibilityStatusMonitor,
) : ViewModel() {

    /**
     * Re-reads the service status on demand.
     *
     * The `ContentObserver` catches changes while the app is alive, but re-checking on
     * resume covers the case where the process was killed while the user was away in
     * Settings (docs/architecture.md).
     */
    private val statusRefreshes = MutableSharedFlow<Boolean>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val manualBrightnessPercent = MutableStateFlow(DEFAULT_MANUAL_PERCENT)

    /**
     * Slider positions arrive far faster than the camera HAL wants to be called, so they are
     * debounced before being applied. The slider itself still follows the finger, because
     * [manualBrightnessPercent] updates immediately.
     */
    private val brightnessRequests = MutableSharedFlow<Int>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @OptIn(FlowPreview::class)
    private val debouncedBrightness = brightnessRequests.debounce(BRIGHTNESS_DEBOUNCE_MILLIS)

    val uiState: StateFlow<HomeUiState> = combine(
        merge(accessibilityStatusMonitor.isEnabledFlow, statusRefreshes),
        torchController.state,
        settingsRepository.settings,
        manualBrightnessPercent,
        minuteTicker(),
    ) { serviceEnabled, torch, settings, manualPercent, now ->
        HomeUiState(
            serviceEnabled = serviceEnabled,
            torch = torch,
            settings = settings,
            manualBrightnessPercent = manualPercent,
            now = now,
            scheduleDecision = BrightnessResolver.resolve(now, settings, torch.capability),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            debouncedBrightness.collect { torchController.setPercent(it) }
        }
        // Seed the slider from whatever level the torch is actually sitting at, so it does
        // not jump the first time the user drags it.
        viewModelScope.launch {
            val state = torchController.state.value
            val level = state.level
            if (level != null) {
                manualBrightnessPercent.value = state.capability.percentForLevel(level)
            }
        }
    }

    /** Switches the torch on or off, honouring the manual brightness. */
    fun onToggleTorch() {
        val capability = torchController.state.value.capability
        torchController.toggle(capability.levelForPercent(manualBrightnessPercent.value))
    }

    /**
     * Called continuously while the brightness slider is dragged.
     *
     * Updates the visible position immediately and schedules the debounced hardware call.
     */
    fun onManualBrightnessChange(percent: Int) {
        manualBrightnessPercent.value = percent.coerceIn(AppSettings.PERCENT_RANGE)
        brightnessRequests.tryEmit(manualBrightnessPercent.value)
    }

    fun onAutoDimmingChange(enabled: Boolean) = update {
        settingsRepository.setAutoDimmingEnabled(enabled)
    }

    fun onNightStartChange(time: LocalTime) = update {
        settingsRepository.setNightStart(time)
    }

    fun onNightEndChange(time: LocalTime) = update {
        settingsRepository.setNightEnd(time)
    }

    fun onNightBrightnessChange(percent: Int) = update {
        settingsRepository.setNightBrightnessPercent(percent)
    }

    fun onDayBrightnessChange(percent: Int) = update {
        settingsRepository.setDayBrightnessPercent(percent)
    }

    /**
     * Whether first-run onboarding is finished, or null while the store is still being read.
     *
     * Nullable on purpose. `HomeUiState` starts from `AppSettings.DEFAULT`, whose
     * `onboardingComplete` is false, so keying the decision off that would flash the
     * onboarding flow for a frame on every single launch.
     */
    val onboardingComplete: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.onboardingComplete }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /** Called from `ON_RESUME`, for when the user has just come back from Settings. */
    fun refreshServiceStatus() {
        statusRefreshes.tryEmit(accessibilityStatusMonitor.isEnabled())
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /**
     * Emits the current time immediately, then on each following minute boundary.
     *
     * Aligning to the boundary rather than ticking every 60 s means the preview line changes
     * when the displayed minute actually changes, instead of drifting up to a minute late.
     */
    private fun minuteTicker() = flow {
        while (true) {
            val now = LocalTime.now()
            emit(now)
            delay(millisUntilNextMinute(now))
        }
    }

    private fun millisUntilNextMinute(now: LocalTime): Long {
        val millisIntoMinute = now.second * 1_000L + now.nano / 1_000_000L
        return MILLIS_PER_MINUTE - millisIntoMinute
    }

    /**
     * Builds [HomeViewModel] from the manual DI container.
     *
     * `AccessibilityService` cannot use Hilt, so the whole project shares this hand-rolled
     * container rather than mixing two injection styles.
     */
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return HomeViewModel(
                torchController = container.torchController,
                settingsRepository = container.settingsRepository,
                accessibilityStatusMonitor = container.accessibilityStatusMonitor,
            ) as T
        }
    }

    private companion object {
        const val BRIGHTNESS_DEBOUNCE_MILLIS = 50L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Convenience for the preview line: "level 3 of 21". */
val TorchCapability.maxLevelOrNull: Int?
    get() = (this as? TorchCapability.Variable)?.maxLevel
