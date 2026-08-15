package com.bordware.nighttorch.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bordware.nighttorch.R
import com.bordware.nighttorch.appContainer
import com.bordware.nighttorch.ui.home.HomeScreen
import com.bordware.nighttorch.ui.home.HomeViewModel
import com.bordware.nighttorch.ui.onboarding.OnboardingScreen
import com.bordware.nighttorch.ui.onboarding.OnboardingViewModel
import com.bordware.nighttorch.ui.theme.NightTorchTheme

/**
 * The app's only screen.
 *
 * Note the torch keeps working whether or not this activity is alive: the accessibility
 * service and the UI share one `TorchController` through the `AppContainer`.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(appContainer)
    }

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        OnboardingViewModel.Factory(appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NightTorchTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
                val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()

                // The ContentObserver catches changes while this process is alive; re-reading
                // on resume covers a process death while the user was away in Settings.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshServiceStatus()
                    onboardingViewModel.refreshServiceStatus()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (onboardingComplete) {
                        // Null means the store has not been read yet. Drawing nothing for that
                        // frame is right: the launch splash is still covering the window, and
                        // guessing either way would flash the wrong screen.
                        null -> Unit

                        false -> OnboardingScreen(
                            state = onboardingState,
                            onOpenAccessibilitySettings = {
                                onboardingViewModel.onOpenedAccessibilitySettings()
                                openAccessibilitySettings()
                            },
                            onNightStartChange = onboardingViewModel::onNightStartChange,
                            onNightEndChange = onboardingViewModel::onNightEndChange,
                            onNightBrightnessChange = onboardingViewModel::onNightBrightnessChange,
                            onDayBrightnessChange = onboardingViewModel::onDayBrightnessChange,
                            onAutoDimmingChange = onboardingViewModel::onAutoDimmingChange,
                            onFinish = onboardingViewModel::onOnboardingFinished,
                            modifier = Modifier.padding(innerPadding),
                        )

                        else -> HomeScreen(
                            state = state,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onToggleTorch = viewModel::onToggleTorch,
                            onManualBrightnessChange = viewModel::onManualBrightnessChange,
                            onAutoDimmingChange = viewModel::onAutoDimmingChange,
                            onNightStartChange = viewModel::onNightStartChange,
                            onNightEndChange = viewModel::onNightEndChange,
                            onNightBrightnessChange = viewModel::onNightBrightnessChange,
                            onDayBrightnessChange = viewModel::onDayBrightnessChange,
                            onOpenSourceCode = { openUrl(getString(R.string.source_code_url)) },
                            contentPadding = innerPadding,
                        )
                    }
                }
            }
        }
    }

    /**
     * Opens the system accessibility settings list.
     *
     * Deep-linking straight to this app's own entry is undocumented and OEM-dependent, so it
     * is deliberately not attempted (docs/architecture.md). The plain list always works.
     */
    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No accessibility settings activity on this device", e)
            Toast.makeText(this, R.string.error_no_settings_app, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Hands a URL to whatever browser the user has.
     *
     * The app has no INTERNET permission and does not need one: firing an intent is not a
     * network operation, and the browser does the fetching.
     */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No browser available to open $url", e)
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
