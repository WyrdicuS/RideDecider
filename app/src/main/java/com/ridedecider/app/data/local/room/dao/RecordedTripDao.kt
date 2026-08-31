package com.ridedecider.app.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ridedecider.app.data.local.room.entity.RecordedTripEntity

/**
 * Data Access Object para operaciones sobre el historial persistente de viajes.
 */
@Dao
interface RecordedTripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(trip: RecordedTripEntity): Long

    @Query("SELECT * FROM recorded_trips WHERE id = :id")
    suspend fun getTripById(id: String): RecordedTripEntity?

    @Query("SELECT * FROM recorded_trips WHERE status = 'COMPLETED' AND completedTimestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY completedTimestamp ASC")
    suspend fun getCompletedTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity>

    @Query("SELECT * FROM recorded_trips WHERE (recordedTimestamp BETWEEN :startTimestamp AND :endTimestamp) OR (completedTimestamp BETWEEN :startTimestamp AND :endTimestamp) OR (cancelledTimestamp BETWEEN :startTimestamp AND :endTimestamp) ORDER BY recordedTimestamp ASC")
    suspend fun getTripsBetween(startTimestamp: Long, endTimestamp: Long): List<RecordedTripEntity>

    @Query("SELECT COALESCE((SELECT COALESCE(SUM(finalEarningsEur), 0.0) FROM recorded_trips WHERE status = 'COMPLETED' AND completedTimestamp BETWEEN :startTimestamp AND :endTimestamp) + (SELECT COALESCE(SUM(cancellationFeeEur), 0.0) FROM recorded_trips WHERE status LIKE 'CANCELLED%' AND cancellationFeeEur > 0 AND cancelledTimestamp BETWEEN :startTimestamp AND :endTimestamp), 0.0)")
    suspend fun getCompletedEarningsBetween(startTimestamp: Long, endTimestamp: Long): Double

    @Query("SELECT COALESCE(SUM(actualDurationMinutes), 0.0) FROM recorded_trips WHERE status = 'COMPLETED' AND completedTimestamp BETWEEN :startTimestamp AND :endTimestamp")
    suspend fun getCompletedDurationMinutesBetween(startTimestamp: Long, endTimestamp: Long): Double

    @Query("SELECT * FROM recorded_trips WHERE status = 'ACCEPTED_BY_DRIVER' ORDER BY recordedTimestamp DESC LIMIT 1")
    suspend fun getActiveAssignedTrip(): RecordedTripEntity?

    @Query("SELECT * FROM recorded_trips ORDER BY recordedTimestamp DESC")
    suspend fun getAllTrips(): List<RecordedTripEntity>

    @Query("DELETE FROM recorded_trips")
    suspend fun clearAll(): Int
}
