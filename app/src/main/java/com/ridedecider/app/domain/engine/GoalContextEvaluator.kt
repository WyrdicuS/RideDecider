package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.GoalContextMetrics

object GoalContextEvaluator {

    fun evaluate(
        metrics: EvaluationMetrics,
        activeProgress: EarningsProgress,
        rawFare: Double?
    ): GoalContextMetrics? {
        if (activeProgress.targetEur <= 0.0) return null

        val requiredHourly = activeProgress.requiredHourlyRate
        val remainingEur = activeProgress.remainingEur
        val remainingHours = activeProgress.remainingHours

        val targetPaceRatio = if (requiredHourly > 0.0) {
            metrics.grossPerHour / requiredHourly
        } else {
            null
        }

        val estimatedGoalContribution = if (remainingEur > 0.0 && rawFare != null && rawFare > 0.0) {
            rawFare / remainingEur
        } else {
            null
        }

        val estimatedTimeConsumption = if (remainingHours > 0.0 && metrics.totalDurationMinutes > 0.0) {
            metrics.totalDurationMinutes / (remainingHours * 60.0)
        } else {
            null
        }

        return GoalContextMetrics(
            targetPaceRatio = targetPaceRatio,
            estimatedGoalContribution = estimatedGoalContribution,
            estimatedTimeConsumption = estimatedTimeConsumption,
            progressStatus = activeProgress.status,
            remainingEur = remainingEur,
            remainingHours = remainingHours,
            requiredHourlyRate = requiredHourly
        )
    }
}
