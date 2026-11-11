import unittest

from app.core.hierarquia import forca, integrar
from app.entrada import simulador


class TestHierarquia(unittest.TestCase):
    def test_forca(self):
        self.assertAlmostEqual(forca(0.7, 0.8), 0.56)
        self.assertEqual(forca(2, 2), 1.0)
        self.assertEqual(forca(-1, 0.5), 0.0)

    def test_heranca(self):
        r = integrar([0.8, 0.75, 0.75, 0.75, 0.2])
        self.assertAlmostEqual(r.niveis[1].f_integrada, 0.6 * 0.75 + 0.4 * 0.8, places=3)

    def test_corta_heranca_se_anterior_fraco(self):
        r = integrar([0.10, 0.9, 0.0, 0.0, 0.0])          # N1 = 0.10 < 0.15
        self.assertAlmostEqual(r.niveis[1].f_integrada, 0.6 * 0.9, places=3)
        self.assertFalse(r.niveis[1].herdou)

    def test_k8_fraco_nao_derruba(self):
        r = integrar([0.8, 0.75, 0.75, 0.75, 0.2])
        self.assertEqual(r.niveis[4].f_integrada, r.niveis[3].f_integrada)
        self.assertEqual(r.status, "presenca")

    def test_alta_concordancia(self):
        r, _ = simulador.rodar("alta_concordancia")
        self.assertAlmostEqual(r.f_final, 0.75, delta=0.03)
        self.assertEqual(r.status, "presenca")
        self.assertTrue(r.simulado)

    def test_anomalia(self):
        r, _ = simulador.rodar("anomalia")
        self.assertEqual(r.status, "anomalia")

    def test_vazio_e_sinal_fraco(self):
        self.assertEqual(simulador.rodar("vazio")[0].status, "vazio")
        self.assertEqual(simulador.rodar("sinal_fraco")[0].status, "vazio")

    def test_sem_sensor_nao_inventa(self):
        r = integrar([0.6, None, None, None, None])
        self.assertTrue(all(not n.disponivel for n in r.niveis[1:]))
        self.assertEqual(r.f_final, 0.6)

    def test_tamanho_errado(self):
        with self.assertRaises(ValueError):
            integrar([0.1, 0.2])


if __name__ == "__main__":
    unittest.main()
