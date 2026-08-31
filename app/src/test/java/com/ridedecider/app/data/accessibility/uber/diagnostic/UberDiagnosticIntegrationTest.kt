package com.ridedecider.app.data.accessibility.uber.diagnostic

import com.ridedecider.app.data.accessibility.uber.InMemoryProfitabilityConfigProvider
import com.ridedecider.app.data.accessibility.uber.RawUberTripOfferMapper
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityConstants
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityProcessor
import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import com.ridedecider.app.data.accessibility.uber.UberOfferValidator
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para la infraestructura de diagnóstico de accesibilidad.
 *
 * Verifica la captura de métricas, la activación/desactivación del volcado de árbol
 * y que el diagnóstico no altere el resultado matemático y funcional del pipeline.
 */
class UberDiagnosticIntegrationTest {

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

    private fun createValidTripTree(): UberNodeSnapshot {
        val fare = UberNodeSnapshot(text = "15.00 €")
        val pickup = UberNodeSnapshot(text = "2.0 km")
        val pickupTime = UberNodeSnapshot(text = "4 min")
        val tripDist = UberNodeSnapshot(text = "3.0 km")
        val tripTime = UberNodeSnapshot(text = "8 min")
        val btn = UberNodeSnapshot(text = "Aceptar", isClickable = true)
        return UberNodeSnapshot(children = listOf(fare, pickup, pickupTime, tripDist, tripTime, btn))
    }

    // =========================================================================
    // 1. Tree debug desactivado (comportamiento por defecto)
    // =========================================================================
    @Test
    fun treeDebugDisabled_shouldEmitPipelineResultsWithoutTreeDumps() {
        logger.isTreeDebugEnabled = false
        val snapshot = createValidTripTree()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue(logger.pipelineResults.isNotEmpty())
        assertTrue(logger.treeDumps.isEmpty()) // No se imprime árbol
        assertTrue(logger.pipelineResults.any { it.contains("DECISION=ACCEPT") })
    }

    // =========================================================================
    // 2. Tree debug activado
    // =========================================================================
    @Test
    fun treeDebugEnabled_shouldEmitBothPipelineResultsAndTreeDumps() {
        logger.isTreeDebugEnabled = true
        val snapshot = createValidTripTree()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue(logger.pipelineResults.isNotEmpty())
        assertEquals(1, logger.treeDumps.size)
        assertTrue(logger.treeDumps[0].contains("=== UBER ACCESSIBILITY TREE DUMP ==="))
        assertTrue(logger.treeDumps[0].contains("15.00 €"))
    }

    // =========================================================================
    // 3. Logging completamente desactivado
    // =========================================================================
    @Test
    fun loggingDisabled_shouldNotEmitLogsOrTreeDumps() {
        logger.isLoggingEnabled = false
        val snapshot = createValidTripTree()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        assertTrue(logger.eventLogs.isEmpty())
        assertTrue(logger.pipelineResults.isEmpty())
        assertTrue(logger.treeDumps.isEmpty())
    }

    // =========================================================================
    // 4. Métricas operativas acumuladas
    // =========================================================================
    @Test
    fun metricsAccumulation_shouldTrackOperationalCounts() {
        val validSnapshot = createValidTripTree()
        val emptySnapshot = UberNodeSnapshot(text = "Hoy: 0.00 €", children = listOf(UberNodeSnapshot(text = "Desconectar")))

        // 1. Evento no-uber
        processor.processSnapshot(validSnapshot, packageName = "com.other.app")
        // 2. Evento válido
        processor.processSnapshot(validSnapshot, currentTime = 1000L)
        // 3. Evento repetido (debounce)
        processor.processSnapshot(validSnapshot, currentTime = 1050L)
        // 4. Evento no-offer
        processor.processSnapshot(emptySnapshot, currentTime = 2000L)

        val metrics = processor.metrics
        assertEquals(4, metrics.eventsReceived.get())
        assertEquals(3, metrics.uberEventsProcessed.get())
        assertEquals(3, metrics.snapshotsCreated.get())
        assertEquals(1, metrics.validOffersCount.get())
        assertEquals(1, metrics.evaluationsCount.get())
        assertEquals(1, metrics.debouncedCount.get())
        assertEquals(1, metrics.invalidOffersCount.get())

        val summary = metrics.toSummaryString()
        assertTrue(summary.contains("Total eventos recibidos: 4"))
        assertTrue(summary.contains("Evaluaciones completadas: 1"))
    }

    // =========================================================================
    // 5. El diagnóstico no altera el resultado del pipeline
    // =========================================================================
    @Test
    fun diagnosticLogger_doesNotAlterPipelineResult() {
        val snapshot = createValidTripTree()

        val withoutLoggerProcessor = UberAccessibilityProcessor(diagnosticLogger = null)
        val resultWithout = withoutLoggerProcessor.processSnapshot(snapshot, currentTime = 1000L)

        val withLoggerProcessor = UberAccessibilityProcessor(diagnosticLogger = logger)
        val resultWith = withLoggerProcessor.processSnapshot(snapshot, currentTime = 1000L)

        val evalWithout = (resultWithout as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation
        val evalWith = (resultWith as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation

        assertEquals(evalWithout.decision, evalWith.decision)
        assertEquals(evalWithout.metrics?.totalDistanceKm, evalWith.metrics?.totalDistanceKm)
        assertEquals(evalWithout.metrics?.netProfit, evalWith.metrics?.netProfit)
    }
}
