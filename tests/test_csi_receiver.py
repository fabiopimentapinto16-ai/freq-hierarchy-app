import json
import socket
import unittest

from app.entrada.csi_receiver import Receptor, f_locais_do_pacote, parse_pacote, resumo


class TestCsi(unittest.TestCase):
    def test_parse(self):
        p = parse_pacote(b'{"ts":1,"amplitude":0.7,"confianca":0.8,"movimento":0.2,"respiracao":0.5,"rssi":-60}')
        self.assertAlmostEqual(p["amplitude"], 0.7)
        self.assertEqual(p["rssi"], -60.0)

    def test_parse_ruim(self):
        for b in (b"", b"nao e json", b"[1,2]", b'{"amplitude":"x"}', b'{"amplitude":NaN}'):
            self.assertIsNone(parse_pacote(b))

    def test_clip(self):
        p = parse_pacote(b'{"amplitude":5,"confianca":-2}')
        self.assertEqual((p["amplitude"], p["confianca"]), (1.0, 0.0))

    def test_so_n1_existe(self):
        f = f_locais_do_pacote(parse_pacote(b'{"amplitude":0.7,"confianca":0.8}'))
        self.assertAlmostEqual(f[0], 0.56)
        self.assertEqual(f[1:], [None] * 4)

    def test_udp_loopback(self):
        rx = Receptor(porta=0, host="127.0.0.1")
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.sendto(json.dumps({"ts": 1, "amplitude": 0.9, "confianca": 0.9, "movimento": 0.6, "respiracao": 0.1, "rssi": -55}).encode(),
                     ("127.0.0.1", rx.porta))
            p = rx.receber(2.0)
            s.close()
            self.assertIsNotNone(p)
            r, tipo = resumo(p)
            self.assertEqual(tipo, "movimento")
            self.assertAlmostEqual(r.f_final, 0.81, places=2)
            self.assertIsNone(rx.receber(0.2))            # sem pacote: None, nao trava
        finally:
            rx.fechar()


if __name__ == "__main__":
    unittest.main()
