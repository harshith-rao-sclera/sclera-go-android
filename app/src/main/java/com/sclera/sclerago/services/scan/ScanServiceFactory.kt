package com.sclera.sclerago.services.scan

import android.app.Application
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the [ScanService] for a ViewModel's scope, so ViewModels don't construct it (or touch the Application) themselves. */
@Singleton
class ScanServiceFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(scope: CoroutineScope): ScanService = ScanService(context.applicationContext as Application, scope)
}
