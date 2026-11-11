"""Pesos, cores e limiares da hierarquia de frequencias. Tudo que e numero fica aqui."""

NIVEIS_FREQ = (
    {"id": "N1", "nome": "CSI 2.4 GHz",   "w_proprio": 1.00, "w_heranca": 0.00, "cor": "azul",     "hex": "#2979FF"},
    {"id": "N2", "nome": "CSI 5.0 GHz",   "w_proprio": 0.60, "w_heranca": 0.40, "cor": "ciano",    "hex": "#00BCD4"},
    {"id": "N3", "nome": "Micro-ondas",   "w_proprio": 0.55, "w_heranca": 0.45, "cor": "verde",    "hex": "#43A047"},
    {"id": "N4", "nome": "Infravermelho", "w_proprio": 0.55, "w_heranca": 0.45, "cor": "vermelho", "hex": "#E53935"},
    {"id": "N5", "nome": "K8 Anomalia",   "w_proprio": 0.50, "w_heranca": 0.50, "cor": "roxo",     "hex": "#8E24AA"},
)

CORTE_HERANCA = 0.15     # se F_anterior < 0.15, nao herda
LIMIAR_K8 = 0.35         # K8 local abaixo disso nao puxa a presenca para baixo
LIMIAR_PRESENCA = 0.30   # F_final a partir daqui = alguem presente
DIF_ANOMALIA = 0.40      # K8 forte discordando dos niveis de baixo por mais que isso = anomalia

# respiracao x movimento
BANDA_RESP = (0.1, 0.5)      # Hz
BANDA_MOV_MIN = 0.5          # Hz (acima disso e movimento)
LIM_RMS_RESP = 0.02          # amplitude (0..1) minima na banda de respiracao
LIM_RMS_MOV = 0.05           # amplitude minima na banda de movimento

PORTA_CSI = 5005
