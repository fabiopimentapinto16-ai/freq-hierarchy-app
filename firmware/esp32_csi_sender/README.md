# Firmware ESP32 (emissor UDP)

**Status: não testado em placa.** Foi escrito sem um ESP32 em mãos. Compile, abra o monitor serial (115200) e confira.

## O que faz
Conecta no seu Wi-Fi e envia 10 pacotes/s em JSON para o celular (UDP, porta 5005):

```json
{"ts":12.3,"amplitude":0.71,"confianca":0.9,"movimento":0.12,"respiracao":0.45,"rssi":-58,"proxy":0}
```

- `proxy: 0` = veio de CSI real. `proxy: 1` = CSI não chegou em 5 s e o firmware caiu para o RSSI (grosseiro, confiança 0.4).
- `movimento` e `respiracao` são estimativas simples (médias móveis). A análise de banda por FFT (respiração 0.1–0.5 Hz) fica no Python (`main_csi.py`).

## Como usar
1. Arduino IDE (ou PlatformIO) com o pacote **esp32 by Espressif**.
2. Abra `csi_sender_proxy.ino` e edite `SSID`, `SENHA` e `IP_CELULAR`.
3. No app, aba **CSI ao vivo**, o IP do celular aparece na tela. Celular e ESP32 precisam estar **na mesma rede**.
4. Grave na placa. O app mostra "Pacotes recebidos" subindo.

Se `esp_wifi_set_csi` não compilar na sua versão do core, use `#define TENTAR_CSI 0` (só RSSI-proxy) para testar a cadeia.

## Limites honestos
- CSI depende de tráfego do roteador; alguns roteadores não respondem ao tráfego gerado e o CSI fica raro (cai para proxy).
- Isto mede **movimento e respiração do corpo** perto do ESP32. Não mede consciência nem energia espiritual.
