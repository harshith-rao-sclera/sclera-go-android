//
//  NameResolution.kt
//  AssetOnBoard
//
//  Reverse DNS lookups.
//
//  Android's resolver answers in-addr.arpa queries for the local subnet through
//  the router's DNS and, since Android 12, through its own mDNS responder, so
//  this one call recovers names for Macs, Linux boxes and most IoT devices as
//  well as anything the router knows about.
//
//  getCanonicalHostName() has no timeout parameter and blocks on the resolver,
//  so every call is wrapped in withTimeoutOrNull; the abandoned lookup finishes
//  on its own thread and is discarded.
//

package com.example.sclerago.scan.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object NameResolution {

    /** Resolves [address] to a hostname, or null when nothing answers in time. */
    suspend fun reverseLookup(address: IPv4, timeout: Double = 2.0): String? =
        withTimeoutOrNull((timeout * 1000).toLong()) {
            withContext(Dispatchers.IO) {
                val name = try {
                    address.toInetAddress().canonicalHostName
                } catch (_: Exception) {
                    return@withContext null
                }
                // The resolver echoes the literal when it has nothing better.
                if (name.isNullOrEmpty() || name == address.toString()) null else tidy(name)
            }
        }

    /** Strips the trailing dot for display. */
    fun tidy(hostname: String): String = hostname.removeSuffix(".")

    /** The bare device name, without any DNS domain. */
    fun shortName(hostname: String): String =
        tidy(hostname).substringBefore('.').ifEmpty { hostname }
}
