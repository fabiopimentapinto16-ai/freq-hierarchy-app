import unittest

from app.core import fusao as f
from app.core.hierarquia import integrar
from config.pesos import NIVEIS_TELEFONE


class TestFusao(unittest.TestCase):
    def test_tipo_sinal_respiracao_so_com_evidencia(self):
        self.assertEqual(f.tipo_sinal(0.1, False), "quieto")
        self.assertEqual(f.tipo_sinal(0.1, True), "respiracao")
        self.assertEqual(f.tipo_sinal(0.6, True), "misto")
        self.assertEqual(f.tipo_sinal(0.6, False), "movimento")

    def test_k8_aparelho_em_movimento_nao_julga(self):
        self.assertIsNone(f.k8_local(1.0, 0.0, 0.6))
        self.assertEqual(f.k8_local(0.9, 0.1, 0.0), 1.0)
        self.assertEqual(f.k8_local(0.3, 0.1, 0.0), 0.0)

    def test_sem_wifi_e_audio_ficam_sem_sensor(self):
        l = f.f_locais_telefone(0.5, 0.9, 0.0, None, 0.5, 0.5)
        self.assertIsNone(l[2])
        self.assertIsNone(l[3])

    def test_imu_forte_derruba_confianca_da_camera(self):
        firme = f.f_locais_telefone(0.8, 0.9, 0.0, 0.0, 0.8, 0.8)[0]
        tremendo = f.f_locais_telefone(0.8, 0.9, 0.9, 0.0, 0.8, 0.8)[0]
        self.assertLess(tremendo, firme)

    def test_status_telefone(self):
        self.assertEqual(f.status_telefone("presenca", 0.6), "movimento")
        self.assertEqual(f.status_telefone("anomalia", 0.9), "anomalia")
        self.assertEqual(f.status_telefone("vazio", 0.1), "vazio")

    def test_cor_anomalia_roxa_e_alfa_cresce_com_f(self):
        self.assertEqual(f.cor_fusao(1.0, "anomalia", "misto")[:3], (142, 36, 170))
        self.assertLess(f.cor_fusao(0.1, "presenca", "quieto")[3], f.cor_fusao(0.9, "presenca", "quieto")[3])
        self.assertEqual(f.cor_fusao(1.0, "presenca", "quieto")[3], 255)

    def test_hierarquia_do_telefone_usa_nomes_proprios(self):
        r = integrar([0.5, 0.0, None, None, 0.0], niveis=NIVEIS_TELEFONE)
        self.assertIn("Câmera", r.niveis[0].nome)
        self.assertFalse(r.niveis[3].disponivel)

    def test_rotulos_honestos(self):
        self.assertIn("sem CSI", f.ROTULO_ESTIMADO)
        self.assertIn("AO VIVO", f.ROTULO_CSI)


if __name__ == "__main__":
    unittest.main()
