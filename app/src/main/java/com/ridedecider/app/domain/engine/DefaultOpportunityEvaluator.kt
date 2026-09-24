package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.accessibility.uber.KinematicsSource
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.opportunity.Confidence
import com.ridedecider.app.domain.model.opportunity.DecisionReasonCategory
import com.ridedecider.app.domain.model.opportunity.HistoricalContext
import com.ridedecider.app.domain.model.opportunity.OpportunityAssessment
import com.ridedecider.app.domain.model.opportunity.OpportunityQuality
import com.ridedecider.app.domain.model.opportunity.Recommendation
import com.ridedecider.app.domain.model.opportunity.category

class DefaultOpportunityEvaluator : OpportunityEvaluator {

    companion object {
        // grossPerHour / config.minGrossHourlyRate required for each quality level.
        // With default min=24€/h: EXCEPTIONAL ≥ 48€/h, GOOD ≥ 30€/h.
        private const val EXCEPTIONAL_HOURLY_RATIO = 2.0
        private const val GOOD_HOURLY_RATIO = 1.25

        // Minimum hourly ratio to justify reconsidering an operational SPE rejection.
        // With default min=24€/h: override requires ≥ 36€/h.
        private const val OVERRIDE_MIN_HOURLY_RATIO = 1.5

        // grossPerHour / historicalMedian ratio for historical quality upgrade.
        private const val HISTORICAL_UPGRADE_RATIO = 1.5

        // Minimum snapshot count for historical context to be statistically meaningful.
        private const val MIN_RELIABLE_SAMPLE_SIZE = 20
    }

    override fun evaluate(
        evaluation: TripEvaluation,
        trip: Trip,
        historicalContext: HistoricalContext?,
        kinematicsSource: KinematicsSource
    ): OpportunityAssessment {
        return when (evaluation.decision) {
            Decision.UNKNOWN -> assessUnknown(evaluation)
            Decision.REJECT -> assessReject(evaluation, trip, historicalContext, kinematicsSource)
            Decision.ACCEPT -> assessAccept(evaluation, trip, historicalContext, kinematicsSource)
        }
    }

    private fun assessUnknown(evaluation: TripEvaluation): OpportunityAssessment {
        return OpportunityAssessment(
            quality = OpportunityQuality.POOR,
            recommendation = Recommendation.SKIP,
            confidence = Confidence.LOW,
            speDecision = Decision.UNKNOWN,
            speOverridden = false,
            overrideJustification = null,
            reasoning = listOf("SPE no pudo evaluar: datos insuficientes o inválidos")
        )
    }

    private fun assessReject(
        evaluation: TripEvaluation,
        trip: Trip,
        historicalContext: HistoricalContext?,
        kinematicsSource: KinematicsSource
    ): OpportunityAssessment {
        val categories = evaluation.reasons.map { it.category() }.toSet()
        val hasEconomicBlock = DecisionReasonCategory.ECONOMIC in categories
        val hasDataQualityBlock = DecisionReasonCategory.DATA_QUALITY in categories

        if (hasEconomicBlock || hasDataQualityBlock) {
            return assessIrrecoverableReject(evaluation, kinematicsSource, historicalContext)
        }

        if (DecisionReasonCategory.OPERATIONAL in categories) {
            return assessOperationalOverride(evaluation, trip, historicalContext, kinematicsSource)
        }

        return OpportunityAssessment(
            quality = OpportunityQuality.POOR,
            recommendation = Recommendation.SKIP,
            confidence = Confidence.LOW,
            speDecision = Decision.REJECT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = listOf("Rechazo SPE sin categoría de razón reconocida")
        )
    }

    private fun assessIrrecoverableReject(
        evaluation: TripEvaluation,
        kinematicsSource: KinematicsSource,
        historicalContext: HistoricalContext?
    ): OpportunityAssessment {
        val reasoning = mutableListOf<String>()
        val economicReasons = evaluation.reasons
            .filter { it.category() == DecisionReasonCategory.ECONOMIC }
        val dataReasons = evaluation.reasons
            .filter { it.category() == DecisionReasonCategory.DATA_QUALITY }

        if (economicReasons.isNotEmpty()) {
            reasoning.add("Bloqueo económico: ${economicReasons.joinToString(", ") { it.name }}")
        }
        if (dataReasons.isNotEmpty()) {
            reasoning.add("Datos insuficientes: ${dataReasons.joinToString(", ") { it.name }}")
        }

        return OpportunityAssessment(
            quality = OpportunityQuality.POOR,
            recommendation = Recommendation.SKIP,
            confidence = determineConfidence(kinematicsSource, historicalContext),
            speDecision = Decision.REJECT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = reasoning
        )
    }

