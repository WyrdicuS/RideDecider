package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.repository.InMemoryDriverGoalsRepository
import com.ridedecider.app.data.repository.InMemoryEarningsRepository
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EarningsTrackerLifecycleIntegrationTest {

    private lateinit var goalsRepository: InMemoryDriverGoalsRepository
    private lateinit var earningsRepository: InMemoryEarningsRepository
    private lateinit var tracker: EarningsTracker

    private val config = ProfitabilityConfig(
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
        goalsRepository = InMemoryDriverGoalsRepository(
            DriverGoals(
                dailyTargetEur = 120.0,
                weeklyTargetEur = 700.0,
                monthlyTargetEur = 2800.0,
                dailyPlannedHours = 6.0,
                weeklyPlannedHours = 35.0,
                monthlyPlannedHours = 140.0
            )
        )
        earningsRepository = InMemoryEarningsRepository()
        tracker = EarningsTracker(goalsRepository, earningsRepository)
    }

    private fun createTrip(id: String, fare: Double, timestamp: Long): Pair<Trip, TripEvaluation> {
        val trip = Trip(
            id = id,
            timestamp = timestamp,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = 1.5,
            pickupDurationMinutes = 4.0,
            pickupAddress = null,
            tripDistanceKm = 5.0,
            tripDurationMinutes = 12.0,
            dropoffAddress = null
        )
        val eval = TripEvaluation(
            trip = trip,
            configUsed = config,
            metrics = null,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = timestamp
        )
        return Pair(trip, eval)
    }

    // 1. Cancelación con compensación suma exactamente la compensación
    @Test
    fun cancellationWithFee_addsOnlyFeeToEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, eval) = createTrip("c1", 15.0, now)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted(trip.id)
        tracker.markActiveTripStarted()

        // Se cancela por el pasajero con 4.50 € de compensación
        tracker.cancelTrip(trip.id, CancellationReason.RIDER, cancellationFee = 4.50, timestamp = now)

        val progress = tracker.getDailyProgress(now)
        assertEquals(4.50, progress.earnedEur, 0.001)
        assertEquals(115.50, progress.remainingEur, 0.001)
    }

    // 2. Cancelación sin compensación suma exactamente 0.00 €
    @Test
    fun cancellationWithoutFee_addsZeroToEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, eval) = createTrip("c2", 20.0, now)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted(trip.id)
        tracker.markActiveTripStarted()

        tracker.cancelTrip(trip.id, CancellationReason.DRIVER, cancellationFee = null, timestamp = now)

        val progress = tracker.getDailyProgress(now)
        assertEquals(0.0, progress.earnedEur, 0.001)
        assertEquals(120.0, progress.remainingEur, 0.001)
    }

    // 3. Viaje completado con importe final diferente a la tarifa estimada
    @Test
    fun completedTrip_usesFinalEarningsNotEstimated() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, eval) = createTrip("c3", 10.0, now) // Estimado: 10.00 €

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted(trip.id)
        tracker.markActiveTripStarted()

        // Completado con propina: 14.50 € en 18 min
        tracker.completeTrip(trip.id, finalEarnings = 14.50, durationMinutes = 18.0, completedTimestamp = now)

        val progress = tracker.getDailyProgress(now)
        assertEquals(14.50, progress.earnedEur, 0.001)
        assertEquals(105.50, progress.remainingEur, 0.001)
        assertEquals(0.3, progress.workedHours, 0.001) // 18 / 60 = 0.3 h
    }

    // 4. Múltiples viajes completados y cancelados combinados
    @Test
    fun combinedDay_computesTotalAccurately() = runBlocking {
        val now = System.currentTimeMillis()

        // Viaje 1: Completado 25.00 € (30 min)
        val (t1, e1) = createTrip("combo_1", 25.0, now)
        tracker.recordEvaluatedOffer(t1, e1, now)
        tracker.markTripAccepted(t1.id)
        tracker.markActiveTripStarted()
        tracker.completeTrip(t1.id, 25.0, 30.0, now)

        // Viaje 2: Cancelado con compensación 5.00 €
        val (t2, e2) = createTrip("combo_2", 18.0, now)
        tracker.recordEvaluatedOffer(t2, e2, now)
        tracker.markTripAccepted(t2.id)
        tracker.markActiveTripStarted()
        tracker.cancelTrip(t2.id, CancellationReason.NO_SHOW, cancellationFee = 5.00, timestamp = now)

        // Viaje 3: Evaluado y rechazado (0 €)
        val (t3, e3) = createTrip("combo_3", 12.0, now)
        tracker.recordEvaluatedOffer(t3, e3, now)
        tracker.onOfferDismissed(now)

        // Viaje 4: Completado 30.00 € (45 min)
        val (t4, e4) = createTrip("combo_4", 30.0, now)
        tracker.recordEvaluatedOffer(t4, e4, now)
        tracker.markTripAccepted(t4.id)
        tracker.markActiveTripStarted()
        tracker.completeTrip(t4.id, 30.0, 45.0, now)

        val progress = tracker.getDailyProgress(now)
        // Total ganado = 25.0 + 5.0 + 30.0 = 60.00 €
        assertEquals(60.00, progress.earnedEur, 0.001)
        assertEquals(60.00, progress.remainingEur, 0.001)
        assertEquals(50.0, progress.completionPercentage, 0.001)
        // Minutos = 30 + 45 = 75 min = 1.25 h
        assertEquals(1.25, progress.workedHours, 0.001)
        assertEquals(48.0, progress.currentHourlyRate, 0.001) // 60 / 1.25 = 48 €/h
        assertEquals(ProgressStatus.AHEAD, progress.status)
    }
}
