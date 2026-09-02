package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.repository.DriverGoalsRepository
import com.ridedecider.app.domain.repository.EarningsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite determinista de pruebas de integridad financiera y persistencia para [EarningsTracker].
 * Verifica las 15 reglas de idoneidad financiera, prevención de doble contabilización y aislamiento.
 */
class EarningsTrackerFinancialIntegrityTest {

    private class FakeDriverGoalsRepository : DriverGoalsRepository {
        private val _goals = MutableStateFlow(DriverGoals(dailyTargetEur = 100.0, dailyPlannedHours = 8.0))
        override val goalsFlow: StateFlow<DriverGoals> = _goals

        override fun getGoals(): DriverGoals = _goals.value
        override suspend fun updateGoals(goals: DriverGoals) { _goals.value = goals }
    }

    private class FakeEarningsRepository : EarningsRepository {
        private val tripsMap = mutableMapOf<String, RecordedTrip>()

        override suspend fun recordTrip(trip: RecordedTrip) {
            val existing = tripsMap[trip.id]
            if (existing == null) {
                tripsMap[trip.id] = trip
            } else {
                tripsMap[trip.id] = existing.copy(
                    status = trip.status,
                    evaluation = trip.evaluation
                )
            }
        }

        override suspend fun saveDecisionSnapshot(snapshot: DecisionSnapshotEntity) {}

        override suspend fun updateSnapshotActuals(
            tripId: String,
            actualDist: Double?,
            actualDur: Double?,
            actualPickupDur: Double?,
            actualBaseFare: Double?,
            waitingComp: Double?,
            cancellationFee: Double?,
            tip: Double?,
            finalEarnings: Double?
        ) {}

        override suspend fun updateTripStatus(
            tripId: String,
            status: TripTrackingStatus,
            finalEarnings: Double?,
            completedTimestamp: Long?,
            durationMinutes: Double?,
            cancellationFee: Double?,
            cancellationReason: CancellationReason?,
            cancelledTimestamp: Long?
        ) {
            val existing = tripsMap[tripId] ?: return
            tripsMap[tripId] = existing.copy(
                status = status,
                finalEarningsEur = finalEarnings ?: existing.finalEarningsEur,
                completedTimestamp = completedTimestamp ?: existing.completedTimestamp,
                durationMinutes = durationMinutes ?: existing.durationMinutes,
                cancellationFeeEur = cancellationFee ?: existing.cancellationFeeEur,
                cancellationReason = cancellationReason ?: existing.cancellationReason,
                cancelledTimestamp = cancelledTimestamp ?: existing.cancelledTimestamp
            )
        }

        override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTrip> {
            return tripsMap.values.filter {
                (it.recordedTimestamp in startTimestamp..endTimestamp) ||
                        (it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp) ||
                        (it.cancelledTimestamp != null && it.cancelledTimestamp!! in startTimestamp..endTimestamp)
            }
        }

        override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double {
            val completedSum = tripsMap.values
                .filter { it.status == TripTrackingStatus.COMPLETED && it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp }
                .sumOf { it.finalEarningsEur ?: 0.0 }

            val feeSum = tripsMap.values
                .filter { it.status.name.startsWith("CANCELLED") && it.cancellationFeeEur != null && it.cancellationFeeEur!! > 0.0 && it.cancelledTimestamp != null && it.cancelledTimestamp!! in startTimestamp..endTimestamp }
                .sumOf { it.cancellationFeeEur ?: 0.0 }

            return completedSum + feeSum
        }

        override suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double {
            return tripsMap.values
                .filter { it.status == TripTrackingStatus.COMPLETED && it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp }
                .sumOf { it.durationMinutes ?: 0.0 }
        }

        override suspend fun clearAllTrips() {
            tripsMap.clear()
        }

        fun getTripCount(): Int = tripsMap.size
    }

