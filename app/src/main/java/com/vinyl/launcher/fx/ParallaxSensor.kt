package com.vinyl.launcher.fx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pembaca sensor gerak untuk parallax. Memakai TYPE_GAME_ROTATION_VECTOR (giroskop + akselerometer, tanpa
 * magnetometer) bila ada; bila tidak, TYPE_GRAVITY lalu TYPE_ACCELEROMETER. Tidak butuh izin apa pun: sensor gerak
 * standar tidak dilindungi izin, dan frekuensi 50 Hz jauh di bawah batas 200 Hz tanpa izin HIGH_SAMPLING_RATE_SENSORS.
 *
 * Hidup-matinya sensor dikendalikan pemanggil ([start]/[stop]) mengikuti lifecycle: terdaftar hanya saat layar
 * utama terlihat dan parallax aktif, sehingga tidak menguras baterai di latar belakang.
 *
 * Seluruh callback datang di thread utama (listener didaftarkan tanpa Handler), jadi [tilt] aman dibaca dari
 * fase gambar Compose tanpa sinkronisasi tambahan.
 *
 * @param displayRotation putaran layar saat ini (Display.getRotation(): 0..3); dibaca pada tiap sampel.
 */
class ParallaxSensor(context: Context, private val displayRotation: () -> Int) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gravitySensor: Sensor? =
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val filter = ParallaxFilter()
    private var lastNs = 0L
    private var running = false

    /** Ada sensor yang bisa dipakai. */
    val available: Boolean get() = rotationSensor != null || gravitySensor != null

    /** True bila giroskop (rotation vector) dipakai; false = hanya gravitasi, gerak kiri-kanan lebih lemah. */
    val hasGyro: Boolean get() = rotationSensor != null

    /** Posisi mata terhadap layar (lihat [Tilt]). Dibaca di fase gambar agar hanya menggambar ulang, bukan komposisi ulang. */
    var tilt by mutableStateOf(Tilt.ZERO)
        private set

    /** Mulai mendengarkan. Sampel pertama menjadi pose netral. Mengembalikan false bila tidak ada sensor. */
    fun start(): Boolean {
        if (running) return true
        val m = manager ?: return false
        val sensor = rotationSensor ?: gravitySensor ?: return false
        filter.reset()
        lastNs = 0L
        running = m.registerListener(this, sensor, SAMPLING_US)
        return running
    }

    /** Berhenti dan kembalikan parallax ke netral. Aman dipanggil berulang. */
    fun stop() {
        if (!running) return
        manager?.unregisterListener(this)
        running = false
        tilt = Tilt.ZERO
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        val q: Quat? = when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> Quat.fromRotationVector(v[0], v[1], v[2], if (v.size >= 4) v[3] else Float.NaN)
            else -> Quat.fromGravity(v[0], v[1], v[2])
        }
        if (q == null) return
        val now = event.timestamp
        val dt = if (lastNs == 0L) 0f else (now - lastNs) / 1e9f
        lastNs = now
        filter.update(q, dt)
        tilt = filter.tilt(displayRotation())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** 50 Hz: cukup halus untuk parallax dan hemat daya. */
        const val SAMPLING_US = 20_000
    }
}

/** Pertanyaan kemampuan sensor tanpa mendengarkan apa pun (untuk Pengaturan). */
object MotionSensors {
    private fun manager(context: Context) = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** Ada sensor yang bisa dipakai parallax (rotation vector, gravitasi, atau akselerometer). */
    fun available(context: Context): Boolean {
        val m = manager(context) ?: return false
        return m.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null ||
            m.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    /** Ada sensor rotation vector (giroskop terpadu) sehingga gerak kiri-kanan terbaca penuh. */
    fun hasGyro(context: Context): Boolean = manager(context)?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null
}
