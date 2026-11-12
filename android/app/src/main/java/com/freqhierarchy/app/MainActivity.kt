package com.freqhierarchy.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.time.LocalDate

/** Abas: Campo (câmera + overlay + F) | Portais | Flow | Memória | Config. Tudo programático, sem libs.
 *  Sem CSI: tudo ESTIMADO (sensores do celular). Com ESP32 enviando UDP: AO VIVO (CSI). */
class MainActivity : Activity() {
    private lateinit var corpo: LinearLayout
    private lateinit var estado: Consciencia.Estado
    private lateinit var arqEstado: File
    private lateinit var memoria: MemoriaSensor
    private lateinit var sensores: SensoresTelefone
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private var listener: CsiUdpListener? = null
    private var camera: CameraSource? = null
    private val ui = Handler(Looper.getMainLooper())
    private var modo = 0
    private var flowTimer: CountDownTimer? = null

    private val cenarios = linkedMapOf(
        "vazio" to listOf(0.05, 0.04, 0.03, 0.03, 0.02),
        "presenca" to listOf(0.7, 0.6, 0.5, 0.45, 0.2),
        "calado_respiracao" to listOf(0.55, 0.5, 0.4, 0.35, 0.15),
        "movimento" to listOf(0.85, 0.8, 0.7, 0.65, 0.3),
        "sinal_fraco" to listOf(0.2, 0.12, 0.1, 0.08, 0.05),
        "anomalia" to listOf(0.2, 0.15, 0.1, 0.1, 0.9),
        "alta_concordancia" to listOf(0.8, 0.75, 0.75, 0.75, 0.2),
        "so_n1" to listOf(0.6, null, null, null, null),
    )
    private val tipoDoCenario = mapOf("calado_respiracao" to "respiracao", "alta_concordancia" to "respiracao",
        "movimento" to "movimento", "anomalia" to "misto")

    // ---- estado do modo Campo
    @Volatile private var camMov = 0.0
    @Volatile private var camLuma = 0.0
    @Volatile private var camResp = AnaliseCamera.Pico(0.0, 0.0)
    @Volatile private var camPulso = AnaliseCamera.Pico(0.0, 0.0)
    @Volatile private var camUltimoMs = 0L
    @Volatile private var csiPacote: CsiPacote? = null
    @Volatile private var csiUltimoMs = 0L
    @Volatile private var csiContagem = 0
    @Volatile private var csiErro: String? = null
    private val serieLuma = ArrayList<Double>()
    private val serieTempo = ArrayList<Double>()
    private var gradeAnterior: FloatArray? = null
    private var movBase = 0.0
    private val suavTel = Suavizador(0.3)
    private val suavCsi = Suavizador(0.3)
    private var querCamera = false
    private var texView: TextureView? = null
    private var overlay: OverlayView? = null
    private var campoFonte: TextView? = null
    private var campoLeitura: TextView? = null
    private var campoArvore: LinearLayout? = null
    private var campoBotao: Button? = null
    private var ticks = 0
    private var sessaoIni = 0L
    private val sessaoMovs = ArrayList<Double>()
    private var sessaoFSoma = 0.0
    private var sessaoN = 0
    private var sessaoLuma = 0.0

