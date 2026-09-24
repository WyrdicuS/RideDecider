package com.ridedecider.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.model.UberCategory

/**
 * Entidad persistente de Room para el historial de viajes de RideDecider.
 * Preserva la separación estricta entre la oferta estimada y el resultado final completado.
 */
@Entity(tableName = "recorded_trips")
data class RecordedTripEntity(
    @PrimaryKey
    val id: String,

    // Datos de la oferta
    val offerType: String,
    val category: String,
    val estimatedFareEur: Double?,
    val currency: String,
    val pickupDistanceKm: Double?,
    val pickupDurationMinutes: Double?,
    val pickupAddress: String?,
    val tripDistanceKm: Double?,
    val tripDurationMinutes: Double?,
    val dropoffAddress: String?,
    val isCashPayment: Boolean,
    val passengerRating: Double?,

    // Datos de evaluación
    val decision: String,
    val reasonsCommaSeparated: String,

    // Estado del ciclo de vida
    val status: String,
    val wasAutoAssigned: Boolean = false,
    val recordedTimestamp: Long,
    val completedTimestamp: Long? = null,

    // Datos reales confirmados tras finalizar
    val finalEarningsEur: Double? = null,
    val actualDurationMinutes: Double? = null,

    // Datos de cancelación y compensación
    val cancellationFeeEur: Double? = null,
    val cancellationReason: String? = null,
    val cancelledTimestamp: Long? = null
) {
    fun toDomain(): RecordedTrip {
        val domainTrip = Trip(
            id = id,
            timestamp = recordedTimestamp,
            offerType = try { TripOfferType.valueOf(offerType) } catch (e: Exception) { TripOfferType.TRIP_OFFER },
            category = try { UberCategory.valueOf(category) } catch (e: Exception) { UberCategory.UBER_X },
            rawFare = estimatedFareEur,
            currency = currency,
            pickupDistanceKm = pickupDistanceKm,
            pickupDurationMinutes = pickupDurationMinutes,
            pickupAddress = pickupAddress,
            tripDistanceKm = tripDistanceKm,
            tripDurationMinutes = tripDurationMinutes,
            dropoffAddress = dropoffAddress,
            isCashPayment = isCashPayment,
            passengerRating = passengerRating
        )

        val parsedDecision = try { Decision.valueOf(decision) } catch (e: Exception) { Decision.UNKNOWN }
        val parsedReasons = reasonsCommaSeparated.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull {
                try { DecisionReason.valueOf(it.trim()) } catch (e: Exception) { null }
            }

        // R6.7: PLACEHOLDER. La ProfitabilityConfig usada en la evaluacion original NO se
        // persiste en el schema Room actual (no existen columnas para minGrossHourlyRate,
        // costPerKm, etc.). Estos valores se rellenan al reconstruir el TripEvaluation
        // desde la entidad, y NO representan un snapshot historico real de la config del
        // motor. No coinciden intencionadamente con el umbral vivo del motor
        // (InMemoryProfitabilityConfigProvider) para dejar claro que son placeholders.
        // No modificar sin haber persistido antes las columnas correspondientes.
        val domainEvaluation = TripEvaluation(
            trip = domainTrip,
            configUsed = ProfitabilityConfig(
                costPerKm = 0.20,
                costPerHour = 5.0,
                minGrossHourlyRate = 25.0,
                minGrossPerKmRate = 1.20,
                minNetTripProfit = 2.0,
                minNetHourlyRate = 18.0,
                maxPickupDistanceKm = 5.0,
                maxPickupTimeMinutes = 10.0
            ),
            metrics = null,
            decision = parsedDecision,
            reasons = parsedReasons,
            evaluationTimestamp = recordedTimestamp
        )

        val domainStatus = try { TripTrackingStatus.valueOf(status) } catch (e: Exception) { TripTrackingStatus.EVALUATED }
        val parsedCancellationReason = cancellationReason?.let {
            try { com.ridedecider.app.domain.model.CancellationReason.valueOf(it) } catch (e: Exception) { null }
        }

        return RecordedTrip(
            id = id,
            trip = domainTrip,
            evaluation = domainEvaluation,
            status = domainStatus,
            recordedTimestamp = recordedTimestamp,
            completedTimestamp = completedTimestamp,
            finalEarningsEur = finalEarningsEur,
            durationMinutes = actualDurationMinutes,
            cancellationFeeEur = cancellationFeeEur,
            cancellationReason = parsedCancellationReason,
            cancelledTimestamp = cancelledTimestamp
        )
    }

    companion object {
        fun fromDomain(domain: RecordedTrip, wasAutoAssigned: Boolean = false): RecordedTripEntity {
            val trip = domain.trip
            val evaluation = domain.evaluation
            val reasonsJoined = evaluation.reasons.joinToString(",") { it.name }

            return RecordedTripEntity(
                id = domain.id,
                offerType = trip.offerType.name,
                category = trip.category.name,
                estimatedFareEur = trip.rawFare,
                currency = trip.currency,
                pickupDistanceKm = trip.pickupDistanceKm,
                pickupDurationMinutes = trip.pickupDurationMinutes,
                pickupAddress = trip.pickupAddress,
                tripDistanceKm = trip.tripDistanceKm,
                tripDurationMinutes = trip.tripDurationMinutes,
                dropoffAddress = trip.dropoffAddress,
                isCashPayment = trip.isCashPayment,
                passengerRating = trip.passengerRating,
                decision = evaluation.decision.name,
                reasonsCommaSeparated = reasonsJoined,
                status = domain.status.name,
                wasAutoAssigned = wasAutoAssigned,
                recordedTimestamp = domain.recordedTimestamp,
                completedTimestamp = domain.completedTimestamp,
                finalEarningsEur = domain.finalEarningsEur,
                actualDurationMinutes = domain.durationMinutes,
                cancellationFeeEur = domain.cancellationFeeEur,
                cancellationReason = domain.cancellationReason?.name,
                cancelledTimestamp = domain.cancelledTimestamp
            )
        }
    }
}
