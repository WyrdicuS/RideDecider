package com.ridedecider.app.domain.model

/**
 * Representa el progreso económico acumulado y las métricas de ritmo para un período específico.
 * Modelo de dominio 100% puro en Kotlin.
 */
data class EarningsProgress(
    val period: GoalPeriod,
    val targetEur: Double,
    val earnedEur: Double,
    val remainingEur: Double,
    val completionPercentage: Double,
    val plannedHours: Double,
    val workedHours: Double,
    val remainingHours: Double,
    val currentHourlyRate: Double,
    val requiredHourlyRate: Double,
    val status: ProgressStatus
)
