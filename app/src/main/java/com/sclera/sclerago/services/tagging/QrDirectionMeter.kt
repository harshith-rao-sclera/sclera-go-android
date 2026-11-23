package com.sclera.sclerago.services.tagging

import android.content.Context
import android.os.SystemClock
import com.sclera.sclerago.domain.direction.ElapsedClock
import com.sclera.sclerago.domain.direction.QrDirectionSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One scan direction measurement: the rotation-vector sensor and the [session] that turns frames into a direction. The sensor
 * listens only between [resume] and [pause] (the screen resume and pause) and is released by [close].
 */
class QrDirectionMeter internal constructor(private val sensor: RotationVectorSensor, val session: QrDirectionSession) : AutoCloseable {
    /** Whether the device can measure a direction at all (it has a rotation-vector sensor). */
    val isAvailable: Boolean get() = sensor.isAvailable

    fun resume() = sensor.start()

    fun pause() {
        sensor.stop()
        session.reset()
    }

    override fun close() = pause()
}

/** Makes a [QrDirectionMeter] for each scan. */
interface QrDirectionMeterFactory {
    fun create(): QrDirectionMeter
}

/** Builds meters on the device sensors, the Camera2 calibration of the back camera and the declination at the last known location. */
@Singleton
class AndroidQrDirectionMeterFactory @Inject constructor(@ApplicationContext private val context: Context) : QrDirectionMeterFactory {
    private val calibration by lazy { BackCameraCalibration(context) }

    override fun create(): QrDirectionMeter {
        val sensor = RotationVectorSensor(context)
        val session = QrDirectionSession(sensor, calibration, LocationDeclination(context), ElapsedClock { SystemClock.elapsedRealtimeNanos() })
        return QrDirectionMeter(sensor, session)
    }
}
