import unittest

from app.core.hierarquia import Suavizador


class TestSuavizador(unittest.TestCase):
    def test_primeiro_valor_passa_direto(self):
        self.assertEqual(Suavizador(0.3).atualizar([0.8, None]), [0.8, None])

    def test_suaviza(self):
        s = Suavizador(0.5)
        s.atualizar([0.0])
        self.assertAlmostEqual(s.atualizar([1.0])[0], 0.5)

    def test_none_nunca_vira_numero(self):
        s = Suavizador(0.3)
        s.atualizar([0.5, 0.5])
        self.assertIsNone(s.atualizar([0.5, None])[1])
        self.assertEqual(s.atualizar([0.5, 0.9])[1], 0.9)     # volta do zero, sem misturar com valor antigo

    def test_alpha_invalido(self):
        with self.assertRaises(ValueError):
            Suavizador(0)


if __name__ == "__main__":
    unittest.main()
