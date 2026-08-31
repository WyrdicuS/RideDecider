package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripLifecycleState
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripLifecycleStateMachineTest {

    private lateinit var stateMachine: TripLifecycleStateMachine

    private val defaultConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 5.0,
        minGrossHourlyRate = 25.0,
        minGrossPerKmRate = 1.20,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 5.0,
        maxPickupTimeMinutes = 10.0
    )

    @Before
    fun setUp() {
        stateMachine = TripLifecycleStateMachine()
    }

    private fun createTrip(id: String, offerType: TripOfferType = TripOfferType.TRIP_OFFER, fare: Double = 12.50): Pair<Trip, TripEvaluation> {
        val trip = Trip(
            id = id,
            timestamp = System.currentTimeMillis(),
            offerType = offerType,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = 1.5,
            pickupDurationMinutes = 4.0,
            pickupAddress = null,
            tripDistanceKm = 6.0,
            tripDurationMinutes = 12.0,
            dropoffAddress = null
        )
        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = System.currentTimeMillis()
        )
        return Pair(trip, evaluation)
    }

    // 1. Oferta detectada ≠ viaje aceptado
    @Test
    fun offerDetected_isNotAccepted() {
        val (trip, eval) = createTrip("trip_1")
        val state = stateMachine.onOfferDetected(trip, eval)

        assertTrue(state is TripLifecycleState.PendingAcceptance)
        assertFalse(state is TripLifecycleState.Assigned)
        assertFalse(state is TripLifecycleState.ActiveTrip)
        assertFalse(state is TripLifecycleState.Completed)
    }

    // 2. Oferta rechazada/ignorada ≠ viaje aceptado
    @Test
    fun offerDismissed_transitionsToIgnoredOrExpired() {
        val (trip, eval) = createTrip("trip_2")
        stateMachine.onOfferDetected(trip, eval)

        val state = stateMachine.onOfferDismissed(reason = "REJECTED_BY_DRIVER")

        assertTrue(state is TripLifecycleState.IgnoredOrExpired)
        assertEquals("trip_2", (state as TripLifecycleState.IgnoredOrExpired).tripId)
        assertEquals("REJECTED_BY_DRIVER", state.reason)
    }

    // 3. Oferta expirada ≠ viaje aceptado
    @Test
    fun offerExpired_transitionsToIgnoredOrExpired() {
        val (trip, eval) = createTrip("trip_3")
        stateMachine.onOfferDetected(trip, eval)

        val state = stateMachine.onOfferDismissed(reason = "EXPIRED_TIMEOUT")

        assertTrue(state is TripLifecycleState.IgnoredOrExpired)
    }

    // 4. TRIP_OFFER + aceptación real → ACCEPTED_BY_DRIVER / Assigned
    @Test
    fun directOffer_manualAccept_transitionsToAssigned() {
        val (trip, eval) = createTrip("trip_4", TripOfferType.TRIP_OFFER)
        stateMachine.onOfferDetected(trip, eval)

        val state = stateMachine.onOfferAcceptedManually(trip.id)

        assertTrue(state is TripLifecycleState.Assigned)
        val assigned = state as TripLifecycleState.Assigned
        assertEquals(trip.id, assigned.trip.id)
        assertFalse(assigned.wasAutoAssigned)
    }

    // 5. RADAR_OFFER + apertura de tarjeta ≠ aceptación
    @Test
    fun radarOffer_cardOpen_remainsPendingAcceptance() {
        val (trip, eval) = createTrip("radar_1", TripOfferType.RADAR_OFFER)
        val state = stateMachine.onOfferDetected(trip, eval)

        assertTrue(state is TripLifecycleState.PendingAcceptance)
        assertFalse(state is TripLifecycleState.Assigned)
    }

    // 6. RADAR_OFFER + Emparejar confirmado → ASSIGNED
    @Test
    fun radarOffer_manualMatch_transitionsToAssigned() {
        val (trip, eval) = createTrip("radar_2", TripOfferType.RADAR_OFFER)
        stateMachine.onOfferDetected(trip, eval)

        val state = stateMachine.onOfferAcceptedManually(trip.id)

        assertTrue(state is TripLifecycleState.Assigned)
        val assigned = state as TripLifecycleState.Assigned
        assertEquals(trip.id, assigned.trip.id)
        assertFalse(assigned.wasAutoAssigned)
    }

    // 7. RADAR_OFFER + asignación automática confirmada → ASSIGNED (wasAutoAssigned = true)
    @Test
    fun radarOffer_autoAssigned_transitionsToAssigned() {
        val (trip, eval) = createTrip("radar_3", TripOfferType.RADAR_OFFER)
        stateMachine.onOfferDetected(trip, eval)

        val state = stateMachine.onRadarAutoAssigned(trip.id)

        assertTrue(state is TripLifecycleState.Assigned)
        val assigned = state as TripLifecycleState.Assigned
        assertEquals(trip.id, assigned.trip.id)
        assertTrue(assigned.wasAutoAssigned)
    }

    // 8. ACCEPTED/ASSIGNED + ACTIVE_TRIP → viaje iniciado
    @Test
    fun assigned_activeTrip_transitionsToActiveTrip() {
        val (trip, eval) = createTrip("trip_8")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)

        val state = stateMachine.onActiveTripStarted()

        assertTrue(state is TripLifecycleState.ActiveTrip)
        val active = state as TripLifecycleState.ActiveTrip
        assertEquals(trip.id, active.trip.id)
    }

    // 9. ACTIVE_TRIP + finalización → COMPLETED
    @Test
    fun activeTrip_complete_transitionsToCompleted() {
        val (trip, eval) = createTrip("trip_9", fare = 15.0)
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val state = stateMachine.onTripCompleted(finalFareEur = 16.50, durationMinutes = 18.0)

        assertTrue(state is TripLifecycleState.Completed)
        val completed = state as TripLifecycleState.Completed
        assertEquals(trip.id, completed.trip.id)
        assertEquals(16.50, completed.finalFareEur, 0.001)
        assertEquals(18.0, completed.durationMinutes, 0.001)
    }

    // 10. Solo COMPLETED con importe real confirmado suma ganancias
    @Test
    fun onlyCompleted_hasConfirmedEarnings() {
        val (trip, eval) = createTrip("trip_10", fare = 20.0)
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val completedState = stateMachine.onTripCompleted(finalFareEur = 22.50, durationMinutes = 25.0) as TripLifecycleState.Completed
        assertEquals(22.50, completedState.finalFareEur, 0.001)
    }

    // 11. Un viaje evaluado pero nunca aceptado no aparece como viaje realizado
    @Test
    fun evaluatedNeverAccepted_remainsIgnoredOrExpired() {
        val (trip, eval) = createTrip("trip_11")
        stateMachine.onOfferDetected(trip, eval)
        val state = stateMachine.onOfferDismissed()

        assertTrue(state is TripLifecycleState.IgnoredOrExpired)
        assertFalse(state is TripLifecycleState.Completed)
    }

    // 12. Un viaje aceptado pero nunca iniciado no llega a Completed
    @Test
    fun acceptedNeverStarted_doesNotReachCompleted() {
        val (trip, eval) = createTrip("trip_12")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)

        // Si se intenta completar sin haber pasado por ActiveTrip, no pasa a Completed
        val state = stateMachine.onTripCompleted()
        assertTrue(state is TripLifecycleState.Assigned)
    }

    // 13. No duplicar un mismo viaje
    @Test
    fun duplicateOfferDetections_remainInSameTripState() {
        val (trip, eval) = createTrip("trip_13")
        stateMachine.onOfferDetected(trip, eval)
        val state2 = stateMachine.onOfferDetected(trip, eval)

        assertTrue(state2 is TripLifecycleState.PendingAcceptance)
        assertEquals("trip_13", (state2 as TripLifecycleState.PendingAcceptance).trip.id)
    }

    // 14. Los cambios de pantalla repetidos no corrompen el estado
    @Test
    fun repeatedScreenChanges_handledCorrectly() {
        val (trip, eval) = createTrip("trip_14")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)

        // Intento de re-detectar la misma oferta mientras ya está asignada
        val state = stateMachine.onOfferDetected(trip, eval)
        assertTrue(state is TripLifecycleState.Assigned)
    }

    // 15. Cancelación por el pasajero
    @Test
    fun cancellation_byRider_transitionsToCancelled() {
        val (trip, eval) = createTrip("trip_cancel_rider", fare = 10.0)
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val state = stateMachine.onTripCancelled(
            reason = com.ridedecider.app.domain.model.CancellationReason.RIDER,
            cancellationFeeEur = 4.50
        )

        assertTrue(state is TripLifecycleState.Cancelled)
        val cancelled = state as TripLifecycleState.Cancelled
        assertEquals(com.ridedecider.app.domain.model.CancellationReason.RIDER, cancelled.reason)
        assertEquals(4.50, cancelled.cancellationFeeEur ?: 0.0, 0.001)
    }

    // 16. Cancelación por el conductor
    @Test
    fun cancellation_byDriver_transitionsToCancelled() {
        val (trip, eval) = createTrip("trip_cancel_driver")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)

        val state = stateMachine.onTripCancelled(
            reason = com.ridedecider.app.domain.model.CancellationReason.DRIVER
        )

        assertTrue(state is TripLifecycleState.Cancelled)
        val cancelled = state as TripLifecycleState.Cancelled
        assertEquals(com.ridedecider.app.domain.model.CancellationReason.DRIVER, cancelled.reason)
    }

    // 17. Cancelación por no presentación (No-Show)
    @Test
    fun cancellation_noShow_transitionsToCancelled() {
        val (trip, eval) = createTrip("trip_cancel_noshow")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val state = stateMachine.onTripCancelled(
            reason = com.ridedecider.app.domain.model.CancellationReason.NO_SHOW,
            cancellationFeeEur = 5.0
        )

        assertTrue(state is TripLifecycleState.Cancelled)
        val cancelled = state as TripLifecycleState.Cancelled
        assertEquals(com.ridedecider.app.domain.model.CancellationReason.NO_SHOW, cancelled.reason)
        assertEquals(5.0, cancelled.cancellationFeeEur ?: 0.0, 0.001)
    }

    // 18. CANCELLED -> COMPLETED está estrictamente prohibido
    @Test
    fun cancelledTrip_cannotAccidentallyBecomeCompleted() {
        val (trip, eval) = createTrip("trip_cancel_protect")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        // El viaje es cancelado
        val cancelState = stateMachine.onTripCancelled(
            reason = com.ridedecider.app.domain.model.CancellationReason.RIDER,
            cancellationFeeEur = 4.50
        )
        assertTrue(cancelState is TripLifecycleState.Cancelled)

        // Intento accidental de completar un viaje ya cancelado
        val attemptComplete = stateMachine.onTripCompleted(finalFareEur = 15.0)

        // Debe permanecer inmutablemente en Cancelled
        assertTrue(attemptComplete is TripLifecycleState.Cancelled)
        assertFalse(attemptComplete is TripLifecycleState.Completed)
    }
}
