package com.ridedecider.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.GoalPeriod

/**
 * Entidad persistente de Room para los objetivos económicos configurados por el conductor.
 */
@Entity(tableName = "driver_goals")
data class DriverGoalsEntity(
    @PrimaryKey
    val id: Int = 1,
    val activePeriod: String = "DAILY",
    val dailyTargetEur: Double = 120.0,
    val weeklyTargetEur: Double = 0.0,
    val monthlyTargetEur: Double = 0.0,
    val dailyPlannedHours: Double = 5.0,
    val weeklyPlannedHours: Double = 0.0,
    val monthlyPlannedHours: Double = 0.0,
    val updatedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): DriverGoals {
        val period = try {
            GoalPeriod.valueOf(activePeriod)
        } catch (_: Exception) {
            GoalPeriod.DAILY
        }
        return DriverGoals(
            activePeriod = period,
            dailyTargetEur = dailyTargetEur,
            weeklyTargetEur = weeklyTargetEur,
            monthlyTargetEur = monthlyTargetEur,
            dailyPlannedHours = dailyPlannedHours,
            weeklyPlannedHours = weeklyPlannedHours,
            monthlyPlannedHours = monthlyPlannedHours
        )
    }

    companion object {
        fun fromDomain(goals: DriverGoals): DriverGoalsEntity {
            return DriverGoalsEntity(
                id = 1,
                activePeriod = goals.activePeriod.name,
                dailyTargetEur = goals.dailyTargetEur,
                weeklyTargetEur = goals.weeklyTargetEur,
                monthlyTargetEur = goals.monthlyTargetEur,
                dailyPlannedHours = goals.dailyPlannedHours,
                weeklyPlannedHours = goals.weeklyPlannedHours,
                monthlyPlannedHours = goals.monthlyPlannedHours,
                updatedTimestamp = System.currentTimeMillis()
            )
        }
    }
}
