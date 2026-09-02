package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DriverEconomicContext
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.TripProfitabilityLevel

/**
 * Clasificador cualitativo de rentabilidad económica para ofertas de viaje.
 *
 * Separa de manera limpia y determinista:
 * 1. **Rentabilidad Pura del Viaje**: Ratios de ingresos brutos/netos por km y por hora respecto a los umbrales configurados.
 * 2. **Contexto Económico del Conductor**: Necesidad de ritmo horario para alcanzar los objetivos diarios y horas restantes.
 */
class TripProfitabilityClassifier {

    /**
     * Clasifica una oferta evaluada en uno de los 4 niveles cualitativos de rentabilidad.
     *
     * @param metrics Métricas cinemáticas y económicas calculadas (o null si la oferta tiene datos incompletos).
     * @param config Configuración económica y umbrales del conductor.
     * @param decision Decisión base emitida por el motor ([Decision.ACCEPT], [Decision.REJECT], [Decision.UNKNOWN]).
     * @param pickupDistanceKm Distancia de recogida en kilómetros.
     * @param economicContext Contexto de objetivos y progreso económico acumulado (opcional).
     * @return [TripProfitabilityLevel] con el nivel asignado (EXCELLENT, GOOD, ACCEPTABLE, BAD).
     */
    fun classify(
        metrics: EvaluationMetrics?,
        config: ProfitabilityConfig,
        decision: Decision,
        pickupDistanceKm: Double,
        economicContext: DriverEconomicContext? = null
    ): TripProfitabilityLevel {
        if (metrics == null || decision == Decision.UNKNOWN) {
            return TripProfitabilityLevel.BAD
        }

        val minGrossKm = if (config.minGrossPerKmRate > 0.0) config.minGrossPerKmRate else 1.0
        val minGrossH = if (config.minGrossHourlyRate > 0.0) config.minGrossHourlyRate else 15.0
        val minNetH = if (config.minNetHourlyRate > 0.0) config.minNetHourlyRate else 10.0
        val maxPickupKm = if (config.maxPickupDistanceKm > 0.0) config.maxPickupDistanceKm else 5.0

        val kmRatio = metrics.grossPerKm / minGrossKm
        val hourlyRatio = metrics.grossPerHour / minGrossH
        val netHourlyRatio = metrics.netPerHour / minNetH
        val pickupRatio = pickupDistanceKm / maxPickupKm

        // 1. Caso REJECT: Puede ser ACCEPTABLE si el beneficio neto es positivo, el €/h es excelente y solo falló ligeramente en recogida
        if (decision == Decision.REJECT) {
            val isMarginallyRejected = metrics.netProfit > 0.0 &&
                    metrics.grossPerHour >= minGrossH * 1.15 &&
                    pickupRatio <= 1.20 &&
                    kmRatio >= 0.90

            return if (isMarginallyRejected) {
                TripProfitabilityLevel.ACCEPTABLE
            } else {
                TripProfitabilityLevel.BAD
            }
        }

        // 2. Caso ACCEPT: Evaluar si es EXCELLENT, GOOD o ACCEPTABLE
        val isTargetReached = economicContext?.dailyProgress?.status == com.ridedecider.app.domain.model.ProgressStatus.TARGET_REACHED
        val baseGoalHourly = if (economicContext != null && economicContext.dailyProgress.plannedHours > 0.0) {
            economicContext.dailyProgress.targetEur / economicContext.dailyProgress.plannedHours
        } else {
            minGrossH
        }
        val effectiveRequiredHourly = if (isTargetReached) baseGoalHourly else (economicContext?.dailyProgress?.requiredHourlyRate ?: 0.0)
        val targetPaceRatio = if (effectiveRequiredHourly > 0.0) metrics.grossPerHour / effectiveRequiredHourly else null

        // Si ya se alcanzó la meta diaria y el conductor sigue trabajando, exigir viajes de alto rendimiento (>= ritmo objetivo)
        if (isTargetReached && targetPaceRatio != null && targetPaceRatio < 0.95) {
            return TripProfitabilityLevel.BAD
        }

        // Criterio 1: EXCELENTE por rentabilidad pura holgada (>= 25% por encima de umbrales con recogida eficiente)
        val isPureExcellent = kmRatio >= 1.25 &&
                hourlyRatio >= 1.25 &&
                netHourlyRatio >= 1.20 &&
                pickupRatio <= 0.85 &&
                metrics.netProfit >= config.minNetTripProfit * 1.20

        // Criterio 2: EXCELENTE contextual (impulsa fuertemente el ritmo horario hacia la meta o en horas extra)
        val isContextualExcellent = targetPaceRatio != null &&
                targetPaceRatio >= 1.15 &&
                kmRatio >= 1.10 &&
                metrics.netProfit >= config.minNetTripProfit

        if (isPureExcellent || isContextualExcellent) {
            return TripProfitabilityLevel.EXCELLENT
        }

        // Criterio 3: BUENO (cumple holgadamente todos los criterios estándar de rentabilidad)
        val isGood = kmRatio >= 1.0 &&
                hourlyRatio >= 1.0 &&
                netHourlyRatio >= 1.0 &&
                metrics.netProfit >= config.minNetTripProfit

        if (isGood) {
            return TripProfitabilityLevel.GOOD
        }

        // Criterio 4: ACEPTABLE (aprobado por margen ajustado)
        return TripProfitabilityLevel.ACCEPTABLE
    }

