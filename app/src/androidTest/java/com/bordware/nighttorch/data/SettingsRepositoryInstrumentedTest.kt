package com.bordware.nighttorch.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Round-trips [AppSettings] through a real Preferences DataStore.
 *
 * DataStore needs an Android context and real file I/O, so this cannot be a JVM test. It
 * exists to prove the minutes-since-midnight encoding survives a write and read, which is
 * the part most likely to break silently.
 *
 * Uses the app's real store rather than a temporary one, because the DataStore delegate is
 * deliberately a single top-level instance. Each test therefore restores [AppSettings.DEFAULT]
 * afterwards, so a test run does not leave the installed app in a surprising state — in
 * particular it must not leave `onboardingComplete` set, which would skip first-run
 * onboarding on the next manual launch.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {

    private val repository = SettingsRepository(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @After
    fun restoreDefaults() = runBlocking {
        val default = AppSettings.DEFAULT
        repository.setNightStart(default.nightStart)
        repository.setNightEnd(default.nightEnd)
        repository.setNightBrightnessPercent(default.nightBrightnessPercent)
        repository.setDayBrightnessPercent(default.dayBrightnessPercent)
        repository.setAutoDimmingEnabled(default.autoDimmingEnabled)
        repository.setOnboardingComplete(default.onboardingComplete)
        assertEquals(default, repository.settings.first())
    }

    @Test
    fun timesSurviveARoundTripThroughTheStore() = runBlocking {
        repository.setNightStart(LocalTime.of(22, 45))
        repository.setNightEnd(LocalTime.of(5, 15))

        val stored = repository.settings.first()
        assertEquals(LocalTime.of(22, 45), stored.nightStart)
        assertEquals(LocalTime.of(5, 15), stored.nightEnd)
    }

    @Test
    fun midnightAndTheLastMinuteOfTheDayRoundTrip() = runBlocking {
        repository.setNightStart(LocalTime.of(0, 0))
        repository.setNightEnd(LocalTime.of(23, 59))

        val stored = repository.settings.first()
        assertEquals(LocalTime.of(0, 0), stored.nightStart)
        assertEquals(LocalTime.of(23, 59), stored.nightEnd)
    }

    @Test
    fun brightnessPercentagesAreClampedBeforeBeingStored() = runBlocking {
        repository.setNightBrightnessPercent(-20)
        repository.setDayBrightnessPercent(400)

        val stored = repository.settings.first()
        assertEquals(0, stored.nightBrightnessPercent)
        assertEquals(100, stored.dayBrightnessPercent)
    }

    @Test
    fun booleanFlagsRoundTrip() = runBlocking {
        repository.setAutoDimmingEnabled(false)
        repository.setOnboardingComplete(true)

        val afterFirstWrite = repository.settings.first()
        assertEquals(false, afterFirstWrite.autoDimmingEnabled)
        assertEquals(true, afterFirstWrite.onboardingComplete)

        repository.setAutoDimmingEnabled(true)
        assertEquals(true, repository.settings.first().autoDimmingEnabled)
    }

    @Test
    fun theFlowEmitsAgainAfterAWrite() = runBlocking {
        repository.setNightBrightnessPercent(5)
        assertEquals(5, repository.settings.first().nightBrightnessPercent)

        repository.setNightBrightnessPercent(60)
        assertEquals(60, repository.settings.first().nightBrightnessPercent)
    }
}