    private fun assessOperationalOverride(
        evaluation: TripEvaluation,
        trip: Trip,
        historicalContext: HistoricalContext?,
        kinematicsSource: KinematicsSource
    ): OpportunityAssessment {
        val metrics = evaluation.metrics ?: return OpportunityAssessment(
            quality = OpportunityQuality.POOR,
            recommendation = Recommendation.SKIP,
            confidence = Confidence.LOW,
            speDecision = Decision.REJECT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = listOf("Sin métricas disponibles para evaluar reconsideración")
        )

        val config = evaluation.configUsed
        val hourlyRatio = safeRatio(metrics.grossPerHour, config.minGrossHourlyRate)
        val kmRatio = safeRatio(metrics.grossPerKm, config.minGrossPerKmRate)
        val netProfitRatio = safeRatio(metrics.netProfit, config.minNetTripProfit)
        val netHourlyRatio = safeRatio(metrics.netPerHour, config.minNetHourlyRate)

        val allEconomicsPass = hourlyRatio >= 1.0 && kmRatio >= 1.0
                && netProfitRatio >= 1.0 && netHourlyRatio >= 1.0

        val operationalReasons = evaluation.reasons
            .filter { it.category() == DecisionReasonCategory.OPERATIONAL }
            .joinToString(", ") { it.name }

        if (!allEconomicsPass) {
            return OpportunityAssessment(
                quality = OpportunityQuality.POOR,
                recommendation = Recommendation.SKIP,
                confidence = determineConfidence(kinematicsSource, historicalContext),
                speDecision = Decision.REJECT,
                speOverridden = false,
                overrideJustification = null,
                reasoning = listOf(
                    "Rechazo operativo ($operationalReasons) sin métricas económicas compensatorias"
                )
            )
        }

        if (hourlyRatio < OVERRIDE_MIN_HOURLY_RATIO) {
            return OpportunityAssessment(
                quality = OpportunityQuality.MARGINAL,
                recommendation = Recommendation.SKIP,
                confidence = determineConfidence(kinematicsSource, historicalContext),
                speDecision = Decision.REJECT,
                speOverridden = false,
                overrideJustification = null,
                reasoning = listOf(
                    "Rentabilidad ${"%.1f".format(metrics.grossPerHour)}€/h positiva pero insuficiente para compensar recogida ($operationalReasons)"
                )
            )
        }

        val quality = determineQuality(hourlyRatio, kmRatio, metrics, historicalContext)
        val confidence = determineConfidence(kinematicsSource, historicalContext)
        val reasoning = buildOverrideReasoning(metrics, config, hourlyRatio, historicalContext, operationalReasons, trip)

        return OpportunityAssessment(
            quality = quality,
            recommendation = Recommendation.EVALUATE,
            confidence = confidence,
            speDecision = Decision.REJECT,
            speOverridden = true,
            overrideJustification = "Rechazo exclusivamente operativo ($operationalReasons). " +
                    "Rentabilidad ${"%.1f".format(metrics.grossPerHour)}€/h compensa ineficiencia de recogida",
            reasoning = reasoning
        )
    }

    private fun assessAccept(
        evaluation: TripEvaluation,
        trip: Trip,
        historicalContext: HistoricalContext?,
        kinematicsSource: KinematicsSource
    ): OpportunityAssessment {
        val metrics = evaluation.metrics ?: return OpportunityAssessment(
            quality = OpportunityQuality.MARGINAL,
            recommendation = Recommendation.EVALUATE,
            confidence = Confidence.LOW,
            speDecision = Decision.ACCEPT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = listOf("SPE aceptó pero sin métricas disponibles")
        )

        val config = evaluation.configUsed
        val hourlyRatio = safeRatio(metrics.grossPerHour, config.minGrossHourlyRate)
        val kmRatio = safeRatio(metrics.grossPerKm, config.minGrossPerKmRate)

        val quality = determineQuality(hourlyRatio, kmRatio, metrics, historicalContext)
        val confidence = determineConfidence(kinematicsSource, historicalContext)

        val recommendation = when {
            quality == OpportunityQuality.POOR -> Recommendation.SKIP
            quality == OpportunityQuality.MARGINAL -> Recommendation.EVALUATE
            confidence == Confidence.LOW -> Recommendation.EVALUATE
            else -> Recommendation.TAKE
        }

        val reasoning = buildAcceptReasoning(metrics, config, hourlyRatio, kmRatio, historicalContext, trip)

        return OpportunityAssessment(
            quality = quality,
            recommendation = recommendation,
            confidence = confidence,
            speDecision = Decision.ACCEPT,
            speOverridden = false,
            overrideJustification = null,
            reasoning = reasoning
        )
    }

    private fun determineQuality(
        hourlyRatio: Double,
        kmRatio: Double,
        metrics: EvaluationMetrics,
        historicalContext: HistoricalContext?
    ): OpportunityQuality {
        val baseQuality = when {
            hourlyRatio >= EXCEPTIONAL_HOURLY_RATIO -> OpportunityQuality.EXCEPTIONAL

            hourlyRatio >= GOOD_HOURLY_RATIO
                    && kmRatio >= 1.0 -> OpportunityQuality.GOOD

            hourlyRatio >= 1.0 -> OpportunityQuality.MARGINAL

            else -> OpportunityQuality.POOR
        }

        if (historicalContext?.medianGrossPerHour != null
            && historicalContext.medianGrossPerHour > 0.0
            && historicalContext.sampleSize >= MIN_RELIABLE_SAMPLE_SIZE
            && baseQuality != OpportunityQuality.EXCEPTIONAL
        ) {
            val histRatio = metrics.grossPerHour / historicalContext.medianGrossPerHour
            if (histRatio >= HISTORICAL_UPGRADE_RATIO) {
                return upgradeQuality(baseQuality)
            }
        }

        return baseQuality
    }

