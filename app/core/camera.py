"""Matematica do modo Camera (luz visivel). Espelha AnaliseCamera.kt (vetores/camera.json).
Mede movimento e variacoes lentas de brilho. Respiracao e pulso sao ESTIMATIVAS; nada aqui le consciencia nem infravermelho."""
from typing import List, NamedTuple, Optional, Sequence

import numpy as np

from app.core.hierarquia import forca
from config import pesos as P


class Pico(NamedTuple):
    freq: float
    razao: float


def _clip(x, a=0.0, b=1.0):
    return max(a, min(b, float(x)))


def diferenca(a: Sequence[float], b: Sequence[float]) -> float:
    """Diferenca media absoluta (0..255) entre duas grades de luminancia do mesmo tamanho."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    if a.size == 0 or a.shape != b.shape:
        return 0.0
    return float(np.mean(np.abs(a - b)))


def movimento(dif: float) -> float:
    return _clip((dif - P.MOV_RUIDO) / P.MOV_ESCALA)


def confianca(luma_media: float) -> float:
    return 0.9 if P.LUMA_MIN <= luma_media <= P.LUMA_MAX else 0.3


def pico_na_banda(serie, fs: float, lo: float, hi: float, passo: float = 0.02) -> Pico:
    """Pico de energia entre lo..hi Hz. razao = energia do pico / MEDIANA da energia da banda.
    Precisa de >= 8 s de serie. (Mesma conta do Goertzel do Kotlin: |DFT|^2 em cada frequencia.)"""
    x = np.asarray(serie, float)
    n = len(x)
    if fs <= 0 or n < fs * 8:
        return Pico(0.0, 0.0)
    x = (x - x.mean()) * (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / (n - 1)))
    freqs, f = [], lo
    while f <= hi + 1e-9:
        freqs.append(f)
        f += passo
    t = np.arange(n) / fs
    pw = np.array([abs(np.sum(x * np.exp(-2j * np.pi * fr * t))) ** 2 for fr in freqs])
    med = float(np.median(pw))
    if med <= 0:
        return Pico(0.0, 0.0)
    i = int(np.argmax(pw))
    return Pico(round(float(freqs[i]), 4), float(pw[i] / med))


def clareza(p: Pico) -> float:
    return 0.0 if p.razao < P.RAZAO_MIN else _clip((p.razao - P.RAZAO_MIN) / P.CLAREZA_ESCALA)


def f_locais(mov: float, resp: Pico, luma_media: float) -> List[Optional[float]]:
    """F do N1-camera: maior entre movimento e clareza da respiracao. Os demais niveis ficam 'sem sensor'."""
    return [forca(max(mov, clareza(resp)), confianca(luma_media)), None, None, None, None]


def lcg(seed: int):
    """Ruido deterministico uniforme (variancia 1), igual ao dos testes Kotlin."""
    x = seed
    while True:
        x = (x * 1103515245 + 12345) % 2147483648
        yield (x / 2147483648.0 - 0.5) * 3.4641


def serie_sintetica(f: Optional[float], ruido: float, seed: int, fs: float = 10.0, seg: float = 30.0):
    g = lcg(seed)
    n = int(fs * seg)
    return [100 + (np.sin(2 * np.pi * f * i / fs) if f else 0.0) + ruido * next(g) for i in range(n)]
