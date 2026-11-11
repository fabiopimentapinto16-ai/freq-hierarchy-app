"""Respiração x movimento a partir de uma janela de amplitudes (teste no calado).
Banda respiração 0.1-0.5 Hz; movimento > 0.5 Hz. Usa FFT; o rms de cada banda decide o tipo."""
import numpy as np

from app.data.modelos import AnaliseSinal
from config.pesos import BANDA_MOV_MIN, BANDA_RESP, LIM_RMS_MOV, LIM_RMS_RESP


def classificar(rms_resp: float, rms_mov: float) -> str:
    r, m = rms_resp >= LIM_RMS_RESP, rms_mov >= LIM_RMS_MOV
    return "ambos" if (r and m) else "respiracao" if r else "movimento" if m else "quieto"


def analisar_janela(amostras, fs: float, base: float = 0.0) -> AnaliseSinal:
    """amostras: amplitudes 0..1 em fs Hz (>= 8 s). `base` = rms de ruido medido na calibracao (sala parada), descontado."""
    x = np.asarray(amostras, dtype=float)
    if len(x) < max(16, int(fs * 8)) or fs <= 0:
        return AnaliseSinal("quieto", 0.0, 0.0, 0.0)
    x = x - x.mean()
    n = len(x)
    X = np.fft.rfft(x * np.hanning(n))
    f = np.fft.rfftfreq(n, 1.0 / fs)
    P = np.abs(X) ** 2

    def banda(lo, hi):
        m = (f >= lo) & (f < hi) if hi else (f >= lo)
        return m

    mr, mm = banda(*BANDA_RESP), banda(BANDA_MOV_MIN, None)
    mm[0] = False

    def rms(mask):
        Y = np.zeros_like(X)
        Y[mask] = np.fft.rfft(x)[mask]
        return float(np.sqrt(np.mean(np.fft.irfft(Y, n) ** 2)))

    rr, rm = max(0.0, rms(mr) - base), max(0.0, rms(mm) - base)
    tipo = classificar(rr, rm)
    f_resp = float(f[mr][np.argmax(P[mr])]) if rr >= LIM_RMS_RESP and mr.any() else 0.0
    f_mov = float(f[mm][np.argmax(P[mm])]) if rm >= LIM_RMS_MOV and mm.any() else 0.0
    reg = 0.0
    if f_resp > 0 and P[mr].sum() > 0:                  # quanto da energia da banda esta em torno de 1 pico
        i = int(np.argmax(P[mr]))
        reg = float(P[mr][max(0, i - 1):i + 2].sum() / P[mr].sum())
    return AnaliseSinal(tipo, round(f_resp, 3), round(f_mov, 3), round(reg, 3), round(rr, 4), round(rm, 4))


def calibrar(amostras_sala_parada, fs: float) -> float:
    """rms de ruido nas duas bandas com a sala parada (20-30 s sem ninguem se mexendo); depois e descontado."""
    a = analisar_janela(amostras_sala_parada, fs, 0.0)
    return max(a.rms_resp, a.rms_mov)


def tipo_rapido(movimento: float, respiracao: float) -> str:
    """Quando o ESP32 ja manda 'movimento' e 'respiracao' (0..1): classificacao direta, sem FFT."""
    m, r = movimento >= 0.35, respiracao >= 0.35
    return "ambos" if (m and r) else "movimento" if m else "respiracao" if r else "quieto"
