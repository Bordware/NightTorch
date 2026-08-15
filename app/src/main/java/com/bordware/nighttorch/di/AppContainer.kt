package com.bordware.nighttorch.di

import android.content.Context
import com.bordware.nighttorch.data.SettingsRepository
import com.bordware.nighttorch.service.AccessibilityStatusMonitor
import com.bordware.nighttorch.torch.TorchController

/**
 * Hand-rolled dependency container holding the app's process-wide singletons.
 *
 * There is no Hilt here on purpose. `AccessibilityService` is not supported by
 * `@AndroidEntryPoint`, so injecting into the single most important class in the app would
 * need `EntryPointAccessors` boilerplate anyway, and KSP slows reproducible F-Droid builds.
 * For an app this size the container is smaller than the setup Hilt would require.
 *
 * Sharing [torchController] is a correctness requirement rather than a convenience: the
 * accessibility service and the UI must observe the same `TorchState`, and each
 * `TorchController` registers its own system torch callback.
 *
 * @param context the application context. Anything shorter-lived would leak.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * The single [TorchController] for the process.
     *
     * Constructed lazily so that enumerating cameras and reading flash characteristics
     * happens on first use rather than in `Application.onCreate`, keeping cold start
     * cheap for launches that never touch the torch.
     */
    val torchController: TorchController by lazy { TorchController(appContext) }

    /**
     * The single [SettingsRepository] for the process.
     *
     * Must not be duplicated: constructing two `DataStore` instances over the same file
     * throws at runtime, and the underlying store is a top-level delegate precisely so
     * there is exactly one.
     */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** Watches whether the user has the accessibility service switched on. */
    val accessibilityStatusMonitor: AccessibilityStatusMonitor by lazy {
        AccessibilityStatusMonitor(appContext)
    }
}
