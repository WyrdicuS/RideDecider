package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.local.room.dao.DecisionSnapshotDao
import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverGoals
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite determinista completa para la Reconciliación de Resultados Reales vs Estimados (Fase 8.12).
 * Valida los 26 escenarios A-Z de inmutabilidad estimada, reconciliación en Room y ausencia de contaminación.
 */
class ActualTripReconciliationTest {

    private class FakeDecisionSnapshotDao : DecisionSnapshotDao {
        private val snapshotsMap = mutableMapOf<String, DecisionSnapshotEntity>()

        override suspend fun insertSnapshot(snapshot: DecisionSnapshotEntity): Long {
            snapshotsMap[snapshot.snapshotId] = snapshot
            return 1L
        }

        override suspend fun getSnapshotById(snapshotId: String): DecisionSnapshotEntity? {
            return snapshotsMap[snapshotId]
        }

        override suspend fun getSnapshotByInstanceId(instanceId: String): DecisionSnapshotEntity? {
            return snapshotsMap.values.find { it.instanceId == instanceId }
        }

        override suspend fun getSnapshotByTripId(tripId: String): DecisionSnapshotEntity? {
            return snapshotsMap.values.find { it.tripId == tripId }
        }

        override suspend fun getAllSnapshots(): List<DecisionSnapshotEntity> {
            return snapshotsMap.values.toList()
        }

        override suspend fun updateActualResult(tripId: String, actualDist: Double?, actualDur: Double?, finalEarnings: Double?): Int {
            return updateSnapshotActuals(
                tripId = tripId,
                actualDist = actualDist,
                actualDur = actualDur,
                actualPickupDur = null,
                actualBaseFare = null,
                waitingComp = null,
                cancellationFee = null,
                tip = null,
                finalEarnings = finalEarnings
            )
        }

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
        ): Int {
            val existing = snapshotsMap.values.find { it.tripId == tripId } ?: return 0
            val updated = existing.copy(
                actualDistanceKm = actualDist ?: existing.actualDistanceKm,
                actualDurationMinutes = actualDur ?: existing.actualDurationMinutes,
                actualPickupDurationMinutes = actualPickupDur ?: existing.actualPickupDurationMinutes,
                actualBaseFareEur = actualBaseFare ?: existing.actualBaseFareEur,
                waitingCompensationEur = waitingComp ?: existing.waitingCompensationEur,
                cancellationFeeEur = cancellationFee ?: existing.cancellationFeeEur,
                tipEur = tip ?: existing.tipEur,
                finalEarningsEur = finalEarnings ?: existing.finalEarningsEur
            )
            snapshotsMap[existing.snapshotId] = updated
            return 1
        }

