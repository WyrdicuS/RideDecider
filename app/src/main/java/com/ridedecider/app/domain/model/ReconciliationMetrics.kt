package com.ridedecider.app.domain.model

/**
 * Métricas inmutables de reconciliación que miden la desviación real entre las estimaciones
 * iniciales tomadas en t₀ y los resultados reales confirmados tras la finalización del viaje.
 */
data class ReconciliationMetrics(
    val durationErrorMinutes: Double?,      // actualDurationMinutes - estimatedTotalDurationMinutes
    val distanceErrorKm: Double?,            // actualDistanceKm - estimatedTotalDistanceKm
    val fareErrorEur: Double?,               // finalEarningsEur - estimatedFareEur
    val earningsDeltaEur: Double?,           // finalEarningsEur - estimatedFareEur
    val profitErrorEur: Double? = null       // actualNetProfit - estimatedNetProfit (permanece null si no hay datos netos reales)
)
