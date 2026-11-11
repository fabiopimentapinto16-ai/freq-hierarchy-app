package com.freqhierarchy.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Espelho de app/core/consciencia.py (testado com vetores/consciencia.json).
 *  O nível é autoavaliação e prática. O radar CSI mede o corpo, não a consciência. */
object Consciencia {
    const val AVISO = "Este nível é autoavaliação e prática. O radar CSI mede o corpo, não a consciência."
    const val AVISO_CUIDADO = "Não é terapia nem diagnóstico. Se estiver sofrendo muito, converse com alguém de confiança ou procure um profissional de saúde."
    const val PRIVACIDADE = "Seus dados ficam só neste aparelho. Nada é enviado."
    const val PERGUNTA = "Como você está por dentro agora?"
    const val NIVEL_MAX = 7
    const val PRATICAS_PARA_SUBIR = 7
    const val DIAS_PARA_SUBIR = 5
    const val MEDIA_PARA_SUBIR = 0.6

    data class Info(val n: Int, val nome: String, val descricao: String, val pratica: String)

    val NIVEIS = listOf(
        Info(1, "Reação", "Vive no automático; pouco registra emoção ou gatilho.", "3 vezes ao dia, pare 10 segundos e nomeie: o que estou sentindo?"),
        Info(2, "Percepção", "Começa a notar a emoção e o gatilho depois do fato.", "Diário de 1 linha: gatilho → emoção."),
        Info(3, "Observação", "Observa o padrão sem se fundir totalmente nele.", "Pausa de 30 segundos antes de responder em um conflito."),
        Info(4, "Escolha", "Consegue escolher a resposta em vez de só reagir.", "1 escolha consciente por dia (registre qual)."),
        Info(5, "Responsabilidade", "Assume a parte que é sua; menos culpa só no externo.", "Em 1 situação do dia, pergunte: o que é meu aqui?"),
        Info(6, "Coerência", "Pensamento, fala e ação mais alinhados.", "1 ato alinhado ao seu valor principal do dia."),
        Info(7, "Clareza e serviço", "Mais estável; usa a consciência para o bem próprio e do outro.", "1 ação útil e discreta por dia + revisão semanal."),
    )

    data class Checkin(val data: String, val reacao: Int, val pausou: Boolean, val pratica: Boolean, val nivel: Int, val score: Double, val nota: String = "")

    class Estado(var nivel: Int = 1, var entradaNivel: String = "", var checkins: MutableList<Checkin> = mutableListOf())

    data class Metricas(val praticas: Int, val dias7: Int, val media7: Double, val progresso: Double, val podeSubir: Boolean)
    data class Resposta(val score: Double, val subiu: Boolean, val nivel: Int, val metricas: Metricas, val mensagem: String)

    private fun r3(x: Double) = Math.round(x * 1000.0) / 1000.0

    fun scoreDoDia(reacao: Int, pausou: Boolean, pratica: Boolean): Double {
        val r = (5 - reacao.coerceIn(1, 5)) / 4.0
        return r3(0.4 * r + 0.3 * (if (pausou) 1.0 else 0.0) + 0.3 * (if (pratica) 1.0 else 0.0))
    }

    private fun noNivel(e: Estado) = e.checkins.filter { it.nivel == e.nivel && (e.entradaNivel.isEmpty() || it.data >= e.entradaNivel) }

    fun metricas(e: Estado, hoje: LocalDate = LocalDate.now()): Metricas {
        val lst = noNivel(e)
        val ini = hoje.minusDays(6).toString()
        val u7 = lst.filter { it.data >= ini && it.data <= hoje.toString() }
        val praticas = lst.count { it.pratica }
        val dias7 = u7.map { it.data }.toSet().size
        val media7 = if (u7.isNotEmpty()) u7.sumOf { it.score } / u7.size else 0.0
        val prog = minOf(praticas.toDouble() / PRATICAS_PARA_SUBIR, 1.0) * minOf(dias7.toDouble() / DIAS_PARA_SUBIR, 1.0) * minOf(media7 / MEDIA_PARA_SUBIR, 1.0)
        val pode = e.nivel < NIVEL_MAX && praticas >= PRATICAS_PARA_SUBIR && dias7 >= DIAS_PARA_SUBIR && media7 >= MEDIA_PARA_SUBIR
        return Metricas(praticas, dias7, r3(media7), Math.round(1000.0 * prog) / 10.0, pode)
    }