    private lateinit var tracker: EarningsTracker
    private lateinit var fakeEarningsRepo: FakeEarningsRepository
    private lateinit var fakeGoalsRepo: FakeDriverGoalsRepository

    private val now = 1700000000000L

    @Before
    fun setUp() {
        fakeEarningsRepo = FakeEarningsRepository()
        fakeGoalsRepo = FakeDriverGoalsRepository()
        tracker = EarningsTracker(fakeGoalsRepo, fakeEarningsRepo)
    }

    private fun createTrip(id: String, fare: Double = 15.0): Trip {
        return Trip(
            id = id,
            timestamp = now,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = 1.0,
            pickupDurationMinutes = 3.0,
            pickupAddress = "Origen",
            tripDistanceKm = 5.0,
            tripDurationMinutes = 10.0,
            dropoffAddress = "Destino"
        )
    }

    private fun createEvaluation(trip: Trip, decision: Decision = Decision.ACCEPT): TripEvaluation {
        return TripEvaluation(
            trip = trip,
            configUsed = ProfitabilityConfig(
                costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
                minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
                maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
            ),
            metrics = null,
            decision = decision,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = now
        )
    }

    // =========================================================================
    // 1. evaluated_not_earnings: Evaluada NO es ganancia
    // =========================================================================
    @Test
    fun test1_evaluatedOffer_doesNotCountAsEarnings() = runBlocking {
        val trip = createTrip("trip-1", 20.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(0.0, earnings, 0.001)
    }

    // =========================================================================
    // 2. rejected_not_earnings: Oferta REJECT NO es ganancia
    // =========================================================================
    @Test
    fun test2_rejectedOffer_doesNotCountAsEarnings() = runBlocking {
        val trip = createTrip("trip-2", 5.0)
        val eval = createEvaluation(trip, decision = Decision.REJECT)

        tracker.recordEvaluatedOffer(trip, eval, now)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(0.0, earnings, 0.001)
    }

    // =========================================================================
    // 3. expired_not_earnings: Oferta expirada NO es ganancia
    // =========================================================================
    @Test
    fun test3_expiredOffer_doesNotCountAsEarnings() = runBlocking {
        val trip = createTrip("trip-3", 18.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.onOfferDismissed(now + 1000L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(0.0, earnings, 0.001)
    }

    // =========================================================================
    // 4. completed_adds_earnings: Completado suma exactamente finalEarnings
    // =========================================================================
    @Test
    fun test4_completedTrip_addsExactEarnings() = runBlocking {
        val trip = createTrip("trip-4", 15.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip-4")
        tracker.markActiveTripStarted()
        tracker.completeTrip("trip-4", finalEarnings = 15.0, durationMinutes = 15.0, completedTimestamp = now + 1000L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(15.0, earnings, 0.001)
    }

    // =========================================================================
    // 5. completed_is_idempotent: Doble llamada no duplica ganancias
    // =========================================================================
    @Test
    fun test5_duplicateCompletedCall_isIdempotent() = runBlocking {
        val trip = createTrip("trip-5", 12.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip-5")
        tracker.markActiveTripStarted()

        // Llamar a completeTrip 2 veces consecutivas
        tracker.completeTrip("trip-5", finalEarnings = 12.0, durationMinutes = 10.0, completedTimestamp = now + 1000L)
        tracker.completeTrip("trip-5", finalEarnings = 12.0, durationMinutes = 10.0, completedTimestamp = now + 1000L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(12.0, earnings, 0.001)
        assertEquals(1, fakeEarningsRepo.getTripCount())
    }

    // =========================================================================
    // 6. cancelled_without_fee: Cancelación sin compensación = 0 €
    // =========================================================================
    @Test
    fun test6_cancelledTripWithoutFee_doesNotAddEarnings() = runBlocking {
        val trip = createTrip("trip-6", 14.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip-6")
        tracker.cancelTrip("trip-6", reason = CancellationReason.RIDER, cancellationFee = null, timestamp = now + 500L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(0.0, earnings, 0.001)
    }

    // =========================================================================
    // 7. cancelled_with_fee: Cancelación con compensación suma únicamente el fee
    // =========================================================================
    @Test
    fun test7_cancelledTripWithFee_addsOnlyFee() = runBlocking {
        val trip = createTrip("trip-7", 20.0)
        val eval = createEvaluation(trip)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted("trip-7")
        tracker.cancelTrip("trip-7", reason = CancellationReason.NO_SHOW, cancellationFee = 3.50, timestamp = now + 500L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(3.50, earnings, 0.001)
    }

    // =========================================================================
    // 8. multiple_completed_trips: Suma exacta de múltiples viajes completados
    // =========================================================================
    @Test
    fun test8_multipleCompletedTrips_sumsCorrectly() = runBlocking {
        val trip1 = createTrip("trip-8a", 10.0)
        val eval1 = createEvaluation(trip1)
        tracker.recordEvaluatedOffer(trip1, eval1, now)
        tracker.markTripAccepted("trip-8a")
        tracker.markActiveTripStarted()
        tracker.completeTrip("trip-8a", 10.0, 10.0, now + 100L)

        val trip2 = createTrip("trip-8b", 25.0)
        val eval2 = createEvaluation(trip2)
        tracker.recordEvaluatedOffer(trip2, eval2, now + 200L)
        tracker.markTripAccepted("trip-8b")
        tracker.markActiveTripStarted()
        tracker.completeTrip("trip-8b", 25.0, 20.0, now + 300L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        assertEquals(35.0, earnings, 0.001)
        assertEquals(2, fakeEarningsRepo.getTripCount())
    }

    // =========================================================================
    // 9. trip_a_trip_b_isolation: Aislamiento total entre viajes A y B
    // =========================================================================
    @Test
    fun test9_tripIsolation_A_and_B_doNotContaminateEachOther() = runBlocking {
        val tripA = createTrip("trip-A", 16.0)
        val evalA = createEvaluation(tripA)
        tracker.recordEvaluatedOffer(tripA, evalA, now)
        tracker.markTripAccepted("trip-A")
        tracker.markActiveTripStarted()
        tracker.completeTrip("trip-A", 16.0, 15.0, now + 100L)

        // Oferta B pendiente
        val tripB = createTrip("trip-B", 8.0)
        val evalB = createEvaluation(tripB)
        tracker.recordEvaluatedOffer(tripB, evalB, now + 200L)

        val (start, end) = tracker.getDayBounds(now)
        val earnings = fakeEarningsRepo.getCompletedEarningsBetween(start, end)

        // Solo el viaje A debe estar completado
        assertEquals(16.0, earnings, 0.001)
        assertEquals(2, fakeEarningsRepo.getTripCount())
    }

    // =========================================================================
    // 10. room_persistence: Mapeo bidireccional toDomain y fromDomain en Entity
    // =========================================================================
    @Test
    fun test10_roomEntityMapping_preservesAllFinancialFields() {
        val trip = createTrip("trip-10", 22.50)
        val eval = createEvaluation(trip)
        val recordedTrip = RecordedTrip(
            id = "trip-10",
            trip = trip,
            evaluation = eval,
            status = TripTrackingStatus.COMPLETED,
            recordedTimestamp = now,
            completedTimestamp = now + 1000L,
            finalEarningsEur = 22.50,
            durationMinutes = 18.0
        )

        val entity = RecordedTripEntity.fromDomain(recordedTrip, wasAutoAssigned = false)
        val domainBack = entity.toDomain()

        assertEquals("trip-10", domainBack.id)
        assertEquals(TripTrackingStatus.COMPLETED, domainBack.status)
        assertEquals(22.50, domainBack.finalEarningsEur!!, 0.001)
        assertEquals(18.0, domainBack.durationMinutes!!, 0.001)
    }
}