    /**
     * Calcula una puntuación cuantitativa transparente (0 a 100) utilizando
     * 5 componentes normalizados ponderados por los pesos centralizados en [ProfitabilityConfig].
     */
    fun calculateScore(
        metrics: EvaluationMetrics,
        config: ProfitabilityConfig,
        pickupDistanceKm: Double
    ): Int {
        val minGrossH = if (config.minGrossHourlyRate > 0.0) config.minGrossHourlyRate else 24.0
        val minGrossKm = if (config.minGrossPerKmRate > 0.0) config.minGrossPerKmRate else 1.1
        val minNetTrip = if (config.minNetTripProfit > 0.0) config.minNetTripProfit else 2.0
        val maxPickupKm = if (config.maxPickupDistanceKm > 0.0) config.maxPickupDistanceKm else 4.5

        // 1. Componente Horario (Hourly Efficiency) [0.0 - 100.0]
        val hourlyRatio = metrics.grossPerHour / minGrossH
        val hourlyComponent = (hourlyRatio * 75.0).coerceIn(0.0, 100.0)

        // 2. Componente de Distancia (Distance Efficiency) [0.0 - 100.0]
        val kmRatio = metrics.effectiveGrossPerKm / minGrossKm
        val distanceComponent = (kmRatio * 75.0).coerceIn(0.0, 100.0)

        // 3. Componente de Beneficio Neto (Net Profitability) [0.0 - 100.0]
        val netRatio = metrics.netProfit / minNetTrip
        val netProfitComponent = (netRatio * 50.0).coerceIn(0.0, 100.0)

        // 4. Componente de Recogida (Pickup Distance Efficiency) [0.0 - 100.0]
        val pickupRatio = if (maxPickupKm > 0.0) (pickupDistanceKm / maxPickupKm) else 0.0
        val pickupComponent = ((1.0 - pickupRatio) * 100.0).coerceIn(0.0, 100.0)

        // 5. Componente de Utilización de Tiempo y Velocidad (Time Utilization & Traffic Speed) [0.0 - 100.0]
        val timeRatioFactor = (1.0 - metrics.pickupTimeRatio) * 70.0
        val speedFactor = if (metrics.pickupSpeedKmh != null) {
            val speedRatio = metrics.pickupSpeedKmh / config.minPickupSpeedKmh
            (speedRatio * 30.0).coerceIn(0.0, 30.0)
        } else {
            30.0 // Si no hay distancia de recogida, no hay penalización por velocidad
        }
        val timeUtilizationComponent = (timeRatioFactor + speedFactor).coerceIn(0.0, 100.0)

        // Ponderación final con pesos centralizados
        val rawScore = (hourlyComponent * config.hourlyWeight) +
                (distanceComponent * config.distanceWeight) +
                (netProfitComponent * config.netProfitWeight) +
                (pickupComponent * config.pickupWeight) +
                (timeUtilizationComponent * config.timeUtilizationWeight)

        return Math.round(rawScore).toInt().coerceIn(0, 100)
    }
}
