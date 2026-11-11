package com.freqhierarchy.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.time.LocalDate

/** Três modos: Técnico (simulado) | CSI ao vivo (ESP32 via UDP) | Consciência (prática, local). Tudo programático, sem libs. */
class MainActivity : Activity() {
    private lateinit var corpo: LinearLayout
    private lateinit var estado: Consciencia.Estado
    private lateinit var arqEstado: File
    private var listener: CsiUdpListener? = null
    private val ui = Handler(Looper.getMainLooper())
    private var modo = 0

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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        arqEstado = File(filesDir, "consciencia.json")
        estado = Consciencia.carregar(arqEstado)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val abas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Técnico", "CSI ao vivo", "Consciência").forEachIndexed { i, nome ->
            abas.addView(Button(this).apply {
                text = nome; isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { abrir(i) }
            })
        }
        corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        raiz.addView(abas)
        raiz.addView(ScrollView(this).apply { addView(corpo) })
        setContentView(raiz)
        abrir(0)
    }

    override fun onDestroy() { listener?.parar(); super.onDestroy() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun abrir(m: Int) {
        if (m != 1) { listener?.parar(); listener = null }
        modo = m
        corpo.removeAllViews()
        when (m) { 0 -> telaTecnica(); 1 -> telaAoVivo(); else -> telaConsciencia() }
    }

    private fun texto(t: String, tam: Float = 15f, negrito: Boolean = false, cor: Int = Color.parseColor("#222222")) =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(cor)
            if (negrito) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun add(v: View) = corpo.addView(v)

    // ---------------- árvore de frequências (compartilhada pelos modos 0 e 1)
    private fun desenharArvore(destino: LinearLayout, r: Resultado) {
        destino.removeAllViews()
        for (n in r.niveis) {
            val linha = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val cab = if (!n.disponivel) "${n.id} ${n.nome}: sem sensor" else
                "${n.id} ${n.nome}: local %.2f → integrada %.2f%s".format(n.fLocal, n.fIntegrada, if (n.herdou) " (herdou)" else "")
            linha.addView(texto(cab, 14f, cor = if (n.disponivel) Color.parseColor(n.hex) else Color.GRAY))
            linha.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = (n.fIntegrada * 100).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (n.disponivel) n.hex else "#9E9E9E"))
            })
            destino.addView(linha)
        }
        val rotulo = when (r.status) { "anomalia" -> "ANOMALIA (K8 forte discordando dos níveis de baixo)"; "presenca" -> "PRESENÇA"; else -> "VAZIO" }
        destino.addView(texto("Resultado: $rotulo  |  F final %.2f%s".format(r.fFinal, if (r.simulado) "  (SIMULADO)" else ""), 16f, true))
    }

    // ---------------- modo Técnico
    private fun telaTecnica() {
        add(texto("Mede o CORPO (movimento e respiração) por sinal Wi-Fi. Não mede consciência, alma ou energia espiritual.", 13f, cor = Color.DKGRAY))
        add(texto("Cenários simulados (nenhum sensor real)", 16f, true))
        val arvore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cenarios.keys.toList())
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                desenharArvore(arvore, HierarchyEngine.integrar(cenarios.values.toList()[pos], simulado = true))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(sp); add(arvore)
    }

    // ---------------- modo CSI ao vivo
    private fun telaAoVivo() {
        add(texto("CSI ao vivo (ESP32)", 18f, true))
        add(texto("O celular não lê CSI bruto sozinho: o ESP32 mede e envia por UDP para o IP deste aparelho. " +
            "Níveis N2–N5 aparecem como \"sem sensor\" (não inventamos valores).", 13f, cor = Color.DKGRAY))
        val ip = CsiUdpListener.ipLocal() ?: "sem Wi-Fi? conecte na mesma rede do ESP32"
        add(texto("IP deste celular: $ip   |   porta UDP: ${Pesos.PORTA_CSI}", 15f, true))
        val status = texto("Aguardando pacotes…", 14f)
        val tipo = texto("", 15f, true)
        val arvore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(status); add(tipo); add(arvore)
        var contagem = 0
        var ultimo = 0L
        val suav = Suavizador(0.3)
        listener = CsiUdpListener(aoReceber = { p ->
            ultimo = System.currentTimeMillis(); contagem++
            val r = HierarchyEngine.integrar(suav.atualizar(HierarchyEngine.fLocaisDoPacote(p)))
            val t = HierarchyEngine.tipoRapido(p.movimento, p.respiracao)
            ui.post {
                if (modo != 1) return@post
                status.text = "Pacotes recebidos: $contagem  |  RSSI %.0f".format(p.rssi)
                tipo.text = "Sinal: $t  (mov %.2f, resp %.2f)".format(p.movimento, p.respiracao)
                desenharArvore(arvore, r)
            }
        }, aoErro = { msg -> ui.post { if (modo == 1) status.text = "Erro ao abrir a porta UDP: $msg" } }).also { it.iniciar() }
        // avisa quando para de chegar pacote
        val vigia = object : Runnable {
            override fun run() {
                if (modo != 1) return
                if (contagem > 0 && System.currentTimeMillis() - ultimo > 5000) {
                    status.text = "Sinal perdido: sem pacotes há mais de 5 s. Confira o IP e o Wi-Fi do ESP32."
                    tipo.text = ""; arvore.removeAllViews()      // não deixa o último valor na tela como se fosse atual
                    contagem = 0
                }
                ui.postDelayed(this, 1000)
            }
        }
        ui.postDelayed(vigia, 1000)
    }

    // ---------------- modo Consciência
    private fun telaConsciencia() {
        val info = Consciencia.NIVEIS[estado.nivel - 1]
        val m = Consciencia.metricas(estado)
        add(texto(Consciencia.AVISO, 13f, cor = Color.DKGRAY))
        add(texto("Nível ${info.n} de 7: ${info.nome}", 20f, true))
        add(texto(info.descricao))
        add(texto("Microprática do seu nível: ${info.pratica}", 15f, true))
        add(texto("Progresso no nível: %.0f%%".format(m.progresso), 14f))
        add(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = m.progresso.toInt() })
        add(texto("Práticas: ${m.praticas}/${Consciencia.PRATICAS_PARA_SUBIR} | dias com check-in (7d): ${m.dias7}/${Consciencia.DIAS_PARA_SUBIR} | " +
            "média (7d): %.2f | sequência: ${Consciencia.sequencia(estado)} dia(s)".format(m.media7), 13f))

        add(texto("Check-in de hoje", 17f, true))
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
        val registrar = Button(this).apply {
            text = "Registrar check-in"; isAllCaps = false
            setOnClickListener {
                val r = Consciencia.checkin(estado, barra.progress + 1, pausou.isChecked, pratica.isChecked, LocalDate.now(), nota.text.toString())
                Consciencia.salvar(estado, arqEstado)
                abrir(2)
                toast(r.mensagem)
            }
        }
        add(barra); add(valor); add(pausou); add(pratica); add(nota); add(registrar)
        add(texto("Faltar um dia não apaga seu progresso: é só voltar.", 13f, cor = Color.DKGRAY))

        add(texto("Ajustar meu nível (autoavaliação)", 16f, true))
        val sel = Spinner(this)
        sel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Consciencia.NIVEIS.map { "${it.n} – ${it.nome}" })
        sel.setSelection(estado.nivel - 1)
        add(sel)
        add(Button(this).apply {
            text = "Confirmar nível"; isAllCaps = false
            setOnClickListener {
                try {
                    Consciencia.ajustarNivel(estado, sel.selectedItemPosition + 1)
                    Consciencia.salvar(estado, arqEstado)
                    abrir(2); toast("Nível atualizado.")
                } catch (e: IllegalArgumentException) { toast(e.message ?: "Não foi possível ajustar.") }
            }
        })

        add(texto("Caminho", 16f, true))
        add(texto(Consciencia.NIVEIS.joinToString("\n") {
            val s = if (it.n < estado.nivel) "✓" else if (it.n == estado.nivel) "●" else "○"
            "$s ${it.n}. ${it.nome}"
        }, 14f))

        add(texto("Histórico (7 dias)", 16f, true))
        val hist = Consciencia.historico(estado, 7)
        add(texto(if (hist.isEmpty()) "Nenhum check-in ainda." else hist.joinToString("\n") {
            "${it.data}  nível ${it.nivel}  score %.2f  ${if (it.pratica) "prática ✓" else "prática –"}".format(it.score)
        }, 13f))
        add(texto(Consciencia.AVISO_CUIDADO, 12f, cor = Color.DKGRAY))
        add(texto(Consciencia.PRIVACIDADE, 12f, cor = Color.DKGRAY))
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
