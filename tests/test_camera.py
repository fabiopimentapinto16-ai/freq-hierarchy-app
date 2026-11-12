import json
import os
import unittest

from app.core import camera as c

RAIZ = os.path.join(os.path.dirname(__file__), "..", "vetores")


class TestCamera(unittest.TestCase):
    def test_acha_respiracao_e_pulso(self):
        p = c.pico_na_banda(c.serie_sintetica(0.30, 0.5, 1), 10.0, 0.1, 0.5)
        self.assertAlmostEqual(p.freq, 0.30, delta=0.03)
        self.assertGreater(c.clareza(p), 0)
        q = c.pico_na_banda(c.serie_sintetica(1.2, 0.5, 2), 10.0, 0.8, 2.0, 0.05)
        self.assertAlmostEqual(q.freq, 1.2, delta=0.06)

    def test_ruido_puro_nao_vira_respiracao(self):
        for sd in range(1, 200):
            p = c.pico_na_banda(c.serie_sintetica(None, 1.0, sd), 10.0, 0.1, 0.5)
            self.assertEqual(c.clareza(p), 0.0, "semente %d razao %.1f" % (sd, p.razao))

    def test_serie_curta_nao_chuta(self):
        self.assertEqual(c.pico_na_banda(list(range(20)), 10.0, 0.1, 0.5).razao, 0.0)

    def test_movimento_e_confianca(self):
        self.assertEqual(c.movimento(0.5), 0.0)
        self.assertEqual(c.movimento(50.0), 1.0)
        self.assertEqual(c.confianca(5.0), 0.3)
        self.assertEqual(c.diferenca([1], [1, 2]), 0.0)

    def test_camera_so_tem_n1(self):
        l = c.f_locais(0.5, c.Pico(0.0, 0.0), 120.0)
        self.assertGreater(l[0], 0.4)
        self.assertTrue(all(x is None for x in l[1:]))

    def test_vetores_camera(self):
        for v in json.load(open(os.path.join(RAIZ, "camera.json"))):
            p = c.pico_na_banda(c.serie_sintetica(v["f"], v["ruido"], v["seed"]), 10.0, v["lo"], v["hi"], v["passo"])
            self.assertAlmostEqual(p.freq, v["freq"], 3)
            self.assertAlmostEqual(p.razao, v["razao"], delta=max(0.01, v["razao"] * 1e-3))


if __name__ == "__main__":
    unittest.main()
