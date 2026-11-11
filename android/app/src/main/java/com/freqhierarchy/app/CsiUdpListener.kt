package com.freqhierarchy.app

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class CsiPacote(val amplitude: Double, val confianca: Double, val movimento: Double, val respiracao: Double, val rssi: Double)

object CsiParser {
    /** Valida e normaliza um pacote JSON do ESP32. Devolve null se estiver quebrado (nunca levanta). */
    fun parse(texto: String): CsiPacote? = try {
        val o = JSONObject(texto)
        fun v(k: String): Double? {
            val d = o.optDouble(k, 0.0)
            return if (d.isNaN() || d.isInfinite()) null else maxOf(0.0, minOf(1.0, d))
        }
        val a = v("amplitude"); val c = v("confianca"); val m = v("movimento"); val r = v("respiracao")
        if (a == null || c == null || m == null || r == null) null
        else CsiPacote(a, c, m, r, o.optDouble("rssi", 0.0))
    } catch (e: Exception) {
        null
    }
}

/** Escuta UDP na porta 5005 numa thread própria. O celular não lê CSI bruto: o ESP32 mede e envia para cá. */
class CsiUdpListener(private val porta: Int = Pesos.PORTA_CSI, private val aoReceber: (CsiPacote) -> Unit, private val aoErro: (String) -> Unit) {
    @Volatile private var rodando = false
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun iniciar() {
        if (rodando) return
        rodando = true
        thread = Thread {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(java.net.InetSocketAddress(porta))
                s.soTimeout = 1000
                socket = s
                val buf = ByteArray(4096)
                while (rodando) {
                    try {
                        val dp = DatagramPacket(buf, buf.size)
                        s.receive(dp)
                        CsiParser.parse(String(dp.data, 0, dp.length, Charsets.UTF_8))?.let(aoReceber)
                    } catch (_: SocketTimeoutException) {
                    }
                }
                s.close()
            } catch (e: Exception) {
                aoErro(e.message ?: e.javaClass.simpleName)
            }
            rodando = false
        }.also { it.isDaemon = true; it.start() }
    }

    fun parar() {
        rodando = false
        socket?.close()
    }

    companion object {
        /** IP do celular na rede local (é este IP que vai no firmware do ESP32). */
        fun ipLocal(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
