package com.neo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Neo.
 * Required for Hilt dependency injection.
 */
@HiltAndroidApp
class NeoApplication : Application()
