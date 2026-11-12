package com.freqhierarchy.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Energy Camera: ondas em torno do centro, desenhadas SOBRE o preview. Intensidade proporcional a F.
 *  Respiração (só se clara) = onda lenta; movimento = onda irregular; K8 = roxo pulsando. É visualização, não "aura". */
class OverlayView(ctx: Context) : View(ctx) {
    @Volatile var f = 0.0
    @Volatile var status = "vazio"
    @Volatile var tipo = "quieto"
    @Volatile var cor = intArrayOf(158, 158, 158, 60)
    @Volatile var legenda = ""

    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val miolo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fundoLegenda = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val maxR = min(w, h) * 0.45f
        val t = System.nanoTime() / 1e9
        val ff = f.toFloat().coerceIn(0f, 1f)
        val cr = cor
        val tipoAtual = tipo
        for (i in 0..3) {
            val base = maxR * (0.35f + 0.18f * i)
            val onda = when (tipoAtual) {
                "respiracao" -> 0.06 * sin(2 * PI * t / 4.0 + i * 0.6)
                "movimento", "misto" -> 0.08 * (sin(2 * PI * 1.3 * t + i) + 0.6 * sin(2 * PI * 3.1 * t + 2 * i))
                else -> 0.01 * sin(2 * PI * t / 5.0 + i)
            }
            var alfa = cr[3] * (1f - 0.18f * i)
            if (status == "anomalia") alfa *= (0.5f + 0.5f * sin(2 * PI * 2 * t).toFloat())
            traco.color = Color.argb(alfa.toInt().coerceIn(0, 255), cr[0], cr[1], cr[2])
            traco.strokeWidth = 2f + 10f * ff
            c.drawCircle(cx, cy, base * (0.6f + 0.6f * ff) + (onda * maxR).toFloat(), traco)
        }
        miolo.color = Color.argb((cr[3] * 0.35f).toInt().coerceIn(0, 255), cr[0], cr[1], cr[2])
        c.drawCircle(cx, cy, maxR * 0.12f * (0.6f + ff), miolo)
        val l = legenda
        if (l.isNotEmpty()) {
            c.drawRect(0f, h - 56f, w, h, fundoLegenda)
            c.drawText(l, 16f, h - 18f, txt)
        }
        postInvalidateOnAnimation()
    }
}
