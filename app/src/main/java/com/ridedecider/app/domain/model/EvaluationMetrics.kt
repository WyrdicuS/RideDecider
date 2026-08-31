package com.ridedecider.app.domain.model

/**
 * Métricas económicas y cinemáticas calculadas por el DecisionEngine.
 */
data class EvaluationMetrics(
    val totalDistanceKm: Double,
    val totalDurationMinutes: Double,
    val estimatedOperatingCost: Double,
    val grossProfit: Double,
    val netProfit: Double,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val netPerHour: Double
)
