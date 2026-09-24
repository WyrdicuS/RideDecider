package com.ridedecider.app.ui.overlay

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionMode
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.GoalContextMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripProfitabilityLevel
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.model.opportunity.Confidence
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper
import com.ridedecider.app.ui.overlay.model.HudVisualTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de R5: bifurcacion de presentacion MANUAL vs AUTOMATIC en HudUiModelMapper.
 *
 * No re-evalua economia (SPE/OpportunityEvaluator estan FROZEN): construye TripEvaluation
 * y OpportunityAssessment directamente para aislar el comportamiento del mapper.
 */
class HudUiModelMapperAutomaticTest {

    private val config = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 24.0,
        minGrossPerKmRate = 1.10,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 4.5,
        maxPickupTimeMinutes = 9.0
    )

    private fun buildTrip(): Trip = Trip(
        id = "auto-test-trip",
        timestamp = System.currentTimeMillis(),
        offerType = TripOfferType.TRIP_OFFER,
        category = UberCategory.UBER_X,
        rawFare = 15.0,
        currency = "EUR",
        pickupDistanceKm = 2.0,
        pickupDurationMinutes = 4.0,
        pickupAddress = null,
        tripDistanceKm = 8.0,
        tripDurationMinutes = 16.0,
        dropoffAddress = null
    )

    private fun buildEvaluation(
        decision: Decision = Decision.ACCEPT,
        profitabilityLevel: TripProfitabilityLevel = TripProfitabilityLevel.GOOD,
        goalContext: GoalContextMetrics? = null,
        metrics: EvaluationMetrics? = EvaluationMetrics(
            totalDistanceKm = 10.0,
            totalDurationMinutes = 20.0,
            estimatedOperatingCost = 2.0,
            grossProfit = 15.0,
            netProfit = 13.0,
            grossPerKm = 1.5,
            grossPerHour = 45.0,
            netPerHour = 39.0
        )
    ): TripEvaluation = TripEvaluation(
        trip = buildTrip(),
        configUsed = config,
        metrics = metrics,
        decision = decision,
        reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
        evaluationTimestamp = System.currentTimeMillis(),
        profitabilityLevel = profitabilityLevel,
        goalContext = goalContext
    )

    private fun buildAssessment(
        quality: OpportunityQuality = OpportunityQuality.GOOD,
        recommendation: Recommendation = Recommendation.TAKE,
        confidence: Confidence = Confidence.HIGH,
        speDecision: Decision = Decision.ACCEPT,
        speOverridden: Boolean = false,
        overrideJustification: String? = null
    ): OpportunityAssessment = OpportunityAssessment(
        quality = quality,
        recommendation = recommendation,
        confidence = confidence,
        speDecision = speDecision,
        speOverridden = speOverridden,
        overrideJustification = overrideJustification,
        reasoning = listOf("Rentabilidad 45.0€/h")
    )

    private fun buildGoalContext(): GoalContextMetrics = GoalContextMetrics(
        targetPaceRatio = 1.5,
        estimatedGoalContribution = 0.12,
        estimatedTimeConsumption = 0.05,
        progressStatus = ProgressStatus.AHEAD,
        remainingEur = 80.0,
        remainingHours = 3.0,
        requiredHourlyRate = 25.0
    )

    // ══════════════════════════════════════════════════════
    // AUTOMATIC — Semantica de texto (1-4)
    // ══════════════════════════════════════════════════════

    // 1. EXCEPTIONAL + TAKE -> "OPORTUNIDAD EXCEPCIONAL"
    @Test
    fun automatic_exceptionalTake_showsOportunidadExcepcional() {
        val evaluation = buildEvaluation()
        val assessment = buildAssessment(quality = OpportunityQuality.EXCEPTIONAL, recommendation = Recommendation.TAKE)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("OPORTUNIDAD EXCEPCIONAL", model.recommendationText)
        assertEquals(HudVisualTier.EXCELLENT, model.tier)
    }

    // 2. GOOD + TAKE -> "BUENA OPORTUNIDAD"
    @Test
    fun automatic_goodTake_showsBuenaOportunidad() {
        val evaluation = buildEvaluation()
        val assessment = buildAssessment(quality = OpportunityQuality.GOOD, recommendation = Recommendation.TAKE)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("BUENA OPORTUNIDAD", model.recommendationText)
        assertEquals(HudVisualTier.GOOD, model.tier)
    }

    // 3. EVALUATE -> "VALORAR"
    @Test
    fun automatic_evaluate_showsValorar() {
        val evaluation = buildEvaluation()
        val assessment = buildAssessment(quality = OpportunityQuality.MARGINAL, recommendation = Recommendation.EVALUATE, confidence = Confidence.MEDIUM)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("VALORAR", model.recommendationText)
        assertEquals(HudVisualTier.ACCEPTABLE, model.tier)
    }

    // 4. SKIP -> "PASAR"
    @Test
    fun automatic_skip_showsPasar() {
        val evaluation = buildEvaluation(decision = Decision.REJECT)
        val assessment = buildAssessment(quality = OpportunityQuality.POOR, recommendation = Recommendation.SKIP, speDecision = Decision.REJECT)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("PASAR", model.recommendationText)
        assertEquals(HudVisualTier.BAD, model.tier)
    }

    // 5. LOW confidence -> incertidumbre visible
    @Test
    fun automatic_lowConfidence_showsUncertaintyMarker() {
        val evaluation = buildEvaluation()
        val assessment = buildAssessment(
            quality = OpportunityQuality.MARGINAL,
            recommendation = Recommendation.EVALUATE,
            confidence = Confidence.LOW
        )

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("VALORAR — datos limitados", model.recommendationText)
        assertEquals("Confianza baja", model.confidenceText)
    }

    // 6. speOverridden=true -> EVALUATE / valoracion, nunca TAKE visual
    @Test
    fun automatic_operationalOverride_neverShowsTake() {
        val evaluation = buildEvaluation(decision = Decision.REJECT)
        val assessment = buildAssessment(
            quality = OpportunityQuality.EXCEPTIONAL,
            recommendation = Recommendation.EVALUATE,
            speDecision = Decision.REJECT,
            speOverridden = true,
            overrideJustification = "Rechazo exclusivamente operativo (pickup excesivo). Rentabilidad 45.0€/h compensa ineficiencia de recogida"
        )

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("VALORAR", model.recommendationText)
        assertFalse(model.recommendationText == "OPORTUNIDAD EXCEPCIONAL")
        assertFalse(model.recommendationText == "BUENA OPORTUNIDAD")
        assertTrue(model.isOverride)
        assertEquals(assessment.overrideJustification, model.overrideText)
    }

    // 7. assessment == null -> fallback conservador (nunca TAKE)
    @Test
    fun automatic_nullAssessment_acceptFallback_isNeverAffirmative() {
        val evaluation = buildEvaluation(decision = Decision.ACCEPT)

        val model = HudUiModelMapper.map(evaluation, null, DecisionMode.AUTOMATIC)

        assertEquals("OPORTUNIDAD DETECTADA", model.recommendationText)
        assertEquals(HudVisualTier.ACCEPTABLE, model.tier)
        assertNull(model.qualityText)
        assertNull(model.confidenceText)
    }

    @Test
    fun automatic_nullAssessment_rejectFallback_showsPasar() {
        val evaluation = buildEvaluation(decision = Decision.REJECT)

        val model = HudUiModelMapper.map(evaluation, null, DecisionMode.AUTOMATIC)

        assertEquals("PASAR", model.recommendationText)
        assertEquals(HudVisualTier.BAD, model.tier)
    }

    // 8. UNKNOWN -> "SIN DATOS SUFICIENTES"
    @Test
    fun automatic_unknown_showsSinDatosSuficientes() {
        val evaluation = buildEvaluation(decision = Decision.UNKNOWN, metrics = null)
        val assessment = buildAssessment(
            quality = OpportunityQuality.POOR,
            recommendation = Recommendation.SKIP,
            confidence = Confidence.LOW,
            speDecision = Decision.UNKNOWN
        )

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)

        assertEquals("SIN DATOS SUFICIENTES", model.recommendationText)
    }

    @Test
    fun automatic_unknown_nullAssessment_showsSinDatosSuficientes() {
        val evaluation = buildEvaluation(decision = Decision.UNKNOWN, metrics = null)

        val model = HudUiModelMapper.map(evaluation, null, DecisionMode.AUTOMATIC)

        assertEquals("SIN DATOS SUFICIENTES", model.recommendationText)
        assertEquals(HudVisualTier.BAD, model.tier)
    }

    // ══════════════════════════════════════════════════════
    // GOALS INDEPENDENCE (9-11)
    // ══════════════════════════════════════════════════════

    // 9, 10, 11. Misma oferta + Goal A / Goal B / sin Goal -> misma semantica AUTOMATIC
    @Test
    fun automatic_sameOffer_differentGoals_producesSameSemantics() {
        val assessment = buildAssessment(quality = OpportunityQuality.GOOD, recommendation = Recommendation.TAKE)

        val goalA = GoalContextMetrics(
            targetPaceRatio = 5.0,
            estimatedGoalContribution = 0.9,
            estimatedTimeConsumption = 0.5,
            progressStatus = ProgressStatus.BEHIND,
            remainingEur = 3.0,
            remainingHours = 0.2,
            requiredHourlyRate = 200.0
        )
        val goalB = buildGoalContext()

        val evalWithGoalA = buildEvaluation(goalContext = goalA)
        val evalWithGoalB = buildEvaluation(goalContext = goalB)
        val evalWithoutGoal = buildEvaluation(goalContext = null)

        val modelA = HudUiModelMapper.map(evalWithGoalA, assessment, DecisionMode.AUTOMATIC)
        val modelB = HudUiModelMapper.map(evalWithGoalB, assessment, DecisionMode.AUTOMATIC)
        val modelNone = HudUiModelMapper.map(evalWithoutGoal, assessment, DecisionMode.AUTOMATIC)

        assertEquals(modelA.recommendationText, modelB.recommendationText)
        assertEquals(modelB.recommendationText, modelNone.recommendationText)
        assertEquals(modelA.qualityText, modelB.qualityText)
        assertEquals(modelA.tier, modelB.tier)
        assertEquals(modelA.tier, modelNone.tier)

        // Automatic nunca expone GoalContext, independientemente de si estaba presente
        assertNull(modelA.goalPaceText)
        assertNull(modelA.goalContributionText)
        assertNull(modelA.goalStatusText)
        assertNull(modelB.goalPaceText)
        assertNull(modelNone.goalPaceText)
    }

    // ══════════════════════════════════════════════════════
    // MANUAL (12-15)
    // ══════════════════════════════════════════════════════

    // 12. Manual conserva profitabilityLevel/HudVisualTier
    @Test
    fun manual_usesTripProfitabilityLevel_forTier() {
        val evaluation = buildEvaluation(profitabilityLevel = TripProfitabilityLevel.EXCELLENT)

        val model = HudUiModelMapper.map(evaluation, null, DecisionMode.MANUAL)

        assertEquals(HudVisualTier.EXCELLENT, model.tier)
    }

    // 13. Manual puede mostrar GoalContext
    @Test
    fun manual_showsGoalContext_whenPresent() {
        val evaluation = buildEvaluation(goalContext = buildGoalContext())

        val model = HudUiModelMapper.map(evaluation, null, DecisionMode.MANUAL)

        assertEquals("150% ritmo", model.goalPaceText)
        assertTrue(model.goalContributionText!!.contains("del restante"))
        assertEquals("Adelantado", model.goalStatusText)
    }

    // 14. OpportunityQuality NO sustituye profitabilityLevel en Manual
    @Test
    fun manual_opportunityQuality_doesNotOverrideProfitabilityLevelTier() {
        // profitabilityLevel dice GOOD, pero el assessment (si se pasara) diria EXCEPTIONAL.
        // Manual debe ignorar quality para el tier.
        val evaluation = buildEvaluation(profitabilityLevel = TripProfitabilityLevel.GOOD)
        val assessment = buildAssessment(quality = OpportunityQuality.EXCEPTIONAL, recommendation = Recommendation.TAKE)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.MANUAL)

        assertEquals(HudVisualTier.GOOD, model.tier)
        assertFalse(model.tier == HudVisualTier.EXCELLENT)
    }

    // 15. profitabilityScore/OpportunityAssessment no se convierten en Recommendation en Manual
    @Test
    fun manual_doesNotExposeRecommendationFields() {
        val evaluation = buildEvaluation()
        val assessment = buildAssessment(quality = OpportunityQuality.EXCEPTIONAL, recommendation = Recommendation.TAKE)

        val model = HudUiModelMapper.map(evaluation, assessment, DecisionMode.MANUAL)

        assertNull(model.recommendationText)
        assertNull(model.qualityText)
        assertNull(model.confidenceText)
        assertFalse(model.isOverride)
        assertEquals(DecisionMode.MANUAL, model.decisionMode)
    }

    // ══════════════════════════════════════════════════════
    // SAFETY (22-23): mapper no muta las entidades de entrada
    // ══════════════════════════════════════════════════════

    @Test
    fun mapping_doesNotMutate_tripEvaluationOrAssessment() {
        val evaluation = buildEvaluation(goalContext = buildGoalContext())
        val assessment = buildAssessment(speOverridden = true, overrideJustification = "justificacion")
        val evaluationSnapshot = evaluation.copy()
        val assessmentSnapshot = assessment.copy()

        HudUiModelMapper.map(evaluation, assessment, DecisionMode.AUTOMATIC)
        HudUiModelMapper.map(evaluation, assessment, DecisionMode.MANUAL)

        assertEquals(evaluationSnapshot, evaluation)
        assertEquals(assessmentSnapshot, assessment)
    }
}
