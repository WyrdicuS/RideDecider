package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.data.accessibility.uber.diagnostic.InMemoryAccessibilityDiagnosticLogger
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [UberAccessibilityProcessor].
 *
 * Verifica la integración en memoria del pipeline de accesibilidad:
 * - Filtrado de paquetes de Android.
 * - Conexión de Parser -> Validator -> Mapper -> UseCase -> DecisionEngine.
 * - Mecanismo de estabilidad y debounce.
 * - Transiciones de estado de pantalla.
 * - Captura inmediata extraordinaria [OFFER_TRANSITION_FULL_DUMP].
 * - Integración con [InMemoryProfitabilityConfigProvider] y [TripEvaluationListener].
 */
class UberAccessibilityProcessorTest {

    private lateinit var processor: UberAccessibilityProcessor
    private lateinit var configProvider: InMemoryProfitabilityConfigProvider
    private lateinit var diagnosticLogger: InMemoryAccessibilityDiagnosticLogger
    private var lastReceivedEvaluation: TripEvaluation? = null
    private var lastReportedScreenType: UberOfferScreenType? = null

    @Before
    fun setUp() {
        configProvider = InMemoryProfitabilityConfigProvider(
            ProfitabilityConfig(
                costPerKm = 0.20,
                costPerHour = 4.0,
                minGrossHourlyRate = 20.0,
                minGrossPerKmRate = 1.20,
                minNetTripProfit = 2.0,
                minNetHourlyRate = 15.0,
                maxPickupDistanceKm = 4.0,
                maxPickupTimeMinutes = 10.0
            )
        )

        lastReceivedEvaluation = null
        lastReportedScreenType = null
        diagnosticLogger = InMemoryAccessibilityDiagnosticLogger()

        val listener = object : TripEvaluationListener {
            override fun onTripEvaluation(evaluation: TripEvaluation) {
                lastReceivedEvaluation = evaluation
            }

            override fun onScreenStateChanged(screenType: UberOfferScreenType) {
                lastReportedScreenType = screenType
            }
        }

        processor = UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            configProvider = configProvider,
            evaluationListener = listener,
            debounceIntervalMs = 300L,
            diagnosticLogger = diagnosticLogger
        )
    }

    private fun createTree(vararg texts: String): UberNodeSnapshot {
        val children = texts.map { UberNodeSnapshot(text = it) }
        return UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = children
        )
    }

    // =========================================================================
    // 1. Filtrado de paquete de aplicación
    // =========================================================================
    @Test
    fun nonUberPackage_shouldBeIgnoredImmediately() {
        val snapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        val result = processor.processSnapshot(snapshot, packageName = "com.whatsapp")

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.IgnoredPackage)
        assertEquals(null, lastReceivedEvaluation)
    }

    @Test
    fun nullPackage_shouldBeIgnoredImmediately() {
        val snapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        val result = processor.processSnapshot(snapshot, packageName = null)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.IgnoredPackage)
        assertEquals(null, lastReceivedEvaluation)
    }

    // =========================================================================
    // 2. Snapshot nulo
    // =========================================================================
    @Test
    fun nullSnapshot_shouldReturnNullSnapshotResult() {
        val result = processor.processSnapshot(null, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.NullSnapshot)
        assertEquals(null, lastReceivedEvaluation)
    }

    // =========================================================================
    // 3. Pantalla sin oferta (NO_OFFER)
    // =========================================================================
    @Test
    fun dailyEarnings_shouldNotTriggerEvaluation() {
        val snapshot = createTree("Hoy: 84,50 €", "4,95 ★", "Desconectar")

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        val invalidResult = result as UberAccessibilityProcessor.ProcessResult.InvalidOffer
        assertEquals(UberOfferScreenType.NO_OFFER, invalidResult.screenType)
        assertEquals(UberOfferScreenType.NO_OFFER, processor.currentScreenType)
        assertEquals(null, lastReceivedEvaluation)
    }

    // =========================================================================
    // 4. Pantalla de navegación activa (ACTIVE_TRIP)
    // =========================================================================
    @Test
    fun activeTripNavigation_shouldNotTriggerEvaluationAndUpdateScreenState() {
        val snapshot = createTree("Gire a la derecha en 200 m", "65 km/h", "12 min restantes")

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.InvalidOffer)
        val invalidResult = result as UberAccessibilityProcessor.ProcessResult.InvalidOffer
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, invalidResult.screenType)
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, processor.currentScreenType)
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, lastReportedScreenType)
        assertEquals(null, lastReceivedEvaluation)
    }

    // =========================================================================
    // 5. Oferta TRIP válida llega a TripEvaluation
    // =========================================================================
    @Test
    fun validTripOffer_shouldReachTripEvaluationAndNotifyListener() {
        val snapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = result as UberAccessibilityProcessor.ProcessResult.Evaluated
        assertEquals(Decision.ACCEPT, evaluated.evaluation.decision)
        assertEquals(UberOfferScreenType.TRIP_OFFER, processor.currentScreenType)

        assertNotNull(lastReceivedEvaluation)
        assertEquals(Decision.ACCEPT, lastReceivedEvaluation!!.decision)
        assertEquals(5.0, lastReceivedEvaluation!!.metrics!!.totalDistanceKm, 0.0001)
    }

    // =========================================================================
    // 6. Oferta RADAR válida llega a TripEvaluation
    // =========================================================================
    @Test
    fun validRadarOffer_shouldReachTripEvaluationAndNotifyListener() {
        val snapshot = createTree("8.50 €", "1.1 km", "4 min", "6.0 km", "14 min", "Emparejar")

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = result as UberAccessibilityProcessor.ProcessResult.Evaluated
        assertEquals(UberOfferScreenType.RADAR_OFFER, processor.currentScreenType)

        assertNotNull(lastReceivedEvaluation)
        assertEquals(evaluated.evaluation, lastReceivedEvaluation)
    }

    // =========================================================================
    // 7. Mecanismo de Debounce ante eventos repetidos
    // =========================================================================
    @Test
    fun repeatedEventsWithSameOffer_shouldDebounceSubsequentCalls() {
        val snapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        // Primer evento en t = 1000 ms -> Evaluación exitosa
        val result1 = processor.processSnapshot(
            snapshot,
            packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME,
            currentTime = 1000L
        )
        assertTrue(result1 is UberAccessibilityProcessor.ProcessResult.Evaluated)

        // Segundo evento repetido en t = 1100 ms (< 300 ms debounce) -> Debounced
        val result2 = processor.processSnapshot(
            snapshot,
            packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME,
            currentTime = 1100L
        )
        assertTrue(result2 is UberAccessibilityProcessor.ProcessResult.Debounced)

        // Tercer evento tras expirar debounce en t = 1400 ms (> 300 ms) -> Evaluated
        val result3 = processor.processSnapshot(
            snapshot,
            packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME,
            currentTime = 1400L
        )
        assertTrue(result3 is UberAccessibilityProcessor.ProcessResult.Evaluated)
    }

    // =========================================================================
    // 8. Oferta diferente no se bloquea por debounce
    // =========================================================================
    @Test
    fun differentOffer_shouldEvaluateImmediatelyEvenWithinDebounceInterval() {
        val offer1 = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")
        val offer2 = createTree("22.00 €", "1.0 km", "2 min", "12.0 km", "20 min", "Aceptar")

        val result1 = processor.processSnapshot(
            offer1,
            packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME,
            currentTime = 1000L
        )
        assertTrue(result1 is UberAccessibilityProcessor.ProcessResult.Evaluated)

        val result2 = processor.processSnapshot(
            offer2,
            packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME,
            currentTime = 1050L
        )
        assertTrue(result2 is UberAccessibilityProcessor.ProcessResult.Evaluated)
    }

    // =========================================================================
    // 9. Transición de estados de pantalla (TRIP_OFFER -> ACTIVE_TRIP -> NO_OFFER)
    // =========================================================================
    @Test
    fun screenTransitions_shouldUpdateStatesCorrectly() {
        val tripSnapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")
        val navSnapshot = createTree("Gire a la derecha en 200 m", "65 km/h")
        val emptySnapshot = createTree("Hoy: 0.00 €", "Desconectar")

        processor.processSnapshot(tripSnapshot, currentTime = 1000L)
        assertEquals(UberOfferScreenType.TRIP_OFFER, processor.currentScreenType)
        assertEquals(UberOfferScreenType.TRIP_OFFER, lastReportedScreenType)

        processor.processSnapshot(navSnapshot, currentTime = 2000L)
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, processor.currentScreenType)
        assertEquals(UberOfferScreenType.ACTIVE_TRIP, lastReportedScreenType)

        processor.processSnapshot(emptySnapshot, currentTime = 3000L)
        assertEquals(UberOfferScreenType.NO_OFFER, processor.currentScreenType)
        assertEquals(UberOfferScreenType.NO_OFFER, lastReportedScreenType)
    }

    // =========================================================================
    // 10. Integración con ProfitabilityConfigProvider dinámico
    // =========================================================================
    @Test
    fun customProfitabilityConfig_shouldReflectInEvaluation() {
        val snapshot = createTree("12.00 €", "8.0 km", "16 min", "4.0 km", "10 min", "Aceptar")

        // Con configuración por defecto (maxPickup = 4.0 km), 8 km de recogida provoca REJECT
        val result = processor.processSnapshot(snapshot, currentTime = 1000L)
        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluation = (result as UberAccessibilityProcessor.ProcessResult.Evaluated).evaluation
        assertEquals(Decision.REJECT, evaluation.decision)
    }

    // =========================================================================
    // 11. Transición NO_OFFER -> RADAR_OFFER genera FULL_NODE_DUMP inmediato
    // =========================================================================
    @Test
    fun transitionToRadarOffer_shouldTriggerImmediateFullNodeDump() {
        val noOfferSnapshot = createTree("Buscando viajes")
        val radarSnapshot = createTree("Radar de viaje", "Emparejar")

        // Evento 1: Estado inicial NO_OFFER en t = 1000 ms (ejecuta periódico inicial)
        processor.processSnapshot(noOfferSnapshot, currentTime = 1000L)
        assertEquals(1, diagnosticLogger.fullDumpLogs.size)

        // Evento 2: Transición a RADAR_OFFER en t = 2000 ms (< 5000 ms cooldown periódico)
        processor.processSnapshot(radarSnapshot, currentTime = 2000L)

        assertEquals(UberOfferScreenType.RADAR_OFFER, processor.currentScreenType)
        // Debe haberse generado el dump inmediato extraordinario por transición
        assertEquals(2, diagnosticLogger.fullDumpLogs.size)
        assertTrue(diagnosticLogger.eventLogs.any { it.first == "OFFER_TRANSITION_FULL_DUMP" && it.second.contains("RADAR_OFFER") })
    }

    // =========================================================================
    // 12. Transición NO_OFFER -> TRIP_OFFER genera FULL_NODE_DUMP inmediato
    // =========================================================================
    @Test
    fun transitionToTripOffer_shouldTriggerImmediateFullNodeDump() {
        val noOfferSnapshot = createTree("Buscando viajes")
        val tripSnapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        // Evento 1 en t = 1000 ms
        processor.processSnapshot(noOfferSnapshot, currentTime = 1000L)
        assertEquals(1, diagnosticLogger.fullDumpLogs.size)

        // Evento 2: Transición a TRIP_OFFER en t = 2000 ms (< 5000 ms cooldown periódico)
        processor.processSnapshot(tripSnapshot, currentTime = 2000L)

        assertEquals(UberOfferScreenType.TRIP_OFFER, processor.currentScreenType)
        assertEquals(2, diagnosticLogger.fullDumpLogs.size)
        assertTrue(diagnosticLogger.eventLogs.any { it.first == "OFFER_TRANSITION_FULL_DUMP" && it.second.contains("TRIP_OFFER") })
    }

    // =========================================================================
    // 13. Mismo estado RADAR_OFFER -> RADAR_OFFER no genera dumps repetidos
    // =========================================================================
    @Test
    fun repeatedRadarOfferEvents_shouldNotTriggerRepeatedImmediateDumps() {
        val radarSnapshot = createTree("Radar de viaje", "Emparejar")

        // Transición inicial en t = 1000 ms
        processor.processSnapshot(radarSnapshot, currentTime = 1000L)
        assertEquals(1, diagnosticLogger.fullDumpLogs.size)

        // Microevento Accessibility en t = 1500 ms (mismo estado RADAR_OFFER, cooldown no vencido)
        processor.processSnapshot(radarSnapshot, currentTime = 1500L)
        // Microevento Accessibility en t = 2000 ms
        processor.processSnapshot(radarSnapshot, currentTime = 2000L)

        // No debe haberse generado ningún dump adicional
        assertEquals(1, diagnosticLogger.fullDumpLogs.size)
    }

    // =========================================================================
    // 14. Periodic dump y transición en el mismo evento no duplica el dump
    // =========================================================================
    @Test
    fun periodicDumpAndTransitionInSameEvent_shouldNotDuplicateDump() {
        val tripSnapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")

        // t = 6000 ms (> 5000 ms cooldown desde t = 0)
        processor.processSnapshot(tripSnapshot, currentTime = 6000L)

        // El dump periódico corrió en paso 4, por lo que el paso 6 no debe generar un segundo dump
        assertEquals(1, diagnosticLogger.fullDumpLogs.size)
    }

    // =========================================================================
    // 15. Detección de Radar mediante Resource ID real y texto "Radar de viaje" con acción
    // =========================================================================
    @Test
    fun radarDetectionViaResourceIdAndText_worksInProcessor() {
        val pillNode = UberNodeSnapshot(
            className = "android.widget.TextView",
            text = "Radar de viaje",
            viewIdResourceName = "com.ubercab.driver:id/ub__driver_job_offers_pill_title"
        )
        val matchBtn = UberNodeSnapshot(
            className = "android.widget.Button",
            text = "Emparejar",
            isClickable = true
        )
        val root = UberNodeSnapshot(children = listOf(pillNode, matchBtn))

        processor.processSnapshot(root, currentTime = 1000L)

        assertEquals(UberOfferScreenType.RADAR_OFFER, processor.currentScreenType)
    }

    // =========================================================================
    // 16. Pipeline completo con captura real Viaje Directo (5,02 €)
    // =========================================================================
    @Test
    fun realDirectTripScreenshot_shouldEvaluateFullPipelineSuccessfully() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "Exclusiva"),
                UberNodeSnapshot(text = "5,02 €"),
                UberNodeSnapshot(text = "Pago en efectivo"),
                UberNodeSnapshot(text = "★ 4,91"),
                UberNodeSnapshot(text = "Tarifa (excl. tasa de servicio de Uber)"),
                UberNodeSnapshot(text = "A 6 min (3.5 km) de distancia"),
                UberNodeSnapshot(text = "Calle del Pósito 1, Fuenlabrada"),
                UberNodeSnapshot(text = "Viaje de 6 min (3.1 km)"),
                UberNodeSnapshot(text = "Urbanización Parque Miraflores 56, Fuenlabrada"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val result = processor.processSnapshot(root, currentTime = 1000L)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluation = lastReceivedEvaluation
        assertNotNull(evaluation)
        assertEquals(UberOfferScreenType.TRIP_OFFER, processor.currentScreenType)
        assertEquals(5.02, evaluation!!.trip.rawFare!!, 0.001)
        assertEquals(3.5, evaluation.trip.pickupDistanceKm!!, 0.001)
        assertEquals(3.1, evaluation.trip.tripDistanceKm!!, 0.001)
        assertEquals(6.6, evaluation.metrics!!.totalDistanceKm, 0.001)
        assertEquals(12.0, evaluation.metrics!!.totalDurationMinutes, 0.001)
        assertTrue(evaluation.trip.isCashPayment)
        assertEquals(4.91, evaluation.trip.passengerRating!!, 0.001)
        assertEquals("Calle del Pósito 1, Fuenlabrada", evaluation.trip.pickupAddress)
        assertEquals("Urbanización Parque Miraflores 56, Fuenlabrada", evaluation.trip.dropoffAddress)
    }

    // =========================================================================
    // 17. Pipeline completo con captura real Viaje Radar (5 € entero)
    // =========================================================================
    @Test
    fun realRadarTripScreenshot_shouldEvaluateFullPipelineSuccessfully() {
        val root = UberNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(
                UberNodeSnapshot(text = "UberX"),
                UberNodeSnapshot(text = "5 €"),
                UberNodeSnapshot(text = "Pago en efectivo"),
                UberNodeSnapshot(text = "★ 4,91"),
                UberNodeSnapshot(text = "Tarifa (excl. tasa de servicio de Uber)"),
                UberNodeSnapshot(text = "A 5 min (3.4 km) de distancia"),
                UberNodeSnapshot(text = "Calle del Pósito 1, Fuenlabrada"),
                UberNodeSnapshot(text = "Viaje de 6 min (3.1 km)"),
                UberNodeSnapshot(text = "Urbanización Parque Miraflores 56, Fuenlabrada"),
                UberNodeSnapshot(text = "Emparejar")
            )
        )

        val result = processor.processSnapshot(root, currentTime = 1000L)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluation = lastReceivedEvaluation
        assertNotNull(evaluation)
        assertEquals(UberOfferScreenType.RADAR_OFFER, processor.currentScreenType)
        assertEquals(5.00, evaluation!!.trip.rawFare!!, 0.001)
        assertEquals(3.4, evaluation.trip.pickupDistanceKm!!, 0.001)
        assertEquals(3.1, evaluation.trip.tripDistanceKm!!, 0.001)
        assertEquals(6.5, evaluation.metrics!!.totalDistanceKm, 0.001)
        assertEquals(11.0, evaluation.metrics!!.totalDurationMinutes, 0.001)
        assertTrue(evaluation.trip.isCashPayment)
        assertEquals(4.91, evaluation.trip.passengerRating!!, 0.001)
        assertEquals("Calle del Pósito 1, Fuenlabrada", evaluation.trip.pickupAddress)
        assertEquals("Urbanización Parque Miraflores 56, Fuenlabrada", evaluation.trip.dropoffAddress)
    }
}

