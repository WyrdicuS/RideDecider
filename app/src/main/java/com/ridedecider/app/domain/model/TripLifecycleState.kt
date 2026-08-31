package com.ridedecider.app.domain.model

/**
 * Estados del ciclo de vida estricto de una oferta y viaje en RideDecider.
 * Garantiza la trazabilidad desde la detección inicial hasta la finalización real.
 */
sealed class TripLifecycleState {
    /** Sin oferta presente (conductor en línea, mapa en reposo o desconectado). */
    data object Idle : TripLifecycleState()

    /** Oferta detectada (directa o radar) y evaluada por el DecisionEngine. */
    data class OfferDetected(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val detectedAt: Long = System.currentTimeMillis()
    ) : TripLifecycleState()

    /** Oferta visible en pantalla pendiente de decisión manual del conductor o asignación. */
    data class PendingAcceptance(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val detectedAt: Long = System.currentTimeMillis()
    ) : TripLifecycleState()

    /** Asignación real confirmada (por aceptación manual o asignación automática de Radar). */
    data class Assigned(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val assignedAt: Long = System.currentTimeMillis(),
        val wasAutoAssigned: Boolean = false
    ) : TripLifecycleState()

    /** Viaje iniciado y en curso (navegación activa / recogida / destino). */
    data class ActiveTrip(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val startedAt: Long = System.currentTimeMillis()
    ) : TripLifecycleState()

    /** Viaje finalizado con ganancia real e importe confirmado. */
    data class Completed(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val completedAt: Long = System.currentTimeMillis(),
        val finalFareEur: Double,
        val durationMinutes: Double
    ) : TripLifecycleState()

    /** Viaje cancelado tras haber sido aceptado o iniciado (por pasajero, conductor, Uber o No-Show). */
    data class Cancelled(
        val trip: Trip,
        val evaluation: TripEvaluation,
        val reason: CancellationReason,
        val cancellationFeeEur: Double? = null,
        val cancelledAt: Long = System.currentTimeMillis()
    ) : TripLifecycleState()

    /** Oferta descartada, ignorada, rechazada por el conductor o expirada / ganada por otro conductor. */
    data class IgnoredOrExpired(
        val tripId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val reason: String = "EXPIRED_OR_REJECTED"
    ) : TripLifecycleState()
}
