package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripLifecycleState

/**
 * Máquina de estados determinista y pura en Kotlin para gestionar el ciclo de vida
 * de las ofertas y viajes en RideDecider.
 *
 * Reglas fundamentales:
 * 1. Una evaluación NO implica aceptación.
 * 2. La desaparición de una tarjeta NO implica aceptación (transiciona a IgnoredOrExpired).
 * 3. Solo transiciona a Assigned/ActiveTrip ante señales positivas de aceptación manual,
 *    asignación automática de Radar o inicio verificado de ACTIVE_TRIP.
 * 4. Solo transiciona a Completed y suma ganancias tras completar un ACTIVE_TRIP.
 * 5. Idempotente: descarta eventos duplicados del mismo viaje.
 */
class TripLifecycleStateMachine {

    var currentState: TripLifecycleState = TripLifecycleState.Idle
        private set

    /**
     * Procesa la detección de una nueva oferta o actualización de la oferta visible.
     */
    fun onOfferDetected(
        trip: Trip,
        evaluation: TripEvaluation,
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.Idle,
            is TripLifecycleState.Cancelled,
            is TripLifecycleState.IgnoredOrExpired,
            is TripLifecycleState.Completed -> {
                val newState = TripLifecycleState.PendingAcceptance(trip, evaluation, timestamp)
                currentState = newState
                newState
            }
            is TripLifecycleState.PendingAcceptance -> {
                if (state.trip.id == trip.id) {
                    // Mismo viaje: actualizar evaluación si corresponde sin cambiar estado
                    val updated = state.copy(trip = trip, evaluation = evaluation)
                    currentState = updated
                    updated
                } else {
                    // Oferta reemplazada por una nueva
                    val newState = TripLifecycleState.PendingAcceptance(trip, evaluation, timestamp)
                    currentState = newState
                    newState
                }
            }
            is TripLifecycleState.OfferDetected -> {
                val newState = TripLifecycleState.PendingAcceptance(trip, evaluation, timestamp)
                currentState = newState
                newState
            }
            is TripLifecycleState.Assigned -> {
                // Si ya está asignado este viaje, mantener Assigned
                state
            }
            is TripLifecycleState.ActiveTrip -> {
                // En viaje activo no se aceptan nuevas ofertas
                state
            }
        }
    }

    /**
     * Procesa la desaparición de una oferta de la pantalla sin señal previa de asignación.
     * Salida segura: marca como IgnoredOrExpired sin contabilizar ganancias.
     */
    fun onOfferDismissed(
        timestamp: Long = System.currentTimeMillis(),
        reason: String = "DISMISSED_WITHOUT_ASSIGNMENT"
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.PendingAcceptance,
            is TripLifecycleState.OfferDetected -> {
                val tripId = if (state is TripLifecycleState.PendingAcceptance) state.trip.id else ""
                val newState = TripLifecycleState.IgnoredOrExpired(tripId, timestamp, reason)
                currentState = newState
                newState
            }
            else -> state
        }
    }

    /**
     * Procesa la confirmación de aceptación manual del conductor (ej. pulsación en "Aceptar" / "Emparejar").
     */
    fun onOfferAcceptedManually(
        tripId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.PendingAcceptance -> {
                if (tripId == null || tripId == state.trip.id) {
                    val newState = TripLifecycleState.Assigned(
                        trip = state.trip,
                        evaluation = state.evaluation,
                        assignedAt = timestamp,
                        wasAutoAssigned = false
                    )
                    currentState = newState
                    newState
                } else {
                    state
                }
            }
            else -> state
        }
    }

    /**
     * Procesa la confirmación de asignación automática de Trip Radar por parte de Uber.
     */
    fun onRadarAutoAssigned(
        tripId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.PendingAcceptance -> {
                if (tripId == null || tripId == state.trip.id) {
                    val newState = TripLifecycleState.Assigned(
                        trip = state.trip,
                        evaluation = state.evaluation,
                        assignedAt = timestamp,
                        wasAutoAssigned = true
                    )
                    currentState = newState
                    newState
                } else {
                    state
                }
            }
            else -> state
        }
    }

    /**
     * Procesa la transición a ACTIVE_TRIP (pantalla de navegación/recogida en curso).
     */
    fun onActiveTripStarted(
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.Assigned -> {
                val newState = TripLifecycleState.ActiveTrip(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    startedAt = timestamp
                )
                currentState = newState
                newState
            }
            is TripLifecycleState.PendingAcceptance -> {
                // Asignación directa confirmada por el inicio de viaje
                val newState = TripLifecycleState.ActiveTrip(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    startedAt = timestamp
                )
                currentState = newState
                newState
            }
            is TripLifecycleState.ActiveTrip -> {
                state // Ya en viaje activo
            }
            else -> state
        }
    }

    /**
     * Procesa la finalización del viaje activo.
     * Único punto que genera el estado Completed con ganancias confirmadas.
     * NUNCA permite la transición desde Cancelled o IgnoredOrExpired hacia Completed.
     */
    fun onTripCompleted(
        finalFareEur: Double? = null,
        durationMinutes: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.ActiveTrip -> {
                val fare = finalFareEur ?: state.trip.rawFare ?: 0.0
                val dur = durationMinutes ?: state.trip.tripDurationMinutes ?: 0.0
                val newState = TripLifecycleState.Completed(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    completedAt = timestamp,
                    finalFareEur = fare,
                    durationMinutes = dur
                )
                currentState = newState
                newState
            }
            // Prohibir explícitamente cualquier transición desde estados cancelados o terminados
            is TripLifecycleState.Cancelled,
            is TripLifecycleState.IgnoredOrExpired,
            is TripLifecycleState.Completed,
            is TripLifecycleState.Idle,
            is TripLifecycleState.OfferDetected,
            is TripLifecycleState.PendingAcceptance,
            is TripLifecycleState.Assigned -> state
        }
    }

    /**
     * Procesa la cancelación de un viaje (por pasajero, conductor, Uber o No-Show).
     * Registra el motivo y la compensación si existe, pasando a estado terminal Cancelled.
     */
    fun onTripCancelled(
        reason: com.ridedecider.app.domain.model.CancellationReason,
        cancellationFeeEur: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ): TripLifecycleState {
        val state = currentState
        return when (state) {
            is TripLifecycleState.ActiveTrip -> {
                val newState = TripLifecycleState.Cancelled(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    reason = reason,
                    cancellationFeeEur = cancellationFeeEur,
                    cancelledAt = timestamp
                )
                currentState = newState
                newState
            }
            is TripLifecycleState.Assigned -> {
                val newState = TripLifecycleState.Cancelled(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    reason = reason,
                    cancellationFeeEur = cancellationFeeEur,
                    cancelledAt = timestamp
                )
                currentState = newState
                newState
            }
            is TripLifecycleState.PendingAcceptance -> {
                val newState = TripLifecycleState.Cancelled(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    reason = reason,
                    cancellationFeeEur = cancellationFeeEur,
                    cancelledAt = timestamp
                )
                currentState = newState
                newState
            }
            is TripLifecycleState.OfferDetected -> {
                val newState = TripLifecycleState.Cancelled(
                    trip = state.trip,
                    evaluation = state.evaluation,
                    reason = reason,
                    cancellationFeeEur = cancellationFeeEur,
                    cancelledAt = timestamp
                )
                currentState = newState
                newState
            }
            else -> state
        }
    }

    /**
     * Reinicia la máquina de estados a Idle.
     */
    fun reset() {
        currentState = TripLifecycleState.Idle
    }
}
