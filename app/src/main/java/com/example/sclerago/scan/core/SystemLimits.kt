//
//  SystemLimits.kt
//  AssetOnBoard
//
//  Reads the process file-descriptor ceiling so the scanner can size its fan-out.
//
//  A subnet scan is almost entirely blocked on sockets waiting to time out, so
//  throughput is bounded by how many can be open at once. Unlike iOS — where the
//  app raises RLIMIT_NOFILE itself at launch — Android exposes no setrlimit(2)
//  binding to managed code, so the limit is read rather than raised. Zygote hands
//  apps a soft limit of 1024 on modern releases and 32768 on some vendor builds,
//  which is comfortably more than iOS's stock 256.
//

package com.example.sclerago.scan.core

import java.io.File

object SystemLimits {

    /** Conservative floor used when /proc is unreadable. */
    private const val ASSUMED_LIMIT = 1024

    private val cachedLimit: Int by lazy { readSoftLimit() ?: ASSUMED_LIMIT }

    val fileDescriptorLimit: Int get() = cachedLimit

    /**
     * Parses the soft `Max open files` row out of /proc/self/limits.
     *
     * This file is per-process and always readable by its owner, so it survives
     * the /proc hardening that closed off the network tables.
     */
    private fun readSoftLimit(): Int? = try {
        File("/proc/self/limits").useLines { lines ->
            lines.firstOrNull { it.startsWith("Max open files") }
                ?.removePrefix("Max open files")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.let { if (it == "unlimited") 32_768 else it.toIntOrNull() }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Forces the /proc read now rather than on the first probe.
     *
     * Called once at launch, where the iOS build calls setrlimit(2).
     */
    fun warmUp() {
        check(fileDescriptorLimit > 0)
    }

    /**
     * Concurrent socket budget, leaving generous headroom for the rest of the app.
     *
     * Each in-flight probe costs more than one descriptor once the JVM's NIO
     * bookkeeping is counted, so this divides rather than subtracts.
     *
     * A quarter, where the iOS build takes an eighth. That build raises its own
     * ceiling to 16384 first, so an eighth still clears every depth's request;
     * Android cannot raise it, and an eighth of the stock 1024 is 128 — below
     * what Standard and Deep ask for, which silently flattened all three depths
     * to the same concurrency. A quarter restores the intended tiers and still
     * spends no more than 256 of 1024 descriptors on sockets.
     */
    fun socketBudget(preferred: Int): Int =
        maxOf(16, minOf(preferred, fileDescriptorLimit / 4))
}
