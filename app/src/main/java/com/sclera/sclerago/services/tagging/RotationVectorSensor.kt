package com.sclera.sclerago.services.tagging

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sclera.sclerago.domain.direction.AttitudeSource
import com.sclera.sclerago.domain.direction.TimedAttitude

/**
 * The device rotation-vector sensor: keeps the latest reading, stamped with the sensor clock (elapsed-realtime nanoseconds), and
 * the accuracy it reports. It listens only between [start] and [stop]. Readings are never logged or stored.
 */
class RotationVectorSensor(context: Context) : AttitudeSource, SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var registered = false

    @Volatile
    private var sample: TimedAttitude? = null

    @Volatile
    override var accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
        private set

    /** Whether this device has a rotation-vector sensor. */
    val isAvailable: Boolean get() = sensor != null

    override fun latest(): TimedAttitude? = sample

    @Synchronized
    fun start() {
        val m = manager ?: return
        val s = sensor ?: return
        if (!registered) registered = m.registerListener(this, s, SAMPLING_PERIOD_US)
    }

    @Synchronized
    fun stop() {
        if (registered) runCatching { manager?.unregisterListener(this) }
        registered = false
        sample = null
        accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        val size = if (event.values.size >= 4) 4 else 3
        sample = TimedAttitude(event.timestamp, DoubleArray(size) { event.values[it].toDouble() })
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        this.accuracy = accuracy
    }

    private companion object {
        /** 200 Hz: the fastest rate that needs no HIGH_SAMPLING_RATE_SENSORS permission. */
        const val SAMPLING_PERIOD_US = 5_000
    }
}
