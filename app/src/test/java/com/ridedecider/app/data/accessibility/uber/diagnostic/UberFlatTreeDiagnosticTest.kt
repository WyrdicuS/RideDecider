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
 * Tests unitarios para [UberFlatTreeDiagnostic].
 *
 * Verifica el escaneo plano del 100% de los nodos, la localización de "Aceptar" y "Emparejar"
 * a profundidades superiores a 15 niveles, la no confusión de indicadores de demanda ("1-18 min")
 * y la invariancia de los resultados del pipeline.
 */
class UberFlatTreeDiagnosticTest {

    @Test
    fun flatScanSummary_shouldIncludeAllNodesRegardlessOfDepth() {
        val child1 = UberNodeSnapshot(text = "Nodo A", className = "android.widget.TextView")
        val child2 = UberNodeSnapshot(text = "Nodo B", className = "android.widget.Button", isClickable = true)
        val root = UberNodeSnapshot(className = "android.widget.FrameLayout", children = listOf(child1, child2))

        val result = UberFlatTreeDiagnostic.scanAndInspect(root)

        assertTrue(result.flatScanSummary.contains("[DIAG_NODE_SCAN] snapshotNodes=3 scannedNodes=3"))
        assertTrue(result.flatScanSummary.contains("[NODE] index=0 class=android.widget.FrameLayout"))
        assertTrue(result.flatScanSummary.contains("[NODE] index=1 class=android.widget.TextView text=\"Nodo A\""))
        assertTrue(result.flatScanSummary.contains("[NODE] index=2 class=android.widget.Button text=\"Nodo B\""))
        assertTrue(result.flatScanSummary.contains("clickable=true"))
    }

    @Test
    fun searchFindsAceptarEvenAtDepthGreaterThan15() {
        // Construir árbol de 20 niveles de profundidad
        var current = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Aceptar",
            viewIdResourceName = "com.ubercab.driver:id/accept_btn",
            isClickable = true
        )

        for (i in 19 downTo 0) {
            current = UberNodeSnapshot(
                className = "android.widget.ViewGroup",
                viewIdResourceName = "com.ubercab.driver:id/level_$i",
                children = listOf(current)
            )
        }

        val result = UberFlatTreeDiagnostic.scanAndInspect(current)

        assertTrue(result.keywordReports.isNotEmpty())
        val offerReport = result.keywordReports.first { it.contains("[DIAG_OFFER_TEXT_FOUND]") }
        assertTrue(offerReport.contains("text=\"Aceptar\""))
        assertTrue(offerReport.contains("[PARENT_PATH]"))
        assertTrue(offerReport.contains("Level 20") || offerReport.contains("Level 21"))
    }

    @Test
    fun searchFindsEmparejarInRadarOffers() {
        val btnMatch = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            viewIdResourceName = "com.ubercab.driver:id/match_btn",
            isClickable = true
        )
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(btnMatch)
        )

        val result = UberFlatTreeDiagnostic.scanAndInspect(root)

        assertTrue(result.keywordReports.isNotEmpty())
        val radarReport = result.keywordReports.first { it.contains("[DIAG_RADAR_TEXT_FOUND]") }
        assertTrue(radarReport.contains("text=\"Emparejar\""))
    }

    @Test
    fun demandIndicatorsAreNotClassifiedAsOfferActions() {
        val demandRange = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "1-18 min",
            viewIdResourceName = "com.ubercab.driver:id/demand_indicator"
        )
        val root = UberNodeSnapshot(children = listOf(demandRange))

        val result = UberFlatTreeDiagnostic.scanAndInspect(root)

        // "min" coincide como palabra clave de unidad cinemática, pero NO como acción de oferta
        val actionReports = result.keywordReports.filter { it.contains("[DIAG_OFFER_TEXT_FOUND]") }
        assertTrue(actionReports.isEmpty())
    }

    @Test
    fun diagnosticScan_doesNotAlterPipelineEvaluation() {
        val fare = UberNodeSnapshot(text = "15.00 €")
        val pickup = UberNodeSnapshot(text = "2.0 km")
        val pickupTime = UberNodeSnapshot(text = "4 min")
        val tripDist = UberNodeSnapshot(text = "3.0 km")
        val tripTime = UberNodeSnapshot(text = "8 min")
        val btn = UberNodeSnapshot(text = "Aceptar", isClickable = true)
        val snapshot = UberNodeSnapshot(children = listOf(fare, pickup, pickupTime, tripDist, tripTime, btn))

        val logger = InMemoryAccessibilityDiagnosticLogger()
        val diagResult = UberFlatTreeDiagnostic.scanAndInspect(snapshot)
        logger.logFlatScan(diagResult.flatScanSummary)
        for (k in diagResult.keywordReports) {
            logger.logKeywordDiagnostic(k)
        }

        val processorWithDiag = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = InMemoryProfitabilityConfigProvider(),
            diagnosticLogger = logger
        )
        val processorNoDiag = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = InMemoryProfitabilityConfigProvider(),
            diagnosticLogger = null
        )

        val resWith = processorWithDiag.processSnapshot(snapshot, currentTime = 1000L)
        val resNo = processorNoDiag.processSnapshot(snapshot, currentTime = 1000L)

        val evalWith = (resWith as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation
        val evalNo = (resNo as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation

        assertEquals(evalNo.decision, evalWith.decision)
        assertEquals(evalNo.metrics?.grossPerKm, evalWith.metrics?.grossPerKm)
        assertTrue(logger.flatScanLogs.isNotEmpty())
        assertTrue(logger.keywordLogs.isNotEmpty())
    }
}
