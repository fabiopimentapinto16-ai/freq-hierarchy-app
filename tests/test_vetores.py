"""Os vetores em vetores/*.json sao a verdade compartilhada: o Python tem que reproduzi-los
e o Kotlin (android/app/src/test) le os MESMOS arquivos."""
import json
import os
import unittest
from datetime import date

from app.core import consciencia as c
from app.core.hierarquia import integrar

RAIZ = os.path.join(os.path.dirname(__file__), "..", "vetores")


def _ler(nome):
    with open(os.path.join(RAIZ, nome), encoding="utf-8") as f:
        return json.load(f)


class TestVetores(unittest.TestCase):
    def test_hierarquia(self):
        for v in _ler("hierarquia.json"):
            r = integrar(v["f_locais"])
            self.assertEqual(r.status, v["status"], v["nome"])
            self.assertAlmostEqual(r.f_final, v["f_final"], 4, v["nome"])
            for n, esp in zip(r.niveis, v["f_integradas"]):
                self.assertAlmostEqual(n.f_integrada, esp, 4, v["nome"])

    def test_consciencia(self):
        e = c.Estado()
        for v in _ler("consciencia.json"):
            r = c.checkin(e, v["reacao"], v["pausou"], v["pratica"], date.fromisoformat(v["data"]))
            self.assertEqual(e.nivel, v["nivel_depois"], v["data"])
            self.assertEqual(r["subiu"], v["subiu"], v["data"])
            self.assertAlmostEqual(r["score"], v["score"], 3, v["data"])


if __name__ == "__main__":
    unittest.main()
