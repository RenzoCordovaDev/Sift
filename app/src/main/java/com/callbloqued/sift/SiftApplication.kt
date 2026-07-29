package com.callbloqued.sift

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for Sift.
 *
 * Annotating with [HiltAndroidApp] triggers Hilt's code generation and bootstraps
 * the application-level dependency injection component.  All singleton-scoped
 * bindings (Room database, DataStore, repository implementations) are rooted here.
 */
@HiltAndroidApp
class SiftApplication : Application()
