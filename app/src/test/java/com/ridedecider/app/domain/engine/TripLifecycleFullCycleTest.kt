package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.accessibility.uber.RawUberTripOffer
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityParser
import com.ridedecider.app.data.accessibility.uber.UberAccessibilityProcessor
import com.ridedecider.app.data.accessibility.uber.UberNodeSnapshot
import com.ridedecider.app.data.accessibility.uber.UberOfferScreenType
import com.ridedecider.app.data.accessibility.uber.UberOfferValidator
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripLifecycleState
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.ui.overlay.model.HudState
import com.ridedecider.app.ui.overlay.state.HudStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite determinista completa para auditar el ciclo de vida completo de ofertas y viajes en RideDecider:
 * Oferta A -> Evaluación -> Transición -> Cancelación / Finalización -> Limpieza -> Oferta B.
 */
class TripLifecycleFullCycleTest {

    private lateinit var stateMachine: TripLifecycleStateMachine
    private lateinit var decisionEngine: DecisionEngine
    private lateinit var config: ProfitabilityConfig

    @Before
    fun setUp() {
        stateMachine = TripLifecycleStateMachine()
        decisionEngine = DecisionEngine()
        config = ProfitabilityConfig(
            costPerKm = 0.20,
            costPerHour = 4.0,
            minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0,
            minNetTripProfit = 2.0,
            minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0,
            maxPickupTimeMinutes = 10.0
        )
        HudStateHolder.hideImmediately()
    }

    private fun createTrip(
        id: String,
        fare: Double = 15.0,
        pickupKm: Double = 2.0,
        pickupMins: Double = 5.0,
        tripKm: Double = 8.0,
        tripMins: Double = 15.0
    ): Trip {
        return Trip(
            id = id,
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = pickupKm,
            pickupDurationMinutes = pickupMins,
            pickupAddress = "Origen $id",
            tripDistanceKm = tripKm,
            tripDurationMinutes = tripMins,
            dropoffAddress = "Destino $id"
        )
    }

    // =========================================================================
    // TEST 1: Oferta válida A -> Decisión -> Limpieza
    // =========================================================================
    @Test
    fun test1_validOfferA_decisionAndCleanup() {
        val tripA = createTrip("trip-A", fare = 15.0, pickupKm = 1.0, tripKm = 5.0)
        val evalA = decisionEngine.evaluate(tripA, config)

        assertEquals(Decision.ACCEPT, evalA.decision)

        val state1 = stateMachine.onOfferDetected(tripA, evalA)
        assertTrue(state1 is TripLifecycleState.PendingAcceptance)
        assertEquals("trip-A", (state1 as TripLifecycleState.PendingAcceptance).trip.id)

        // Simular emisión HUD
        HudStateHolder.emitEvaluation(evalA)
        assertTrue(HudStateHolder.state.value is HudState.Visible)

        // Simular desaparición de oferta
        val state2 = stateMachine.onOfferDismissed()
        assertTrue(state2 is TripLifecycleState.IgnoredOrExpired)

        HudStateHolder.hideImmediately()
        assertTrue(HudStateHolder.state.value is HudState.Hidden)
    }

