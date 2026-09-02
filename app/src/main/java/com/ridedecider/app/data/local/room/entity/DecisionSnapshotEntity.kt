package com.ridedecider.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ridedecider.app.domain.model.ReconciliationMetrics

/**
 * Entidad inmutable de Room que congela la fotografía histórica de lo que RideDecider
 * sabía y calculó en el instante exacto t₀ de emitir una recomendación económica.
 *
 * Base inmutable para la futura inteligencia adaptativa (Learning Data Foundation).
 */
@Entity(
    tableName = "decision_snapshots",
    indices = [
        Index(value = ["instanceId"]),
        Index(value = ["tripId"]),
        Index(value = ["recordedTimestamp"]),
        Index(value = ["decision"])
    ]
)
data class DecisionSnapshotEntity(
    @PrimaryKey
    val snapshotId: String,          // UUID único del snapshot
    val instanceId: String,          // UUID de la instancia de la oferta
    val tripId: String,              // ID del viaje de dominio (coincide con instanceId)
    val recordedTimestamp: Long,     // Timestamp exacto t₀ de evaluación
    val appVersion: String = "1.0.1",
    val engineVersion: String = "2.0",

    // Datos de la Oferta Estimada
    val offerType: String,
    val category: String,
    val estimatedFareEur: Double?,
    val currency: String,
    val estimatedPickupDistanceKm: Double?,
    val estimatedPickupDurationMinutes: Double?,
    val estimatedTripDistanceKm: Double?,
    val estimatedTripDurationMinutes: Double?,
    val estimatedTotalDistanceKm: Double?,
    val estimatedTotalDurationMinutes: Double?,

    // Métricas Calculadas en t₀ por SPE 2.0
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val effectiveGrossPerKm: Double?,
    val estimatedOperatingCost: Double?,
    val estimatedNetProfit: Double?,
    val netPerHour: Double?,
    val pickupSpeedKmh: Double?,
    val pickupDistanceRatio: Double?,
    val pickupTimeRatio: Double?,
    val profitabilityScore: Int?,

    // Decisión y Razones
    val decision: String,
    val decisionReasonsCommaSeparated: String,
    val profitabilityLevel: String,
    val kinematicsSource: String,

    // Contexto Temporal
    val dayOfWeek: Int,             // Calendar.DAY_OF_WEEK (1=Domingo..7=Sábado)
    val hourOfDay: Int,             // 0..23
    val minuteOfHour: Int,          // 0..59
    val timeBucket: String,         // MORNING_RUSH, DAY, MIDDAY_EXIT, EVENING_RUSH, NIGHT

    // Navegación Waze (Campos preparados para integración futura)
    val wazeEstimatedDurationMinutes: Double? = null,
    val wazeAvailable: Boolean = false,
    val uberWazeDurationDeltaMinutes: Double? = null,
    val wazeEstimateTimestamp: Long? = null,

    // Resultado Real Confirmado (Métricas reales capturadas tras la finalización)
    val actualDistanceKm: Double? = null,
    val actualDurationMinutes: Double? = null,
    val actualPickupDurationMinutes: Double? = null,
    val actualBaseFareEur: Double? = null,
    val waitingCompensationEur: Double? = null,
    val cancellationFeeEur: Double? = null,
    val tipEur: Double? = null,
    val finalEarningsEur: Double? = null
) {
    fun calculateReconciliationMetrics(): ReconciliationMetrics {
        val durErr = if (actualDurationMinutes != null && estimatedTotalDurationMinutes != null) {
            actualDurationMinutes - estimatedTotalDurationMinutes
        } else null

        val distErr = if (actualDistanceKm != null && estimatedTotalDistanceKm != null) {
            actualDistanceKm - estimatedTotalDistanceKm
        } else null

        val fareErr = if (finalEarningsEur != null && estimatedFareEur != null) {
            finalEarningsEur - estimatedFareEur
        } else null

        val deltaEur = if (finalEarningsEur != null && estimatedFareEur != null) {
            finalEarningsEur - estimatedFareEur
        } else null

        return ReconciliationMetrics(
            durationErrorMinutes = durErr,
            distanceErrorKm = distErr,
            fareErrorEur = fareErr,
            earningsDeltaEur = deltaEur,
            profitErrorEur = null
        )
    }
}
