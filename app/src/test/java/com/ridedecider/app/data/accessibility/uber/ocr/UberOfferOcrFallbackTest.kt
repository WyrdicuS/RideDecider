package com.ridedecider.app.data.accessibility.uber.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.ridedecider.app.data.accessibility.uber.RawUberTripOffer
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityProcessor
import com.ridedecider.app.data.accessibility.uber.UberOfferScreenType
import com.ridedecider.app.data.accessibility.uber.UberOfferValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite completa de pruebas unitarias deterministas para [UberOfferOcrFallbackEngine] y el pipeline de OCR.
 */
class UberOfferOcrFallbackTest {

    private class FakeOcrEngine(
        private val mockResultText: String? = null,
        private val shouldFail: Boolean = false,
        private val latencyMs: Long = 20L
    ) : UberOfferOcrFallbackEngine {

        override suspend fun processImage(
            bitmap: Bitmap?,
            cropRect: Rect?,
            screenshotLatencyMs: Long
        ): OcrResult {
            if (shouldFail) {
                return OcrResult(
                    status = OcrStatus.OCR_ERROR,
                    screenshotLatencyMs = screenshotLatencyMs,
                    ocrLatencyMs = latencyMs,
                    errorMessage = "Error simulado en Fake OCR Engine"
                )
            }

            if (mockResultText.isNullOrBlank()) {
                return OcrResult(
                    status = OcrStatus.EMPTY_TEXT,
                    rawText = null,
                    screenshotLatencyMs = screenshotLatencyMs,
                    ocrLatencyMs = latencyMs
                )
            }

            return OcrResult(
                status = OcrStatus.SUCCESS,
                rawText = mockResultText,
                screenshotLatencyMs = screenshotLatencyMs,
                ocrLatencyMs = latencyMs,
                imageWidth = 1080,
                imageHeight = 800
            )
        }

        override fun close() {}
    }

    @Test
    fun test1_ocrSuccess_returnsTextAndMetrics() = runBlocking {
        val ocrEngine = FakeOcrEngine(mockResultText = "Oferta Exclusiva\n8,50 €\n1,2 km · 5 min\nCalle Gran Vía 12")

        val result = ocrEngine.processImage(null, screenshotLatencyMs = 15L)

        assertEquals(OcrStatus.SUCCESS, result.status)
        assertTrue(result.hasText)
        assertNotNull(result.rawText)
        assertTrue(result.rawText!!.contains("8,50 €"))
        assertEquals(15L, result.screenshotLatencyMs)
        assertEquals(35L, result.totalLatencyMs)
    }

    @Test
    fun test2_ocrEmptyText_returnsEmptyStatus() = runBlocking {
        val ocrEngine = FakeOcrEngine(mockResultText = "")

        val result = ocrEngine.processImage(null)

        assertEquals(OcrStatus.EMPTY_TEXT, result.status)
        assertFalse(result.hasText)
        assertNull(result.rawText)
    }

    @Test
    fun test3_ocrError_returnsOcrErrorStatus() = runBlocking {
        val ocrEngine = FakeOcrEngine(shouldFail = true)

        val result = ocrEngine.processImage(null)

        assertEquals(OcrStatus.OCR_ERROR, result.status)
        assertFalse(result.hasText)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun test4_screenshotError_handledInResult() {
        val result = OcrResult(
            status = OcrStatus.SCREENSHOT_ERROR,
            errorMessage = "Fallo al capturar HardwareBuffer"
        )

        assertEquals(OcrStatus.SCREENSHOT_ERROR, result.status)
        assertFalse(result.hasText)
        assertEquals("Fallo al capturar HardwareBuffer", result.errorMessage)
    }

    @Test
    fun test5_shouldTriggerOcrFallback_apiLessThan30_returnsFalse() {
        val processor = UberAccessibilityProcessor()
        val emptyOffer = RawUberTripOffer(detectedOfferType = UberOfferScreenType.NO_OFFER)

        val shouldTrigger = processor.shouldTriggerOcrFallback(emptyOffer, nodeCount = 33, isStructuralJump = true, sdkVersion = 28)

        assertFalse(shouldTrigger)
    }

    @Test
    fun test6_shouldTriggerOcrFallback_whenRutaAHasFare_returnsFalse() {
        val processor = UberAccessibilityProcessor()
        val validRutaAOffer = RawUberTripOffer(
            rawFare = 8.50,
            currency = "EUR",
            detectedOfferType = UberOfferScreenType.TRIP_OFFER
        )

        val shouldTrigger = processor.shouldTriggerOcrFallback(validRutaAOffer, nodeCount = 33, isStructuralJump = true, sdkVersion = 30)

        assertFalse(shouldTrigger)
    }

    @Test
    fun test7_shouldTriggerOcrFallback_whenRutaAHasNoFareAndStructuralJump_returnsTrue() {
        val processor = UberAccessibilityProcessor()
        val noFareOffer = RawUberTripOffer(detectedOfferType = UberOfferScreenType.NO_OFFER)

        val shouldTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 33, isStructuralJump = true, sdkVersion = 30)

        assertTrue(shouldTrigger)
    }

    @Test
    fun test8_shouldTriggerOcrFallback_withHighNodeCountComposeOffer_returnsTrue() {
        val processor = UberAccessibilityProcessor()
        val noFareOffer = RawUberTripOffer(detectedOfferType = UberOfferScreenType.NO_OFFER)

        // Con 176 nodos (realme Android 15 Compose offer card sobre mapa) sin salto estructural
        val shouldTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 176, isStructuralJump = false, sdkVersion = 30)

