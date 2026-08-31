package com.ridedecider.app.domain.model

/**
 * Configuración de costes operativos y umbrales mínimos de rentabilidad del conductor.
 */
data class ProfitabilityConfig(
    val costPerKm: Double,
    val costPerHour: Double,
    val minGrossHourlyRate: Double,
    val minGrossPerKmRate: Double,
    val minNetTripProfit: Double,
    val minNetHourlyRate: Double,
    val maxPickupDistanceKm: Double,
    val maxPickupTimeMinutes: Double
)
