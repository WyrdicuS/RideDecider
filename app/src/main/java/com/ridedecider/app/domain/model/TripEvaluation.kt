package com.ridedecider.app.domain.model

/**
 * Resultado completo e inmutable de la evaluación de una oferta de viaje de Uber.
 */
data class TripEvaluation(
    val trip: Trip,
    val configUsed: ProfitabilityConfig,
    val metrics: EvaluationMetrics?,
    val decision: Decision,
    val reasons: List<DecisionReason>,
    val evaluationTimestamp: Long,
    val profitabilityLevel: TripProfitabilityLevel = TripProfitabilityLevel.BAD
)
