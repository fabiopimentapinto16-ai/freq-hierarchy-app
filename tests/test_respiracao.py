import unittest

import numpy as np

from app.core.respiracao import analisar_janela, calibrar, tipo_rapido
from app.entrada import simulador


class TestRespiracao(unittest.TestCase):
    def test_cenarios(self):
        esperado = {"vazio": "quieto", "calado_respiracao": "respiracao", "movimento": "ambos"}
        for nome, tipo in esperado.items():
            a = analisar_janela(simulador.janela(nome), simulador.FS)
            self.assertEqual(a.tipo_sinal, tipo, nome)

    def test_frequencia_da_respiracao(self):
        a = analisar_janela(simulador.janela("calado_respiracao"), simulador.FS)
        self.assertAlmostEqual(a.f_resp, 0.28, delta=0.06)
        self.assertGreater(a.regularidade, 0.5)

    def test_frequencia_do_movimento(self):
        a = analisar_janela(simulador.janela("movimento"), simulador.FS)
        self.assertAlmostEqual(a.f_mov, 1.6, delta=0.2)

    def test_janela_curta_e_quieta(self):
        self.assertEqual(analisar_janela([0.5] * 10, 20).tipo_sinal, "quieto")

    def test_calibracao_desconta_ruido(self):
        rng = np.random.default_rng(1)
        t = np.arange(600) / 20.0
        ruido = 0.5 + 0.03 * np.sin(2 * np.pi * 0.3 * t) + rng.normal(0, 0.002, 600)
        sem = analisar_janela(ruido, 20)
        base = calibrar(ruido, 20)
        com = analisar_janela(ruido, 20, base)
        self.assertEqual(sem.tipo_sinal, "respiracao")
        self.assertEqual(com.tipo_sinal, "quieto")

    def test_tipo_rapido(self):
        self.assertEqual(tipo_rapido(0.1, 0.1), "quieto")
        self.assertEqual(tipo_rapido(0.1, 0.6), "respiracao")
        self.assertEqual(tipo_rapido(0.6, 0.1), "movimento")
        self.assertEqual(tipo_rapido(0.6, 0.6), "ambos")


if __name__ == "__main__":
    unittest.main()
