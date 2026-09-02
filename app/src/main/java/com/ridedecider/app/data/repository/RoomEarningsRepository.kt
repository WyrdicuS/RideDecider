package com.ridedecider.app.data.repository

import com.ridedecider.app.data.local.room.dao.DecisionSnapshotDao
import com.ridedecider.app.data.local.room.dao.RecordedTripDao
import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.repository.EarningsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de [EarningsRepository] respaldada por Room en SQLite.
 * Ejecuta todas las operaciones de base de datos en [Dispatchers.IO] sin bloquear hilos críticos.
 */
class RoomEarningsRepository(
    private val recordedTripDao: RecordedTripDao,
    private val decisionSnapshotDao: DecisionSnapshotDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EarningsRepository {

    override suspend fun saveDecisionSnapshot(snapshot: DecisionSnapshotEntity) = withContext(ioDispatcher) {
        decisionSnapshotDao?.insertSnapshot(snapshot)
        Unit
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
    ) = withContext(ioDispatcher) {
        decisionSnapshotDao?.updateSnapshotActuals(
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
        Unit
    }

    override suspend fun recordTrip(trip: RecordedTrip) = withContext(ioDispatcher) {
        val existing = recordedTripDao.getTripById(trip.id)
        if (existing == null) {
            val entity = RecordedTripEntity.fromDomain(trip)
            recordedTripDao.insertOrUpdate(entity)
        } else {
            // Protección estricta: NUNCA degradar un estado terminal COMPLETED o CANCELLED
            if (existing.status == "COMPLETED" || existing.status.startsWith("CANCELLED")) {
                return@withContext
            }
            // Preservar datos de finalización previos si ya existían
            val updated = existing.copy(
                status = trip.status.name,
                decision = trip.evaluation.decision.name,
                reasonsCommaSeparated = trip.evaluation.reasons.joinToString(",") { it.name }
            )
            recordedTripDao.insertOrUpdate(updated)
        }
        Unit
    }

    override suspend fun updateTripStatus(
        tripId: String,
        status: TripTrackingStatus,
        finalEarnings: Double?,
        completedTimestamp: Long?,
        durationMinutes: Double?,
        cancellationFee: Double?,
        cancellationReason: com.ridedecider.app.domain.model.CancellationReason?,
        cancelledTimestamp: Long?
    ) = withContext(ioDispatcher) {
        val existing = recordedTripDao.getTripById(tripId) ?: return@withContext
        val updated = existing.copy(
            status = status.name,
            finalEarningsEur = finalEarnings ?: existing.finalEarningsEur,
            completedTimestamp = completedTimestamp ?: existing.completedTimestamp,
            actualDurationMinutes = durationMinutes ?: existing.actualDurationMinutes,
            cancellationFeeEur = cancellationFee ?: existing.cancellationFeeEur,
            cancellationReason = cancellationReason?.name ?: existing.cancellationReason,
            cancelledTimestamp = cancelledTimestamp ?: existing.cancelledTimestamp
        )
        recordedTripDao.insertOrUpdate(updated)
        Unit
    }

    override suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTrip> = withContext(ioDispatcher) {
        recordedTripDao.getTripsBetween(startTimestamp, endTimestamp).map { it.toDomain() }
    }

    override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double = withContext(ioDispatcher) {
        recordedTripDao.getCompletedEarningsBetween(startTimestamp, endTimestamp)
    }

    override suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double = withContext(ioDispatcher) {
        recordedTripDao.getCompletedDurationMinutesBetween(startTimestamp, endTimestamp)
    }

    override suspend fun clearAllTrips() = withContext(ioDispatcher) {
        recordedTripDao.clearAll()
        Unit
    }
}
