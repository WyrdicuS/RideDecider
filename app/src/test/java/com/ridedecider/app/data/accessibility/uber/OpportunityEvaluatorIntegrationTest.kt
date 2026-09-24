package com.ridedecider.app.data.accessibility.uber

import com.ridedecider.app.data.accessibility.uber.ocr.OcrResult
import com.ridedecider.app.data.accessibility.uber.ocr.OcrStatus
import com.ridedecider.app.domain.engine.DecisionEngine
import com.ridedecider.app.domain.engine.DefaultOpportunityEvaluator
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.model.opportunity.Confidence
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de integración de 8.16-R4.4: verifican que [UberAccessibilityProcessor] invoca al
 * OpportunityEvaluator después del SPE (DecisionEngine) en ambas rutas de entrada
 * (accessibility tree y OCR fallback), y que las invariantes de Architecture B se
 * mantienen a través del punto de integración real.
 *
 * No duplica los 17 tests de R4 ni los 27 de calibración: solo verifica el cableado
 * (processor -> evaluator -> ProcessResult/listener) y los invariantes de contorno
 * que dependen de esa integración.
 */
class OpportunityEvaluatorIntegrationTest {

    private val config = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 20.0,
        minGrossPerKmRate = 1.20,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 15.0,
        maxPickupDistanceKm = 4.0,
        maxPickupTimeMinutes = 10.0
    )

    private lateinit var configProvider: InMemoryProfitabilityConfigProvider
    private var lastAssessment: OpportunityAssessment? = null
    private var lastEvaluation: TripEvaluation? = null

    @Before
    fun setUp() {
        configProvider = InMemoryProfitabilityConfigProvider(config)
        lastAssessment = null
        lastEvaluation = null
    }

    private fun createTree(vararg texts: String): UberNodeSnapshot {
        val children = texts.map { UberNodeSnapshot(text = it) }
        return UberNodeSnapshot(className = "android.widget.FrameLayout", children = children)
    }

    private fun buildProcessor(): UberAccessibilityProcessor {
        val listener = object : TripEvaluationListener {
            override fun onTripEvaluation(evaluation: TripEvaluation) {
                lastEvaluation = evaluation
            }

            override fun onOpportunityAssessment(assessment: OpportunityAssessment) {
                lastAssessment = assessment
            }
        }
        return UberAccessibilityProcessor(
            parser = UberAccessibilityParser(),
            validator = UberOfferValidator(),
            mapper = RawUberTripOfferMapper(),
            evaluateUseCase = EvaluateIncomingTripUseCase(DecisionEngine()),
            opportunityEvaluator = DefaultOpportunityEvaluator(),
            configProvider = configProvider,
            evaluationListener = listener,
            debounceIntervalMs = 300L
        )
    }

    // ════════════════════════════════════════════════════════
    // 1. SPE ACCEPT -> OpportunityAssessment disponible (ruta accessibility tree)
    // ════════════════════════════════════════════════════════

    @Test
    fun `1 - SPE ACCEPT via accessibility tree produces OpportunityAssessment through ProcessResult and listener`() {
        // fare=15€, pickup 2km/4min, trip 3km/8min -> total 5km/12min=0.2h
        // grossPerHour=75 (hourlyRatio=3.75 -> EXCEPTIONAL), grossPerKm=3.0 (kmRatio=2.5)
        val snapshot = createTree("15.00 €", "2.0 km", "4 min", "3.0 km", "8 min", "Aceptar")
        val processor = buildProcessor()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = result as UberAccessibilityProcessor.ProcessResult.Evaluated

        // SPE inalterado por la integración
        assertEquals(Decision.ACCEPT, evaluated.evaluation.decision)
        assertEquals(75.0, evaluated.evaluation.metrics!!.grossPerHour, 0.01)

        // Opportunity Layer disponible y consistente
        assertNotNull(evaluated.opportunityAssessment)
        assertEquals(Decision.ACCEPT, evaluated.opportunityAssessment.speDecision)
        assertEquals(OpportunityQuality.EXCEPTIONAL, evaluated.opportunityAssessment.quality)
        assertEquals(Recommendation.TAKE, evaluated.opportunityAssessment.recommendation)
        assertFalse(evaluated.opportunityAssessment.speOverridden)

        // Listener notificado con el mismo resultado
        assertEquals(evaluated.opportunityAssessment, lastAssessment)
        assertEquals(evaluated.evaluation, lastEvaluation)
    }

    // ════════════════════════════════════════════════════════
    // 1b. Ruta OCR fallback también queda cableada
    // ════════════════════════════════════════════════════════

    @Test
    fun `1b - SPE ACCEPT via OCR fallback path also produces OpportunityAssessment`() {
        val processor = buildProcessor()
        val ocrResult = OcrResult(
            status = OcrStatus.SUCCESS,
            rawText = "OFERTA EXCLUSIVA\n8,50 €\nA 1,2 km (3 min) de distancia\nViaje de 4,8 km (12 min)\nAceptar",
            screenshotLatencyMs = 20L,
            ocrLatencyMs = 15L
        )

        val processResult = processor.processOcrResult(ocrResult)

        assertTrue(processResult is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = processResult as UberAccessibilityProcessor.ProcessResult.Evaluated
        assertEquals(Decision.ACCEPT, evaluated.evaluation.decision)
        assertNotNull(evaluated.opportunityAssessment)
        assertEquals(Decision.ACCEPT, evaluated.opportunityAssessment.speDecision)
        assertEquals(evaluated.opportunityAssessment, lastAssessment)
    }

    // ════════════════════════════════════════════════════════
    // 3. SPE economic REJECT -> nunca override
    // ════════════════════════════════════════════════════════

    @Test
    fun `3 - SPE economic REJECT never overrides through the integration`() {
        // fare=3€, pickup 1km/2min (dentro de límites operativos), trip 3km/8min -> total 4km/10min=0.1667h
        // grossPerHour=18 (<20), grossPerKm=0.75 (<1.20), netProfit=1.53 (<2) -> solo razones ECONOMIC
        val snapshot = createTree("3.00 €", "1.0 km", "2 min", "3.0 km", "8 min", "Aceptar")
        val processor = buildProcessor()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = result as UberAccessibilityProcessor.ProcessResult.Evaluated

        // SPE inalterado: sigue rechazando por razones económicas
        assertEquals(Decision.REJECT, evaluated.evaluation.decision)

        // Opportunity Layer: bloqueo irrecuperable
        assertFalse(evaluated.opportunityAssessment.speOverridden)
        assertEquals(Recommendation.SKIP, evaluated.opportunityAssessment.recommendation)
        assertEquals(OpportunityQuality.POOR, evaluated.opportunityAssessment.quality)
    }

    // ════════════════════════════════════════════════════════
    // 4 + 5. SPE operational REJECT -> EVALUATE con override, nunca TAKE
    // ════════════════════════════════════════════════════════

    @Test
    fun `4-5 - SPE operational-only REJECT with strong economics overrides to EVALUATE but never TAKE`() {
        // fare=40€, pickup 6km (>4.0 limite)/5min (<=10 limite), trip 8km/16min -> total 14km/21min=0.35h
        // grossPerHour=114.3, grossPerKm=2.857, netProfit=35.8, netPerHour=102.3 -> todo economico pasa
        // Unica razon de rechazo: REJECT_EXCESSIVE_PICKUP_DISTANCE (operational)
        val snapshot = createTree("40.00 €", "6.0 km", "5 min", "8.0 km", "16 min", "Aceptar")
        val processor = buildProcessor()

        val result = processor.processSnapshot(snapshot, packageName = UberAccessibilityConstants.UBER_PACKAGE_NAME)

        assertTrue(result is UberAccessibilityProcessor.ProcessResult.Evaluated)
        val evaluated = result as UberAccessibilityProcessor.ProcessResult.Evaluated

        // SPE inalterado: sigue rechazando por pickup excesivo
        assertEquals(Decision.REJECT, evaluated.evaluation.decision)

        // Opportunity Layer: override operacional, nunca TAKE
        assertTrue(evaluated.opportunityAssessment.speOverridden)
        assertEquals(Recommendation.EVALUATE, evaluated.opportunityAssessment.recommendation)
        assertTrue(evaluated.opportunityAssessment.recommendation != Recommendation.TAKE)
        assertEquals(OpportunityQuality.EXCEPTIONAL, evaluated.opportunityAssessment.quality)
        assertNotNull(evaluated.opportunityAssessment.overrideJustification)
    }

    // ════════════════════════════════════════════════════════
    // 2. SPE UNKNOWN -> OpportunityAssessment correcto (mismo par de clases production)
    // ════════════════════════════════════════════════════════

    @Test
    fun `2 - SPE UNKNOWN produces SKIP POOR LOW through the same production evaluator wired into the processor`() {
        // Se construye directamente con las mismas clases production que usa el processor
        // (EvaluateIncomingTripUseCase(DecisionEngine()) + DefaultOpportunityEvaluator()) para forzar
        // UNKNOWN_INVALID_DATA sin depender del formato de texto del parser.
        val useCase = EvaluateIncomingTripUseCase(DecisionEngine())
        val evaluator = DefaultOpportunityEvaluator()

        val trip = Trip(
            id = "unknown-case",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 0.0,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 2.0,
            pickupAddress = "Origin",
            tripDistanceKm = 5.0,
            tripDurationMinutes = 10.0,
            dropoffAddress = "Destination"
        )

        val evaluation = useCase(trip, config)
        assertEquals(Decision.UNKNOWN, evaluation.decision)

        val assessment = evaluator.evaluate(
            evaluation, trip, historicalContext = null,
            kinematicsSource = com.ridedecider.app.data.accessibility.uber.KinematicsSource.MISSING
        )

        assertEquals(OpportunityQuality.POOR, assessment.quality)
        assertEquals(Recommendation.SKIP, assessment.recommendation)
        assertEquals(Confidence.LOW, assessment.confidence)
        assertEquals(Decision.UNKNOWN, assessment.speDecision)
        assertFalse(assessment.speOverridden)
    }

    // ════════════════════════════════════════════════════════
    // 6. OCR_SUSPECT nunca produce TAKE (mismo par de clases production)
    // ════════════════════════════════════════════════════════

    @Test
    fun `6 - OCR_SUSPECT kinematics source never produces TAKE through the production evaluator`() {
        val useCase = EvaluateIncomingTripUseCase(DecisionEngine())
        val evaluator = DefaultOpportunityEvaluator()

        // Misma oferta excepcional del caso 1 (ACCEPT + EXCEPTIONAL + TAKE con EXPLICIT_DUAL)
        val trip = Trip(
            id = "ocr-suspect-case",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 15.0,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 4.0,
            pickupAddress = "Origin",
            tripDistanceKm = 3.0,
            tripDurationMinutes = 8.0,
            dropoffAddress = "Destination"
        )

        val evaluation = useCase(trip, config)
        assertEquals(Decision.ACCEPT, evaluation.decision)

        val assessment = evaluator.evaluate(
            evaluation, trip, historicalContext = null,
            kinematicsSource = com.ridedecider.app.data.accessibility.uber.KinematicsSource.OCR_SUSPECT
        )

        assertEquals(Confidence.LOW, assessment.confidence)
        assertTrue(assessment.recommendation != Recommendation.TAKE)
    }
}
