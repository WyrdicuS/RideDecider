package com.ridedecider.app.data.accessibility.uber.ocr

/**
 * Estado del resultado de la ejecución del fallback OCR.
 */
enum class OcrStatus {
    /** El proceso de OCR reconoció texto en la imagen. */
    SUCCESS,

    /** El proceso de OCR finalizó correctamente pero no encontró texto en la imagen. */
    EMPTY_TEXT,

    /** Error durante el procesamiento de reconocimiento de texto. */
    OCR_ERROR,

    /** Error al capturar o recortar la imagen de la pantalla. */
    SCREENSHOT_ERROR,

    /** Intento de OCR descartado por throttling o ejecución simultánea en progreso. */
    DEBOUNCED,

    /** El fallback OCR no está disponible (ej. API < 30 o falta de servicio). */
    UNAVAILABLE
}

/**
 * Resultado inmutable del procesamiento de captura y OCR local on-device.
 */
data class OcrResult(
    val status: OcrStatus,
    val rawText: String? = null,
    val screenshotLatencyMs: Long = 0L,
    val ocrLatencyMs: Long = 0L,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    /**
     * Latencia total combinada del proceso de captura + OCR (milisegundos).
     */
    val totalLatencyMs: Long
        get() = screenshotLatencyMs + ocrLatencyMs

    /**
     * Indica si el resultado contiene texto válido reconocido.
     */
    val hasText: Boolean
        get() = status == OcrStatus.SUCCESS && !rawText.isNullOrBlank()
}
