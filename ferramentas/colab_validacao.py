# -*- coding: utf-8 -*-
"""VALIDACAO NO COLAB da matematica da camera do Freq Hierarchy (mesma conta do app Android).

COMO USAR (celula do Colab):
    !git clone https://github.com/fabiopimentapinto16-ai/freq-hierarchy-app.git
    %cd freq-hierarchy-app
    %run ferramentas/colab_validacao.py

O que ele faz:
  A) Sinais SINTETICOS: taxa de falso alarme (ruido puro) e de acerto (respiracao/pulso com ruido) para o limite RAZAO_MIN.
  B) Seus VIDEOS do celular (grave 30-60 s com o celular apoiado, boa luz, pessoa parada diante da camera):
     o script pede o upload. Nomeie com uma etiqueta: *_resp.mp4 (pessoa parada respirando), *_vazio.mp4 (sem ninguem),
     *_mov.mp4 (pessoa se mexendo). Sem etiqueta ele so mostra os numeros.
  C) Sugere um RAZAO_MIN a partir dos seus videos (acima do maior *_vazio e abaixo do menor *_resp).

HONESTIDADE: isto valida o ALGORITMO em video, nao o app no aparelho. Respiracao/pulso por luminancia sao ESTIMATIVAS.
Nao le pensamento, nao mede consciencia. Se mudar o limite aqui, repita o mesmo valor em config/pesos.py e AnaliseCamera.kt.
"""
import glob
import os
import sys

import numpy as np

sys.path.insert(0, os.getcwd())
from app.core import camera as C          # noqa: E402
from config import pesos as P             # noqa: E402

try:
    import matplotlib
    if "google.colab" not in sys.modules:
        matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except Exception:                         # pragma: no cover
    plt = None

LARG, ALT, FPS_ALVO = 32, 24, 10.0
VIDEO_DIR = os.environ.get("VIDEO_DIR")   # para testes locais sem upload
SAIDA = os.environ.get("SAIDA_DIR", ".")


# ------------------------------------------------------------------ A) sintetico
def parte_a(n_ruido=1000, n_sinal=200):
    print("=" * 70, "\nA) SINAIS SINTETICOS (limite RAZAO_MIN = %.1f)\n" % P.RAZAO_MIN, "=" * 70, sep="")
    ruido = [C.pico_na_banda(C.serie_sintetica(None, 1.0, s), 10.0, *P.BANDA_RESP_CAM).razao for s in range(1, n_ruido + 1)]
    falso = np.mean([r >= P.RAZAO_MIN for r in ruido])
    print("Ruido puro (%d series): razao max %.1f | p99 %.1f | FALSO ALARME %.2f%%" % (len(ruido), max(ruido), np.percentile(ruido, 99), 100 * falso))
    print("\nAcerto da respiracao 0.3 Hz (amplitude 1) conforme o ruido:")
    print("  ruido | acerto | razao minima")
    for ru in (0.2, 0.5, 1.0, 1.5, 2.0, 3.0):
        rs = [C.pico_na_banda(C.serie_sintetica(0.3, ru, s), 10.0, *P.BANDA_RESP_CAM).razao for s in range(1, n_sinal + 1)]
        print("  %4.1f  | %5.1f%% | %8.1f" % (ru, 100 * np.mean([r >= P.RAZAO_MIN for r in rs]), min(rs)))
    return ruido


# ------------------------------------------------------------------ B) videos
def grade_do_video(caminho):
    """Mesma grade 32x24 de luminancia do app, a ~10 fps. Devolve (tempos, grades)."""
    import cv2
    cap = cv2.VideoCapture(caminho)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    passo = max(1, int(round(fps / FPS_ALVO)))
    ts, gs, i = [], [], 0
    while True:
        ok, fr = cap.read()
        if not ok:
            break
        if i % passo == 0:
            g = cv2.cvtColor(fr, cv2.COLOR_BGR2GRAY)
            h, w = g.shape
            ys = (np.arange(ALT) * h) // ALT + h // (2 * ALT)
            xs = (np.arange(LARG) * w) // LARG + w // (2 * LARG)
            gs.append(g[np.ix_(ys, xs)].astype(float).ravel())
            ts.append(i / fps)
        i += 1
    cap.release()
    return np.array(ts), gs


