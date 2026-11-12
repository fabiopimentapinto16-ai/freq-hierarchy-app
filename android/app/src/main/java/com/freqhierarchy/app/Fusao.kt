package com.freqhierarchy.app

/** Modo TELEFONE PURO: junta câmera, IMU e RSSI em F locais (N1..N5) e decide tipo, status e cor.
 *  Tudo ESTIMADO (sensores do celular). Não calcula CSI e não lê pensamento. Espelha app/core/fusao.py (vetores/fusao.json). */
object Fusao {
    const val ROTULO_ESTIMADO = "ESTIMADO (sensores do celular) — sem CSI"
    const val ROTULO_CSI = "AO VIVO (CSI)"
    const val ROTULO_SIMULADO = "SIMULADO (demonstração, nenhum sensor real)"

    private fun clip(x: Double, a: Double = 0.0, b: Double = 1.0) = maxOf(a, minOf(b, x))
    private fun r4(x: Double) = Math.round(x * 10000.0) / 10000.0

    /** quieto | movimento | respiracao | misto. Respiração só entra com evidência forte. */
    fun tipoSinal(movimento: Double, respiracaoClara: Boolean): String {
        val m = movimento >= Pesos.LIM_TIPO_RAPIDO
        return if (m && respiracaoClara) "misto" else if (m) "movimento" else if (respiracaoClara) "respiracao" else "quieto"
    }

    /** Pico inesperado: salto do movimento da câmera acima da base recente, com o aparelho firme.
     *  Aparelho em movimento => null (não dá para saber se é a cena ou o telefone). */
    fun k8Local(mov: Double, movBase: Double, imuMov: Double): Double? {
        if (imuMov >= Pesos.LIM_IMU_APARELHO) return null
        return r4(clip((mov - movBase - Pesos.K8_SALTO_MIN) / Pesos.K8_SALTO_ESCALA))
    }

    /** N1 câmera (confiança cai se o aparelho se mexe) | N2 IMU | N3 RSSI (null sem Wi-Fi) | N4 áudio (sem sensor) | N5 K8. */
    fun fLocaisTelefone(camAmp: Double, camConf: Double, imuMov: Double, rssiDesvio: Double?, mov: Double, movBase: Double): List<Double?> {
        val n1 = HierarchyEngine.forca(camAmp, clip(camConf) * (1.0 - 0.8 * clip(imuMov)))
        val n2 = HierarchyEngine.forca(imuMov, 0.8)
        val n3 = rssiDesvio?.let { HierarchyEngine.forca(clip(it / Pesos.RSSI_ESCALA_DB), Pesos.CONF_RSSI) }
        return listOf(r4(n1), r4(n2), n3?.let { r4(it) }, null, k8Local(mov, movBase, imuMov))
    }

    /** vazio | presenca | movimento (APARELHO em movimento) | anomalia. */
    fun statusTelefone(statusHierarquia: String, imuMov: Double): String =
        if (statusHierarquia == "anomalia") "anomalia" else if (imuMov >= Pesos.LIM_IMU_APARELHO) "movimento" else statusHierarquia

    /** Cor ARGB. Intensidade (alfa) cresce com F. Anomalia = roxo; aparelho em movimento = amarelo. */
    fun corFusao(fFinal: Double, status: String, tipo: String): IntArray {
        val rgb = if (status == "presenca") when (tipo) {
            "respiracao" -> intArrayOf(0, 188, 212)
            "movimento", "misto" -> intArrayOf(255, 167, 38)
            else -> intArrayOf(41, 121, 255)
        } else when (status) {
            "movimento" -> intArrayOf(255, 213, 79)
            "anomalia" -> intArrayOf(142, 36, 170)
            else -> intArrayOf(158, 158, 158)
        }
        val a = Math.round(255 * (0.15 + 0.85 * clip(fFinal))).toInt()
        return intArrayOf(rgb[0], rgb[1], rgb[2], a)
    }
}
