"""Cenarios simulados (sempre marcados como SIMULADO na tela e no resultado)."""
import numpy as np

from app.core.hierarquia import integrar
from app.core.respiracao import analisar_janela

# F_local de N1..N5 por cenario
CENARIOS = {
    "vazio":             [0.05, 0.04, 0.03, 0.03, 0.02],
    "presenca":          [0.70, 0.60, 0.50, 0.45, 0.20],
    "calado_respiracao": [0.55, 0.50, 0.40, 0.35, 0.15],
    "movimento":         [0.85, 0.80, 0.70, 0.65, 0.30],
    "sinal_fraco":       [0.20, 0.12, 0.10, 0.08, 0.05],
    "anomalia":          [0.20, 0.15, 0.10, 0.10, 0.90],
    "alta_concordancia": [0.80, 0.75, 0.75, 0.75, 0.20],
}

FS = 20.0


def janela(cenario: str, dur: float = 30.0, fs: float = FS, semente: int = 7):
    """Amplitudes (0..1) de uma janela sintetica do cenario."""
    rng = np.random.default_rng(semente)
    t = np.arange(int(dur * fs)) / fs
    x = 0.5 + rng.normal(0, 0.004, t.size)                          # ruido de base
    resp = 0.06 * np.sin(2 * np.pi * 0.28 * t)                      # ~17 respiracoes/min
    if cenario in ("presenca", "calado_respiracao", "alta_concordancia"):
        x += resp
    if cenario in ("movimento", "anomalia"):
        x += 0.25 * np.sin(2 * np.pi * 1.6 * t) * (np.sin(2 * np.pi * 0.1 * t) > -0.3) + 0.5 * resp
    if cenario == "sinal_fraco":
        x += rng.normal(0, 0.01, t.size)
    return x


def rodar(cenario: str):
    """Devolve (ResultadoHierarquia, AnaliseSinal) de um cenario."""
    if cenario not in CENARIOS:
        raise KeyError("cenario desconhecido: %s" % cenario)
    return integrar(CENARIOS[cenario], simulado=True), analisar_janela(janela(cenario), FS)