def analisar_video(caminho):
    ts, gs = grade_do_video(caminho)
    if len(gs) < 20:
        return None
    luma = np.array([g.mean() for g in gs])
    mov, m = [], 0.0
    for a, b in zip(gs, gs[1:]):                          # EMA 0.3, como o app
        m += 0.3 * (C.movimento(C.diferenca(a, b)) - m)
        mov.append(m)
    fs = (len(ts) - 1) / (ts[-1] - ts[0])
    janelas = []                                          # janela de 30 s a cada 5 s, como a janela do app
    n30, n5 = int(30 * fs), int(5 * fs)
    ini = 0
    while ini + int(10 * fs) <= len(luma) and (ini + n30 <= len(luma) or not janelas):   # so janelas cheias (30 s); video curto = 1 janela
        seg = luma[ini:ini + n30]
        r, p = C.pico_na_banda(seg, fs, *P.BANDA_RESP_CAM), C.pico_na_banda(seg, fs, *P.BANDA_PULSO_CAM, passo=0.05)
        janelas.append((ts[ini], len(seg) / fs, r, p))
        ini += n5
    return {"ts": ts, "luma": luma, "mov": np.array(mov), "fs": fs, "janelas": janelas}


def etiqueta(nome):
    n = os.path.basename(nome).lower()
    return "resp" if "_resp" in n else "vazio" if "_vazio" in n else "mov" if "_mov" in n else None


def relatorio_video(nome, r):
    print("\n--- %s  [%s]  %.0f s a %.1f fps" % (os.path.basename(nome), etiqueta(nome) or "sem etiqueta", r["ts"][-1], r["fs"]))
    print("   brilho medio %.0f (confianca %.1f) | movimento medio %.2f, max %.2f" % (r["luma"].mean(), C.confianca(r["luma"].mean()), r["mov"].mean(), r["mov"].max()))
    print("   janela(s) | respiracao: freq -> /min  razao  claro? | pulso: freq -> bpm  razao  claro?")
    for t0, dur, rp, pl in r["janelas"]:
        print("   %5.0f+%-3.0f | %.2f Hz -> %4.0f  %7.1f  %-5s | %.2f Hz -> %4.0f  %7.1f  %-5s" % (
            t0, dur, rp.freq, rp.freq * 60, rp.razao, "SIM" if C.clareza(rp) > 0 else "nao",
            pl.freq, pl.freq * 60, pl.razao, "SIM" if C.clareza(pl) > 0 else "nao"))


def grafico_video(nome, r):
    if plt is None:
        return
    fig, ax = plt.subplots(3, 1, figsize=(9, 8))
    ax[0].plot(r["ts"], r["luma"] - r["luma"].mean(), lw=0.8); ax[0].set_title("Brilho medio (sem media) - %s" % os.path.basename(nome)); ax[0].set_xlabel("s")
    ax[1].plot(r["ts"][1:], r["mov"], color="darkorange", lw=0.8); ax[1].set_title("Movimento (0..1)"); ax[1].set_ylim(0, 1); ax[1].set_xlabel("s")
    seg = r["luma"][-int(30 * r["fs"]):]
    n = len(seg); x = (seg - seg.mean()) * (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / (n - 1)))
    fr = np.arange(0.1, 2.0, 0.02); t = np.arange(n) / r["fs"]
    pw = np.array([abs(np.sum(x * np.exp(-2j * np.pi * f * t))) ** 2 for f in fr])
    razao = pw / np.median(pw[(fr >= 0.1) & (fr <= 0.5)])
    ax[2].plot(fr, razao, color="purple"); ax[2].axhline(P.RAZAO_MIN, color="gray", ls="--", label="RAZAO_MIN")
    ax[2].axvspan(*P.BANDA_RESP_CAM, alpha=0.12, color="cyan", label="banda respiracao"); ax[2].axvspan(*P.BANDA_PULSO_CAM, alpha=0.12, color="red", label="banda pulso")
    ax[2].set_yscale("log"); ax[2].set_xlabel("Hz"); ax[2].set_title("Espectro / mediana da banda de respiracao"); ax[2].legend()
    fig.tight_layout()
    fig.savefig(os.path.join(SAIDA, "validacao_%s.png" % os.path.splitext(os.path.basename(nome))[0]), dpi=110)
    plt.show() if "google.colab" in sys.modules else plt.close(fig)


