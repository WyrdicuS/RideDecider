package com.ridedecider.app.domain.model

data class GoalContextMetrics(
    val targetPaceRatio: Double?,
    val estimatedGoalContribution: Double?,
    val estimatedTimeConsumption: Double?,
    val progressStatus: ProgressStatus,
    val remainingEur: Double,
    val remainingHours: Double,
    val requiredHourlyRate: Double
)
