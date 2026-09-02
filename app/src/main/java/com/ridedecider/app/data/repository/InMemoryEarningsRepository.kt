package com.ridedecider.app.data.repository

import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity
import com.ridedecider.app.domain.model.RecordedTrip
import com.ridedecider.app.domain.model.TripTrackingStatus
import com.ridedecider.app.domain.repository.EarningsRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación en memoria del repositorio de viajes y ganancias del conductor.
 * Thread-safe para acceso concurrente desde corrutinas.
 */
class InMemoryEarningsRepository : EarningsRepository {

    private val tripsMap = ConcurrentHashMap<String, RecordedTrip>()
    private val snapshotsMap = ConcurrentHashMap<String, DecisionSnapshotEntity>()

    override suspend fun recordTrip(trip: RecordedTrip) {
        tripsMap[trip.id] = trip
    }

    override suspend fun saveDecisionSnapshot(snapshot: DecisionSnapshotEntity) {
        snapshotsMap[snapshot.snapshotId] = snapshot
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
        val snapshot = snapshotsMap.values.find { it.tripId == tripId || it.instanceId == tripId } ?: return
        val updated = snapshot.copy(
            actualDistanceKm = actualDist ?: snapshot.actualDistanceKm,
            actualDurationMinutes = actualDur ?: snapshot.actualDurationMinutes,
            actualPickupDurationMinutes = actualPickupDur ?: snapshot.actualPickupDurationMinutes,
            actualBaseFareEur = actualBaseFare ?: snapshot.actualBaseFareEur,
            waitingCompensationEur = waitingComp ?: snapshot.waitingCompensationEur,
            cancellationFeeEur = cancellationFee ?: snapshot.cancellationFeeEur,
            tipEur = tip ?: snapshot.tipEur,
            finalEarningsEur = finalEarnings ?: snapshot.finalEarningsEur
        )
        snapshotsMap[snapshot.snapshotId] = updated
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
            (it.completedTimestamp != null && it.completedTimestamp in startTimestamp..endTimestamp) ||
            (it.cancelledTimestamp != null && it.cancelledTimestamp in startTimestamp..endTimestamp)
        }.sortedBy { it.recordedTimestamp }
    }

    override suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double {
        val completedSum = tripsMap.values.filter {
            it.status == TripTrackingStatus.COMPLETED &&
            it.completedTimestamp != null &&
            it.completedTimestamp in startTimestamp..endTimestamp
        }.sumOf { it.finalEarningsEur ?: 0.0 }

        val cancelledFeeSum = tripsMap.values.filter {
            it.status.name.startsWith("CANCELLED") &&
            it.cancellationFeeEur != null && it.cancellationFeeEur > 0.0 &&
            it.cancelledTimestamp != null &&
            it.cancelledTimestamp in startTimestamp..endTimestamp
        }.sumOf { it.cancellationFeeEur ?: 0.0 }

        return completedSum + cancelledFeeSum
    }

    override suspend fun getWorkedMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double {
        return tripsMap.values.filter { trip ->
            trip.status == TripTrackingStatus.COMPLETED &&
            trip.completedTimestamp != null &&
            trip.completedTimestamp in startTimestamp..endTimestamp
        }.sumOf { it.durationMinutes ?: 0.0 }
    }

    override suspend fun clearAllTrips() {
        tripsMap.clear()
    }
}
