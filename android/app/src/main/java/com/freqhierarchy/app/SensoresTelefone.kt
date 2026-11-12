package com.freqhierarchy.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import kotlin.math.sqrt

/** IMU (movimento do APARELHO) e RSSI do Wi-Fi conectado (série temporal). RSSI NÃO é CSI: é só a intensidade do sinal. */
class SensoresTelefone(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var linear = true
    private var gx = 0.0; private var gy = 0.0; private var gz = 0.0
    private var ema = 0.0
    @Volatile var imuMov = 0.0
        private set
    @Volatile var temImu = false
        private set
    private val rssi = ArrayList<Pair<Long, Double>>()

    fun iniciar() {
        val s = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER).also { linear = false }
        if (s != null) { temImu = true; sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun parar() { sm.unregisterListener(this) }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        var x = e.values[0].toDouble(); var y = e.values[1].toDouble(); var z = e.values[2].toDouble()
        if (!linear) {                                   // acelerômetro cru: tira a gravidade com filtro passa-baixa
            gx = 0.9 * gx + 0.1 * x; gy = 0.9 * gy + 0.1 * y; gz = 0.9 * gz + 0.1 * z
            x -= gx; y -= gy; z -= gz
        }
        ema += 0.1 * (sqrt(x * x + y * y + z * z) - ema)
        imuMov = movimentoDoAparelho(ema)
    }

    /** Chamar ~1x por segundo (UI thread). Guarda 20 s de RSSI do Wi-Fi conectado. */
    @Suppress("DEPRECATION")
    fun amostrarRssi(agoraMs: Long = System.currentTimeMillis()) {
        val v = try { wm?.connectionInfo?.rssi } catch (e: Exception) { null } ?: return
        if (v !in -126..-1) return
        synchronized(rssi) {
            rssi.add(agoraMs to v.toDouble())
            while (rssi.isNotEmpty() && agoraMs - rssi[0].first > 20000) rssi.removeAt(0)
        }
    }

    /** Desvio-padrão (dB) do RSSI recente, ou null se não há Wi-Fi/amostras suficientes (N3 fica "sem sensor"). */
    fun rssiDesvio(): Double? = synchronized(rssi) { desvio(rssi.map { it.second }) }

    companion object {
        /** Aceleração linear média (m/s²) -> 0..1. Abaixo de 0,3 é mão parada. */
        fun movimentoDoAparelho(acel: Double): Double = maxOf(0.0, minOf(1.0, (acel - 0.3) / 2.0))

        fun desvio(v: List<Double>): Double? {
            if (v.size < 5) return null
            val m = v.average()
            return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }
    }
}