def parte_b():
    print("\n" + "=" * 70, "\nB) SEUS VIDEOS\n" + "=" * 70, sep="")
    if VIDEO_DIR:
        arqs = sorted(sum((glob.glob(os.path.join(VIDEO_DIR, e)) for e in ("*.mp4", "*.mov", "*.webm", "*.mkv")), []))
    else:
        try:
            from google.colab import files
            print("Selecione 1 ou mais videos (nomes terminando em _resp, _vazio ou _mov).")
            arqs = list(files.upload().keys())
        except Exception:
            print("Fora do Colab e sem VIDEO_DIR: pulando a parte B.")
            return {}
    res = {}
    for a in arqs:
        r = analisar_video(a)
        if r is None:
            print("\n%s: video curto demais (preciso de pelo menos ~2 s)." % a)
            continue
        relatorio_video(a, r); grafico_video(a, r); res[a] = r
    return res


# ------------------------------------------------------------------ C) limite sugerido
def parte_c(res, ruido):
    print("\n" + "=" * 70, "\nC) LIMITE SUGERIDO\n" + "=" * 70, sep="")
    def razoes(tag):
        return [max(rp.razao for _, _, rp, _ in r["janelas"]) if tag == "max" else min(rp.razao for _, _, rp, _ in r["janelas"])
                for a, r in res.items() if etiqueta(a) == ("vazio" if tag == "max" else "resp")]
    vazio, resp = razoes("max"), razoes("resp")
    alto_vazio = max(vazio) if vazio else None
    baixo_resp = min(resp) if resp else None
    p99 = float(np.percentile(ruido, 99.9)) if len(ruido) else None
    print("Ruido sintetico p99.9: %s | maior razao em *_vazio: %s | menor razao em *_resp: %s" % (
        "%.1f" % p99 if p99 else "-", "%.1f" % alto_vazio if alto_vazio else "sem video", "%.1f" % baixo_resp if baixo_resp else "sem video"))
    if alto_vazio is not None and baixo_resp is not None:
        piso = max(alto_vazio * 1.5, (p99 or 0) * 1.2, 15.0)      # margem acima do pior ruido visto (video vazio e sintetico)
        if baixo_resp >= 2 * piso:
            print("=> Separam bem. RAZAO_MIN sugerido: %.0f  (atual %.0f). Margem: respiracao real >= %.1fx o limite." % (piso, P.RAZAO_MIN, baixo_resp / piso))
        elif baixo_resp > piso:
            print("=> Separam, mas com pouca folga. RAZAO_MIN sugerido: %.0f  (atual %.0f). Grave mais videos antes de confiar." % (piso, P.RAZAO_MIN))
        else:
            print("=> NAO separam: a respiracao real ficou abaixo do ruido de um video vazio. Melhore luz, apoio do celular, enquadre peito/rosto, "
                  "grave por mais tempo. Nao baixe o limite para 'forcar' a deteccao: isso gera falso alarme.")
    else:
        print("=> Para sugerir um limite grave pelo menos 1 video *_vazio e 1 *_resp.")
    print("\nSe mudar o valor, edite RAZAO_MIN em config/pesos.py E em AnaliseCamera.kt (precisam ser iguais) e rode os testes.")


if __name__ == "__main__":
    ruido = parte_a()
    res = parte_b()
    parte_c(res, ruido)
