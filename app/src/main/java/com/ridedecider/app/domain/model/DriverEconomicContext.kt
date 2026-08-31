package com.ridedecider.app.domain.model

/**
 * Contexto económico del conductor disponible durante la evaluación de una oferta.
 * Permite al DecisionEngine valorar si la oferta ayuda a cumplir las metas diarias/semanales.
 */
data class DriverEconomicContext(
    val dailyProgress: EarningsProgress,
    val weeklyProgress: EarningsProgress,
    val monthlyProgress: EarningsProgress,
    val timestamp: Long = System.currentTimeMillis()
)
