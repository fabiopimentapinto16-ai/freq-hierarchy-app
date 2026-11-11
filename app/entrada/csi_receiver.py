"""Recebe os pacotes UDP JSON do ESP32 (porta 5005):
{"ts":..., "amplitude":0.7, "confianca":0.8, "movimento":0.2, "respiracao":0.5, "rssi":-60}
Celular comum NAO le CSI bruto sozinho: o ESP32 mede e manda para o IP deste aparelho."""
import json
import socket
import time
from collections import deque
from typing import Optional

from app.core.hierarquia import forca, integrar
from app.core.respiracao import analisar_janela, tipo_rapido
from config.pesos import PORTA_CSI

CAMPOS = ("amplitude", "confianca", "movimento", "respiracao")


def parse_pacote(dados: bytes) -> Optional[dict]:
    """Valida e normaliza um pacote. Devolve None se estiver quebrado (nunca levanta)."""
    try:
        p = json.loads(dados.decode("utf-8", "replace"))
        if not isinstance(p, dict):
            return None
        out = {"ts": float(p.get("ts", time.time())), "rssi": float(p.get("rssi", 0.0))}
        for c in CAMPOS:
            v = float(p.get(c, 0.0))
            if v != v or v in (float("inf"), float("-inf")):
                return None
            out[c] = max(0.0, min(1.0, v))
        return out
    except Exception:
        return None


def f_locais_do_pacote(p: dict):
    """So o N1 (CSI 2.4 GHz) existe num ESP32. N2..N5 ficam None (sem sensor): a tela mostra 'sem sensor'."""
    return [forca(p["amplitude"], p["confianca"]), None, None, None, None]


class Receptor:
    def __init__(self, porta: int = PORTA_CSI, host: str = "0.0.0.0", janela_s: float = 30.0):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((host, porta))
        self.porta = self.sock.getsockname()[1]
        self.amostras = deque()                    # (ts, amplitude)
        self.janela_s = janela_s

    def receber(self, timeout: float = 2.0) -> Optional[dict]:
        self.sock.settimeout(timeout)
        try:
            dados, _ = self.sock.recvfrom(4096)
        except (socket.timeout, OSError):
            return None
        p = parse_pacote(dados)
        if p:
            self.amostras.append((p["ts"], p["amplitude"]))
            while self.amostras and p["ts"] - self.amostras[0][0] > self.janela_s:
                self.amostras.popleft()
        return p

    def analise_janela(self, base: float = 0.0):
        """FFT da janela acumulada (precisa de >= 8 s). None se ainda nao ha dados suficientes."""
        if len(self.amostras) < 16:
            return None
        ts = [a[0] for a in self.amostras]
        dur = ts[-1] - ts[0]
        if dur < 8:
            return None
        return analisar_janela([a[1] for a in self.amostras], (len(ts) - 1) / dur, base)

    def fechar(self):
        self.sock.close()


def resumo(p: dict):
    """Hierarquia (so N1 real) + tipo rapido, para uma linha de status."""
    r = integrar(f_locais_do_pacote(p))
    return r, tipo_rapido(p["movimento"], p["respiracao"])
