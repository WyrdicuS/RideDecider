package com.ridedecider.app.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ridedecider.app.data.local.room.entity.DecisionSnapshotEntity

/**
 * Data Access Object para el almacenamiento y consulta de la Learning Data Foundation.
 */
@Dao
interface DecisionSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshot(snapshot: DecisionSnapshotEntity): Long

    @Query("SELECT * FROM decision_snapshots WHERE snapshotId = :snapshotId")
    suspend fun getSnapshotById(snapshotId: String): DecisionSnapshotEntity?

    @Query("SELECT * FROM decision_snapshots WHERE instanceId = :instanceId")
    suspend fun getSnapshotByInstanceId(instanceId: String): DecisionSnapshotEntity?

    @Query("SELECT * FROM decision_snapshots WHERE tripId = :tripId")
    suspend fun getSnapshotByTripId(tripId: String): DecisionSnapshotEntity?

    @Query("SELECT * FROM decision_snapshots ORDER BY recordedTimestamp DESC")
    suspend fun getAllSnapshots(): List<DecisionSnapshotEntity>

    @Query("UPDATE decision_snapshots SET actualDistanceKm = :actualDist, actualDurationMinutes = :actualDur, finalEarningsEur = :finalEarnings WHERE tripId = :tripId")
    suspend fun updateActualResult(tripId: String, actualDist: Double?, actualDur: Double?, finalEarnings: Double?): Int

    @Query("""
        UPDATE decision_snapshots 
        SET actualDistanceKm = COALESCE(:actualDist, actualDistanceKm),
            actualDurationMinutes = COALESCE(:actualDur, actualDurationMinutes),
            actualPickupDurationMinutes = COALESCE(:actualPickupDur, actualPickupDurationMinutes),
            actualBaseFareEur = COALESCE(:actualBaseFare, actualBaseFareEur),
            waitingCompensationEur = COALESCE(:waitingComp, waitingCompensationEur),
            cancellationFeeEur = COALESCE(:cancellationFee, cancellationFeeEur),
            tipEur = COALESCE(:tip, tipEur),
            finalEarningsEur = COALESCE(:finalEarnings, finalEarningsEur)
        WHERE tripId = :tripId
    """)
    suspend fun updateSnapshotActuals(
        tripId: String,
        actualDist: Double?,
        actualDur: Double?,
        actualPickupDur: Double?,
        actualBaseFare: Double?,
        waitingComp: Double?,
        cancellationFee: Double?,
        tip: Double?,
        finalEarnings: Double?
    ): Int

    @Query("DELETE FROM decision_snapshots")
    suspend fun clearAllSnapshots(): Int
}
