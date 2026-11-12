package com.freqhierarchy.app

/** Espelho de app/core/hierarquia.py. Os números vêm de config/pesos.py (testado com vetores/hierarquia.json). */
object Pesos {
    const val CORTE_HERANCA = 0.15
    const val LIMIAR_K8 = 0.35
    const val LIMIAR_PRESENCA = 0.30
    const val DIF_ANOMALIA = 0.40
    const val PORTA_CSI = 5005
    const val LIM_TIPO_RAPIDO = 0.35
    const val LIM_IMU_APARELHO = 0.5
    const val K8_SALTO_MIN = 0.2
    const val K8_SALTO_ESCALA = 0.6
    const val RSSI_ESCALA_DB = 6.0
    const val CONF_RSSI = 0.4

    data class Nivel(val id: String, val nome: String, val wProprio: Double, val wHeranca: Double, val hex: String)

    val NIVEIS = listOf(
        Nivel("N1", "CSI 2.4 GHz", 1.00, 0.00, "#2979FF"),
        Nivel("N2", "CSI 5.0 GHz", 0.60, 0.40, "#00BCD4"),
        Nivel("N3", "Micro-ondas", 0.55, 0.45, "#43A047"),
        Nivel("N4", "Infravermelho", 0.55, 0.45, "#E53935"),
        Nivel("N5", "K8 Anomalia", 0.50, 0.50, "#8E24AA"),
    )

    /** Modo TELEFONE PURO (sem CSI): mesmos pesos, outras fontes. Tudo ESTIMADO. Espelha NIVEIS_TELEFONE do Python. */
    val NIVEIS_TELEFONE = listOf(
        Nivel("N1", "Câmera (movimento óptico)", 1.00, 0.00, "#2979FF"),
        Nivel("N2", "IMU (movimento do aparelho)", 0.60, 0.40, "#00BCD4"),
        Nivel("N3", "Wi-Fi RSSI (contexto)", 0.55, 0.45, "#43A047"),
        Nivel("N4", "Áudio ambiente", 0.55, 0.45, "#E53935"),
        Nivel("N5", "K8 Anomalia", 0.50, 0.50, "#8E24AA"),
    )
}

data class NivelResultado(
    val id: String, val nome: String, val hex: String,
    val fLocal: Double?, val fIntegrada: Double, val herdou: Boolean, val disponivel: Boolean,
)

data class Resultado(val niveis: List<NivelResultado>, val fFinal: Double, val status: String, val simulado: Boolean)

object HierarchyEngine {
    private fun clip(x: Double, a: Double = 0.0, b: Double = 1.0) = maxOf(a, minOf(b, x))
    private fun round4(x: Double) = Math.round(x * 10000.0) / 10000.0

    /** F = amplitude x confiança, limitada a 0..1. */
    fun forca(amplitude: Double, confianca: Double) = clip(clip(amplitude) * clip(confianca))

    /** fLocais: 5 valores (N1..N5). null = nível sem sensor: não inventamos, repete o anterior e marca indisponível. */
    fun integrar(fLocais: List<Double?>, simulado: Boolean = false, niveis: List<Pesos.Nivel> = Pesos.NIVEIS): Resultado {
        require(fLocais.size == niveis.size) { "preciso de 5 valores (N1..N5)" }
        val res = ArrayList<NivelResultado>()
        var prev = 0.0
        for ((i, cfg) in niveis.withIndex()) {
            val fl = fLocais[i]
            val disp = fl != null
            val flC = fl?.let { clip(it) }
            var f: Double
            var herdou = false
            if (i == 0) {
                f = flC ?: 0.0
            } else if (!disp) {
                f = prev; herdou = true
            } else if (cfg.id == "N5" && flC!! < Pesos.LIMIAR_K8) {
                f = prev; herdou = true                                  // regra K8
            } else {
                val wh = if (prev >= Pesos.CORTE_HERANCA) cfg.wHeranca else 0.0
                f = cfg.wProprio * flC!! + wh * prev
                herdou = wh > 0
            }
            f = clip(f)
            res.add(NivelResultado(cfg.id, cfg.nome, cfg.hex, flC, round4(f), herdou, disp))
            prev = f
        }
        return Resultado(res, round4(prev), status(res, prev), simulado)
    }

    private fun status(n: List<NivelResultado>, fFinal: Double): String {
        val k8 = n.last()
        val ant = n[n.size - 2].fIntegrada
        val k = k8.fLocal
        if (k8.disponivel && k != null && k >= Pesos.LIMIAR_K8) {
            if (ant < Pesos.LIMIAR_PRESENCA || Math.abs(k - ant) > Pesos.DIF_ANOMALIA) return "anomalia"
        }
        return if (fFinal >= Pesos.LIMIAR_PRESENCA) "presenca" else "vazio"
    }

    /** Quando o ESP32 já manda movimento e respiração (0..1): classificação direta, sem FFT. */
    fun tipoRapido(movimento: Double, respiracao: Double): String {
        val m = movimento >= Pesos.LIM_TIPO_RAPIDO
        val r = respiracao >= Pesos.LIM_TIPO_RAPIDO
        return if (m && r) "ambos" else if (m) "movimento" else if (r) "respiracao" else "quieto"
    }

    /** Só o N1 (CSI 2.4 GHz) existe num ESP32. N2..N5 ficam "sem sensor". */
    fun fLocaisDoPacote(p: CsiPacote): List<Double?> = listOf(forca(p.amplitude, p.confianca), null, null, null, null)
}

/** Espelho de Suavizador (Python): média móvel exponencial por nível; null (sem sensor) continua null. */
class Suavizador(private val alpha: Double = 0.3) {
    init { require(alpha > 0.0 && alpha <= 1.0) { "alpha entre 0 (exclusive) e 1" } }
    private var v: Array<Double?> = emptyArray()

    fun atualizar(f: List<Double?>): List<Double?> {
        if (v.size != f.size) v = arrayOfNulls(f.size)
        return f.indices.map { i ->
            val x = f[i]
            v[i] = if (x == null) null else {
                val c = maxOf(0.0, minOf(1.0, x)); val a = v[i]
                if (a == null) c else a + alpha * (c - a)
            }
            v[i]
        }
    }
}