    data class Leitura(val fonte: String, val resultado: Resultado, val tipo: String, val status: String, val detalhe: String)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        arqEstado = File(filesDir, "consciencia.json")
        estado = Consciencia.carregar(arqEstado)
        memoria = MemoriaSensor(File(filesDir, "memoria_sensor.json")).carregar()
        sensores = SensoresTelefone(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Tema.FUNDO) }
        raiz.addView(TextView(this).apply {
            text = "◈ Hierarquia de Frequências"; textSize = 20f; setTextColor(Tema.OURO); typeface = Tema.TITULO
            setPadding(dp(16), dp(14), dp(16), 0)
        })
        raiz.addView(TextView(this).apply {
            text = "instrumento de estimativa  ·  prática de consciência"; textSize = 11f; setTextColor(Tema.SUAVE)
            setPadding(dp(16), 0, dp(16), dp(6))
        })
        val abas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Campo", "Portais", "Flow", "Memória", "Config").forEachIndexed { i, nome ->
            abas.addView(Button(this).apply {
                text = nome; isAllCaps = false; textSize = 12f; setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { abrir(i) }
            })
        }
        corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        raiz.addView(abas)
        raiz.addView(ScrollView(this).apply { setBackgroundColor(Tema.FUNDO); addView(corpo) })
        Tema.aplicar(abas)
        setContentView(raiz)
        abrir(0)
    }

    override fun onPause() { super.onPause(); parar(); flowTimer?.cancel() }
    override fun onDestroy() { parar(); flowTimer?.cancel(); super.onDestroy() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Para tudo que roda em segundo plano (câmera, IMU, UDP) e grava a sessão do sensor na memória, se valer. */
    private fun parar() {
        pararCamera()
        listener?.parar(); listener = null
        sensores.parar()
    }

    private fun abrir(m: Int) {
        if (m != 0) parar()
        if (m != 2) { flowTimer?.cancel(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        modo = m
        corpo.removeAllViews()
        when (m) { 0 -> telaCampo(); 1 -> telaPortais(); 2 -> telaFlow(); 3 -> telaMemoria(); 4 -> telaConfig(); else -> telaTecnica() }
        Tema.aplicar(corpo)
    }

    private fun texto(t: String, tam: Float = 15f, negrito: Boolean = false, cor: Int = Tema.TEXTO) =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(cor)
            if (negrito) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun add(v: View) = corpo.addView(v)
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun cinza() = Tema.SUAVE

    // ---------------- árvore de frequências
    private fun desenharArvore(destino: LinearLayout, r: Resultado) {
        destino.removeAllViews()
        for (n in r.niveis) {
            val linha = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val cab = if (!n.disponivel) "${n.id} ${n.nome}: sem sensor" else
                "${n.id} ${n.nome}: local %.2f → integrada %.2f%s".format(n.fLocal, n.fIntegrada, if (n.herdou) " (herdou)" else "")
            linha.addView(texto(cab, 14f, cor = if (n.disponivel) Color.parseColor(n.hex) else Tema.SEM_SENSOR))
            linha.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = (n.fIntegrada * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (n.disponivel) n.hex else "#9E9E9E"))
            })
            destino.addView(linha)
        }
        val rotulo = when (r.status) { "anomalia" -> "ANOMALIA"; "presenca" -> "PRESENÇA"; "movimento" -> "APARELHO EM MOVIMENTO"; else -> "VAZIO" }
        destino.addView(texto("Resultado: $rotulo  |  F final %.2f".format(r.fFinal), 16f, true))
    }

    // ================= CAMPO =================
    private fun telaCampo() {
        val simulado = prefs.getBoolean("simulado", false)
        campoFonte = texto("", 14f, true).also { add(it) }
        val larguraPx = resources.displayMetrics.widthPixels - dp(32)
        val frame = FrameLayout(this)
        if (!simulado) {
            val tp = CameraSource.tamanhoPreview(this)
            val altura = if (tp != null) (larguraPx.toDouble() * tp.width / tp.height).toInt() else larguraPx * 4 / 3
            val tv = TextureView(this)
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { if (querCamera) ligarCamera() }
                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            }
            texView = tv
            frame.addView(tv, FrameLayout.LayoutParams(larguraPx, altura))
            frame.setBackgroundColor(Color.BLACK)
        } else {
            texView = null
            frame.setBackgroundColor(Color.parseColor("#101820"))
        }
        val ov = OverlayView(this)
        overlay = ov
        val alturaFrame = if (simulado) larguraPx * 3 / 4 else (texView?.layoutParams?.height ?: larguraPx * 4 / 3)
        frame.addView(ov, FrameLayout.LayoutParams(larguraPx, alturaFrame))
        add(frame.also { it.layoutParams = LinearLayout.LayoutParams(larguraPx, alturaFrame) })

        if (!simulado) {
            campoBotao = Button(this).apply {
                text = if (querCamera) "Desligar câmera" else "Ligar câmera"; isAllCaps = false
                setOnClickListener { if (camera != null || querCamera) { pararCamera(); querCamera = false; text = "Ligar câmera" } else pedirCamera() }
            }.also { add(it) }
        } else add(texto("Modo simulado ligado (Config): o overlay mostra o cenário escolhido, sem sensores.", 13f, cor = cinza()))
        campoLeitura = texto("", 14f, true).also { it.typeface = Tema.MONO; add(it) }
        campoArvore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { add(it) }
        add(texto(Consciencia.AVISO_ATENCAO, 12f, cor = cinza()))
        add(texto("A câmera só calcula números (movimento e variação de brilho). A imagem não é gravada nem enviada. " +
            "Respiração e pulso são estimativas e só aparecem com oscilação clara.", 12f, cor = cinza()))

        if (!simulado) {
            sensores.iniciar()
            listener = CsiUdpListener(aoReceber = { p -> csiPacote = p; csiUltimoMs = System.currentTimeMillis(); csiContagem++ },
                aoErro = { m -> csiErro = m }).also { it.iniciar() }
        }
        if (!simulado && querCamera && texView?.isAvailable == true) ligarCamera()
        ticks = 0
        ui.post(object : Runnable {
            override fun run() { if (modo != 0) return; passoCampo(); ui.postDelayed(this, 100) }
        })
    }

    private fun pedirCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            campoFonte?.text = "Preciso da permissão da câmera. Aceite e toque em Ligar câmera de novo."
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
            return
        }
        querCamera = true
        campoBotao?.text = "Desligar câmera"
        if (texView?.isAvailable == true) ligarCamera()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 10 && modo == 0 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) pedirCamera()
    }

    private fun ligarCamera() {
        val st = texView?.surfaceTexture ?: return
        camera?.parar()
        serieLuma.clear(); serieTempo.clear(); gradeAnterior = null; camMov = 0.0; camUltimoMs = 0L
        sessaoIni = System.currentTimeMillis(); sessaoMovs.clear(); sessaoFSoma = 0.0; sessaoN = 0; sessaoLuma = 0.0
        camera = CameraSource(this, st, aoQuadro = { grade, t ->
            val ant = gradeAnterior
            gradeAnterior = grade
            val luma = grade.average()
            val mov = if (ant != null) AnaliseCamera.movimento(AnaliseCamera.diferenca(ant, grade)) else 0.0
            serieLuma.add(luma); serieTempo.add(t)
            while (serieTempo.size > 2 && t - serieTempo[0] > 30.0) { serieLuma.removeAt(0); serieTempo.removeAt(0) }
            camMov += 0.3 * (mov - camMov)
            val dur = serieTempo.last() - serieTempo.first()
            if (dur >= 10.0) {
                val fs = (serieTempo.size - 1) / dur
                val x = serieLuma.toDoubleArray()
                camResp = AnaliseCamera.picoNaBanda(x, fs, 0.1, 0.5)
                camPulso = AnaliseCamera.picoNaBanda(x, fs, 0.8, 2.0, 0.05)
            }
            camLuma = luma
            camUltimoMs = System.currentTimeMillis()
        }, aoErro = { m -> ui.post { campoFonte?.text = "Erro da câmera: $m" } }).also { it.iniciar() }
    }

    private fun pararCamera() {
        camera?.parar(); camera = null
        if (sessaoIni > 0L) {
            val seg = ((System.currentTimeMillis() - sessaoIni) / 1000).toInt()
            if (sessaoN > 0) {
                val ok = memoria.registrar(MemoriaSensor.Sessao(LocalDate.now().toString(), "ESTIMADO", seg,
                    Math.round(sessaoFSoma / sessaoN * 1000) / 1000.0, MemoriaSensor.piso(sessaoMovs), Math.round(sessaoLuma / sessaoN * 10) / 10.0))
                if (ok) memoria.salvar()
            }
            sessaoIni = 0L
        }
    }

    /** Decide a fonte (SIMULADO / AO VIVO CSI / ESTIMADO) e calcula a hierarquia. Nunca mistura rótulos. */
    private fun calcular(agora: Long): Leitura {
        if (prefs.getBoolean("simulado", false)) {
            val nomes = cenarios.keys.toList()
            val nome = nomes[prefs.getInt("cenario", 1).coerceIn(0, nomes.size - 1)]
            val r = HierarchyEngine.integrar(cenarios[nome]!!, simulado = true, niveis = Pesos.NIVEIS_TELEFONE)
            val tipo = tipoDoCenario[nome] ?: "quieto"
            return Leitura(Fusao.ROTULO_SIMULADO, r, tipo, Fusao.statusTelefone(r.status, 0.0), "Cenário: $nome")
        }
        val p = csiPacote
        if (p != null && agora - csiUltimoMs < 5000) {
            val r = HierarchyEngine.integrar(suavCsi.atualizar(HierarchyEngine.fLocaisDoPacote(p)))
            val t = HierarchyEngine.tipoRapido(p.movimento, p.respiracao).let { if (it == "ambos") "misto" else it }
            return Leitura(Fusao.ROTULO_CSI, r, t, r.status, "Pacotes: $csiContagem | RSSI %.0f | N2–N5 sem sensor (CSI só traz o N1)".format(p.rssi))
        }
        val camViva = agora - camUltimoMs < 1000
        val imu = if (sensores.temImu) sensores.imuMov else 0.0
        val movCorr = if (camViva) memoria.descontar(camMov) else 0.0
        val resp = camResp
        val clara = camViva && AnaliseCamera.clareza(resp) > 0
        val loc = Fusao.fLocaisTelefone(maxOf(movCorr, if (clara) AnaliseCamera.clareza(resp) else 0.0), AnaliseCamera.confianca(camLuma),
            imu, sensores.rssiDesvio(), movCorr, movBase).toMutableList()
        if (!camViva) { loc[0] = null; loc[4] = null }          // câmera desligada: N1 e K8 ficam "sem sensor"
        if (!sensores.temImu) loc[1] = null
        val r = HierarchyEngine.integrar(suavTel.atualizar(loc), niveis = Pesos.NIVEIS_TELEFONE)
        if (camViva) movBase += 0.02 * (movCorr - movBase)       // base lenta para o K8 achar saltos
        val det = StringBuilder()
        if (camViva) {
            det.append("Movimento %.2f".format(movCorr))
            det.append(if (clara) " | Respiração ~%.0f/min (estimativa)".format(resp.freq * 60) else " | sem respiração clara")
            if (AnaliseCamera.clareza(camPulso) > 0) det.append(" | Pulso ~%.0f bpm (estimativa)".format(camPulso.freq * 60))
        } else det.append("Câmera desligada: N1 e K8 sem sensor.")
        if (imu >= Pesos.LIM_IMU_APARELHO) det.append("\nAparelho em movimento: leitura da câmera não é confiável.")
        if (csiContagem > 0) det.append("\nCSI sem pacotes há mais de 5 s: voltando para ESTIMADO.")
        csiErro?.let { det.append("\n(UDP indisponível: $it)") }
        if (camViva && sessaoIni > 0L) {
            sessaoFSoma += r.fFinal; sessaoN++; sessaoLuma += camLuma
            if (imu < 0.3 && sessaoMovs.size < 3000) sessaoMovs.add(camMov)
        }
        val tipo = Fusao.tipoSinal(movCorr, clara)
        return Leitura(Fusao.ROTULO_ESTIMADO, r, tipo, Fusao.statusTelefone(r.status, imu), det.toString())
    }

    private fun passoCampo() {
        val agora = System.currentTimeMillis()
        if (ticks % 10 == 0) sensores.amostrarRssi(agora)
        val l = calcular(agora)
        val ov = overlay
        if (ov != null) {
            ov.f = l.resultado.fFinal; ov.status = l.status; ov.tipo = l.tipo
            ov.cor = Fusao.corFusao(l.resultado.fFinal, l.status, l.tipo); ov.legenda = l.fonte
        }
        campoFonte?.apply {
            text = l.fonte
            setTextColor(when { l.fonte.startsWith("AO VIVO") -> Tema.AO_VIVO; l.fonte.startsWith("SIMULADO") -> Tema.SIMULADO; else -> Tema.ESTIMADO })
        }
        if (ticks % 5 == 0) {
            campoLeitura?.text = "Sinal: ${l.tipo} | status: ${l.status}\n${l.detalhe}"
            campoArvore?.let { desenharArvore(it, l.resultado) }
        }
        ticks++
    }

    // ================= PORTAIS =================
    private fun telaPortais() {
        val info = Consciencia.NIVEIS[estado.nivel - 1]
        val m = Consciencia.metricas(estado)
        add(texto(Consciencia.AVISO, 13f, cor = cinza()))
        add(texto("Nível ${info.n} de 7: ${info.nome}", 20f, true))
        add(texto("☽ Portal de hoje: ${info.portal}", 16f, true, Tema.OURO))
        add(texto(info.descricao))
        add(texto("Microprática do seu nível: ${info.pratica}", 15f, true))
        add(texto("Progresso no nível: %.0f%%".format(m.progresso), 14f))
        add(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = m.progresso.toInt() })
        add(texto("Dias com a prática (7d): ${m.diasPratica7}/${Consciencia.DIAS_PARA_SUBIR} | dias com check-in (7d): ${m.dias7} | " +
            "média (7d): %.2f | sequência: ${Consciencia.sequencia(estado)} dia(s)".format(m.media7), 13f))
        if (m.podeSubir) {
            add(texto("Suas práticas indicam que você pode avançar para o nível ${estado.nivel + 1}. Quem confirma é você.", 14f, true, Tema.AO_VIVO))
            add(Button(this).apply {
                text = "Confirmar avanço para o nível ${estado.nivel + 1}"; isAllCaps = false
                setOnClickListener {
                    try { Consciencia.ajustarNivel(estado, estado.nivel + 1); Consciencia.salvar(estado, arqEstado); abrir(1); toast("Nível atualizado.") }
                    catch (e: IllegalArgumentException) { toast(e.message ?: "Não foi possível avançar.") }
                }
            })
        }

        add(texto("✦ Check-in de hoje", 17f, true, Tema.OURO))
        add(texto("Hoje eu reagi no automático com que frequência? (1 = quase nunca, 5 = quase sempre)", 14f))
        val valor = texto("3", 16f, true)
        val barra = SeekBar(this).apply {
            max = 4; progress = 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { valor.text = (p + 1).toString() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val pausou = CheckBox(this).apply { text = "Consegui pausar antes de responder em algum momento" }
        val pratica = CheckBox(this).apply { text = "Fiz a microprática do meu nível" }
        val nota = EditText(this).apply { hint = Consciencia.PERGUNTA + " (opcional)"; maxLines = 3 }
        add(barra); add(valor); add(pausou); add(pratica); add(nota)
        add(Button(this).apply {
            text = "Registrar check-in"; isAllCaps = false
            setOnClickListener {
                val r = Consciencia.checkin(estado, barra.progress + 1, pausou.isChecked, pratica.isChecked, LocalDate.now(), nota.text.toString())
                Consciencia.salvar(estado, arqEstado)
                abrir(1); toast(r.mensagem)
            }
        })
        add(texto("Faltar um dia não apaga seu progresso: é só voltar.", 13f, cor = cinza()))

        add(texto("Ajustar meu nível (autoavaliação)", 16f, true))
        val sel = Spinner(this)
        sel.adapter = Tema.adaptador(this, Consciencia.NIVEIS.map { "${it.n} – ${it.nome}" })
        sel.setSelection(estado.nivel - 1)
        add(sel)
        add(Button(this).apply {
            text = "Confirmar nível"; isAllCaps = false
            setOnClickListener {
                try { Consciencia.ajustarNivel(estado, sel.selectedItemPosition + 1); Consciencia.salvar(estado, arqEstado); abrir(1); toast("Nível atualizado.") }
                catch (e: IllegalArgumentException) { toast(e.message ?: "Não foi possível ajustar.") }
            }
        })

        add(texto("✦ Caminho dos 7 portais", 16f, true, Tema.OURO))
        add(texto(Consciencia.NIVEIS.joinToString("\n") {
            val s = if (it.n < estado.nivel) "✓" else if (it.n == estado.nivel) "●" else "○"
            "$s ${it.n}. ${it.nome} — ${it.portal}"
        }, 14f))
        add(texto(Consciencia.AVISO_PORTAIS, 12f, cor = cinza()))
        add(texto(Consciencia.AVISO_CUIDADO, 12f, cor = cinza()))
        add(texto(Consciencia.PRIVACIDADE, 12f, cor = cinza()))
    }

    // ================= FLOW =================
    private fun telaFlow() {
        val info = Consciencia.NIVEIS[estado.nivel - 1]
        add(texto("◎ Modo Flow — ${info.portal}", 20f, true, Tema.OURO))
        add(texto("Treina as CONDIÇÕES de foco engajado. Não induz nenhum estado.", 13f, cor = cinza()))
        add(texto(Consciencia.focoDoNivel(estado.nivel), 15f))
        add(texto("Microprática: ${info.pratica}", 15f, true))
        add(texto(Consciencia.FLOW_CONDICOES.joinToString("\n") { "• $it" }, 14f))
        val dur = Spinner(this)
        val opcoes = listOf(5, 10, 15, 25)
        dur.adapter = Tema.adaptador(this, opcoes.map { "$it minutos" })
        dur.setSelection(1)
        val tarefa = EditText(this).apply { hint = "Qual é a tarefa única? (opcional)"; maxLines = 2 }
        add(dur); add(tarefa)
        add(Button(this).apply {
            text = "Silenciei as notificações. Iniciar"; isAllCaps = false
            setOnClickListener { iniciarFlow(opcoes[dur.selectedItemPosition], tarefa.text.toString()) }
        })
        val rs = Consciencia.resumoFlow(estado)
        add(texto("Últimos 30 dias: ${rs.sessoes} sessão(ões), ${rs.boaAbsorcao} com boa absorção relatada, ${rs.minutos} min no total.", 13f))
        add(texto(Consciencia.AVISO_ATENCAO, 12f, cor = cinza()))
        add(texto("Foco pela câmera frontal (olhar/rosto): não implementado nesta versão. O cronômetro e o seu relato são a base.", 12f, cor = cinza()))
    }

    private fun iniciarFlow(minutos: Int, tarefa: String) {
        corpo.removeAllViews()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val info = Consciencia.NIVEIS[estado.nivel - 1]
        val cron = texto("", 40f, true).apply { gravity = android.view.Gravity.CENTER }
        add(texto(info.portal + if (tarefa.isNotBlank()) " — $tarefa" else "", 17f, true))
        add(cron)
        add(texto("Faça a microprática: ${info.pratica}", 15f))
        add(texto("Uma coisa só. Se a atenção fugir, volte com calma.", 13f, cor = cinza()))
        val inicio = System.currentTimeMillis()
        flowTimer?.cancel()
        flowTimer = object : CountDownTimer(minutos * 60_000L, 1000) {
            override fun onTick(ms: Long) { cron.text = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60) }
            override fun onFinish() { finalizarFlow(minutos, tarefa) }
        }.start()
        add(Button(this).apply {
            text = "Terminei"; isAllCaps = false
            setOnClickListener {
                flowTimer?.cancel()
                val feitos = (((System.currentTimeMillis() - inicio) / 60000).toInt()).coerceIn(1, minutos)
                finalizarFlow(feitos, tarefa)
            }
        })
        add(Button(this).apply { text = "Cancelar"; isAllCaps = false; setOnClickListener { abrir(2) } })
    }

    private fun finalizarFlow(minutos: Int, tarefa: String) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        corpo.removeAllViews()
        add(texto("Como foi a sessão?", 20f, true))
        val pratica = CheckBox(this).apply { text = "Concluí a microprática" }
        add(pratica)
        add(texto("Estive absorvido na tarefa? (1 = nada, 5 = totalmente)", 14f))
        val valor = texto("3", 16f, true)
        val barra = SeekBar(this).apply {
            max = 4; progress = 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { valor.text = (p + 1).toString() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        add(barra); add(valor)
        add(Button(this).apply {
            text = "Registrar sessão"; isAllCaps = false
            setOnClickListener {
                val r = Consciencia.registrarFlow(estado, minutos, pratica.isChecked, barra.progress + 1, LocalDate.now(), tarefa)
                Consciencia.salvar(estado, arqEstado)
                abrir(2); toast(r.mensagem)
            }
        })
    }

    // ================= MEMÓRIA =================
    private fun telaMemoria() {
        add(texto("❖ Memória (só neste aparelho)", 20f, true, Tema.OURO))
        add(texto("Piso de ruído de movimento aprendido: %.3f (descontado das leituras da câmera)".format(memoria.baselineMov), 14f))
        add(texto("A memória ajuda a calibrar o ESTIMADO. Ela nunca vira CSI nem finge leitura ao vivo.", 12f, cor = cinza()))
        add(texto("Sessões do sensor", 16f, true))
        add(texto(if (memoria.sessoes.isEmpty()) "Nenhuma ainda. Ligue a câmera na aba Campo por pelo menos 30 s." else
            memoria.sessoes.takeLast(10).reversed().joinToString("\n") {
                "${it.data}  ${it.fonte}  ${it.segundos}s  F médio %.2f  brilho %.0f".format(it.fMedio, it.brilho)
            }, 13f))
        add(texto("Check-ins (14 dias)", 16f, true))
        val h = Consciencia.historico(estado, 14)
        add(texto(if (h.isEmpty()) "Nenhum check-in ainda." else h.joinToString("\n") {
            "${it.data}  nível ${it.nivel}  score %.2f  ${if (it.pratica) "prática ✓" else "prática –"}".format(it.score)
        }, 13f))
        val rs = Consciencia.resumoFlow(estado)
        add(texto("Flow (30 dias): ${rs.sessoes} sessão(ões), ${rs.boaAbsorcao} com boa absorção relatada.", 14f))
        add(texto(Consciencia.PRIVACIDADE, 12f, cor = cinza()))
    }

    // ================= CONFIG =================
    private fun telaConfig() {
        add(texto("⚙ Config", 20f, true, Tema.OURO))
        val sw = Switch(this).apply { text = "Modo simulado (demonstração sem sensores)"; isChecked = prefs.getBoolean("simulado", false) }
        sw.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("simulado", on).apply() }
        add(sw)
        val sp = Spinner(this)
        sp.adapter = Tema.adaptador(this, cenarios.keys.toList())
        sp.setSelection(prefs.getInt("cenario", 1).coerceIn(0, cenarios.size - 1))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.edit().putInt("cenario", pos).apply() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(texto("Cenário do modo simulado:", 14f)); add(sp)

        add(texto("Permissões", 16f, true))
        val cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        add(texto("Câmera: ${if (cam) "concedida" else "não concedida (será pedida ao ligar a câmera)"}", 14f))
        add(texto("IMU e Wi-Fi: sem permissão especial.", 13f, cor = cinza()))

        add(texto("ESP32 / CSI (opcional)", 16f, true))
        add(texto("IP deste celular: ${CsiUdpListener.ipLocal() ?: "sem Wi-Fi"}   |   porta UDP: ${Pesos.PORTA_CSI}", 14f, true))
        add(texto("O app escuta UDP sempre que a aba Campo está aberta. Se chegarem pacotes do ESP32, a fonte muda para AO VIVO (CSI); " +
            "sem pacotes, continua ESTIMADO. Wi-Fi do carro/Starlink é só conectividade, não sensor.", 13f, cor = cinza()))
        add(Button(this).apply { text = "Abrir cenários técnicos (CSI simulado)"; isAllCaps = false; setOnClickListener { abrir(5) } })

        add(texto("Verdades do app", 16f, true))
        add(texto("• Sem CSI, tudo é estimado pelos sensores do celular.\n• RSSI não é CSI.\n• Não lemos pensamentos, não curamos nada e não há aura.\n" +
            "• Os níveis 1–7 são prática humana, não score de Wi-Fi.\n• Câmera = luz visível, não infravermelho térmico.", 13f))
    }

    // ================= TÉCNICO (cenários CSI simulados) =================
    private fun telaTecnica() {
        add(texto("Cenários técnicos (CSI simulado)", 18f, true))
        add(texto("Mede o CORPO (movimento e respiração) por sinal Wi-Fi. Não mede consciência, alma ou energia espiritual. Nenhum sensor real aqui.", 13f, cor = cinza()))
        val arvore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sp = Spinner(this)
        sp.adapter = Tema.adaptador(this, cenarios.keys.toList())
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val r = HierarchyEngine.integrar(cenarios.values.toList()[pos], simulado = true)
                desenharArvore(arvore, r)
                arvore.addView(texto("(SIMULADO)", 12f, cor = cinza()))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(sp); add(arvore)
        add(Button(this).apply { text = "Voltar à Config"; isAllCaps = false; setOnClickListener { abrir(4) } })
    }
}
