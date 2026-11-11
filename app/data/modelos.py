"""Estruturas de dados (sem logica)."""
from dataclasses import dataclass, field, asdict
from typing import List, Optional


@dataclass
class NivelResultado:
    id: str
    nome: str
    cor: str
    hex: str
    f_local: Optional[float]      # None = sem sensor neste nivel
    f_integrada: float
    herdou: bool                  # True quando o valor veio (so ou em parte) do nivel anterior
    disponivel: bool              # False = sem sensor (nao inventamos valor)


@dataclass
class ResultadoHierarquia:
    niveis: List[NivelResultado] = field(default_factory=list)
    f_final: float = 0.0
    status: str = "vazio"         # vazio | presenca | anomalia
    simulado: bool = False

    def to_dict(self):
        return asdict(self)


@dataclass
class AnaliseSinal:
    tipo_sinal: str               # quieto | respiracao | movimento | ambos
    f_resp: float                 # Hz do pico na banda de respiracao (0 se nao ha)
    f_mov: float                  # Hz do pico na banda de movimento (0 se nao ha)
    regularidade: float           # 0..1 (quanto a respiracao e regular)
    rms_resp: float = 0.0
    rms_mov: float = 0.0

    def to_dict(self):
        return asdict(self)
