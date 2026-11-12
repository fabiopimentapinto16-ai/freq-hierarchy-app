"""Pesos, cores e limiares da hierarquia de frequencias. Tudo que e numero fica aqui."""

NIVEIS_FREQ = (
    {"id": "N1", "nome": "CSI 2.4 GHz",   "w_proprio": 1.00, "w_heranca": 0.00, "cor": "azul",     "hex": "#2979FF"},
    {"id": "N2", "nome": "CSI 5.0 GHz",   "w_proprio": 0.60, "w_heranca": 0.40, "cor": "ciano",    "hex": "#00BCD4"},
    {"id": "N3", "nome": "Micro-ondas",   "w_proprio": 0.55, "w_heranca": 0.45, "cor": "verde",    "hex": "#43A047"},
    {"id": "N4", "nome": "Infravermelho", "w_proprio": 0.55, "w_heranca": 0.45, "cor": "vermelho", "hex": "#E53935"},
    {"id": "N5", "nome": "K8 Anomalia",   "w_proprio": 0.50, "w_heranca": 0.50, "cor": "roxo",     "hex": "#8E24AA"},
)

# Modo TELEFONE PURO (sem CSI): mesmos pesos, outras fontes. Tudo ESTIMADO.
NIVEIS_TELEFONE = (
    {"id": "N1", "nome": "Câmera (movimento óptico)", "w_proprio": 1.00, "w_heranca": 0.00, "cor": "azul",     "hex": "#2979FF"},
    {"id": "N2", "nome": "IMU (movimento do aparelho)", "w_proprio": 0.60, "w_heranca": 0.40, "cor": "ciano",  "hex": "#00BCD4"},
    {"id": "N3", "nome": "Wi-Fi RSSI (contexto)",       "w_proprio": 0.55, "w_heranca": 0.45, "cor": "verde",  "hex": "#43A047"},
    {"id": "N4", "nome": "Áudio ambiente",              "w_proprio": 0.55, "w_heranca": 0.45, "cor": "vermelho", "hex": "#E53935"},
    {"id": "N5", "nome": "K8 Anomalia",                 "w_proprio": 0.50, "w_heranca": 0.50, "cor": "roxo",   "hex": "#8E24AA"},
)

LIM_TIPO = 0.35          # movimento/respiracao >= isso conta como presente no tipo_sinal
LIM_IMU_APARELHO = 0.5   # IMU acima disso: aparelho em movimento, leitura da camera nao e confiavel
K8_SALTO_MIN = 0.2       # salto de movimento acima da base recente a partir do qual K8 comeca a subir
K8_SALTO_ESCALA = 0.6
RSSI_ESCALA_DB = 6.0     # desvio-padrao do RSSI (dB) que vale amplitude 1.0
CONF_RSSI = 0.4          # RSSI e contexto fraco para corpo

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

# Camera (luz visivel): limites calibrados com ruido sintetico; AJUSTE com videos reais no Colab (ferramentas/colab_validacao.py)
# e depois repita os mesmos valores em AnaliseCamera.kt.
RAZAO_MIN = 30.0         # razao pico/mediana minima para aceitar "oscilacao clara" (ruido puro fica abaixo de ~25)
CLAREZA_ESCALA = 100.0   # quanto acima do RAZAO_MIN vale clareza 1.0
MOV_RUIDO = 1.0          # diferenca media (0..255) abaixo disso e ruido do sensor
MOV_ESCALA = 8.0         # diferenca acima do ruido que vale movimento 1.0
LUMA_MIN = 30.0          # cena escura demais abaixo disso
LUMA_MAX = 230.0         # cena estourada acima disso
BANDA_RESP_CAM = (0.1, 0.5)
BANDA_PULSO_CAM = (0.8, 2.0)
