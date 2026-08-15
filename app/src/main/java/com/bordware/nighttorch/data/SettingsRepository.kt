package com.bordware.nighttorch.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

/**
 * The one DataStore instance for the process.
 *
 * Declared as a top-level delegate because constructing two `DataStore`s over the same file
 * throws at runtime. [SettingsRepository] must therefore be a singleton — see `AppContainer`.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nighttorch_settings",
)

/**
 * Reads and writes [AppSettings].
 *
 * [settings] never throws on a read failure: a corrupt or unreadable store falls back to
 * [AppSettings.DEFAULT] rather than propagating, because the accessibility service collects
 * this flow and a crash there would silently kill the torch shortcut.
 *
 * Every write validates before storing, so an out-of-range value cannot be persisted even
 * if a caller passes one.
 *
 * @param context any context; the application context is retained.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /**
     * The current settings, re-emitting on every change.
     *
     * The accessibility service collects this into a `@Volatile` field rather than reading
     * DataStore on the key-event path — `onKeyEvent` must never touch disk. See docs/architecture.md.
     */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                Log.w(TAG, "Could not read settings, falling back to defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { it.toAppSettings() }

    /** Sets the time the night window opens. Seconds and nanoseconds are discarded. */
    suspend fun setNightStart(time: LocalTime) = putInt(Keys.NIGHT_START_MINUTES, time.toMinutes())

    /** Sets the time the night window closes. Seconds and nanoseconds are discarded. */
    suspend fun setNightEnd(time: LocalTime) = putInt(Keys.NIGHT_END_MINUTES, time.toMinutes())

    /** Sets the brightness used inside the night window. Clamped to `0..100`. */
    suspend fun setNightBrightnessPercent(percent: Int) =
        putInt(Keys.NIGHT_BRIGHTNESS_PERCENT, percent.coerceIn(AppSettings.PERCENT_RANGE))

    /** Sets the brightness used outside the night window. Clamped to `0..100`. */
    suspend fun setDayBrightnessPercent(percent: Int) =
        putInt(Keys.DAY_BRIGHTNESS_PERCENT, percent.coerceIn(AppSettings.PERCENT_RANGE))

    /** Enables or disables time-based brightness selection entirely. */
    suspend fun setAutoDimmingEnabled(enabled: Boolean) =
        putBoolean(Keys.AUTO_DIMMING_ENABLED, enabled)

    /** Marks first-run onboarding as finished or skipped. */
    suspend fun setOnboardingComplete(complete: Boolean) =
        putBoolean(Keys.ONBOARDING_COMPLETE, complete)

    private suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val default = AppSettings.DEFAULT
        return AppSettings(
            nightStart = this[Keys.NIGHT_START_MINUTES]?.toLocalTimeOrNull() ?: default.nightStart,
            nightEnd = this[Keys.NIGHT_END_MINUTES]?.toLocalTimeOrNull() ?: default.nightEnd,
            nightBrightnessPercent = this[Keys.NIGHT_BRIGHTNESS_PERCENT]
                ?.coerceIn(AppSettings.PERCENT_RANGE)
                ?: default.nightBrightnessPercent,
            dayBrightnessPercent = this[Keys.DAY_BRIGHTNESS_PERCENT]
                ?.coerceIn(AppSettings.PERCENT_RANGE)
                ?: default.dayBrightnessPercent,
            autoDimmingEnabled = this[Keys.AUTO_DIMMING_ENABLED] ?: default.autoDimmingEnabled,
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: default.onboardingComplete,
        )
    }

    private object Keys {
        val NIGHT_START_MINUTES = intPreferencesKey("night_start_minutes")
        val NIGHT_END_MINUTES = intPreferencesKey("night_end_minutes")
        val NIGHT_BRIGHTNESS_PERCENT = intPreferencesKey("night_brightness_percent")
        val DAY_BRIGHTNESS_PERCENT = intPreferencesKey("day_brightness_percent")
        val AUTO_DIMMING_ENABLED = booleanPreferencesKey("auto_dimming_enabled")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}

