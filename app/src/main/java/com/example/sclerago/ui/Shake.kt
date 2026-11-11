package com.example.sclerago.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/**
 * Detects a deliberate shake: two separate jolts above ~2.0 g within 1.2 seconds.
 * A single bump (setting the phone down, walking), however long, does not trigger it.
 */
private class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var firstJolt = 0L
    private var lastJolt = 0L
    private var jolts = 0
    private var lastTrigger = 0L
    private var above = false

    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0] / SensorManager.GRAVITY_EARTH
        val gy = e.values[1] / SensorManager.GRAVITY_EARTH
        val gz = e.values[2] / SensorManager.GRAVITY_EARTH
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        // Count rising edges only: a jolt ends when force drops back near resting (1 g),
        // so one long bump can never count twice.
        if (above) {
            if (g < RELEASE_G) above = false
            return
        }
        if (g < THRESHOLD_G) return
        above = true
        val now = System.currentTimeMillis()
        if (now - lastJolt < MIN_GAP_MS) return
        if (now - firstJolt > WINDOW_MS) { jolts = 0; firstJolt = now }
        lastJolt = now
        jolts++
        if (jolts >= JOLTS && now - lastTrigger > COOLDOWN_MS) {
            jolts = 0
            lastTrigger = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val THRESHOLD_G = 2.0f
        const val RELEASE_G = 1.4f
        const val MIN_GAP_MS = 100L
        const val WINDOW_MS = 1200L
        const val JOLTS = 2
        const val COOLDOWN_MS = 1500L
    }
}

/** Listens for shakes while the app is resumed (in the foreground). */
@Composable
fun ShakeEffect(onShake: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(owner) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector { callback() }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> accel?.let { sm.registerListener(detector, it, SensorManager.SENSOR_DELAY_GAME) }
                Lifecycle.Event.ON_PAUSE -> sm.unregisterListener(detector)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            sm.unregisterListener(detector)
        }
    }
}