    private fun determineConfidence(
        kinematicsSource: KinematicsSource,
        historicalContext: HistoricalContext?
    ): Confidence {
        val base = when (kinematicsSource) {
            KinematicsSource.EXPLICIT_DUAL -> Confidence.HIGH
            KinematicsSource.UNIFIED_INFERRED,
            KinematicsSource.EXPLICIT_ZERO_PICKUP,
            KinematicsSource.LEGACY_UNSPECIFIED -> Confidence.MEDIUM
            KinematicsSource.OCR_SUSPECT,
            KinematicsSource.MISSING -> Confidence.LOW
        }

        if (historicalContext != null && historicalContext.sampleSize < MIN_RELIABLE_SAMPLE_SIZE) {
            return degradeConfidence(base)
        }

        return base
    }

    private fun buildOverrideReasoning(
        metrics: EvaluationMetrics,
        config: ProfitabilityConfig,
        hourlyRatio: Double,
        historicalContext: HistoricalContext?,
        operationalReasons: String,
        trip: Trip
    ): List<String> {
        val reasoning = mutableListOf<String>()
        reasoning.add("Rechazo SPE exclusivamente operativo: $operationalReasons")
        reasoning.add(
            "Rentabilidad ${"%.1f".format(metrics.grossPerHour)}€/h " +
                    "(${"%.1f".format(hourlyRatio)}× mínimo de ${"%.0f".format(config.minGrossHourlyRate)}€/h)"
        )
        reasoning.add("Beneficio neto ${"%.1f".format(metrics.netProfit)}€")
        addHistoricalReasoning(reasoning, metrics, historicalContext)
        addContextualReasoning(reasoning, trip)
        return reasoning
    }

    private fun buildAcceptReasoning(
        metrics: EvaluationMetrics,
        config: ProfitabilityConfig,
        hourlyRatio: Double,
        kmRatio: Double,
        historicalContext: HistoricalContext?,
        trip: Trip
    ): List<String> {
        val reasoning = mutableListOf<String>()
        reasoning.add(
            "Rentabilidad ${"%.1f".format(metrics.grossPerHour)}€/h " +
                    "(${"%.1f".format(hourlyRatio)}× mínimo)"
        )
        reasoning.add(
            "${"%.2f".format(metrics.grossPerKm)}€/km " +
                    "(${"%.1f".format(kmRatio)}× mínimo)"
        )
        reasoning.add("Beneficio neto ${"%.1f".format(metrics.netProfit)}€")
        addHistoricalReasoning(reasoning, metrics, historicalContext)
        addContextualReasoning(reasoning, trip)
        return reasoning
    }

    private fun addHistoricalReasoning(
        reasoning: MutableList<String>,
        metrics: EvaluationMetrics,
        historicalContext: HistoricalContext?
    ) {
        if (historicalContext == null) return
        val median = historicalContext.medianGrossPerHour ?: return
        if (median <= 0.0) return

        val histRatio = metrics.grossPerHour / median
        val sampleNote = if (historicalContext.sampleSize < MIN_RELIABLE_SAMPLE_SIZE) {
            " (muestra limitada: ${historicalContext.sampleSize} viajes)"
        } else {
            " (${historicalContext.sampleSize} viajes)"
        }
        reasoning.add(
            "${"%.1f".format(histRatio)}× tu mediana histórica de ${"%.1f".format(median)}€/h$sampleNote"
        )
    }

    private fun addContextualReasoning(reasoning: MutableList<String>, trip: Trip) {
        if (trip.isCashPayment) {
            reasoning.add("Pago en efectivo")
        }
    }

    private fun upgradeQuality(quality: OpportunityQuality): OpportunityQuality = when (quality) {
        OpportunityQuality.POOR -> OpportunityQuality.MARGINAL
        OpportunityQuality.MARGINAL -> OpportunityQuality.GOOD
        OpportunityQuality.GOOD -> OpportunityQuality.EXCEPTIONAL
        OpportunityQuality.EXCEPTIONAL -> OpportunityQuality.EXCEPTIONAL
    }

    private fun degradeConfidence(confidence: Confidence): Confidence = when (confidence) {
        Confidence.HIGH -> Confidence.MEDIUM
        Confidence.MEDIUM -> Confidence.LOW
        Confidence.LOW -> Confidence.LOW
    }

    private fun safeRatio(value: Double, minimum: Double): Double {
        if (minimum <= 0.0) return if (value > 0.0) Double.MAX_VALUE else 0.0
        return value / minimum
    }
}
