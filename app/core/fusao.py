"""Modo TELEFONE PURO: junta camera, IMU e RSSI em F locais (N1..N5) e decide tipo, status e cor.
Tudo ESTIMADO (sensores do celular). Nao calcula CSI e nao le pensamento. Espelhado em Fusao.kt (vetores/fusao.json)."""
from typing import List, Optional, Tuple

from app.core.hierarquia import forca
from config.pesos import (CONF_RSSI, K8_SALTO_ESCALA, K8_SALTO_MIN, LIM_IMU_APARELHO, LIM_TIPO, RSSI_ESCALA_DB)

ROTULO_ESTIMADO = "ESTIMADO (sensores do celular) — sem CSI"
ROTULO_CSI = "AO VIVO (CSI)"
ROTULO_SIMULADO = "SIMULADO (demonstração, nenhum sensor real)"


def _clip(x, a=0.0, b=1.0):
    return max(a, min(b, float(x)))


def tipo_sinal(movimento: float, respiracao_clara: bool) -> str:
    """quieto | movimento | respiracao | misto. Respiracao so entra com evidencia forte (respiracao_clara)."""
    m = movimento >= LIM_TIPO
    return "misto" if (m and respiracao_clara) else "movimento" if m else "respiracao" if respiracao_clara else "quieto"


def k8_local(mov: float, mov_base: float, imu_mov: float) -> Optional[float]:
    """Pico inesperado: salto do movimento da camera acima da base recente, com o aparelho firme.
    Aparelho em movimento => None (nao da para saber se e a cena ou o telefone)."""
    if imu_mov >= LIM_IMU_APARELHO:
        return None
    return round(_clip((mov - mov_base - K8_SALTO_MIN) / K8_SALTO_ESCALA), 4)


def f_locais_telefone(cam_amp: float, cam_conf: float, imu_mov: float, rssi_desvio: Optional[float],
                      mov: float, mov_base: float) -> List[Optional[float]]:
    """N1 camera (confianca cai se o aparelho se mexe) | N2 IMU | N3 RSSI (None sem Wi-Fi) | N4 audio (sem sensor) | N5 K8."""
    n1 = forca(cam_amp, _clip(cam_conf) * (1.0 - 0.8 * _clip(imu_mov)))
    n2 = forca(imu_mov, 0.8)
    n3 = None if rssi_desvio is None else forca(_clip(rssi_desvio / RSSI_ESCALA_DB), CONF_RSSI)
    return [round(n1, 4), round(n2, 4), None if n3 is None else round(n3, 4), None, k8_local(mov, mov_base, imu_mov)]


def status_telefone(status_hierarquia: str, imu_mov: float) -> str:
    """vazio | presenca | movimento (APARELHO em movimento) | anomalia."""
    if status_hierarquia == "anomalia":
        return "anomalia"
    return "movimento" if imu_mov >= LIM_IMU_APARELHO else status_hierarquia


_BASE = {"vazio": (158, 158, 158), "movimento": (255, 213, 79), "anomalia": (142, 36, 170)}
_PRESENCA = {"quieto": (41, 121, 255), "respiracao": (0, 188, 212), "movimento": (255, 167, 38), "misto": (255, 167, 38)}


def cor_fusao(f_final: float, status: str, tipo: str) -> Tuple[int, int, int, int]:
    """(R, G, B, A). Intensidade (alfa) cresce com F. Anomalia = roxo; aparelho em movimento = amarelo."""
    rgb = _PRESENCA.get(tipo, _PRESENCA["quieto"]) if status == "presenca" else _BASE.get(status, _BASE["vazio"])
    a = int(round(255 * (0.15 + 0.85 * _clip(f_final))))
    return rgb[0], rgb[1], rgb[2], a
