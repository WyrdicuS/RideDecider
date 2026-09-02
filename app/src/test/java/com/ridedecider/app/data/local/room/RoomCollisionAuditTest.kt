package com.ridedecider.app.data.local.room

import com.ridedecider.app.data.local.room.dao.RecordedTripDao
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity
import com.ridedecider.app.data.repository.RoomEarningsRepository
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.model.UberCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Test forense determinista de colisión de claves primarias.
 * Demuestra el comportamiento real de OnConflictStrategy.REPLACE y RoomEarningsRepository ante IDs duplicados.
 */
class RoomCollisionAuditTest {

    private class FakeRecordedTripDao : RecordedTripDao {
        private val dbMap = mutableMapOf<String, RecordedTripEntity>()

        override suspend fun insertOrUpdate(trip: RecordedTripEntity): Long {
            dbMap[trip.id] = trip
            return 1L
        }

        override suspend fun getTripById(id: String): RecordedTripEntity? {
            return dbMap[id]
        }

        override suspend fun getCompletedTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity> {
            return dbMap.values.filter { it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp }
        }

        override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity> {
            return dbMap.values.filter { (it.recordedTimestamp in startTimestamp..endTimestamp) }
        }

        override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double {
            return dbMap.values
                .filter { it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp }
                .sumOf { it.finalEarningsEur ?: 0.0 }
        }

        override suspend fun getCompletedDurationMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double {
            return dbMap.values
                .filter { it.status == "COMPLETED" && it.completedTimestamp != null && it.completedTimestamp!! in startTimestamp..endTimestamp }
                .sumOf { it.actualDurationMinutes ?: 0.0 }
        }

        override suspend fun getActiveAssignedTrip(): RecordedTripEntity? {
            return dbMap.values.firstOrNull { it.status == "ACCEPTED_BY_DRIVER" }
        }

        override suspend fun getAllTrips(): List<RecordedTripEntity> {
            return dbMap.values.toList()
        }

        override suspend fun clearAll(): Int {
            val size = dbMap.size
            dbMap.clear()
            return size
        }
    }

    private lateinit var fakeDao: FakeRecordedTripDao
    private lateinit var repository: RoomEarningsRepository

    @Before
    fun setUp() {
        fakeDao = FakeRecordedTripDao()
        repository = RoomEarningsRepository(fakeDao)
    }

    private fun createSampleTrip(id: String, fare: Double = 15.0, timestamp: Long = 1000000L): Trip {
        return Trip(
            id = id,
            timestamp = timestamp,
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

    private fun createEvaluation(trip: Trip): TripEvaluation {
        return TripEvaluation(
            trip = trip,
            configUsed = ProfitabilityConfig(
                costPerKm = 0.20, costPerHour = 4.0, minGrossHourlyRate = 20.0,
                minGrossPerKmRate = 1.0, minNetTripProfit = 2.0, minNetHourlyRate = 12.0,
                maxPickupDistanceKm = 5.0, maxPickupTimeMinutes = 10.0
            ),
            metrics = null,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = trip.timestamp
        )
    }

    @Test
    fun testPrimaryKeyCollision_protectedRepository_preservesCompletedStatusAndEarnings() = runBlocking {
        val collisionId = "uber_COLLISION_123"
        val tripA = createSampleTrip(collisionId, fare = 15.0, timestamp = 1000000L)
        val evalA = createEvaluation(tripA)
        val recordedA = RecordedTrip(
            id = collisionId,
            trip = tripA,
            evaluation = evalA,
            status = TripTrackingStatus.COMPLETED,
            recordedTimestamp = 1000000L,
            completedTimestamp = 1001000L,
            finalEarningsEur = 15.0,
            durationMinutes = 13.0
        )

        // 1. Guardar Viaje A como COMPLETED con 15.0 €
        repository.recordTrip(recordedA)
        repository.updateTripStatus(collisionId, TripTrackingStatus.COMPLETED, finalEarnings = 15.0, completedTimestamp = 1001000L, durationMinutes = 13.0)

        val earningsBefore = repository.getCompletedEarningsBetween(0L, 2000000L)
        assertEquals(15.0, earningsBefore, 0.001)

        // 2. Colisión: Intentar re-evaluar la misma ID "uber_COLLISION_123"
        val tripB = createSampleTrip(collisionId, fare = 15.0, timestamp = 5000000L)
        val evalB = createEvaluation(tripB)
        val recordedB = RecordedTrip(
            id = collisionId,
            trip = tripB,
            evaluation = evalB,
            status = TripTrackingStatus.EVALUATED,
            recordedTimestamp = 5000000L
        )

        // 3. Evaluar Oferta B con el mismo ID
        repository.recordTrip(recordedB)

        // 4. Comprobar que el estado de la Oferta A en Room SE MANTIENE como COMPLETED (Protegido)
        val entityInDb = fakeDao.getTripById(collisionId)
        assertNotNull(entityInDb)
        assertEquals("COMPLETED", entityInDb!!.status)

        // 5. Comprobar que las ganancias acumuladas se preservan intactas en 15.0 €
        val earningsAfter = repository.getCompletedEarningsBetween(0L, 10000000L)
        assertEquals(15.0, earningsAfter, 0.001)
    }
}
