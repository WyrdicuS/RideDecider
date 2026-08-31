package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider
import com.ridedecider.app.data.accessibility.uber.RawUberTripOfferMapper
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityProcessor
import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import com.ridedecider.app.data.accessibility.uber.UberOfferValidator
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [UberOfferLightDiagnostic] y la integración ligera de diagnóstico.
 */
class UberOfferLightDiagnosticTest {

    @Test
    fun emptyOrNoOfferSnapshot_shouldReturnNoCandidates() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "Estás en línea.", className = "android.widget.TextView"),
                UberNodeSnapshot(text = "Buscando viajes", className = "android.widget.TextView")
            )
        )

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun acceptButton_shouldProduceOfferCandidate() {
        val acceptBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            viewIdResourceName = "com.ubercab.driver:id/accept_btn",
            isClickable = true,
            isEnabled = true,
            isVisibleToUser = true
        )
        val root = UberNodeSnapshot(children = listOf(acceptBtn))

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertEquals(1, candidates.size)
        val result = candidates[0]
        assertTrue(result.contains("[OFFER_CANDIDATE]"))
        assertTrue(result.contains("text=\"Aceptar\""))
        assertTrue(result.contains("className=\"Button\""))
        assertTrue(result.contains("resourceId=\"com.ubercab.driver:id/accept_btn\""))
        assertTrue(result.contains("clickable=true"))
    }

    @Test
    fun emparejarButton_shouldProduceRadarCandidate() {
        val matchBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            viewIdResourceName = "com.ubercab.driver:id/match_btn",
            isClickable = true
        )
        val root = UberNodeSnapshot(children = listOf(matchBtn))

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertEquals(1, candidates.size)
        val result = candidates[0]
        assertTrue(result.contains("[RADAR_CANDIDATE]"))
        assertTrue(result.contains("text=\"Emparejar\""))
        assertTrue(result.contains("resourceId=\"com.ubercab.driver:id/match_btn\""))
    }

    @Test
    fun matchKeywordInContentDescription_shouldProduceRadarCandidate() {
        val node = UberNodeSnapshot(
            className = "android.view.View",
            contentDescription = "Match trip offer",
            isClickable = true
        )
        val root = UberNodeSnapshot(children = listOf(node))

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertEquals(1, candidates.size)
        assertTrue(candidates[0].contains("[RADAR_CANDIDATE]"))
        assertTrue(candidates[0].contains("contentDescription=\"Match trip offer\""))
    }

    @Test
    fun demandIndicator_shouldNotBeConsideredOfferCandidate() {
        val demandNode = UberNodeSnapshot(text = "1-18 min", className = "android.widget.TextView")
        val root = UberNodeSnapshot(children = listOf(demandNode))

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun isolatedFareText_shouldNotBeConsideredOfferCandidate() {
        val fareNode = UberNodeSnapshot(text = "5,02 €", className = "android.widget.TextView")
        val root = UberNodeSnapshot(children = listOf(fareNode))

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun diagnosticThrottling_shouldNotBlockPipelineAndYieldSameResult() {
        val fare = UberNodeSnapshot(text = "15.00 €")
        val pickup = UberNodeSnapshot(text = "2.0 km")
        val pickupTime = UberNodeSnapshot(text = "4 min")
        val tripDist = UberNodeSnapshot(text = "3.0 km")
        val tripTime = UberNodeSnapshot(text = "8 min")
        val btn = UberNodeSnapshot(text = "Aceptar", isClickable = true)
        val snapshot = UberNodeSnapshot(children = listOf(fare, pickup, pickupTime, tripDist, tripTime, btn))

        val logger = InMemoryAccessibilityDiagnosticLogger()
        val processor = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = InMemoryProfitabilityConfigProvider(),
            diagnosticLogger = logger
        )

        // Evento 1 a t=1000ms
        val res1 = processor.processSnapshot(snapshot, currentTime = 1000L)
        // Evento 2 en ráfaga a t=1050ms (dentro del throttle de 150ms)
        val res2 = processor.processSnapshot(snapshot, currentTime = 1050L)

        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue(res2 is UberAccessibilityProcessor.ProcessResult.Debounced)

        // El primer evento corrió el diagnóstico ligero
        assertEquals(1, logger.eventLogs.count { it.first == "LIGHT_SCAN" })
        assertEquals(1, logger.pipelineResults.count { it.contains("[OFFER_CANDIDATE]") })
    }

    @Test
    fun largeSnapshot_doesNotPerformUnnecessaryOperations() {
        // Árbol con 200 nodos sin candidatos
        val dummyNodes = (1..200).map { UberNodeSnapshot(text = "Item $it", className = "android.view.View") }
        val root = UberNodeSnapshot(children = dummyNodes)

        val candidates = UberOfferLightDiagnostic.scanCandidates(root)

        assertTrue(candidates.isEmpty())
    }
}
