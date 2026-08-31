package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverEconomicContext
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation

/**
 * Motor de decisión económico de RideDecider.
 *
 * Clase pura de Kotlin independiente del framework de Android, responsable de:
 * 1. Validar la integridad de los datos extraídos de la oferta de Uber.
 * 2. Calcular distancias, tiempos, costes operativos y métricas de rentabilidad (€/km, €/hora, beneficio neto).
 * 3. Aplicar las reglas de decisión para emitir una recomendación ([Decision.ACCEPT], [Decision.REJECT] o [Decision.UNKNOWN]).
 */
class DecisionEngine(
    private val profitabilityClassifier: TripProfitabilityClassifier = TripProfitabilityClassifier()
) {

    /**
     * Evalúa una oferta de viaje aplicando la configuración económica del conductor.
     *
     * @param trip Oferta de viaje extraída de Uber.
     * @param config Configuración de costes y umbrales mínimos del conductor.
     * @param evaluationTimestamp Timestamp de la evaluación (por defecto tiempo actual del sistema).
     * @return [TripEvaluation] con las métricas calculadas y la recomendación fundamentada.
     */
    fun evaluate(
        trip: Trip,
        config: ProfitabilityConfig,
        evaluationTimestamp: Long = System.currentTimeMillis(),
        economicContext: DriverEconomicContext? = null
    ): TripEvaluation {
        // 1. Validar presencia de datos obligatorios
        val missingReasons = validateRequiredData(trip)
        if (missingReasons.isNotEmpty()) {
            return TripEvaluation(
                trip = trip,
                configUsed = config,
                metrics = null,
                decision = Decision.UNKNOWN,
                reasons = missingReasons,
                evaluationTimestamp = evaluationTimestamp,
                profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.BAD
            )
        }

        // En este punto sabemos que los valores requeridos no son nulos
        val rawFare = trip.rawFare!!
        val pickupDistanceKm = trip.pickupDistanceKm!!
        val pickupDurationMinutes = trip.pickupDurationMinutes!!
        val tripDistanceKm = trip.tripDistanceKm!!
        val tripDurationMinutes = trip.tripDurationMinutes!!

        // 2. Validar datos físicamente posibles y no negativos (sin valores artificiales)
        if (rawFare <= 0.0 ||
            pickupDistanceKm < 0.0 ||
            tripDistanceKm < 0.0 ||
            pickupDurationMinutes < 0.0 ||
            tripDurationMinutes < 0.0
        ) {
            return TripEvaluation(
                trip = trip,
                configUsed = config,
                metrics = null,
                decision = Decision.UNKNOWN,
                reasons = listOf(DecisionReason.UNKNOWN_INVALID_DATA),
                evaluationTimestamp = evaluationTimestamp,
                profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.BAD
            )
        }

        val totalDistanceKm = pickupDistanceKm + tripDistanceKm
        val totalDurationMinutes = pickupDurationMinutes + tripDurationMinutes

        if (totalDistanceKm <= 0.0 || totalDurationMinutes <= 0.0) {
            return TripEvaluation(
                trip = trip,
                configUsed = config,
                metrics = null,
                decision = Decision.UNKNOWN,
                reasons = listOf(DecisionReason.UNKNOWN_INVALID_DATA),
                evaluationTimestamp = evaluationTimestamp,
                profitabilityLevel = com.ridedecider.app.domain.model.TripProfitabilityLevel.BAD
            )
        }

        // 3. Cálculos cinemáticos y de costes operativos
        val totalDurationHours = totalDurationMinutes / 60.0
        val estimatedOperatingCost = (totalDistanceKm * config.costPerKm) + (totalDurationHours * config.costPerHour)
        val grossProfit = rawFare
        val netProfit = rawFare - estimatedOperatingCost

        // 4. Cálculos de ratios de rentabilidad
        val grossPerKm = rawFare / totalDistanceKm
        val grossPerHour = rawFare / totalDurationHours
        val netPerHour = netProfit / totalDurationHours

        val metrics = EvaluationMetrics(
            totalDistanceKm = totalDistanceKm,
            totalDurationMinutes = totalDurationMinutes,
            estimatedOperatingCost = estimatedOperatingCost,
            grossProfit = grossProfit,
            netProfit = netProfit,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netPerHour = netPerHour
        )

        // 5. Evaluación de reglas de decisión
        val rejectReasons = mutableListOf<DecisionReason>()

        if (pickupDistanceKm > config.maxPickupDistanceKm) {
            rejectReasons.add(DecisionReason.REJECT_EXCESSIVE_PICKUP_DISTANCE)
        }

        if (pickupDurationMinutes > config.maxPickupTimeMinutes) {
            rejectReasons.add(DecisionReason.REJECT_EXCESSIVE_PICKUP_TIME)
        }

        if (grossPerKm < config.minGrossPerKmRate) {
            rejectReasons.add(DecisionReason.REJECT_LOW_KM_RATE)
        }

        if (grossPerHour < config.minGrossHourlyRate) {
            rejectReasons.add(DecisionReason.REJECT_LOW_HOURLY_RATE)
        }

        if (netProfit < config.minNetTripProfit) {
            rejectReasons.add(DecisionReason.REJECT_LOW_NET_PROFIT)
        }

        if (netPerHour < config.minNetHourlyRate) {
            rejectReasons.add(DecisionReason.REJECT_LOW_NET_HOURLY_RATE)
        }

        val decision = if (rejectReasons.isNotEmpty()) {
            Decision.REJECT
        } else {
            Decision.ACCEPT
        }

        val reasons = if (rejectReasons.isNotEmpty()) {
            rejectReasons.toMutableList()
        } else {
            mutableListOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY)
        }

        if (economicContext != null) {
            val daily = economicContext.dailyProgress
            if (daily.status == com.ridedecider.app.domain.model.ProgressStatus.TARGET_REACHED) {
                reasons.add(DecisionReason.CONTEXT_DAILY_TARGET_REACHED)
            } else if (daily.requiredHourlyRate > 0.0) {
                if (grossPerHour >= daily.requiredHourlyRate) {
                    reasons.add(DecisionReason.CONTEXT_ABOVE_REQUIRED_HOURLY_RATE)
                } else {
                    reasons.add(DecisionReason.CONTEXT_BELOW_REQUIRED_HOURLY_RATE)
                }
            }
        }

        val profitabilityLevel = profitabilityClassifier.classify(
            metrics = metrics,
            config = config,
            decision = decision,
            pickupDistanceKm = pickupDistanceKm,
            economicContext = economicContext
        )

        return TripEvaluation(
            trip = trip,
            configUsed = config,
            metrics = metrics,
            decision = decision,
            reasons = reasons,
            evaluationTimestamp = evaluationTimestamp,
            profitabilityLevel = profitabilityLevel
        )
    }

    private fun validateRequiredData(trip: Trip): List<DecisionReason> {
        val missing = mutableListOf<DecisionReason>()

        if (trip.rawFare == null) {
            missing.add(DecisionReason.UNKNOWN_MISSING_FARE)
        }
        if (trip.pickupDistanceKm == null) {
            missing.add(DecisionReason.UNKNOWN_MISSING_PICKUP_DISTANCE)
        }
        if (trip.pickupDurationMinutes == null) {
            missing.add(DecisionReason.UNKNOWN_MISSING_PICKUP_TIME)
        }
        if (trip.tripDistanceKm == null) {
            missing.add(DecisionReason.UNKNOWN_MISSING_TRIP_DISTANCE)
        }
        if (trip.tripDurationMinutes == null) {
            missing.add(DecisionReason.UNKNOWN_MISSING_TRIP_TIME)
        }

        return missing
    }
}
