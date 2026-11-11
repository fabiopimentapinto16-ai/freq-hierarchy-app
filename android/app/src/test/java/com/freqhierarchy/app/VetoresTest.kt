package com.freqhierarchy.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Lê os MESMOS vetores do Python (arquivos .json da pasta vetores): Kotlin e Python têm que dar o mesmo resultado. */
class VetoresTest {
    private fun ler(nome: String) = JSONArray(File(System.getProperty("vetores.dir"), nome).readText(Charsets.UTF_8))

    @Test fun hierarquiaIgualAoPython() {
        val arr = ler("hierarquia.json")
        assertTrue(arr.length() >= 8)
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val nome = v.getString("nome")
            val fl = v.getJSONArray("f_locais")
            val locais = (0 until fl.length()).map { if (fl.isNull(it)) null else fl.getDouble(it) }
            val r = HierarchyEngine.integrar(locais)
            assertEquals(nome, v.getString("status"), r.status)
            assertEquals(nome, v.getDouble("f_final"), r.fFinal, 1e-4)
            val esp = v.getJSONArray("f_integradas")
            for (k in 0 until esp.length()) assertEquals("$nome N${k + 1}", esp.getDouble(k), r.niveis[k].fIntegrada, 1e-4)
        }
    }

    @Test fun consciencia() {
        val arr = ler("consciencia.json")
        val e = Consciencia.Estado()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val r = Consciencia.checkin(e, v.getInt("reacao"), v.getBoolean("pausou"), v.getBoolean("pratica"), LocalDate.parse(v.getString("data")))
            val d = v.getString("data")
            assertEquals(d, v.getInt("nivel_depois"), e.nivel)
            assertEquals(d, v.getBoolean("subiu"), r.subiu)
            assertEquals(d, v.getDouble("score"), r.score, 1e-3)
        }
    }

    @Test fun semSensorNaoInventa() {
        val r = HierarchyEngine.integrar(listOf(0.6, null, null, null, null))
        assertNull(r.niveis[1].fLocal)
        assertTrue(!r.niveis[1].disponivel)
    }

    @Test fun naoSobeDoisNiveis() {
        try { Consciencia.ajustarNivel(Consciencia.Estado(), 3); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun pacoteQuebradoNaoLevanta() {
        assertNull(CsiParser.parse("lixo"))
        val p = CsiParser.parse("""{"amplitude":2,"confianca":0.5,"movimento":0.1,"respiracao":0.2}""")!!
        assertEquals(1.0, p.amplitude, 0.0)
    }

    @Test fun suavizadorIgualAoPython() {
        val s = Suavizador(0.5)
        s.atualizar(listOf(0.0))
        assertEquals(0.5, s.atualizar(listOf(1.0))[0]!!, 1e-9)
        val t = Suavizador(0.3)
        t.atualizar(listOf(0.5, 0.5))
        assertNull(t.atualizar(listOf(0.5, null))[1])
        assertEquals(0.9, t.atualizar(listOf(0.5, 0.9))[1]!!, 1e-9)
    }

    @Test fun persistenciaIdaEVolta() {
        val e = Consciencia.Estado()
        Consciencia.checkin(e, 2, true, true, LocalDate.parse("2026-01-01"), "ok")
        val arq = File.createTempFile("consc", ".json")
        Consciencia.salvar(e, arq)
        val r = Consciencia.carregar(arq)
        assertEquals(1, r.checkins.size)
        assertEquals("ok", r.checkins[0].nota)
        assertEquals(e.nivel, r.nivel)
        arq.delete()
        assertEquals(1, Consciencia.carregar(File("/nao/existe.json")).nivel)   // arquivo ausente/quebrado nao derruba o app
    }
}

class CameraTest {
    /** Ruído determinístico (LCG, uniforme com variância 1): o mesmo gerador foi usado para calibrar o limite em Python. */
    private fun ruido(seed: Long): () -> Double {
        var x = seed
        return { x = (x * 1103515245L + 12345L) % 2147483648L; (x / 2147483648.0 - 0.5) * 3.4641 }
    }

    private fun seno(f: Double, fs: Double, seg: Double, ru: Double, seed: Long): DoubleArray {
        val g = ruido(seed)
        return DoubleArray((fs * seg).toInt()) { 100 + Math.sin(2 * Math.PI * f * it / fs) + ru * g() }
    }

    @Test fun achaRespiracao() {
        val p = AnaliseCamera.picoNaBanda(seno(0.30, 10.0, 30.0, 0.5, 1), 10.0, 0.1, 0.5)
        assertEquals(0.30, p.freq, 0.03)
        assertTrue(p.razao > AnaliseCamera.RAZAO_MIN)
        assertTrue(AnaliseCamera.clareza(p) > 0.0)
    }

    @Test fun achaPulso() {
        val p = AnaliseCamera.picoNaBanda(seno(1.2, 10.0, 30.0, 0.5, 2), 10.0, 0.8, 2.0, 0.05)
        assertEquals(1.2, p.freq, 0.06)
    }

    @Test fun soRuidoNaoViraRespiracao() {
        for (sd in 1L..40L) {
            val g = ruido(sd)
            val s = DoubleArray(300) { 100 + g() }
            assertEquals("semente $sd", 0.0, AnaliseCamera.clareza(AnaliseCamera.picoNaBanda(s, 10.0, 0.1, 0.5)), 0.0)
        }
    }

    @Test fun serieCurtaNaoChuta() {
        assertEquals(0.0, AnaliseCamera.picoNaBanda(DoubleArray(20) { it.toDouble() }, 10.0, 0.1, 0.5).razao, 0.0)
    }

    @Test fun movimentoEConfianca() {
        assertEquals(0.0, AnaliseCamera.movimento(0.5), 0.0)
        assertEquals(1.0, AnaliseCamera.movimento(50.0), 0.0)
        assertEquals(0.3, AnaliseCamera.confianca(5.0), 0.0)
        assertEquals(0.0, AnaliseCamera.diferenca(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0.0)
    }

    @Test fun camaraSoTemN1() {
        val l = AnaliseCamera.fLocais(0.5, AnaliseCamera.Pico(0.0, 0.0), 120.0)
        assertTrue(l[0]!! > 0.4); assertNull(l[1]); assertNull(l[4])
    }
}
