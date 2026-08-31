package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.data.accessibility.uber.diagnostic.InMemoryAccessibilityDiagnosticLogger
import com.ridedecider.app.data.accessibility.uber.diagnostic.UberOfferLightDiagnostic
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para verificar el rendimiento, throttling, protección de ráfagas
 * y detección precisa de ofertas sin congelación ni diagnósticos masivos.
 */
class UberEventThrottlingTest {

    private lateinit var logger: InMemoryAccessibilityDiagnosticLogger
    private lateinit var processor: UberAccessibilityProcessor

    @Before
    fun setUp() {
        logger = InMemoryAccessibilityDiagnosticLogger(
            isLoggingEnabled = true,
            isTreeDebugEnabled = false
        )
        processor = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = InMemoryProfitabilityConfigProvider(),
            diagnosticLogger = logger
        )
    }

    private fun createValidTripSnapshot(): UberNodeSnapshot {
        val fare = UberNodeSnapshot(text = "15.00 €")
        val pickup = UberNodeSnapshot(text = "2.0 km")
        val pickupTime = UberNodeSnapshot(text = "4 min")
        val tripDist = UberNodeSnapshot(text = "3.0 km")
        val tripTime = UberNodeSnapshot(text = "8 min")
        val btn = UberNodeSnapshot(
            text = "Aceptar",
            className = "android.widget.Button",
            viewIdResourceName = "com.ubercab.driver:id/accept_btn",
            isClickable = true
        )
        return UberNodeSnapshot(children = listOf(fare, pickup, pickupTime, tripDist, tripTime, btn))
    }

    @Test
    fun consecutiveBurstEvents_shouldBeThrottledAndNotOverloadLogs() {
        val snapshot = createValidTripSnapshot()

        // Simular ráfaga de 20 eventos separados por 10 ms (200 ms en total)
        for (i in 0 until 20) {
            val timestamp = 1000L + (i * 10L)
            processor.processSnapshot(snapshot, currentTime = timestamp)
        }

        // El escaneo diagnóstico ligero sólo debe haberse ejecutado con throttling (máximo 2 veces en 200 ms con ventana de 150 ms)
        val lightScanCount = logger.eventLogs.count { it.first == "LIGHT_SCAN" }
        assertTrue("Los escaneos ligeros ($lightScanCount) deben estar acotados por throttling", lightScanCount in 1..2)

        // El volcado masivo plano nunca debe ejecutarse
        assertTrue(logger.flatScanLogs.isEmpty())
        assertTrue(logger.treeDumps.isEmpty())
    }

    @Test
    fun realOfferStillReachesParserAndProducesEvaluation() {
        val snapshot = createValidTripSnapshot()

        val result = processor.processSnapshot(snapshot, currentTime = 1000L)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluation = (result as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation
        assertEquals(Decision.ACCEPT, evaluation.decision)
        assertEquals(5.0, evaluation.metrics?.totalDistanceKm ?: 0.0, 0.01)
    }

    @Test
    fun demandIndicatorWithoutOffer_isNotInterpretedAsOffer() {
        val demandNode = UberNodeSnapshot(
            text = "1-18 min",
            className = "android.widget.TextView"
        )
        val mapNode = UberNodeSnapshot(
            text = "Alta demanda",
            className = "android.widget.TextView"
        )
        val root = UberNodeSnapshot(children = listOf(demandNode, mapNode))

        val result = processor.processSnapshot(root, currentTime = 1000L)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        val invalidResult = result as UberAccessibilityProcessor.ProcessResult.InvalidOffer
        assertEquals(UberOfferScreenType.NO_OFFER, invalidResult.screenType)
        assertTrue(invalidResult.reasons.contains(UberValidationReason.MISSING_OFFER_TYPE))

        // Candidatos de oferta vacíos
        val candidates = UberOfferLightDiagnostic.scanCandidates(root)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun acceptAndEmparejar_remainDetectableByLightDiagnostic() {
        val acceptNode = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            isClickable = true
        )
        val radarNode = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            isClickable = true
        )

        val acceptCandidates = UberOfferLightDiagnostic.scanCandidates(UberNodeSnapshot(children = listOf(acceptNode)))
        val radarCandidates = UberOfferLightDiagnostic.scanCandidates(UberNodeSnapshot(children = listOf(radarNode)))

        assertEquals(1, acceptCandidates.size)
        assertTrue(acceptCandidates[0].contains("[OFFER_CANDIDATE]"))
        assertTrue(acceptCandidates[0].contains("text=\"Aceptar\""))

        assertEquals(1, radarCandidates.size)
        assertTrue(radarCandidates[0].contains("[RADAR_CANDIDATE]"))
        assertTrue(radarCandidates[0].contains("text=\"Emparejar\""))
    }
}
