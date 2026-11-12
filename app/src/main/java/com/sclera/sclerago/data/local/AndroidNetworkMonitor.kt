package com.sclera.sclerago.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sclera.sclerago.domain.repository.NetworkMonitor

/** Connectivity check moved unchanged from the login screen. */
class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    override fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
