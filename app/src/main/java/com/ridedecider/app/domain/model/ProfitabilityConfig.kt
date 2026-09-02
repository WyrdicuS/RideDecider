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
    val maxPickupTimeMinutes: Double,
    val pickupDistanceWeight: Double = 1.5,
    val minPickupSpeedKmh: Double = 10.0,
    val hourlyWeight: Double = 0.35,
    val distanceWeight: Double = 0.25,
    val netProfitWeight: Double = 0.20,
    val pickupWeight: Double = 0.10,
    val timeUtilizationWeight: Double = 0.10
)
