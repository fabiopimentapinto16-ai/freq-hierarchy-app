"""Hierarquia de frequencias: forca F, heranca entre niveis e regra K8."""
from typing import Optional, Sequence

from config.pesos import (CORTE_HERANCA, DIF_ANOMALIA, LIMIAR_K8, LIMIAR_PRESENCA, NIVEIS_FREQ)
from app.data.modelos import NivelResultado, ResultadoHierarquia


def _clip(x, a=0.0, b=1.0):
    return max(a, min(b, float(x)))


def forca(amplitude: float, confianca: float) -> float:
    """F = amplitude x confianca, limitada a 0..1."""
    return _clip(_clip(amplitude) * _clip(confianca))


def integrar(f_locais: Sequence[Optional[float]], simulado: bool = False) -> ResultadoHierarquia:
    """f_locais: 5 valores (N1..N5). None = nivel sem sensor (nao inventamos: repete o anterior e marca indisponivel).

    F_integrada = w_proprio * F_local + w_heranca * F_anterior;  F_anterior < 0.15 corta a heranca.
    K8 (ultimo nivel): se F_local < 0.35, F_integrada = F_anterior (nao puxa a presenca para baixo)."""
    if len(f_locais) != len(NIVEIS_FREQ):
        raise ValueError("preciso de %d valores (N1..N5)" % len(NIVEIS_FREQ))
    res, prev = [], 0.0
    for i, (cfg, fl) in enumerate(zip(NIVEIS_FREQ, f_locais)):
        disp = fl is not None
        fl_c = _clip(fl) if disp else None
        herdou = False
        if i == 0:
            f = fl_c if disp else 0.0
        elif not disp:
            f, herdou = prev, True
        elif cfg["id"] == "N5" and fl_c < LIMIAR_K8:
            f, herdou = prev, True                                    # regra K8
        else:
            wh = cfg["w_heranca"] if prev >= CORTE_HERANCA else 0.0   # corta heranca se o anterior e fraco
            f, herdou = cfg["w_proprio"] * fl_c + wh * prev, wh > 0
        f = _clip(f)
        res.append(NivelResultado(cfg["id"], cfg["nome"], cfg["cor"], cfg["hex"], fl_c, round(f, 4), herdou, disp))
        prev = f
    return ResultadoHierarquia(res, round(prev, 4), _status(res, prev), simulado)


def _status(niveis, f_final) -> str:
    k8 = niveis[-1]
    ant = niveis[-2].f_integrada
    if k8.disponivel and k8.f_local is not None and k8.f_local >= LIMIAR_K8:
        if ant < LIMIAR_PRESENCA or abs(k8.f_local - ant) > DIF_ANOMALIA:
            return "anomalia"
    return "presenca" if f_final >= LIMIAR_PRESENCA else "vazio"


class Suavizador:
    """Media movel exponencial por nivel para o modo ao vivo (evita a tela piscar no limiar).
    None (sem sensor) continua None: nunca vira numero. Valor novo apos um None comeca do valor medido."""

    def __init__(self, alpha: float = 0.3):
        if not 0.0 < alpha <= 1.0:
            raise ValueError("alpha entre 0 (exclusive) e 1")
        self.alpha = alpha
        self._v: list = []

    def atualizar(self, f_locais):
        if len(self._v) != len(f_locais):
            self._v = [None] * len(f_locais)
        out = []
        for i, x in enumerate(f_locais):
            if x is None:
                self._v[i] = None
            else:
                x = _clip(x)
                self._v[i] = x if self._v[i] is None else self._v[i] + self.alpha * (x - self._v[i])
            out.append(self._v[i])
        return out
