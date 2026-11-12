"""Os vetores em vetores/*.json sao a verdade compartilhada: o Python tem que reproduzi-los
e o Kotlin (android/app/src/test) le os MESMOS arquivos."""
import json
import os
import unittest
from datetime import date

from app.core import consciencia as c
from app.core import fusao as fu
from app.core.hierarquia import integrar
from config.pesos import NIVEIS_TELEFONE

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
            self.assertEqual(r["pode_subir"], v["pode_subir"], v["data"])
            self.assertAlmostEqual(r["score"], v["score"], 3, v["data"])
        c.ajustar_nivel(e, 2, date(2026, 1, 10))      # confirmacao do usuario
        self.assertEqual(e.nivel, 2)

    def test_flow(self):
        for v in _ler("flow.json"):
            r = c.registrar_flow(c.Estado(), v["minutos"], v["pratica"], v["absorcao"], date(2026, 1, 1))
            self.assertEqual(r["registro"]["minutos"], v["minutos_reg"])
            self.assertEqual(r["registro"]["absorcao"], v["absorcao_reg"])
            self.assertEqual(r["registro"]["boa_absorcao"], v["boa_absorcao"])
            self.assertEqual(r["mensagem"], v["mensagem"])

    def test_fusao(self):
        v = _ler("fusao.json")
        for x in v["tipo_sinal"]:
            self.assertEqual(fu.tipo_sinal(x["mov"], x["resp"]), x["esperado"])
        for x in v["k8"]:
            self.assertEqual(fu.k8_local(x["mov"], x["base"], x["imu"]), x["esperado"])
        for x in v["f_locais"]:
            self.assertEqual(fu.f_locais_telefone(x["cam_amp"], x["cam_conf"], x["imu"], x["rssi_desvio"], x["mov"], x["mov_base"]), x["esperado"])
        for x in v["status"]:
            self.assertEqual(fu.status_telefone(x["status"], x["imu"]), x["esperado"])
        for x in v["cor"]:
            self.assertEqual(list(fu.cor_fusao(x["f"], x["status"], x["tipo"])), x["esperado"])
        for x in v["hierarquia_telefone"]:
            r = integrar(x["f_locais"], niveis=NIVEIS_TELEFONE)
            self.assertEqual(r.status, x["status"])
            self.assertAlmostEqual(r.f_final, x["f_final"], 4)


if __name__ == "__main__":
    unittest.main()
