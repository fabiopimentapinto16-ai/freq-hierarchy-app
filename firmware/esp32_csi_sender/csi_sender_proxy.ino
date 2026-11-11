/*
 * ESP32 -> UDP JSON para o app (porta 5005).
 * Pacote: {"ts":..,"amplitude":0..1,"confianca":0..1,"movimento":0..1,"respiracao":0..1,"rssi":-60,"proxy":0|1}
 *
 * STATUS: NAO TESTADO em placa (escrito sem ESP32 na bancada). Compile e confira o monitor serial.
 *
 * Duas fontes, escolhidas sozinhas:
 *  - CSI real (esp_wifi_set_csi): amplitude media das subportadoras. Precisa de trafego vindo do roteador;
 *    por isso o firmware faz ping no gateway ~10x/s.
 *  - RSSI-proxy (fallback): se nao chegar CSI em 5 s, usa o RSSI do Wi-Fi (muito mais grosseiro, "proxy":1,
 *    confianca baixa). Serve para testar a cadeia ESP32 -> celular, nao para medir respiracao com qualidade.
 *
 * respiracao/movimento aqui sao estimativas simples (duas medias moveis), nao FFT. A analise de banda
 * 0.1-0.5 Hz (FFT) fica no lado Python (main_csi.py).
 *
 * Edite SSID, SENHA e IP_CELULAR (o app mostra o IP do celular na aba "CSI ao vivo").
 */
#include <WiFi.h>
#include <WiFiUdp.h>
#include <math.h>
#include "esp_wifi.h"

#ifndef TENTAR_CSI
#define TENTAR_CSI 1       // 0 = so RSSI-proxy
#endif

const char* SSID = "SEU_WIFI";
const char* SENHA = "SUA_SENHA";
const char* IP_CELULAR = "192.168.0.50";
const uint16_t PORTA = 5005;

WiFiUDP udp;
volatile float csiAmp = 0;          // amplitude media do ultimo pacote CSI
volatile uint32_t csiCont = 0;      // pacotes CSI recebidos
volatile uint32_t csiUltimoMs = 0;

#if TENTAR_CSI
static void IRAM_ATTR csiCb(void* ctx, wifi_csi_info_t* info) {
  if (!info || !info->buf || info->len < 2) return;
  const int8_t* d = info->buf;
  int n = info->len / 2;
  float soma = 0;
  for (int i = 0; i < n; i++) {
    float im = d[2 * i], re = d[2 * i + 1];
    soma += sqrtf(re * re + im * im);
  }
  csiAmp = soma / n;
  csiCont++;
  csiUltimoMs = millis();
}

static void ligarCsi() {
  wifi_csi_config_t cfg = {};
  cfg.lltf_en = true; cfg.htltf_en = true; cfg.stbc_htltf2_en = true;
  cfg.ltf_merge_en = true; cfg.channel_filter_en = true; cfg.manu_scale = false;
  esp_wifi_set_csi_config(&cfg);
  esp_wifi_set_csi_rx_cb(&csiCb, NULL);
  esp_wifi_set_csi(true);
}
#endif

// ---- gerador de trafego: UDP no gateway (o roteador responde com frames que geram CSI em muitos modelos)
WiFiUDP trafego;
static void gerarTrafego() {
  trafego.beginPacket(WiFi.gatewayIP(), 7);
  trafego.write((const uint8_t*)"x", 1);
  trafego.endPacket();
}

float emaRapida = 0, emaLenta = 0, emaMuitoLenta = 0, maxAmp = 1, desvio = 0;
uint32_t ultimoEnvio = 0, ultimoTrafego = 0, ultimoCsiVisto = 0;

static float clip01(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }

void setup() {
  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  WiFi.begin(SSID, SENHA);
  Serial.print("Conectando");
  while (WiFi.status() != WL_CONNECTED) { delay(300); Serial.print("."); }
  Serial.printf("\nOK. IP do ESP32: %s | enviando para %s:%u\n", WiFi.localIP().toString().c_str(), IP_CELULAR, PORTA);
#if TENTAR_CSI
  ligarCsi();
#endif
}

void loop() {
  uint32_t agora = millis();
  if (agora - ultimoTrafego >= 100) { ultimoTrafego = agora; gerarTrafego(); }
  if (agora - ultimoEnvio < 100) return;          // 10 pacotes por segundo
  ultimoEnvio = agora;

  bool csiVivo = TENTAR_CSI && csiCont > ultimoCsiVisto && (agora - csiUltimoMs) < 5000;
  float amp, conf;
  if (csiVivo) {
    ultimoCsiVisto = csiCont;
    float a = csiAmp;
    maxAmp = fmaxf(a, maxAmp * 0.999f);           // normaliza pelo maior valor recente
    amp = clip01(a / maxAmp);
    conf = 0.9f;
  } else {
    float rssi = WiFi.RSSI();                      // -90 .. -30 dBm  ->  0 .. 1
    amp = clip01((rssi + 90.0f) / 60.0f);
    conf = 0.4f;                                   // proxy: confianca baixa de proposito
  }

  emaRapida += 0.30f * (amp - emaRapida);          // ~0.3 s
  emaLenta += 0.05f * (amp - emaLenta);            // ~2 s
  emaMuitoLenta += 0.01f * (amp - emaMuitoLenta);  // ~10 s
  float mov = fabsf(amp - emaRapida) + fabsf(emaRapida - emaLenta);
  desvio += 0.1f * (mov - desvio);
  float movimento = clip01(desvio * 8.0f);
  float resp = fabsf(emaLenta - emaMuitoLenta);    // oscilacao lenta (estimativa, nao FFT)
  float respiracao = clip01(resp * 10.0f) * (movimento < 0.5f ? 1.0f : 0.3f);

  char buf[200];
  int n = snprintf(buf, sizeof(buf),
    "{\"ts\":%.3f,\"amplitude\":%.3f,\"confianca\":%.2f,\"movimento\":%.3f,\"respiracao\":%.3f,\"rssi\":%d,\"proxy\":%d}",
    agora / 1000.0, amp, conf, movimento, respiracao, WiFi.RSSI(), csiVivo ? 0 : 1);
  udp.beginPacket(IP_CELULAR, PORTA);
  udp.write((const uint8_t*)buf, n);
  udp.endPacket();
}
