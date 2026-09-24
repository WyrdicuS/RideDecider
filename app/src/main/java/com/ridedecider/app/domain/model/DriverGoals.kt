package com.ridedecider.app.domain.model

/**
 * Objetivos económicos y horas de trabajo planificadas configuradas por el conductor.
 * Solo un único objetivo activo a la vez (Diario, Semanal o Mensual) para evitar mezclas.
 */
data class DriverGoals(
    val activePeriod: GoalPeriod = GoalPeriod.DAILY,
    val dailyTargetEur: Double = 120.0,
    val weeklyTargetEur: Double = 0.0,
    val monthlyTargetEur: Double = 0.0,
    val dailyPlannedHours: Double = 5.0,
    val weeklyPlannedHours: Double = 0.0,
    val monthlyPlannedHours: Double = 0.0
) {
    val activeTargetEur: Double
        get() = when (activePeriod) {
            GoalPeriod.DAILY -> dailyTargetEur
            GoalPeriod.WEEKLY -> weeklyTargetEur
            GoalPeriod.MONTHLY -> monthlyTargetEur
        }

    val activePlannedHours: Double
        get() = when (activePeriod) {
            GoalPeriod.DAILY -> dailyPlannedHours
            GoalPeriod.WEEKLY -> weeklyPlannedHours
            GoalPeriod.MONTHLY -> monthlyPlannedHours
        }

    // R6.7: DEUDA TECNICA. El fallback `24.0` cuando plannedHours <= 0.0 no representa
    // el objetivo personal (que en ese caso es indeterminado) ni el umbral economico del
    // motor (ese vive en ProfitabilityConfig y es independiente de Goals). Coincide
    // numericamente con InMemoryProfitabilityConfigProvider.minGrossHourlyRate por
    // casualidad. Consumidores actuales: log de diagnostico en UberAccessibilityService
    // ("[GOALS_SYNCED]") — inofensivo. Cambiar a `Double?` requeriria propagar
    // opcionalidad por toda la cadena de Goals y queda fuera del alcance R6.7.
    // Regla: los consumidores deben validar `activePlannedHours > 0.0` antes de usar
    // este getter como ritmo objetivo real.
    val activeHourlyTarget: Double
        get() = if (activePlannedHours > 0.0) activeTargetEur / activePlannedHours else 24.0

    init {
        require(dailyTargetEur >= 0.0) { "El objetivo diario no puede ser negativo" }
        require(weeklyTargetEur >= 0.0) { "El objetivo semanal no puede ser negativo" }
        require(monthlyTargetEur >= 0.0) { "El objetivo mensual no puede ser negativo" }
        require(dailyPlannedHours >= 0.0) { "Las horas diarias planificadas no pueden ser negativas" }
        require(weeklyPlannedHours >= 0.0) { "Las horas semanales planificadas no pueden ser negativas" }
        require(monthlyPlannedHours >= 0.0) { "Las horas mensuales planificadas no pueden ser negativas" }
    }
}
