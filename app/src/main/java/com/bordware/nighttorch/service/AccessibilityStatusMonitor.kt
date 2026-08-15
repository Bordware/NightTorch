package com.bordware.nighttorch.service

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports whether [FlashlightAccessibilityService] is currently enabled.
 *
 * Reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` directly rather than calling
 * `AccessibilityManager.getEnabledAccessibilityServiceList()`, which has historically
 * returned stale results on some OEM builds (docs/architecture.md).
 *
 * @param context any context; the application context is retained.
 */
class AccessibilityStatusMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val expected = ComponentName(appContext, FlashlightAccessibilityService::class.java)

    /**
     * Emits the current enabled state, and again whenever the user changes it.
     *
     * Backed by a `ContentObserver` so the UI updates the moment the user flips the switch
     * in Settings, without waiting for them to navigate back. The screen should still
     * re-check on resume as a belt-and-braces measure — see [isEnabled].
     */
    val isEnabledFlow: Flow<Boolean> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(isEnabled())
            }
        }

        appContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        trySend(isEnabled())

        awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    /**
     * Reads the enabled state right now.
     *
     * The stored value is a colon-separated list of flattened component names, and the two
     * flattened forms both occur in the wild: `com.example/com.example.Svc` and the short
     * `com.example/.Svc`. Observed on a real device, where one app's entry was stored long
     * and another's short. [ComponentName.unflattenFromString] normalises both, which is
     * why this parses rather than doing a substring match — a naive `contains` check on the
     * long form silently reports "disabled" for a service the user has actually enabled.
     */
    fun isEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(SERVICE_SEPARATOR)
            .asSequence()
            .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
            .any { it.matchesExpected() }
    }

    /**
     * Compares case-insensitively. Package and class names are case-sensitive in Java, but
     * the setting is user-editable state written by many parties, so this is deliberately
     * lenient rather than risking a false "disabled".
     */
    private fun ComponentName.matchesExpected(): Boolean =
        packageName.equals(expected.packageName, ignoreCase = true) &&
            className.equals(expected.className, ignoreCase = true)

    private companion object {
        const val SERVICE_SEPARATOR = ':'
    }
}
