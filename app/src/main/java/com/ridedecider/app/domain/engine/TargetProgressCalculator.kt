package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProgressStatus

/**
 * Calculadora pura en Kotlin para evaluar el progreso hacia los objetivos del conductor y el ritmo requerido.
 * Libre de dependencias del framework de Android o efectos secundarios.
 */
object TargetProgressCalculator {

    /**
     * Calcula el estado de progreso para un objetivo y período determinados.
     *
     * @param period Período temporal (DAILY, WEEKLY, MONTHLY).
     * @param targetEur Objetivo económico en euros (>= 0.0).
     * @param earnedEur Ganancias acumuladas ya completadas en euros (>= 0.0).
     * @param plannedHours Horas planificadas de trabajo (>= 0.0).
     * @param workedHours Horas efectivamente trabajadas en el período (>= 0.0).
     * @return [EarningsProgress] con los cálculos matemáticos precisos.
     */
    fun calculateProgress(
        period: GoalPeriod,
        targetEur: Double,
        earnedEur: Double,
        plannedHours: Double,
        workedHours: Double
    ): EarningsProgress {
        val safeTarget = maxOf(0.0, targetEur)
        val safeEarned = maxOf(0.0, earnedEur)
        val safePlanned = maxOf(0.0, plannedHours)
        val safeWorked = maxOf(0.0, workedHours)

        val remainingEur = maxOf(0.0, safeTarget - safeEarned)
        val completionPercentage = when {
            safeTarget <= 0.0 -> if (safeEarned > 0.0) 100.0 else 0.0
            else -> (safeEarned / safeTarget) * 100.0
        }

        val remainingHours = maxOf(0.0, safePlanned - safeWorked)
        val currentHourlyRate = if (safeWorked > 0.0) safeEarned / safeWorked else 0.0
        val requiredHourlyRate = calculateRequiredHourlyRate(remainingEur, remainingHours)

        val status = when {
            safeEarned >= safeTarget && safeTarget > 0.0 -> ProgressStatus.TARGET_REACHED
            remainingHours <= 0.0 && remainingEur > 0.0 -> ProgressStatus.BEHIND
            safeWorked > 0.0 && currentHourlyRate >= requiredHourlyRate -> ProgressStatus.AHEAD
            safeWorked > 0.0 && currentHourlyRate >= requiredHourlyRate * 0.90 -> ProgressStatus.ON_TRACK
            else -> ProgressStatus.BEHIND
        }

        return EarningsProgress(
            period = period,
            targetEur = safeTarget,
            earnedEur = safeEarned,
            remainingEur = remainingEur,
            completionPercentage = completionPercentage,
            plannedHours = safePlanned,
            workedHours = safeWorked,
            remainingHours = remainingHours,
            currentHourlyRate = currentHourlyRate,
            requiredHourlyRate = requiredHourlyRate,
            status = status
        )
    }

    /**
     * Calcula el ritmo horario necesario (€/h) para alcanzar el objetivo restante.
     *
     * Fórmula: dinero restante / horas restantes
     */
    fun calculateRequiredHourlyRate(remainingEur: Double, remainingHours: Double): Double {
        if (remainingEur <= 0.0) return 0.0
        if (remainingHours <= 0.0) return 0.0
        return remainingEur / remainingHours
    }
}
