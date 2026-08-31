package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.CancellationReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripLifecycleStateMachineComprehensiveTest {

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

    // 1. Invariante: CANCELLED -> COMPLETED es ESTRICTAMENTE IMPOSIBLE
    @Test
    fun cancelled_cannotTransitionToCompleted() {
        val (trip, eval) = createTrip("test_cancel_lock")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val cancelState = stateMachine.onTripCancelled(
            reason = CancellationReason.RIDER,
            cancellationFeeEur = 4.50
        )
        assertTrue(cancelState is TripLifecycleState.Cancelled)

        // Intento de completar
        val attempt = stateMachine.onTripCompleted(finalFareEur = 25.0)
        assertTrue(attempt is TripLifecycleState.Cancelled)
        assertEquals(4.50, (attempt as TripLifecycleState.Cancelled).cancellationFeeEur ?: 0.0, 0.001)
    }

    // 2. Invariante: COMPLETED -> CANCELLED o ACTIVE_TRIP es ESTRICTAMENTE IMPOSIBLE
    @Test
    fun completed_cannotTransitionToCancelledOrActive() {
        val (trip, eval) = createTrip("test_complete_lock")
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val completedState = stateMachine.onTripCompleted(finalFareEur = 15.0, durationMinutes = 14.0)
        assertTrue(completedState is TripLifecycleState.Completed)

        // Intento de cancelar
        val attemptCancel = stateMachine.onTripCancelled(reason = CancellationReason.RIDER)
        assertTrue(attemptCancel is TripLifecycleState.Completed)

        // Intento de reactivar
        val attemptActive = stateMachine.onActiveTripStarted()
        assertTrue(attemptActive is TripLifecycleState.Completed)
    }

    // 3. Cancelaciones con motivos clasificados
    @Test
    fun cancellations_allReasonsSupported() {
        val reasons = listOf(
            CancellationReason.RIDER to 4.50,
            CancellationReason.DRIVER to null,
            CancellationReason.UBER to 3.00,
            CancellationReason.NO_SHOW to 5.00,
            CancellationReason.UNKNOWN to null
        )

        for ((reason, fee) in reasons) {
            val sm = TripLifecycleStateMachine()
            val (trip, eval) = createTrip("trip_${reason.name}")
            sm.onOfferDetected(trip, eval)
            sm.onOfferAcceptedManually(trip.id)
            sm.onActiveTripStarted()

            val state = sm.onTripCancelled(reason, fee)
            assertTrue(state is TripLifecycleState.Cancelled)
            val cancelled = state as TripLifecycleState.Cancelled
            assertEquals(reason, cancelled.reason)
            assertEquals(fee, cancelled.cancellationFeeEur)
        }
    }

    // 4. Finalización con importe final diferente a la tarifa estimada
    @Test
    fun completed_withRecalculatedFareAndTip() {
        val (trip, eval) = createTrip("trip_recalc", fare = 8.50)
        stateMachine.onOfferDetected(trip, eval)
        stateMachine.onOfferAcceptedManually(trip.id)
        stateMachine.onActiveTripStarted()

        val state = stateMachine.onTripCompleted(finalFareEur = 12.30, durationMinutes = 16.5)
        assertTrue(state is TripLifecycleState.Completed)
        val completed = state as TripLifecycleState.Completed
        assertEquals(8.50, completed.trip.rawFare ?: 0.0, 0.001) // Estimada original intacta
        assertEquals(12.30, completed.finalFareEur, 0.001) // Final real registrada
        assertEquals(16.5, completed.durationMinutes, 0.001)
    }

    // 5. Idempotencia ante eventos repetidos del mismo viaje
    @Test
    fun idempotence_repeatedEventsDoNotCorruptState() {
        val (trip, eval) = createTrip("trip_idempotent")
        val s1 = stateMachine.onOfferDetected(trip, eval)
        val s2 = stateMachine.onOfferDetected(trip, eval)
        assertEquals(s1, s2)

        stateMachine.onOfferAcceptedManually(trip.id)
        val s3 = stateMachine.onOfferAcceptedManually(trip.id)
        assertTrue(s3 is TripLifecycleState.Assigned)

        stateMachine.onActiveTripStarted()
        val s4 = stateMachine.onActiveTripStarted()
        assertTrue(s4 is TripLifecycleState.ActiveTrip)
    }

    // 6. Asignación automática de Trip Radar
    @Test
    fun radar_autoAssigned_flagPreserved() {
        val (trip, eval) = createTrip("trip_radar_auto", offerType = TripOfferType.RADAR_OFFER)
        stateMachine.onOfferDetected(trip, eval)
        val state = stateMachine.onRadarAutoAssigned(trip.id)

        assertTrue(state is TripLifecycleState.Assigned)
        assertTrue((state as TripLifecycleState.Assigned).wasAutoAssigned)
    }
}
