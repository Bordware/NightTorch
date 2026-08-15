package com.bordware.nighttorch

import android.app.Application
import android.content.Context
import com.bordware.nighttorch.di.AppContainer

/**
 * Application subclass that owns the [AppContainer].
 *
 * Both the UI and the accessibility service reach their shared singletons through here.
 * The accessibility service in particular has no other injection route.
 */
class NightTorchApp : Application() {

    /** Process-wide singletons. Valid from `onCreate` until the process dies. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Retrieves the shared [AppContainer] from any context.
 *
 * Works from an `AccessibilityService` as well as an `Activity`, which is the whole reason
 * the container hangs off the `Application` rather than being injected.
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as NightTorchApp).container
