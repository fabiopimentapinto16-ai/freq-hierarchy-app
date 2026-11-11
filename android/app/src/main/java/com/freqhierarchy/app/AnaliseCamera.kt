package com.freqhierarchy.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Matemática do modo Câmera (sem Android, testável). A câmera vê luz visível: mede movimento e variações
 *  lentas de brilho. Respiração e pulso são ESTIMATIVAS; nada aqui lê consciência nem infravermelho térmico. */
object AnaliseCamera {
    data class Pico(val freq: Double, val razao: Double)

    private fun clip01(x: Double) = maxOf(0.0, minOf(1.0, x))

    /** Diferença média absoluta (0..255) entre duas grades de luminância do mesmo tamanho. */
    fun diferenca(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var s = 0.0
        for (i in a.indices) s += abs(a[i] - b[i])
        return s / a.size
    }

    /** Movimento 0..1 a partir da diferença média (ruído do sensor ~1 nível é descontado). */
    fun movimento(diferenca: Double): Double = clip01((diferenca - 1.0) / 8.0)

    /** Confiança 0..1: cena muito escura ou estourada não serve. */
    fun confianca(luminanciaMedia: Double): Double = if (luminanciaMedia in 30.0..230.0) 0.9 else 0.3

    /** Procura o pico de energia (Goertzel) entre lo..hi Hz. razao = energia do pico / MEDIANA da energia da banda
     *  (ruído puro fica abaixo de ~25; uma oscilação regular passa de 50). Precisa de pelo menos 8 s de série. */
    fun picoNaBanda(serie: DoubleArray, fs: Double, lo: Double, hi: Double, passo: Double = 0.02): Pico {
        val n = serie.size
        if (fs <= 0 || n < fs * 8) return Pico(0.0, 0.0)
        val media = serie.average()
        val x = DoubleArray(n) { (serie[it] - media) * (0.5 - 0.5 * cos(2 * PI * it / (n - 1))) }   // tira tendência + janela de Hann
        var melhorF = 0.0; var melhorP = 0.0; val ps = ArrayList<Double>()
        var f = lo
        while (f <= hi + 1e-9) {
            val w = 2 * PI * f / fs
            val c = 2 * cos(w)
            var s1 = 0.0; var s2 = 0.0
            for (v in x) { val s0 = v + c * s1 - s2; s2 = s1; s1 = s0 }
            val p = s1 * s1 + s2 * s2 - c * s1 * s2
            ps.add(p)
            if (p > melhorP) { melhorP = p; melhorF = f }
            f += passo
        }
        ps.sort()
        val mediana = if (ps.isEmpty()) 0.0 else ps[ps.size / 2]
        return if (mediana <= 0.0) Pico(0.0, 0.0) else Pico(melhorF, melhorP / mediana)
    }

    const val RAZAO_MIN = 30.0    // abaixo disso, tratamos como "sem oscilação clara" (calibrado: ruído puro máx ~24)

    /** Pontuação 0..1 de quão clara é a oscilação (usada como amplitude quando a pessoa está parada). */
    fun clareza(p: Pico): Double = if (p.razao < RAZAO_MIN) 0.0 else clip01((p.razao - RAZAO_MIN) / 100.0)

    /** F do N1-câmera: maior entre movimento e clareza da respiração. Os demais níveis ficam "sem sensor". */
    fun fLocais(movimento: Double, resp: Pico, luminanciaMedia: Double): List<Double?> =
        listOf(HierarchyEngine.forca(maxOf(movimento, clareza(resp)), confianca(luminanciaMedia)), null, null, null, null)
}