        assertTrue(shouldTrigger)
    }

    @Test
    fun test8b_shouldTriggerOcrFallback_withVeryLowNodeCount_returnsFalse() {
        val processor = UberAccessibilityProcessor()
        val noFareOffer = RawUberTripOffer(detectedOfferType = UberOfferScreenType.NO_OFFER)

        // Con menos de 10 nodos (pantalla vacía/desconocida) sin salto estructural
        val shouldTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 5, isStructuralJump = false, sdkVersion = 30)

        assertFalse(shouldTrigger)
    }

    @Test
    fun test9_debounce_preventsRepeatedOcrTriggers() {
        val processor = UberAccessibilityProcessor()
        val noFareOffer = RawUberTripOffer(detectedOfferType = UberOfferScreenType.NO_OFFER)

        val now = 1000000L
        val firstTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 33, isStructuralJump = true, currentTime = now, sdkVersion = 30)
        assertTrue(firstTrigger)

        processor.recordOcrAttempt(now)

        // Intento 500ms después (dentro del cooldown de 2000ms)
        val secondTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 33, isStructuralJump = true, currentTime = now + 500L, sdkVersion = 30)
        assertFalse(secondTrigger)

        // Intento 2500ms después (superado el cooldown)
        val thirdTrigger = processor.shouldTriggerOcrFallback(noFareOffer, nodeCount = 33, isStructuralJump = true, currentTime = now + 2500L, sdkVersion = 30)
        assertTrue(thirdTrigger)
    }

    @Test
    fun test10_parseFromText_validOcrString_extractsRawOfferCorrectly() {
        val parser = UberAccessibilityParser()
        val ocrText = """
            OFERTA EXCLUSIVA
            8,50 €
            A 1,2 km (3 min) de distancia
            Viaje de 4,8 km (12 min)
            Aceptar
        """.trimIndent()

        val rawOffer = parser.parseFromText(ocrText)

        assertEquals(8.50, rawOffer.rawFare!!, 0.01)
        assertEquals("EUR", rawOffer.currency)
        assertEquals(1.2, rawOffer.pickupDistanceKm!!, 0.01)
        assertEquals(3.0, rawOffer.pickupDurationMinutes!!, 0.01)
        assertEquals(4.8, rawOffer.tripDistanceKm!!, 0.01)
        assertEquals(12.0, rawOffer.tripDurationMinutes!!, 0.01)
        assertEquals(UberOfferScreenType.TRIP_OFFER, rawOffer.detectedOfferType)
    }

    @Test
    fun test11_parseFromText_insufficientText_doesNotProduceFalseOffer() {
        val parser = UberAccessibilityParser()
        val validator = UberOfferValidator()
        val ocrText = "Ajustes de cuenta\nUsuario en línea\nSoporte"

        val rawOffer = parser.parseFromText(ocrText)
        val validationResult = validator.validate(rawOffer)

        assertFalse(validationResult.isValidOffer)
        assertNull(rawOffer.rawFare)
        assertEquals(UberOfferScreenType.NO_OFFER, validationResult.screenType)
    }

    @Test
    fun test12_processOcrResult_evaluatesOfferCorrectly() {
        val processor = UberAccessibilityProcessor()
        val ocrResult = OcrResult(
            status = OcrStatus.SUCCESS,
            rawText = "OFERTA EXCLUSIVA\n8,50 €\nA 1,2 km (3 min) de distancia\nViaje de 4,8 km (12 min)\nAceptar",
            screenshotLatencyMs = 20L,
            ocrLatencyMs = 15L
        )

        val processResult = processor.processOcrResult(ocrResult)

        assertTrue(processResult is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluation = (processResult as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation
        assertNotNull(evaluation.trip)
        assertEquals(8.50, evaluation.trip.rawFare!!, 0.01)
    }

    @Test
    fun test13_processOcrResult_emptyResult_returnsInvalidOffer() {
        val processor = UberAccessibilityProcessor()
        val ocrResult = OcrResult(status = OcrStatus.EMPTY_TEXT)

        val processResult = processor.processOcrResult(ocrResult)

        assertTrue(processResult is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
    }

    @Test
    fun test14_processOcrResult_errorResult_returnsInvalidOfferWithoutCrash() {
        val processor = UberAccessibilityProcessor()
        val ocrResult = OcrResult(status = OcrStatus.OCR_ERROR, errorMessage = "Simulated crash")

        val processResult = processor.processOcrResult(ocrResult)

        assertTrue(processResult is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
    }

    @Test
    fun test15_ocrFallback_reusesValidationAndMappingPipeline() {
        val parser = UberAccessibilityParser()
        val validator = UberOfferValidator()

        val ocrText = "8,50 €\n1,2 km (3 min)\n4,8 km (12 min)\nAceptar"
        val rawOffer = parser.parseFromText(ocrText)
        val validation = validator.validate(rawOffer)

        assertTrue(validation.isValidOffer)
        assertEquals(UberOfferScreenType.TRIP_OFFER, validation.screenType)
    }

    @Test
    fun test16_android11RutaA_remainsIntact() {
        // Confirma que si la Ruta A devuelve una oferta con tarifa, no se invoca la Ruta B
        val processor = UberAccessibilityProcessor()
        val rawOfferWithFare = RawUberTripOffer(
            rawFare = 12.50,
            currency = "EUR",
            detectedOfferType = UberOfferScreenType.TRIP_OFFER
        )

        assertFalse(processor.shouldTriggerOcrFallback(rawOfferWithFare, nodeCount = 35, isStructuralJump = true, sdkVersion = 30))
    }
}
