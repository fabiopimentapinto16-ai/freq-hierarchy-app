# Frequency Hierarchy App

Hierarquia de frequências (N1–N5 com herança e regra K8) para **detectar presença, movimento e respiração** por sinal Wi-Fi, mais um módulo de **prática de consciência** (níveis 1–7). Projeto independente do T-Token.

## O que isto mede (e o que não mede)

- **CSI (Channel State Information)** é a forma como o sinal Wi-Fi muda ao passar por uma sala. Pessoas se movendo ou respirando alteram esse sinal. Isso é **física do corpo**.
- **Não mede consciência, alma nem energia espiritual.** O app nunca afirma isso.
- O **nível de consciência (1–7) é prática e autoavaliação**: sobe com check-in diário e microprática, **não** vem do Wi-Fi. O sinal de respiração/movimento só pode *sugerir* uma pausa ("que tal parar 30 s e perceber como você está?").
- Não é terapia nem diagnóstico.

## Hierarquia

`F = amplitude × confiança`. Cada nível integra o seu valor local com o do anterior:
`F_int = w_proprio·F_local + w_heranca·F_anterior` (herança cortada se `F_anterior < 0.15`).

| Nível | Fonte | Disponível com ESP32? |
|---|---|---|
| N1 | CSI 2.4 GHz | **sim** (único sensor real) |
| N2 | CSI 5.0 GHz | não ("sem sensor") |
| N3 | Micro-ondas | não ("sem sensor") |
| N4 | Infravermelho | não ("sem sensor") |
| N5 | K8 Anomalia | não ("sem sensor") |

No modo ao vivo os valores passam por uma média móvel (`Suavizador`) para a tela não piscar no limiar, e se os pacotes param por mais de 5 s o app mostra "sinal perdido" em vez de manter o último valor. N2–N5 aparecem como **sem sensor**: o valor não é inventado, o nível apenas repete o anterior. Os cenários simulados (modo Técnico) usam os 5 níveis só para demonstrar a lógica e vêm marcados **SIMULADO**.

**Regra K8:** se o K8 local é fraco (< 0.35), ele não puxa a presença para baixo (`F_int = F_anterior`).
**Anomalia:** K8 forte (≥ 0.35) *discordando* dos níveis de baixo — o anterior está abaixo de 0.30 ou difere do K8 em mais de 0.40.
**Status:** `vazio` / `presenca` (F final ≥ 0.30) / `anomalia`.

Todos os números ficam em `config/pesos.py` (e espelhados em `HierarchyEngine.kt`).

## Respiração × movimento

Banda de respiração 0.1–0.5 Hz, movimento > 0.5 Hz, por FFT da janela (≥ 8 s). **Calibração:** com a sala parada por 20–30 s, o ruído de base é medido e descontado (`respiracao.calibrar`). No ESP32/Android a classificação é a rápida (campos `movimento` e `respiracao`), sem FFT.

## Consciência (níveis 1–7)

Reação · Percepção · Observação · Escolha · Responsabilidade · Coerência · Clareza e serviço. Cada nível tem uma microprática. Check-in diário: reação automática (1–5), pausou?, fez a prática?, nota opcional.
Sobe de nível com **≥ 7 práticas no nível, ≥ 5 dias com check-in nos últimos 7 e média ≥ 0.6**. Só sobe 1 por vez. **Faltar um dia não apaga o progresso.** Seus dados ficam **só no aparelho** (JSON local), nada é enviado.

## Como rodar

### Python (PC)
```bash
pip install -r requirements.txt
python main.py                 # cenários simulados no terminal
streamlit run app_streamlit.py # abas Corpo/sinal (Técnico) e Consciência
python -m unittest discover -s tests -t .
```

### Com ESP32 (CSI ao vivo)
1. Grave `firmware/esp32_csi_sender/csi_sender_proxy.ino` (veja o README da pasta; **não testado em placa**).
2. Descubra o IP do celular/PC (o app Android mostra na aba *CSI ao vivo*) e coloque em `IP_CELULAR`. Mesma rede Wi-Fi.
3. No PC: `python main_csi.py --calibrar 25` (porta UDP 5005; nos 25 s iniciais deixe a sala parada para medir o ruído de base). No Android: aba **CSI ao vivo**.

O celular comum **não lê CSI bruto sozinho**: o ESP32 mede e envia por UDP.

### Android
Projeto Kotlin em `android/` (`com.freqhierarchy.app`, minSdk 26, targetSdk 34). Modos: **Técnico**, **CSI**, **Câmera**, **Consciência**. O APK é gerado pelo GitHub Actions (*Build APK*, artefato `freq-hierarchy-apk`).

### Modo Câmera (Android)
Usa a câmera do celular só para calcular números: movimento (diferença entre quadros) e variações lentas de brilho, de onde saem estimativas de respiração (0,1–0,5 Hz) e pulso (0,8–2 Hz). A imagem não é mostrada, gravada nem enviada. A câmera comum vê **luz visível**: não lê infravermelho térmico nem micro-ondas, então N2–N5 seguem "sem sensor". Respiração e pulso são estimativas e exigem pessoa parada, boa luz e ~20 s. O limite de "oscilação clara" foi calibrado com ruído sintético (ruído puro fica abaixo; o teste cobre 40 sementes), não com pessoas reais.

## Testes e o que foi (ou não) verificado

- **Python:** testes unitários (`tests/`), incluindo vetores compartilhados. Rodados.
- **Kotlin ↔ Python:** `vetores/hierarquia.json` e `vetores/consciencia.json` são lidos pelos dois lados (`tests/test_vetores.py` e `android/app/src/test/.../VetoresTest.kt`), para dar o mesmo resultado. O Kotlin só é compilado/testado no GitHub Actions.
- **Firmware ESP32:** não testado em placa.
- **Interface Android e câmera:** não testadas em aparelho (a matemática da câmera tem testes unitários; o acesso à câmera só compila no CI).
