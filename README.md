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

## Consciência (níveis 1–7) — portais

Reação (Portal da Pausa) · Percepção (Nomear) · Observação (Testemunha) · Escolha · Responsabilidade (Meu) · Coerência (Alinhar) · Sabedoria (Serviço, manutenção, não um fim). Cada nível tem uma microprática. Check-in diário: reação automática (1–5), pausou?, fez a prática?, nota opcional.
O app **sugere** avançar quando há **>= 5 dos últimos 7 dias com a prática do nível** e **média do score >= 0,6**; **quem confirma é você**. Só se avança 1 nível por vez. Faltar um dia não apaga o progresso. O nível **não** vem de Wi-Fi nem de câmera. Seus dados ficam só no aparelho (JSON local).

**Modo Flow:** treina condições de foco (uma tarefa, meta curta, feedback imediato, pouca distração, cronômetro). O app não induz nenhum estado e não afirma "você entrou em flow".

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

### Android (Kotlin, `android/`)
`com.freqhierarchy.app`, minSdk 26, targetSdk 34. Visual índigo/dourado (estética mística) com números em fonte monoespaçada e **rótulo de origem do dado** em tudo (lado científico). O APK sai do GitHub Actions (*Build APK*) e fica na Release `apk-latest`.

Cinco abas:
1. **Campo**: preview da câmera + overlay de ondas (intensidade ∝ F; respiração clara = onda lenta; movimento = irregular; K8 = roxo pulsando), F, status e a árvore N1–N5.
2. **Portais**: nível (1–7), portal do dia, microprática, check-in de 3 perguntas, progresso, caminho. O app **sugere** o avanço; **você confirma**.
3. **Flow**: sessão guiada (portal, duração, cronômetro, microprática, autoavaliação 1–5). Registra "sessão com boa absorção relatada", nunca "você entrou em flow".
4. **Memória**: piso de ruído aprendido, sessões do sensor, check-ins e flow (tudo local).
5. **Config**: modo simulado (demo sem sensores), permissões, IP do celular para o ESP32, cenários técnicos.

**Fonte do dado (sempre escrita na tela):**
- `ESTIMADO (sensores do celular) — sem CSI`: padrão. N1 câmera (movimento óptico), N2 IMU (movimento do aparelho), N3 Wi-Fi RSSI (contexto fraco; RSSI **não** é CSI), N4 áudio (não implementado: "sem sensor"), N5 K8 (salto de movimento acima da base recente, com o aparelho firme).
- `AO VIVO (CSI)`: só quando chegam pacotes UDP do ESP32 nos últimos 5 s. Se param, volta sozinho para ESTIMADO.
- `SIMULADO`: modo demonstração.

IMU forte (>= 0,5) = "aparelho em movimento": a confiança da câmera cai e o K8 não julga. A memória só desconta o ruído de movimento; **nunca** fabrica CSI.

### Câmera (luz visível)
Só calcula números (movimento por diferença de quadros e variação lenta de brilho); nada é gravado ou enviado. Respiração (0,1–0,5 Hz) e pulso (0,8–2 Hz) são **estimativas** e só aparecem com oscilação clara (razão pico/mediana >= `RAZAO_MIN`). A câmera comum não lê infravermelho térmico nem micro-ondas.

### Validar no Colab (ajustar os parâmetros)
```python
!git clone https://github.com/fabiopimentapinto16-ai/freq-hierarchy-app.git
%cd freq-hierarchy-app
%run ferramentas/colab_validacao.py
```
Roda a mesma matemática da câmera em (A) sinais sintéticos (falso alarme e acerto), (B) **seus vídeos** do celular (nomeie `*_resp.mp4`, `*_vazio.mp4`, `*_mov.mp4`) e (C) sugere um `RAZAO_MIN`. Isso valida o **algoritmo**, não o app no aparelho.

### Desvios da spec original (e por quê)
- **Camera2 em vez de CameraX** (sem dependências externas, menos risco de build).
- **Persistência em JSON local** em vez de Room/DataStore.
- **N2 (IMU)** entra como movimento do aparelho; ele **não** vira "presença" sozinha: se o aparelho se mexe, o status vira "movimento do aparelho".
- **Não implementados** (opcionais da spec): áudio (N4), foco atencional pela câmera frontal, scan de Wi-Fi/auto-ativação por SSID.

## Testes e o que foi (ou não) verificado

- **Python:** testes unitários (`tests/`), incluindo vetores compartilhados. Rodados.
- **Matemática da câmera:** testada com séries sintéticas e com vídeos sintéticos gerados em OpenCV (script do Colab). **Ainda não** com vídeos reais de pessoas.
- **Kotlin ↔ Python:** `vetores/hierarquia.json` e `vetores/consciencia.json` são lidos pelos dois lados (`tests/test_vetores.py` e `android/app/src/test/.../VetoresTest.kt`), para dar o mesmo resultado. O Kotlin só é compilado/testado no GitHub Actions.
- **Firmware ESP32:** não testado em placa.
- **Interface Android e câmera:** não testadas em aparelho (a matemática da câmera tem testes unitários; o acesso à câmera só compila no CI).
