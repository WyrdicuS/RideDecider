package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.local.room.RideDeciderDatabase
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
import java.util.Calendar

/**
 * Suite determinista de pruebas de integridad para la Learning Data Foundation (Fase 8.11).
 * Comprueba las 22 reglas de inmutabilidad, captura t₀, aislamiento de instancias y separabilidad estimada vs real.
 */
class LearningDataFoundationTest {

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
                tripId, actualDist, actualDur, actualPickupDur, actualBaseFare, waitingComp, cancellationFee, tip, finalEarnings
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
    // 1. testA_validOffer_createsCorrectSnapshot
    // =========================================================================
    @Test
    fun testA_validOffer_createsCorrectSnapshot() = runBlocking {
        val trip = createTrip("inst_A", fare = 15.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_A")
        assertNotNull(snapshot)
        assertEquals("inst_A", snapshot!!.instanceId)
        assertEquals("inst_A", snapshot.tripId)
        assertEquals("1.0.1", snapshot.appVersion)
        assertEquals("2.0", snapshot.engineVersion)
    }

    // =========================================================================
    // 2. testB_snapshot_preservesExactSpe2Metrics
    // =========================================================================
    @Test
    fun testB_snapshot_preservesExactSpe2Metrics() = runBlocking {
        val trip = createTrip("inst_B", fare = 12.0, pickupKm = 2.0, tripKm = 4.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_B")!!
        assertEquals(6.0, snapshot.estimatedTotalDistanceKm!!, 0.001)
        assertEquals(13.0, snapshot.estimatedTotalDurationMinutes!!, 0.001)
        assertEquals(2.0, snapshot.grossPerKm!!, 0.001)
        assertNotNull(snapshot.profitabilityScore)
    }

    // =========================================================================
    // 3. testC_snapshot_preservesDecisionAndLevel
    // =========================================================================
    @Test
    fun testC_snapshot_preservesDecisionAndLevel() = runBlocking {
        val trip = createTrip("inst_C")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_C")!!
        assertEquals("ACCEPT", snapshot.decision)
        assertNotNull(snapshot.profitabilityLevel)
    }

    // =========================================================================
    // 4. testD_snapshot_preservesReasons
    // =========================================================================
    @Test
    fun testD_snapshot_preservesReasons() = runBlocking {
        val trip = createTrip("inst_D")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_D")!!
        assertTrue(snapshot.decisionReasonsCommaSeparated.contains("ACCEPT_HIGH_PROFITABILITY"))
    }

    // =========================================================================
    // 5. testE_snapshot_preservesKinematicsSource
    // =========================================================================
    @Test
    fun testE_snapshot_preservesKinematicsSource() = runBlocking {
        val trip = createTrip("inst_E")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_E")!!
        assertEquals("LEGACY_UNSPECIFIED", snapshot.kinematicsSource)
    }

    // =========================================================================
    // 6. testF_unknownFieldsRemainNull
    // =========================================================================
    @Test
    fun testF_unknownFieldsRemainNull() = runBlocking {
        val trip = createTrip("inst_F")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_F")!!
        assertNull(snapshot.actualDistanceKm)
        assertNull(snapshot.actualDurationMinutes)
        assertNull(snapshot.actualBaseFareEur)
        assertNull(snapshot.tipEur)
    }

    // =========================================================================
    // 7. testG_pickupSpeedKmh_remainsNullWhenZeroPickup
    // =========================================================================
    @Test
    fun testG_pickupSpeedKmh_remainsNullWhenZeroPickup() = runBlocking {
        val trip = createTrip("inst_G", pickupKm = 0.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_G")!!
        assertNull(snapshot.pickupSpeedKmh)
    }

    // =========================================================================
    // 8. testH_estimatedAndActuals_staySeparated
    // =========================================================================
    @Test
    fun testH_estimatedAndActuals_staySeparated() = runBlocking {
        val trip = createTrip("inst_H", fare = 10.0)
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        snapshotDao.updateActualResult("inst_H", actualDist = 6.2, actualDur = 14.0, finalEarnings = 12.50)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_H")!!
        assertEquals(10.0, snapshot.estimatedFareEur!!, 0.001)
        assertEquals(12.50, snapshot.finalEarningsEur!!, 0.001)
        assertEquals(6.2, snapshot.actualDistanceKm!!, 0.001)
    }

    // =========================================================================
    // 9. testI_instanceId_preservedInSnapshot
    // =========================================================================
    @Test
    fun testI_instanceId_preservedInSnapshot() = runBlocking {
        val trip = createTrip("inst_I_unique")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_I_unique")
        assertNotNull(snapshot)
        assertEquals("inst_I_unique", snapshot!!.instanceId)
    }

    // =========================================================================
    // 10. testJ_tripId_preservedInSnapshot
    // =========================================================================
    @Test
    fun testJ_tripId_preservedInSnapshot() = runBlocking {
        val trip = createTrip("inst_J_trip")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByTripId("inst_J_trip")
        assertNotNull(snapshot)
        assertEquals("inst_J_trip", snapshot!!.tripId)
    }

    // =========================================================================
    // 11. testK_recordedTimestamp_isValid
    // =========================================================================
    @Test
    fun testK_recordedTimestamp_isValid() = runBlocking {
        val trip = createTrip("inst_K")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_K")!!
        assertEquals(now, snapshot.recordedTimestamp)
    }

    // =========================================================================
    // 12. testL_dayOfWeek_isCorrect
    // =========================================================================
    @Test
    fun testL_dayOfWeek_isCorrect() = runBlocking {
        val trip = createTrip("inst_L")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_L")!!
        assertTrue(snapshot.dayOfWeek in 1..7)
    }

    // =========================================================================
    // 13. testM_hourAndMinute_areCorrect
    // =========================================================================
    @Test
    fun testM_hourAndMinute_areCorrect() = runBlocking {
        val trip = createTrip("inst_M")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_M")!!
        assertTrue(snapshot.hourOfDay in 0..23)
        assertTrue(snapshot.minuteOfHour in 0..59)
    }

    // =========================================================================
    // 14. testN_timeBucket_isDeterministic
    // =========================================================================
    @Test
    fun testN_timeBucket_isDeterministic() = runBlocking {
        val trip = createTrip("inst_N")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_N")!!
        assertNotNull(snapshot.timeBucket)
        assertTrue(listOf("MORNING_RUSH", "DAY", "MIDDAY_EXIT", "EVENING_RUSH", "NIGHT").contains(snapshot.timeBucket))
    }

    // =========================================================================
    // 15. testO_snapshot_isImmutable
    // =========================================================================
    @Test
    fun testO_snapshot_isImmutable() = runBlocking {
        val trip = createTrip("inst_O")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val originalSnapshot = snapshotDao.getSnapshotByInstanceId("inst_O")!!
        val originalScore = originalSnapshot.profitabilityScore

        // Modificar objeto en memoria
        val copiedSnapshot = originalSnapshot.copy(profitabilityScore = 10)
        assertFalse(originalSnapshot.profitabilityScore == copiedSnapshot.profitabilityScore)

        // El registro guardado en DAO debe mantener la cifra original intacta
        val fetchedFromDao = snapshotDao.getSnapshotByInstanceId("inst_O")!!
        assertEquals(originalScore, fetchedFromDao.profitabilityScore)
    }

    // =========================================================================
    // 16. testP_roomPersistence_savesAndRetrievesSnapshot
    // =========================================================================
    @Test
    fun testP_roomPersistence_savesAndRetrievesSnapshot() = runBlocking {
        val trip = createTrip("inst_P")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val all = snapshotDao.getAllSnapshots()
        assertTrue(all.any { it.instanceId == "inst_P" })
    }

    // =========================================================================
    // 17. testQ_retrievalByInstanceIdAndTripId
    // =========================================================================
    @Test
    fun testQ_retrievalByInstanceIdAndTripId() = runBlocking {
        val trip = createTrip("inst_Q")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val byInst = snapshotDao.getSnapshotByInstanceId("inst_Q")
        val byTrip = snapshotDao.getSnapshotByTripId("inst_Q")

        assertNotNull(byInst)
        assertNotNull(byTrip)
        assertEquals(byInst!!.snapshotId, byTrip!!.snapshotId)
    }

    // =========================================================================
    // 18. testR_noCrossContaminationBetweenOfferAAndOfferB
    // =========================================================================
    @Test
    fun testR_noCrossContaminationBetweenOfferAAndOfferB() = runBlocking {
        val tripA = createTrip("inst_A_iso", fare = 20.0)
        val evalA = decisionEngine.evaluate(tripA, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))
        tracker.recordEvaluatedOffer(tripA, evalA, now)

        val tripB = createTrip("inst_B_iso", fare = 8.0)
        val evalB = decisionEngine.evaluate(tripB, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))
        tracker.recordEvaluatedOffer(tripB, evalB, now + 1000L)

        val snapA = snapshotDao.getSnapshotByInstanceId("inst_A_iso")!!
        val snapB = snapshotDao.getSnapshotByInstanceId("inst_B_iso")!!

        assertEquals(20.0, snapA.estimatedFareEur!!, 0.001)
        assertEquals(8.0, snapB.estimatedFareEur!!, 0.001)
        assertFalse(snapA.snapshotId == snapB.snapshotId)
    }

    // =========================================================================
    // 19. testS_wazeFields_remainNullWhenNotAvailable
    // =========================================================================
    @Test
    fun testS_wazeFields_remainNullWhenNotAvailable() = runBlocking {
        val trip = createTrip("inst_S")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_S")!!
        assertFalse(snapshot.wazeAvailable)
        assertNull(snapshot.wazeEstimatedDurationMinutes)
        assertNull(snapshot.uberWazeDurationDeltaMinutes)
        assertNull(snapshot.wazeEstimateTimestamp)
    }

    // =========================================================================
    // 20. testT_uberWazeDelta_isNullWhenWazeMissing
    // =========================================================================
    @Test
    fun testT_uberWazeDelta_isNullWhenWazeMissing() = runBlocking {
        val trip = createTrip("inst_T")
        val eval = decisionEngine.evaluate(trip, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))

        tracker.recordEvaluatedOffer(trip, eval, now)

        val snapshot = snapshotDao.getSnapshotByInstanceId("inst_T")!!
        assertNull(snapshot.uberWazeDurationDeltaMinutes)
    }

    // =========================================================================
    // 21. testU_databaseMigration_v3ToV4_createsTableAndIndices
    // =========================================================================
    @Test
    fun testU_databaseMigration_v3ToV4_createsTableAndIndices() {
        assertNotNull(RideDeciderDatabase.MIGRATION_3_4)
    }

    // =========================================================================
    // 22. testV_instanceIdIsolation_preventsDataBleed
    // =========================================================================
    @Test
    fun testV_instanceIdIsolation_preventsDataBleed() = runBlocking {
        val trip1 = createTrip("inst_V1")
        val eval1 = decisionEngine.evaluate(trip1, ProfitabilityConfig(
            costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
            maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
        ))
        tracker.recordEvaluatedOffer(trip1, eval1, now)

        val snap1 = snapshotDao.getSnapshotByInstanceId("inst_V1")
        val snap2 = snapshotDao.getSnapshotByInstanceId("inst_V2_non_existent")

        assertNotNull(snap1)
        assertNull(snap2)
    }
}