    /** Dias seguidos com check-in até hoje (ou ontem). Faltar um dia NÃO apaga o progresso do nível. */
    fun sequencia(e: Estado, hoje: LocalDate = LocalDate.now()): Int {
        val dias = e.checkins.map { it.data }.toSet()
        var d = hoje
        var n = 0
        if (d.toString() !in dias) d = d.minusDays(1)
        while (d.toString() in dias) { n++; d = d.minusDays(1) }
        return n
    }

    fun checkin(e: Estado, reacao: Int, pausou: Boolean, pratica: Boolean, hoje: LocalDate = LocalDate.now(), nota: String = ""): Resposta {
        if (e.entradaNivel.isEmpty()) e.entradaNivel = hoje.toString()
        e.checkins = e.checkins.filter { it.data != hoje.toString() }.toMutableList()
        val sc = scoreDoDia(reacao, pausou, pratica)
        e.checkins.add(Checkin(hoje.toString(), reacao.coerceIn(1, 5), pausou, pratica, e.nivel, sc, nota.take(280)))
        if (e.checkins.size > 400) e.checkins = e.checkins.takeLast(400).toMutableList()
        var m = metricas(e, hoje)
        var subiu = false
        if (m.podeSubir) {
            e.nivel += 1
            e.entradaNivel = hoje.plusDays(1).toString()      // o novo nível conta a partir de amanhã
            subiu = true
            m = metricas(e, hoje)
        }
        val msg = if (subiu) "Você subiu para o nível ${e.nivel} (${NIVEIS[e.nivel - 1].nome})."
        else if (e.nivel == NIVEL_MAX) "Nível máximo: mantenha a prática." else "Check-in registrado."
        return Resposta(sc, subiu, e.nivel, m, msg)
    }

    /** Autoavaliação: pode descer a qualquer nível; só sobe 1 por vez e só com as práticas em dia. */
    fun ajustarNivel(e: Estado, novo: Int, hoje: LocalDate = LocalDate.now()) {
        require(novo in 1..NIVEL_MAX) { "nível entre 1 e 7" }
        if (novo == e.nivel) return
        if (novo > e.nivel) {
            require(novo == e.nivel + 1) { "só é possível subir 1 nível por vez" }
            require(metricas(e, hoje).podeSubir) { "faltam práticas do nível atual para subir" }
        }
        e.nivel = novo
        e.entradaNivel = hoje.toString()
    }

    fun historico(e: Estado, dias: Int = 30, hoje: LocalDate = LocalDate.now()): List<Checkin> {
        val ini = hoje.minusDays((maxOf(1, dias) - 1).toLong()).toString()
        return e.checkins.filter { it.data >= ini }.sortedBy { it.data }
    }

    // ---- persistência local (mesmo formato JSON do Python)
    fun carregar(arq: File): Estado = try {
        val o = JSONObject(arq.readText(Charsets.UTF_8))
        val lista = mutableListOf<Checkin>()
        val a = o.optJSONArray("checkins") ?: JSONArray()
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            lista.add(Checkin(c.getString("data"), c.getInt("reacao"), c.getBoolean("pausou"), c.getBoolean("pratica"),
                c.getInt("nivel"), c.getDouble("score"), c.optString("nota", "")))
        }
        Estado(o.optInt("nivel", 1).coerceIn(1, NIVEL_MAX), o.optString("entrada_nivel", ""), lista)
    } catch (e: Exception) {
        Estado()
    }

    fun salvar(e: Estado, arq: File) {
        val a = JSONArray()
        for (c in e.checkins) a.put(JSONObject().put("data", c.data).put("reacao", c.reacao).put("pausou", c.pausou)
            .put("pratica", c.pratica).put("nivel", c.nivel).put("score", c.score).put("nota", c.nota))
        val o = JSONObject().put("nivel", e.nivel).put("entrada_nivel", e.entradaNivel).put("checkins", a)
        val tmp = File(arq.path + ".tmp")
        tmp.writeText(o.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(arq)) { arq.writeText(o.toString(), Charsets.UTF_8); tmp.delete() }
    }
}
