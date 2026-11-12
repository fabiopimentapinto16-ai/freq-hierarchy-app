import unittest
from datetime import date

from app.core import consciencia as c

D = date(2026, 1, 1)


class TestFlow(unittest.TestCase):
    def test_boa_absorcao_precisa_pratica_e_nota_alta(self):
        e = c.Estado()
        self.assertTrue(c.registrar_flow(e, 10, True, 4, D)["registro"]["boa_absorcao"])
        self.assertFalse(c.registrar_flow(e, 10, True, 3, D)["registro"]["boa_absorcao"])
        self.assertFalse(c.registrar_flow(e, 10, False, 5, D)["registro"]["boa_absorcao"])

    def test_nunca_afirma_que_entrou_em_flow(self):
        e = c.Estado()
        for pr, ab in [(True, 5), (False, 1), (True, 3)]:
            m = c.registrar_flow(e, 10, pr, ab, D)["mensagem"].lower()
            self.assertNotIn("entrou em flow", m)
            self.assertNotIn("você está em flow", m)
        self.assertEqual(c.registrar_flow(e, 10, True, 5, D)["mensagem"], "Sessão com boa absorção relatada.")

    def test_limites(self):
        r = c.registrar_flow(c.Estado(), 999, True, 9, D)["registro"]
        self.assertEqual((r["minutos"], r["absorcao"]), (120, 5))
        r = c.registrar_flow(c.Estado(), 0, True, 0, D)["registro"]
        self.assertEqual((r["minutos"], r["absorcao"]), (1, 1))

    def test_resumo_e_persistencia(self):
        e = c.Estado()
        c.registrar_flow(e, 10, True, 5, D)
        c.registrar_flow(e, 5, False, 2, D)
        self.assertEqual(c.resumo_flow(e, 30, D), {"sessoes": 2, "boa_absorcao": 1, "minutos": 15})
        e2 = c.Estado.from_dict(e.to_dict())
        self.assertEqual(len(e2.flows), 2)

    def test_condicoes_e_foco(self):
        self.assertEqual(len(c.FLOW_CONDICOES), 5)
        for n in range(1, 8):
            self.assertTrue(c.foco_do_nivel(n))


if __name__ == "__main__":
    unittest.main()