    // =========================================================================
    // TEST 2: Oferta A -> Desaparece -> Estado IgnoredOrExpired
    // =========================================================================
    @Test
    fun test2_offerA_dismissed_transitionsToIgnoredOrExpired() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)

        stateMachine.onOfferDetected(tripA, evalA)
        val newState = stateMachine.onOfferDismissed(reason = "CARD_DISAPPEARED")

        assertTrue(newState is TripLifecycleState.IgnoredOrExpired)
        assertEquals("trip-A", (newState as TripLifecycleState.IgnoredOrExpired).tripId)
        assertEquals("CARD_DISAPPEARED", newState.reason)
    }

    // =========================================================================
    // TEST 3: Oferta A -> Nueva Oferta B (Verificar separación completa)
    // =========================================================================
    @Test
    fun test3_offerA_replacedByOfferB_completelyIsolated() {
        val tripA = createTrip("trip-A", fare = 25.0, pickupKm = 1.0, tripKm = 15.0)
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)

        val tripB = createTrip("trip-B", fare = 6.0, pickupKm = 4.0, tripKm = 2.0)
        val evalB = decisionEngine.evaluate(tripB, config)

        val stateB = stateMachine.onOfferDetected(tripB, evalB)

        assertTrue(stateB is TripLifecycleState.PendingAcceptance)
        val pendingB = stateB as TripLifecycleState.PendingAcceptance

        assertEquals("trip-B", pendingB.trip.id)
        assertEquals(6.0, pendingB.trip.rawFare!!, 0.001)
        assertEquals(4.0, pendingB.trip.pickupDistanceKm!!, 0.001)
        assertEquals(2.0, pendingB.trip.tripDistanceKm!!, 0.001)
        // Verificar que no se conservó ningún campo de tripA
        assertFalse(pendingB.trip.rawFare == 25.0)
    }

    // =========================================================================
    // TEST 4: Oferta A -> Aceptación manual -> Asignado
    // =========================================================================
    @Test
    fun test4_offerA_acceptedManually_transitionsToAssigned() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)

        val assignedState = stateMachine.onOfferAcceptedManually("trip-A")

        assertTrue(assignedState is TripLifecycleState.Assigned)
        assertEquals("trip-A", (assignedState as TripLifecycleState.Assigned).trip.id)
        assertFalse(assignedState.wasAutoAssigned)
    }

    // =========================================================================
    // TEST 5: Oferta A -> Inicio de viaje activo (ActiveTrip)
    // =========================================================================
    @Test
    fun test5_offerA_activeTripStarted_transitionsToActiveTrip() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)
        stateMachine.onOfferAcceptedManually("trip-A")

        val activeState = stateMachine.onActiveTripStarted()

        assertTrue(activeState is TripLifecycleState.ActiveTrip)
        assertEquals("trip-A", (activeState as TripLifecycleState.ActiveTrip).trip.id)
    }

    // =========================================================================
    // TEST 6: Viaje activo -> Finalización completa (Completed -> Estado limpio para Oferta B)
    // =========================================================================
    @Test
    fun test6_activeTrip_completed_allowsCleanTransitionToNewOfferB() {
        val tripA = createTrip("trip-A", fare = 18.0)
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)
        stateMachine.onOfferAcceptedManually("trip-A")
        stateMachine.onActiveTripStarted()

        val completedState = stateMachine.onTripCompleted(finalFareEur = 18.0, durationMinutes = 20.0)

        assertTrue(completedState is TripLifecycleState.Completed)
        val completed = completedState as TripLifecycleState.Completed
        assertEquals(18.0, completed.finalFareEur, 0.001)

        // Ahora llega Oferta B después de finalizar el viaje
        val tripB = createTrip("trip-B", fare = 10.0)
        val evalB = decisionEngine.evaluate(tripB, config)

        val newOfferState = stateMachine.onOfferDetected(tripB, evalB)

        assertTrue(newOfferState is TripLifecycleState.PendingAcceptance)
        assertEquals("trip-B", (newOfferState as TripLifecycleState.PendingAcceptance).trip.id)
    }

    // =========================================================================
    // TEST 7: Eventos de accesibilidad duplicados (Debounce idempotente)
    // =========================================================================
    @Test
    fun test7_identicalOfferEvents_processedIdempotently() {
        val processor = UberAccessibilityProcessor()
        val now = 1000000L
        val snap1 = UberNodeSnapshot(
            children = listOf(
                UberNodeSnapshot(text = "15,00 €"),
                UberNodeSnapshot(text = "A 1,0 km (3 min) de distancia"),
                UberNodeSnapshot(text = "Viaje de 5,0 km (10 min)"),
                UberNodeSnapshot(text = "Aceptar")
            )
        )

        val res1 = processor.processSnapshot(snap1, currentTime = now)
        assertTrue(res1 is UberAccessibilityProcessor.ProcessResult.Evaluated)

        // Evento idéntico 100ms después
        val res2 = processor.processSnapshot(snap1, currentTime = now + 100L)
        assertTrue(res2 is UberAccessibilityProcessor.ProcessResult.Debounced)
    }

    // =========================================================================
    // TEST 8: Recomposición masiva (Estabilidad de máquina de estados)
    // =========================================================================
    @Test
    fun test8_massiveRecomposition_maintainsSingleOfferState() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)

        for (i in 1..50) {
            val state = stateMachine.onOfferDetected(tripA, evalA, timestamp = 1000L + i * 10L)
            assertTrue(state is TripLifecycleState.PendingAcceptance)
            assertEquals("trip-A", (state as TripLifecycleState.PendingAcceptance).trip.id)
        }
    }

    // =========================================================================
    // TEST 9: Ruta A válida -> OCR no la reemplaza innecesariamente
    // =========================================================================
    @Test
    fun test9_routeAValid_ocrDoesNotTrigger() {
        val processor = UberAccessibilityProcessor()
        val validOffer = RawUberTripOffer(
            rawFare = 12.0,
            currency = "EUR",
            detectedOfferType = UberOfferScreenType.TRIP_OFFER
        )

        val shouldTrigger = processor.shouldTriggerOcrFallback(validOffer, nodeCount = 35, isStructuralJump = true, sdkVersion = 30)

        assertFalse(shouldTrigger)
    }

    // =========================================================================
    // TEST 10: Cancelación de viaje activo
    // =========================================================================
    @Test
    fun test10_activeTrip_cancelled_transitionsToCancelledState() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)
        stateMachine.onOfferAcceptedManually("trip-A")
        stateMachine.onActiveTripStarted()

        val cancelledState = stateMachine.onTripCancelled(
            reason = CancellationReason.RIDER,
            cancellationFeeEur = 3.50
        )

        assertTrue(cancelledState is TripLifecycleState.Cancelled)
        val cancelled = cancelledState as TripLifecycleState.Cancelled
        assertEquals(CancellationReason.RIDER, cancelled.reason)
        assertEquals(3.50, cancelled.cancellationFeeEur!!, 0.001)
    }

    // =========================================================================
    // TEST 11: Separación completa entre Oferta A cancelada y nueva Oferta B
    // =========================================================================
    @Test
    fun test11_cancelledTrip_allowsCleanTransitionToNewOfferB() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)
        stateMachine.onOfferAcceptedManually("trip-A")
        stateMachine.onTripCancelled(reason = CancellationReason.UBER)

        val tripB = createTrip("trip-B", fare = 14.0)
        val evalB = decisionEngine.evaluate(tripB, config)

        val stateB = stateMachine.onOfferDetected(tripB, evalB)

        assertTrue(stateB is TripLifecycleState.PendingAcceptance)
        assertEquals("trip-B", (stateB as TripLifecycleState.PendingAcceptance).trip.id)
    }

    // =========================================================================
    // TEST 12: Prohibición de transición desde IgnoredOrExpired a Completed
    // =========================================================================
    @Test
    fun test12_dismissedOffer_cannotTransitionToCompleted() {
        val tripA = createTrip("trip-A")
        val evalA = decisionEngine.evaluate(tripA, config)
        stateMachine.onOfferDetected(tripA, evalA)
        val dismissedState = stateMachine.onOfferDismissed()

        assertTrue(dismissedState is TripLifecycleState.IgnoredOrExpired)

        val invalidCompletedState = stateMachine.onTripCompleted(finalFareEur = 15.0)

        // Debe mantenerse en IgnoredOrExpired sin transicionar a Completed
        assertTrue(invalidCompletedState is TripLifecycleState.IgnoredOrExpired)
    }
}
