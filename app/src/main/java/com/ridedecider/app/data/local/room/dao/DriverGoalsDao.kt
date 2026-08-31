package com.ridedecider.app.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ridedecider.app.data.local.room.entity.DriverGoalsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object para los objetivos económicos del conductor.
 */
@Dao
interface DriverGoalsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(goals: DriverGoalsEntity): Long

    @Query("SELECT * FROM driver_goals WHERE id = 1")
    suspend fun getGoals(): DriverGoalsEntity?

    @Query("SELECT * FROM driver_goals WHERE id = 1")
    fun getGoalsFlow(): Flow<DriverGoalsEntity?>
}
