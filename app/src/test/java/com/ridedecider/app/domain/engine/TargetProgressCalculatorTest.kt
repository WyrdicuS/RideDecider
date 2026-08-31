package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProgressStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetProgressCalculatorTest {

    // 1. Objetivo diario 150 €, ganancias 0 €
    @Test
    fun target150_earned0_shouldCalculateCorrectly() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 0.0,
            plannedHours = 8.0,
            workedHours = 0.0
        )

        assertEquals(150.0, progress.targetEur, 0.001)
        assertEquals(0.0, progress.earnedEur, 0.001)
        assertEquals(150.0, progress.remainingEur, 0.001)
        assertEquals(0.0, progress.completionPercentage, 0.001)
        assertEquals(8.0, progress.plannedHours, 0.001)
        assertEquals(0.0, progress.workedHours, 0.001)
        assertEquals(8.0, progress.remainingHours, 0.001)
        assertEquals(0.0, progress.currentHourlyRate, 0.001)
        assertEquals(18.75, progress.requiredHourlyRate, 0.001) // 150 / 8 = 18.75 €/h
        assertEquals(ProgressStatus.BEHIND, progress.status)
    }

    // 2. Objetivo diario 150 €, ganancias 75 € en 4 horas (jornada 8h)
    @Test
    fun target150_earned75_shouldCalculateCorrectly() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 75.0,
            plannedHours = 8.0,
            workedHours = 4.0
        )

        assertEquals(75.0, progress.remainingEur, 0.001)
        assertEquals(50.0, progress.completionPercentage, 0.001)
        assertEquals(4.0, progress.remainingHours, 0.001)
        assertEquals(18.75, progress.currentHourlyRate, 0.001) // 75 / 4 = 18.75 €/h
        assertEquals(18.75, progress.requiredHourlyRate, 0.001) // 75 / 4 = 18.75 €/h
        assertEquals(ProgressStatus.AHEAD, progress.status) // Ritmo exacto >= requerido
    }

    // 3. Objetivo diario alcanzado (150 € / 150 €)
    @Test
    fun target150_earned150_shouldBeTargetReached() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 150.0,
            plannedHours = 8.0,
            workedHours = 6.0
        )

        assertEquals(0.0, progress.remainingEur, 0.001)
        assertEquals(100.0, progress.completionPercentage, 0.001)
        assertEquals(2.0, progress.remainingHours, 0.001)
        assertEquals(25.0, progress.currentHourlyRate, 0.001)
        assertEquals(0.0, progress.requiredHourlyRate, 0.001)
        assertEquals(ProgressStatus.TARGET_REACHED, progress.status)
    }

    // 4. Objetivo superado (180 € / 150 €)
    @Test
    fun target150_earned180_shouldBeTargetReachedWithZeroRemaining() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 180.0,
            plannedHours = 8.0,
            workedHours = 7.0
        )

        assertEquals(0.0, progress.remainingEur, 0.001)
        assertEquals(120.0, progress.completionPercentage, 0.001)
        assertEquals(0.0, progress.requiredHourlyRate, 0.001)
        assertEquals(ProgressStatus.TARGET_REACHED, progress.status)
    }

    // 5. Cero horas restantes sin haber alcanzado el objetivo
    @Test
    fun zeroRemainingHours_unreachedTarget_shouldBeBehindWithZeroRequiredRate() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 100.0,
            plannedHours = 8.0,
            workedHours = 8.0
        )

        assertEquals(50.0, progress.remainingEur, 0.001)
        assertEquals(0.0, progress.remainingHours, 0.001)
        assertEquals(0.0, progress.requiredHourlyRate, 0.001) // Sin horas restantes
        assertEquals(ProgressStatus.BEHIND, progress.status)
    }

    // 6. Horas restantes negativas — protección estricta (nunca permitir valores negativos)
    @Test
    fun workedMoreThanPlanned_remainingHoursMustNeverBeNegative() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 120.0,
            plannedHours = 8.0,
            workedHours = 10.0 // Trabajó 10h en una jornada de 8h
        )

        assertEquals(0.0, progress.remainingHours, 0.001)
        assertTrue("Las horas restantes nunca deben ser negativas", progress.remainingHours >= 0.0)
        assertEquals(30.0, progress.remainingEur, 0.001)
        assertEquals(ProgressStatus.BEHIND, progress.status)
    }

    // 7. Ritmo necesario correctamente calculado
    @Test
    fun requiredHourlyRate_formulaValidation() {
        // 68 € restantes en 3h 28min (3.4667 h) = 19.62 €/h
        val remainingEur = 68.0
        val remainingHours = 3.0 + (28.0 / 60.0)
        val rate = TargetProgressCalculator.calculateRequiredHourlyRate(remainingEur, remainingHours)

        assertEquals(19.615, rate, 0.01)
    }

    // 8. Jornada de 5 horas
    @Test
    fun fiveHourShift_shouldCalculateProperly() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 100.0,
            earnedEur = 40.0,
            plannedHours = 5.0,
            workedHours = 2.0
        )

        assertEquals(60.0, progress.remainingEur, 0.001)
        assertEquals(3.0, progress.remainingHours, 0.001)
        assertEquals(20.0, progress.currentHourlyRate, 0.001) // 40 / 2 = 20 €/h
        assertEquals(20.0, progress.requiredHourlyRate, 0.001) // 60 / 3 = 20 €/h
        assertEquals(ProgressStatus.AHEAD, progress.status)
    }

    // 9. Jornada de 8 horas
    @Test
    fun eightHourShift_shouldCalculateProperly() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 160.0,
            earnedEur = 60.0,
            plannedHours = 8.0,
            workedHours = 3.0
        )

        assertEquals(100.0, progress.remainingEur, 0.001)
        assertEquals(5.0, progress.remainingHours, 0.001)
        assertEquals(20.0, progress.currentHourlyRate, 0.001) // 60 / 3 = 20 €/h
        assertEquals(20.0, progress.requiredHourlyRate, 0.001) // 100 / 5 = 20 €/h
        assertEquals(ProgressStatus.AHEAD, progress.status)
    }

    // 10. Jornada de 10 horas
    @Test
    fun tenHourShift_shouldCalculateProperly() {
        val progress = TargetProgressCalculator.calculateProgress(
            period = GoalPeriod.DAILY,
            targetEur = 250.0,
            earnedEur = 100.0,
            plannedHours = 10.0,
            workedHours = 4.0
        )

        assertEquals(150.0, progress.remainingEur, 0.001)
        assertEquals(6.0, progress.remainingHours, 0.001)
        assertEquals(25.0, progress.currentHourlyRate, 0.001) // 100 / 4 = 25 €/h
        assertEquals(25.0, progress.requiredHourlyRate, 0.001) // 150 / 6 = 25 €/h
        assertEquals(ProgressStatus.AHEAD, progress.status)
    }
}
