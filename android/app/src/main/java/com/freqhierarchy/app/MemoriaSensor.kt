package com.freqhierarchy.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Memória local resumida das sessões do sensor: um "piso de ruído" de movimento que é descontado depois.
 *  Ajuda quando o ambiente é parecido; NUNCA fabrica CSI nem finge leitura ao vivo. */
class MemoriaSensor(private val arq: File) {
    data class Sessao(val data: String, val fonte: String, val segundos: Int, val fMedio: Double, val movBase: Double, val brilho: Double)

    var baselineMov = 0.0
        private set
    var sessoes: MutableList<Sessao> = mutableListOf()
        private set

    fun carregar(): MemoriaSensor {
        try {
            val o = JSONObject(arq.readText(Charsets.UTF_8))
            baselineMov = o.optDouble("baseline_mov", 0.0)
            val a = o.optJSONArray("sessoes") ?: JSONArray()
            sessoes = (0 until a.length()).map {
                val s = a.getJSONObject(it)
                Sessao(s.getString("data"), s.getString("fonte"), s.getInt("segundos"), s.getDouble("f_medio"), s.getDouble("mov_base"), s.getDouble("brilho"))
            }.toMutableList()
        } catch (e: Exception) { baselineMov = 0.0; sessoes = mutableListOf() }
        return this
    }

    fun salvar() {
        val a = JSONArray()
        for (s in sessoes) a.put(JSONObject().put("data", s.data).put("fonte", s.fonte).put("segundos", s.segundos)
            .put("f_medio", s.fMedio).put("mov_base", s.movBase).put("brilho", s.brilho))
        val o = JSONObject().put("baseline_mov", baselineMov).put("sessoes", a)
        try { arq.parentFile?.mkdirs(); arq.writeText(o.toString(), Charsets.UTF_8) } catch (_: Exception) {}
    }

    /** Só guarda sessões com pelo menos 30 s. A base é suavizada (70% antiga + 30% nova) e limitada a 0..0,3. */
    fun registrar(s: Sessao): Boolean {
        if (s.segundos < 30) return false
        val nova = s.movBase.coerceIn(0.0, 0.3)
        baselineMov = if (sessoes.isEmpty()) nova else 0.7 * baselineMov + 0.3 * nova
        sessoes.add(s.copy(movBase = nova))
        if (sessoes.size > 30) sessoes = sessoes.takeLast(30).toMutableList()
        return true
    }

    fun descontar(mov: Double): Double = maxOf(0.0, mov - baselineMov)

    companion object {
        /** Piso de ruído: percentil 20 dos movimentos medidos com o aparelho firme. */
        fun piso(movs: List<Double>): Double {
            if (movs.isEmpty()) return 0.0
            val o = movs.sorted()
            return o[(o.size * 0.2).toInt().coerceIn(0, o.size - 1)]
        }
    }
}