        override suspend fun clearAllSnapshots(): Int {
            val count = snapshotsMap.size
            snapshotsMap.clear()
            return count
        }
    }

    private class FakeEarningsRepository(val snapshotDao: FakeDecisionSnapshotDao) : EarningsRepository {
        private val tripsMap = mutableMapOf<String, RecordedTrip>()

        override suspend fun recordTrip(trip: RecordedTrip) {
            tripsMap[trip.id] = trip
        }

        override suspend fun saveDecisionSnapshot(snapshot: DecisionSnapshotEntity) {
            snapshotDao.insertSnapshot(snapshot)
        }

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
        ) {
            snapshotDao.updateSnapshotActuals(
                tripId = tripId,
                actualDist = actualDist,
                actualDur = actualDur,
                actualPickupDur = actualPickupDur,
                actualBaseFare = actualBaseFare,
                waitingComp = waitingComp,
                cancellationFee = cancellationFee,
                tip = tip,
                finalEarnings = finalEarnings
            )
        }

        override suspend fun updateTripStatus(
            tripId: String,
            status: TripTrackingStatus,
            finalEarnings: Double?,
            completedTimestamp: Long?,
            durationMinutes: Double?,
            cancellationFee: Double?,
            cancellationReason: CancellationReason?,
            cancelledTimestamp: Long?
        ) {}

        override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTrip> = emptyList()
        override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double = 0.0
        override suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double = 0.0
        override suspend fun clearAllTrips() { tripsMap.clear() }
    }

    private class FakeDriverGoalsRepository : DriverGoalsRepository {
        private val _goals = MutableStateFlow(DriverGoals(dailyTargetEur = 120.0, dailyPlannedHours = 5.0))
        override val goalsFlow: StateFlow<DriverGoals> = _goals
        override fun getGoals(): DriverGoals = _goals.value
        override suspend fun updateGoals(goals: DriverGoals) { _goals.value = goals }
    }

    private lateinit var snapshotDao: FakeDecisionSnapshotDao
    private lateinit var repo: FakeEarningsRepository
    private lateinit var tracker: EarningsTracker
    private lateinit var decisionEngine: DecisionEngine

    private val now = 1700000000000L

    @Before
    fun setUp() {
        snapshotDao = FakeDecisionSnapshotDao()
        repo = FakeEarningsRepository(snapshotDao)
        tracker = EarningsTracker(FakeDriverGoalsRepository(), repo)
        decisionEngine = DecisionEngine()
    }

    private fun createTrip(id: String, fare: Double = 15.0, pickupKm: Double = 1.0, tripKm: Double = 5.0): Trip {
        return Trip(
            id = id,
            timestamp = now,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = pickupKm,
            pickupDurationMinutes = 3.0,
            pickupAddress = "Origen $id",
            tripDistanceKm = tripKm,
            tripDurationMinutes = 10.0,
            dropoffAddress = "Destino $id"
        )
    }

    // =========================================================================
    // TESTS A - H: Actuals Individual Updates
    // =========================================================================
    @Test
    fun testA_snapshotCanReceiveActualDuration() = runBlocking {
        val trip = createTrip("trip_A", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_A", actualDur = 14.5)

        val snap = snapshotDao.getSnapshotByTripId("trip_A")!!
        assertEquals(14.5, snap.actualDurationMinutes!!, 0.001)
    }

    @Test
    fun testB_snapshotCanReceiveActualDistance() = runBlocking {
        val trip = createTrip("trip_B", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_B", actualDist = 6.8)

        val snap = snapshotDao.getSnapshotByTripId("trip_B")!!
        assertEquals(6.8, snap.actualDistanceKm!!, 0.001)
    }

    @Test
    fun testC_snapshotCanReceiveActualPickupDuration() = runBlocking {
        val trip = createTrip("trip_C", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_C", actualPickupDur = 4.2)

        val snap = snapshotDao.getSnapshotByTripId("trip_C")!!
        assertEquals(4.2, snap.actualPickupDurationMinutes!!, 0.001)
    }

    @Test
    fun testD_snapshotCanReceiveActualBaseFare() = runBlocking {
        val trip = createTrip("trip_D", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_D", actualBaseFare = 14.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_D")!!
        assertEquals(14.0, snap.actualBaseFareEur!!, 0.001)
    }

    @Test
    fun testE_snapshotCanReceiveWaitingCompensation() = runBlocking {
        val trip = createTrip("trip_E", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_E", waitingComp = 1.80)

        val snap = snapshotDao.getSnapshotByTripId("trip_E")!!
        assertEquals(1.80, snap.waitingCompensationEur!!, 0.001)
    }

    @Test
    fun testF_snapshotCanReceiveCancellationFee() = runBlocking {
        val trip = createTrip("trip_F", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_F", cancellationFee = 3.50)

        val snap = snapshotDao.getSnapshotByTripId("trip_F")!!
        assertEquals(3.50, snap.cancellationFeeEur!!, 0.001)
    }

    @Test
    fun testG_snapshotCanReceiveTip() = runBlocking {
        val trip = createTrip("trip_G", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_G", tip = 2.00)

        val snap = snapshotDao.getSnapshotByTripId("trip_G")!!
        assertEquals(2.00, snap.tipEur!!, 0.001)
    }

    @Test
    fun testH_snapshotCanReceiveFinalEarnings() = runBlocking {
        val trip = createTrip("trip_H", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_H", finalEarnings = 17.50)

        val snap = snapshotDao.getSnapshotByTripId("trip_H")!!
        assertEquals(17.50, snap.finalEarningsEur!!, 0.001)
    }

    // =========================================================================
    // TESTS I - Z: Invariantes, Reconciliación y Aislamiento
    // =========================================================================
    @Test
    fun testI_estimatedFieldsRemainIntactAfterActualsUpdate() = runBlocking {
        val trip = createTrip("trip_I", fare = 15.0, pickupKm = 1.0, tripKm = 5.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_I", actualDist = 8.0, actualDur = 20.0, finalEarnings = 18.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_I")!!
        assertEquals(15.0, snap.estimatedFareEur!!, 0.001)
        assertEquals(1.0, snap.estimatedPickupDistanceKm!!, 0.001)
        assertEquals(5.0, snap.estimatedTripDistanceKm!!, 0.001)
        assertEquals(6.0, snap.estimatedTotalDistanceKm!!, 0.001)
        assertEquals(13.0, snap.estimatedTotalDurationMinutes!!, 0.001)
    }

    @Test
    fun testJ_partialUpdate_preservesUnchangedFields() = runBlocking {
        val trip = createTrip("trip_J", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_J", actualDur = 12.0)
        repo.updateSnapshotActuals("trip_J", finalEarnings = 16.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_J")!!
        assertEquals(12.0, snap.actualDurationMinutes!!, 0.001)
        assertEquals(16.0, snap.finalEarningsEur!!, 0.001)
    }

    @Test
    fun testK_subsequentUpdate_completesSnapshotResult() = runBlocking {
        val trip = createTrip("trip_K", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_K", actualDist = 6.0)
        repo.updateSnapshotActuals("trip_K", actualDur = 13.0, finalEarnings = 15.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_K")!!
        assertEquals(6.0, snap.actualDistanceKm!!, 0.001)
        assertEquals(13.0, snap.actualDurationMinutes!!, 0.001)
        assertEquals(15.0, snap.finalEarningsEur!!, 0.001)
    }

    @Test
    fun testL_durationErrorCalculation_isAccurate() = runBlocking {
        val trip = createTrip("trip_L", fare = 15.0, pickupKm = 1.0, tripKm = 5.0) // estTotalDur = 13.0
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_L", actualDur = 17.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_L")!!
        val recon = snap.calculateReconciliationMetrics()

        assertEquals(4.0, recon.durationErrorMinutes!!, 0.001) // 17.0 - 13.0 = +4.0 min
    }

    @Test
    fun testM_distanceErrorCalculation_isAccurate() = runBlocking {
        val trip = createTrip("trip_M", fare = 15.0, pickupKm = 1.0, tripKm = 5.0) // estTotalDist = 6.0
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_M", actualDist = 7.5)

        val snap = snapshotDao.getSnapshotByTripId("trip_M")!!
        val recon = snap.calculateReconciliationMetrics()

        assertEquals(1.5, recon.distanceErrorKm!!, 0.001) // 7.5 - 6.0 = +1.5 km
    }

    @Test
    fun testN_fareErrorCalculation_isAccurate() = runBlocking {
        val trip = createTrip("trip_N", fare = 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_N", finalEarnings = 18.50)

        val snap = snapshotDao.getSnapshotByTripId("trip_N")!!
        val recon = snap.calculateReconciliationMetrics()

        assertEquals(3.50, recon.fareErrorEur!!, 0.001) // 18.50 - 15.00 = +3.50 €
    }

    @Test
    fun testO_earningsDelta_isAccurate() = runBlocking {
        val trip = createTrip("trip_O", fare = 20.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_O", finalEarnings = 22.00)

        val snap = snapshotDao.getSnapshotByTripId("trip_O")!!
        val recon = snap.calculateReconciliationMetrics()

        assertEquals(2.00, recon.earningsDeltaEur!!, 0.001)
    }

    @Test
    fun testP_profitError_remainsNullWhenActualNetProfitCannotBeComputed() = runBlocking {
        val trip = createTrip("trip_P", fare = 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_P", finalEarnings = 15.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_P")!!
        val recon = snap.calculateReconciliationMetrics()

        assertNull("profitErrorEur debe permanecer null cuando no hay coste real calculado", recon.profitErrorEur)
    }

    @Test
    fun testQ_zeroDivisionProtection_handlesNullsSafely() = runBlocking {
        val snap = DecisionSnapshotEntity(
            snapshotId = "s_zero", instanceId = "inst_zero", tripId = "trip_zero", recordedTimestamp = now,
            offerType = "TRIP_OFFER", category = "UBER_X", estimatedFareEur = 0.0, currency = "EUR",
            estimatedPickupDistanceKm = 0.0, estimatedPickupDurationMinutes = 0.0, estimatedTripDistanceKm = 0.0, estimatedTripDurationMinutes = 0.0,
            estimatedTotalDistanceKm = 0.0, estimatedTotalDurationMinutes = 0.0, grossPerKm = null, grossPerHour = null,
            effectiveGrossPerKm = null, estimatedOperatingCost = null, estimatedNetProfit = null, netPerHour = null,
            pickupSpeedKmh = null, pickupDistanceRatio = 0.0, pickupTimeRatio = 0.0, profitabilityScore = 100,
            decision = "ACCEPT", decisionReasonsCommaSeparated = "ACCEPT", profitabilityLevel = "GOOD", kinematicsSource = "EXPLICIT_DUAL",
            dayOfWeek = 1, hourOfDay = 12, minuteOfHour = 0, timeBucket = "DAY"
        )
        snapshotDao.insertSnapshot(snap)

        val retrieved = snapshotDao.getSnapshotById("s_zero")!!
        val recon = retrieved.calculateReconciliationMetrics()

        assertNull(recon.durationErrorMinutes)
        assertNull(recon.distanceErrorKm)
        assertNull(recon.fareErrorEur)
    }

    @Test
    fun testR_nullProtected_whenNoActualsProvided() = runBlocking {
        val trip = createTrip("trip_R", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        val snap = snapshotDao.getSnapshotByTripId("trip_R")!!
        val recon = snap.calculateReconciliationMetrics()

        assertNull(recon.durationErrorMinutes)
        assertNull(recon.distanceErrorKm)
        assertNull(recon.fareErrorEur)
    }

    @Test
    fun testS_twoSnapshots_doNotCrossContaminate() = runBlocking {
        val tripA = createTrip("trip_A_cross", 10.0)
        val evalA = decisionEngine.evaluate(tripA, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(tripA, evalA, now)

        val tripB = createTrip("trip_B_cross", 20.0)
        val evalB = decisionEngine.evaluate(tripB, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(tripB, evalB, now + 1000L)

        repo.updateSnapshotActuals("trip_A_cross", finalEarnings = 12.0)

        val snapA = snapshotDao.getSnapshotByTripId("trip_A_cross")!!
        val snapB = snapshotDao.getSnapshotByTripId("trip_B_cross")!!

        assertEquals(12.0, snapA.finalEarningsEur!!, 0.001)
        assertNull(snapB.finalEarningsEur)
    }

    @Test
    fun testT_instanceId_maintainsCorrelation() = runBlocking {
        val trip = createTrip("inst_T_corr")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        val snap = snapshotDao.getSnapshotByInstanceId("inst_T_corr")
        assertNotNull(snap)
        assertEquals("inst_T_corr", snap!!.instanceId)
    }

    @Test
    fun testU_tripId_maintainsCorrelation() = runBlocking {
        val trip = createTrip("trip_U_corr")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        val snap = snapshotDao.getSnapshotByTripId("trip_U_corr")
        assertNotNull(snap)
        assertEquals("trip_U_corr", snap!!.tripId)
    }

    @Test
    fun testV_repeatedUpdate_isIdempotent() = runBlocking {
        val trip = createTrip("trip_V_idem", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_V_idem", actualDur = 15.0, finalEarnings = 15.0)
        repo.updateSnapshotActuals("trip_V_idem", actualDur = 15.0, finalEarnings = 15.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_V_idem")!!
        assertEquals(15.0, snap.actualDurationMinutes!!, 0.001)
        assertEquals(15.0, snap.finalEarningsEur!!, 0.001)
    }

    @Test
    fun testW_cancellation_reconciledCorrectlyWhenFeeExists() = runBlocking {
        val trip = createTrip("trip_W_cancel", 20.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_W_cancel", cancellationFee = 4.50)

        val snap = snapshotDao.getSnapshotByTripId("trip_W_cancel")!!
        assertEquals(4.50, snap.cancellationFeeEur!!, 0.001)
        assertNull(snap.finalEarningsEur)
    }

    @Test
    fun testX_nonExistentSnapshot_doesNotCrashOnUpdate() = runBlocking {
        repo.updateSnapshotActuals("non_existent_trip_id", actualDur = 10.0, finalEarnings = 10.0)
        val snap = snapshotDao.getSnapshotByTripId("non_existent_trip_id")
        assertNull(snap)
    }

    @Test
    fun testY_roomReadWrite_preservesReconciliationResults() = runBlocking {
        val trip = createTrip("trip_Y_room", 18.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_Y_room", actualDist = 12.0, actualDur = 25.0, finalEarnings = 21.0)

        val retrieved = snapshotDao.getSnapshotByTripId("trip_Y_room")!!
        val recon = retrieved.calculateReconciliationMetrics()

        assertEquals(3.0, recon.earningsDeltaEur!!, 0.001) // 21.0 - 18.0 = +3.0 €
    }

    @Test
    fun testZ_wazeDoesNotParticipateInReconciliationLogic() = runBlocking {
        val trip = createTrip("trip_Z_waze", 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(0.2, 4.0, 20.0, 1.0, 2.0, 12.0, 5.0, 10.0))
        tracker.recordEvaluatedOffer(trip, eval, now)

        repo.updateSnapshotActuals("trip_Z_waze", actualDur = 15.0, finalEarnings = 15.0)

        val snap = snapshotDao.getSnapshotByTripId("trip_Z_waze")!!
        assertFalse(snap.wazeAvailable)
        assertNull(snap.wazeEstimatedDurationMinutes)
        assertNull(snap.uberWazeDurationDeltaMinutes)
        assertNull(snap.wazeEstimateTimestamp)

        val recon = snap.calculateReconciliationMetrics()
        assertEquals(2.0, recon.durationErrorMinutes!!, 0.001) // 15.0 - 13.0 = 2.0 min
    }
}
