package com.ridedecider.app.data.accessibility.uber.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Interfaz para el motor de fallback OCR local on-device.
 */
interface UberOfferOcrFallbackEngine {
    /**
     * Procesa un [Bitmap] mediante OCR local on-device.
     *
     * @param bitmap Captura de pantalla a procesar.
     * @param cropRect Región opcional a recortar antes del OCR para optimizar velocidad.
     * @param screenshotLatencyMs Latencia transcurrida durante la captura de la imagen.
     * @return [OcrResult] inmutable con el texto o estado del error.
     */
    suspend fun processImage(
        bitmap: Bitmap?,
        cropRect: Rect? = null,
        screenshotLatencyMs: Long = 0L
    ): OcrResult

    /**
     * Cierra y libera los recursos del reconocedor OCR.
     */
    fun close()
}

/**
 * Implementación de [UberOfferOcrFallbackEngine] que utiliza Google ML Kit Text Recognition on-device.
 *
 * Totalmente desacoplada del pipeline de dominio y del HUD, 100% offline y ejecutada off-main thread.
 */
class MlKitUberOfferOcrFallback(
    private val textRecognizerProvider: (() -> TextRecognizer)? = null
) : UberOfferOcrFallbackEngine {

    private val isProcessing = AtomicBoolean(false)

    private val recognizer: TextRecognizer by lazy {
        textRecognizerProvider?.invoke() ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun processImage(
        bitmap: Bitmap?,
        cropRect: Rect?,
        screenshotLatencyMs: Long
    ): OcrResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (bitmap == null) {
            return@withContext OcrResult(
                status = OcrStatus.SCREENSHOT_ERROR,
                screenshotLatencyMs = screenshotLatencyMs,
                ocrLatencyMs = 0L,
                errorMessage = "Bitmap es nulo"
            )
        }

        // Protección de reentrada: No permitir ejecuciones simultáneas de OCR
        if (!isProcessing.compareAndSet(false, true)) {
            return@withContext OcrResult(
                status = OcrStatus.DEBOUNCED,
                screenshotLatencyMs = screenshotLatencyMs,
                ocrLatencyMs = 0L,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                errorMessage = "OCR ya en progreso (Debounced)"
            )
        }

        var croppedBitmap: Bitmap? = null
        try {
            // 1. Determinar el Bitmap a procesar (recortado o completo)
            val finalBitmap = if (cropRect != null && isRectValid(cropRect, bitmap.width, bitmap.height)) {
                try {
                    croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        cropRect.left,
                        cropRect.top,
                        cropRect.width(),
                        cropRect.height()
                    )
                    croppedBitmap
                } catch (_: Exception) {
                    bitmap
                }
            } else {
                // Fallback de recorte: Procesar el 65% inferior de la pantalla donde se ubica la tarjeta
                try {
                    val topOffset = (bitmap.height * 0.35).toInt()
                    val cropHeight = bitmap.height - topOffset
                    if (topOffset > 0 && cropHeight > 100) {
                        croppedBitmap = Bitmap.createBitmap(bitmap, 0, topOffset, bitmap.width, cropHeight)
                        croppedBitmap
                    } else {
                        bitmap
                    }
                } catch (_: Exception) {
                    bitmap
                }
            }

            val image = InputImage.fromBitmap(finalBitmap, 0)
            val ocrTaskResult: Text = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { textResult ->
                        if (continuation.isActive) {
                            continuation.resume(textResult)
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }
            }

            val rawText = ocrTaskResult.text.trim()
            val ocrLatency = System.currentTimeMillis() - startTime

            if (rawText.isBlank()) {
                OcrResult(
                    status = OcrStatus.EMPTY_TEXT,
                    rawText = null,
                    screenshotLatencyMs = screenshotLatencyMs,
                    ocrLatencyMs = ocrLatency,
                    imageWidth = finalBitmap.width,
                    imageHeight = finalBitmap.height
                )
            } else {
                OcrResult(
                    status = OcrStatus.SUCCESS,
                    rawText = rawText,
                    screenshotLatencyMs = screenshotLatencyMs,
                    ocrLatencyMs = ocrLatency,
                    imageWidth = finalBitmap.width,
                    imageHeight = finalBitmap.height
                )
            }
        } catch (e: Exception) {
            val ocrLatency = System.currentTimeMillis() - startTime
            OcrResult(
                status = OcrStatus.OCR_ERROR,
                rawText = null,
                screenshotLatencyMs = screenshotLatencyMs,
                ocrLatencyMs = ocrLatency,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                errorMessage = e.message ?: "Error desconocido en ML Kit"
            )
        } finally {
            // Liberar memoria del Bitmap recortado temporal
            try {
                if (croppedBitmap != null && !croppedBitmap.isRecycled && croppedBitmap != bitmap) {
                    croppedBitmap.recycle()
                }
            } catch (_: Exception) {}
            isProcessing.set(false)
        }
    }

    private fun isRectValid(rect: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        return rect.left >= 0 && rect.top >= 0 &&
                rect.right <= imageWidth && rect.bottom <= imageHeight &&
                rect.width() > 50 && rect.height() > 50
    }

    override fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {}
    }
}
