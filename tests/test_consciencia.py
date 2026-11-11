import os
import tempfile
import unittest
from datetime import date, timedelta

from app.core import consciencia as c

D0 = date(2026, 1, 1)


def dia(i):
    return D0 + timedelta(days=i)


class TestConsciencia(unittest.TestCase):
    def test_sete_niveis_com_pratica(self):
        self.assertEqual(len(c.NIVEIS), 7)
        for n in range(1, 8):
            i = c.info_nivel(n)
            self.assertTrue(i["nome"] and i["pratica"] and i["descricao"])

    def test_score(self):
        self.assertEqual(c.score_do_dia(1, True, True), 1.0)
        self.assertEqual(c.score_do_dia(5, False, False), 0.0)

    def test_nao_sobe_sem_praticas(self):
        e = c.Estado()
        for i in range(10):                      # 10 dias bons, mas sem fazer a microprática
            r = c.checkin(e, 1, True, False, hoje=dia(i))
        self.assertEqual(e.nivel, 1)
        self.assertFalse(r["subiu"])

    def test_sobe_com_pratica_e_consistencia(self):
        e = c.Estado()
        subiu_em = None
        for i in range(10):
            r = c.checkin(e, 2, True, True, hoje=dia(i))
            if r["subiu"]:
                subiu_em = i
                break
        self.assertEqual(subiu_em, 6)            # 7 praticas em 7 dias seguidos
        self.assertEqual(e.nivel, 2)
        self.assertEqual(c.metricas(e, dia(6))["progresso"], 0.0)   # novo nivel recomeca

    def test_faltar_um_dia_nao_zera(self):
        e = c.Estado()
        for i in (0, 1, 2, 4, 5):                # faltou o dia 3
            c.checkin(e, 2, True, True, hoje=dia(i))
        m = c.metricas(e, dia(5))
        self.assertEqual(m["praticas"], 5)
        self.assertGreater(m["progresso"], 0)
        self.assertEqual(c.sequencia(e, dia(5)), 2)

    def test_nao_marca_nivel_7_direto(self):
        e = c.Estado()
        with self.assertRaises(ValueError):
            c.ajustar_nivel(e, 7)
        with self.assertRaises(ValueError):
            c.ajustar_nivel(e, 2)                # sem praticas
        c.ajustar_nivel(Estado := c.Estado(nivel=4, entrada_nivel="2026-01-01"), 2, dia(0))   # descer pode
        self.assertEqual(Estado.nivel, 2)

    def test_nivel_maximo_nao_passa_de_7(self):
        e = c.Estado(nivel=7, entrada_nivel=dia(0).isoformat())
        for i in range(10):
            r = c.checkin(e, 1, True, True, hoje=dia(i))
        self.assertEqual(e.nivel, 7)
        self.assertEqual(r["mensagem"], "Nível máximo: mantenha a prática.")

    def test_um_checkin_por_dia(self):
        e = c.Estado()
        c.checkin(e, 5, False, False, hoje=dia(0))
        c.checkin(e, 1, True, True, hoje=dia(0))
        self.assertEqual(len(e.checkins), 1)
        self.assertEqual(e.checkins[0]["score"], 1.0)

    def test_caminho_e_historico(self):
        e = c.Estado(nivel=3)
        st = [x["estado"] for x in c.caminho(e)]
        self.assertEqual(st, ["concluido", "concluido", "atual"] + ["futuro"] * 4)
        for i in range(40):
            c.checkin(e, 3, True, False, hoje=dia(i))
        self.assertEqual(len(c.historico(e, 30, dia(39))), 30)

    def test_persistencia(self):
        e = c.Estado()
        c.checkin(e, 2, True, True, hoje=dia(0), nota="calmo")
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "x", "estado.json")
            c.salvar(e, p)
            e2 = c.carregar(p)
            self.assertEqual(e2.checkins[0]["nota"], "calmo")
            self.assertEqual(c.carregar(os.path.join(d, "nao_existe.json")).nivel, 1)

    def test_sugestao_nao_afirma_nivel_pelo_wifi(self):
        s = c.sugestao_pausa("respiracao", True)
        self.assertIn("pausa", s)
        self.assertNotIn("nível", s.lower())
        self.assertIsNone(c.sugestao_pausa("movimento", False))

    def test_avisos(self):
        self.assertIn("não a consciência", c.AVISO)
        self.assertIn("Não é terapia", c.AVISO_CUIDADO)


if __name__ == "__main__":
    unittest.main()
