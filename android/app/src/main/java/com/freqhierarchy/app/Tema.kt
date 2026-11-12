package com.freqhierarchy.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView

/** Visual "místico + científico": índigo profundo, dourado e violeta (místico) com números em monoespaçada e
 *  rótulo de origem do dado em tudo (científico). É estética: não afirma nada que o app não mede. */
object Tema {
    val FUNDO = Color.parseColor("#0B1026")
    val CARTAO = Color.parseColor("#151B3B")
    val TEXTO = Color.parseColor("#E8EAF6")
    val SUAVE = Color.parseColor("#9FA8DA")
    val OURO = Color.parseColor("#FFD54F")
    val VIOLETA = Color.parseColor("#7E57C2")
    val ESTIMADO = Color.parseColor("#64B5F6")
    val AO_VIVO = Color.parseColor("#81C784")
    val SIMULADO = Color.parseColor("#FFB74D")
    val SEM_SENSOR = Color.parseColor("#78849E")

    private fun redondo(cor: Int, raio: Float, borda: Int? = null) = GradientDrawable().apply {
        setColor(cor); cornerRadius = raio
        if (borda != null) setStroke(2, borda)
    }

    /** Aplica o tema em toda a árvore de views (botões, campos, caixas, barras). */
    fun aplicar(v: View) {
        when (v) {
            is Button -> { v.background = redondo(VIOLETA, 28f, OURO); v.setTextColor(Color.WHITE) }
            is EditText -> { v.setTextColor(TEXTO); v.setHintTextColor(SUAVE); v.background = redondo(CARTAO, 18f, SUAVE) }
            is CheckBox -> v.setTextColor(TEXTO)
            is Switch -> v.setTextColor(TEXTO)
            is ProgressBar -> if (v.progressTintList == null) v.progressTintList = android.content.res.ColorStateList.valueOf(OURO)
            is android.widget.Spinner -> v.background = redondo(CARTAO, 18f, SUAVE)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) aplicar(v.getChildAt(i))
    }

    fun adaptador(ctx: Context, itens: List<String>) = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_dropdown_item, itens) {
        override fun getView(p: Int, c: View?, g: ViewGroup): View =
            (super.getView(p, c, g) as TextView).also { it.setTextColor(TEXTO); it.setBackgroundColor(Color.TRANSPARENT) }
        override fun getDropDownView(p: Int, c: View?, g: ViewGroup): View =
            (super.getDropDownView(p, c, g) as TextView).also { it.setTextColor(TEXTO); it.setBackgroundColor(CARTAO) }
    }

    val MONO: Typeface = Typeface.MONOSPACE
    val TITULO: Typeface = Typeface.create("serif", Typeface.BOLD)
}
