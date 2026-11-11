package com.freqhierarchy.app

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

/** Lê quadros da câmera SÓ para calcular números (grade 32x24 de luminância). Nada é mostrado, gravado nem enviado. */
class CameraSource(private val ctx: Activity, private val aoQuadro: (FloatArray, Double) -> Unit, private val aoErro: (String) -> Unit) {
    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var sessao: CameraCaptureSession? = null
    private var leitor: ImageReader? = null
    private var ultimoMs = 0L

    companion object { const val LARG = 32; const val ALT = 24; const val INTERVALO_MS = 100L }

    @Suppress("MissingPermission")
    fun iniciar() {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                ?: cm.cameraIdList.firstOrNull() ?: run { aoErro("Nenhuma câmera encontrada"); return }
            val mapa = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val tam = mapa?.getOutputSizes(ImageFormat.YUV_420_888)?.filter { it.width >= 160 }?.minByOrNull { it.width * it.height }
                ?: mapa?.getOutputSizes(ImageFormat.YUV_420_888)?.minByOrNull { it.width * it.height }
                ?: run { aoErro("Câmera sem formato YUV"); return }
            val t = HandlerThread("camera-fh").also { it.start() }
            thread = t
            val h = Handler(t.looper)
            val r = ImageReader.newInstance(tam.width, tam.height, ImageFormat.YUV_420_888, 2)
            leitor = r
            r.setOnImageAvailableListener({ rd ->
                val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val agora = System.currentTimeMillis()
                    if (agora - ultimoMs >= INTERVALO_MS) {
                        ultimoMs = agora
                        val p = img.planes[0]
                        val buf = p.buffer
                        val rs = p.rowStride
                        val grade = FloatArray(LARG * ALT)
                        for (gy in 0 until ALT) for (gx in 0 until LARG) {
                            val x = (gx * img.width) / LARG + img.width / (2 * LARG)
                            val y = (gy * img.height) / ALT + img.height / (2 * ALT)
                            grade[gy * LARG + gx] = (buf.get(y * rs + x).toInt() and 0xFF).toFloat()
                        }
                        aoQuadro(grade, agora / 1000.0)
                    }
                } finally { img.close() }
            }, h)
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    camera = c
                    try {
                        @Suppress("DEPRECATION")
                        c.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                sessao = s
                                try {
                                    val rq = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    rq.addTarget(r.surface)
                                    s.setRepeatingRequest(rq.build(), null, h)
                                } catch (e: Exception) { aoErro(e.message ?: "falha ao iniciar a captura") }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { aoErro("Falha ao configurar a câmera") }
                        }, h)
                    } catch (e: Exception) { aoErro(e.message ?: "falha na sessão da câmera") }
                }
                override fun onDisconnected(c: CameraDevice) { c.close() }
                override fun onError(c: CameraDevice, erro: Int) { c.close(); aoErro("Erro da câmera ($erro)") }
            }, h)
        } catch (e: CameraAccessException) { aoErro(e.message ?: "sem acesso à câmera")
        } catch (e: SecurityException) { aoErro("Sem permissão da câmera")
        } catch (e: Exception) { aoErro(e.message ?: e.javaClass.simpleName) }
    }

    fun parar() {
        try { sessao?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { leitor?.close() } catch (_: Exception) {}
        thread?.quitSafely()
        sessao = null; camera = null; leitor = null; thread = null
    }
}
