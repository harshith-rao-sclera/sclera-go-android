package com.example.sclerago

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt's entry point: builds the app-wide dependency graph (see di/). */
@HiltAndroidApp
class ScleraApplication : Application()
