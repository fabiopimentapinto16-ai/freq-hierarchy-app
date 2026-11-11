package com.freqhierarchy.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Lê os MESMOS vetores do Python (vetores/*.json): Kotlin e Python têm que dar o mesmo resultado. */
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
}
