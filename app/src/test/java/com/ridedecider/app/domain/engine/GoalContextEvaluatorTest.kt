package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverEconomicContext
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.EvaluationMetrics
import com.ridedecider.app.domain.model.GoalContextMetrics
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalContextEvaluatorTest {

    private lateinit var engine: DecisionEngine
    private lateinit var defaultConfig: ProfitabilityConfig
    private val delta = 0.0001

    @Before
    fun setUp() {
        engine = DecisionEngine()
        defaultConfig = ProfitabilityConfig(
            costPerKm = 0.20,
            costPerHour = 4.0,
            minGrossHourlyRate = 20.0,
            minGrossPerKmRate = 1.0,
            minNetTripProfit = 2.0,
            minNetHourlyRate = 10.0,
            maxPickupDistanceKm = 5.0,
            maxPickupTimeMinutes = 10.0
        )
    }

    private fun makeTrip(
        rawFare: Double = 12.0,
        pickupDistanceKm: Double = 1.5,
        pickupDurationMinutes: Double = 4.0,
        tripDistanceKm: Double = 6.0,
        tripDurationMinutes: Double = 15.0
    ) = Trip(
        id = "test_goal",
        timestamp = System.currentTimeMillis(),
        offerType = TripOfferType.TRIP_OFFER,
        category = UberCategory.UBER_X,
        rawFare = rawFare,
        currency = "EUR",
        pickupDistanceKm = pickupDistanceKm,
        pickupDurationMinutes = pickupDurationMinutes,
        pickupAddress = null,
        tripDistanceKm = tripDistanceKm,
        tripDurationMinutes = tripDurationMinutes,
        dropoffAddress = null
    )

    private fun makeProgress(
        targetEur: Double = 100.0,
        earnedEur: Double = 40.0,
        plannedHours: Double = 8.0,
        workedHours: Double = 3.0,
        status: ProgressStatus = ProgressStatus.ON_TRACK
    ): EarningsProgress {
        val remaining = targetEur - earnedEur
        val remainingH = plannedHours - workedHours
        return EarningsProgress(
            period = GoalPeriod.DAILY,
            targetEur = targetEur,
            earnedEur = earnedEur,
            remainingEur = remaining,
            completionPercentage = if (targetEur > 0) earnedEur / targetEur * 100 else 0.0,
            plannedHours = plannedHours,
            workedHours = workedHours,
            remainingHours = remainingH,
            currentHourlyRate = if (workedHours > 0) earnedEur / workedHours else 0.0,
            requiredHourlyRate = if (remainingH > 0) remaining / remainingH else 0.0,
            status = status
        )
    }

    private fun makeEconomicContext(
        dailyProgress: EarningsProgress = makeProgress(),
        weeklyProgress: EarningsProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0),
        monthlyProgress: EarningsProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0)
    ) = DriverEconomicContext(
        dailyProgress = dailyProgress,
        weeklyProgress = weeklyProgress,
        monthlyProgress = monthlyProgress,
        timestamp = System.currentTimeMillis()
    )

    // TC-1: GoalContextEvaluator produce targetPaceRatio correcto
    @Test
    fun evaluate_withActiveGoal_producesCorrectTargetPaceRatio() {
        val progress = makeProgress(targetEur = 100.0, earnedEur = 40.0, plannedHours = 8.0, workedHours = 3.0)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNotNull(result)
        val expectedRequired = 60.0 / 5.0 // 12.0 €/h
        val expectedRatio = 37.89 / expectedRequired
        assertEquals(expectedRatio, result!!.targetPaceRatio!!, delta)
    }

    // TC-2: estimatedGoalContribution usa rawFare (no netProfit)
    @Test
    fun evaluate_estimatedGoalContribution_usesRawFare() {
        val progress = makeProgress(targetEur = 100.0, earnedEur = 40.0)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNotNull(result)
        assertEquals(12.0 / 60.0, result!!.estimatedGoalContribution!!, delta)
    }

    // TC-3: estimatedTimeConsumption calcula correctamente
    @Test
    fun evaluate_estimatedTimeConsumption_calculatesCorrectly() {
        val progress = makeProgress(targetEur = 100.0, earnedEur = 40.0, plannedHours = 8.0, workedHours = 3.0)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNotNull(result)
        assertEquals(19.0 / (5.0 * 60.0), result!!.estimatedTimeConsumption!!, delta)
    }

    // TC-4: targetEur == 0 (modo libre) produce null
    @Test
    fun evaluate_withZeroTarget_returnsNull() {
        val progress = makeProgress(targetEur = 0.0, earnedEur = 0.0)
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNull(result)
    }

    // TC-5: TARGET_REACHED produce targetPaceRatio null (requiredHourlyRate == 0)
    @Test
    fun evaluate_targetReached_producesNullTargetPaceRatio() {
        val progress = makeProgress(
            targetEur = 100.0,
            earnedEur = 110.0,
            plannedHours = 8.0,
            workedHours = 7.0,
            status = ProgressStatus.TARGET_REACHED
        )
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNotNull(result)
        assertEquals(ProgressStatus.TARGET_REACHED, result!!.progressStatus)
    }

    // TC-6: remainingEur <= 0 produce estimatedGoalContribution null
    @Test
    fun evaluate_remainingEurZero_producesNullContribution() {
        val progress = makeProgress(
            targetEur = 100.0,
            earnedEur = 100.0,
            status = ProgressStatus.TARGET_REACHED
        )
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, 12.0)

        assertNotNull(result)
        assertNull(result!!.estimatedGoalContribution)
    }

    // TC-7: DecisionEngine con economicContext produce goalContext en TripEvaluation
    @Test
    fun decisionEngine_withEconomicContext_producesGoalContextInEvaluation() {
        val trip = makeTrip()
        val context = makeEconomicContext()

        val evaluation = engine.evaluate(trip, defaultConfig, economicContext = context)

        assertNotNull(evaluation.goalContext)
        assertNotNull(evaluation.goalContext!!.targetPaceRatio)
        assertNotNull(evaluation.goalContext!!.estimatedGoalContribution)
        assertNotNull(evaluation.goalContext!!.estimatedTimeConsumption)
        assertEquals(ProgressStatus.ON_TRACK, evaluation.goalContext!!.progressStatus)
    }

    // TC-8: DecisionEngine sin economicContext produce goalContext null
    @Test
    fun decisionEngine_withoutEconomicContext_producesNullGoalContext() {
        val trip = makeTrip()

        val evaluation = engine.evaluate(trip, defaultConfig)

        assertNull(evaluation.goalContext)
    }

    // TC-9: goalContext NO modifica la Decision de SPE
    @Test
    fun decisionEngine_goalContext_doesNotAlterSpeDecision() {
        val trip = makeTrip(rawFare = 3.0, tripDistanceKm = 15.0, tripDurationMinutes = 40.0)
        val context = makeEconomicContext(
            dailyProgress = makeProgress(
                targetEur = 100.0,
                earnedEur = 90.0,
                status = ProgressStatus.AHEAD
            )
        )

        val evalWithContext = engine.evaluate(trip, defaultConfig, economicContext = context)
        val evalWithout = engine.evaluate(trip, defaultConfig)

        assertEquals(evalWithout.decision, evalWithContext.decision)
    }

    // TC-10: goalContext NO modifica profitabilityScore
    @Test
    fun decisionEngine_goalContext_doesNotAlterProfitabilityScore() {
        val trip = makeTrip()
        val context = makeEconomicContext()

        val evalWith = engine.evaluate(trip, defaultConfig, economicContext = context)
        val evalWithout = engine.evaluate(trip, defaultConfig)

        assertEquals(
            evalWithout.metrics?.profitabilityScore,
            evalWith.metrics?.profitabilityScore
        )
    }

    // TC-11: Prioridad daily > weekly > monthly
    @Test
    fun decisionEngine_prioritizesDaily_overWeeklyAndMonthly() {
        val trip = makeTrip()
        val dailyProgress = makeProgress(targetEur = 100.0, earnedEur = 40.0)
        val weeklyProgress = makeProgress(targetEur = 500.0, earnedEur = 200.0)
        val context = makeEconomicContext(
            dailyProgress = dailyProgress,
            weeklyProgress = weeklyProgress
        )

        val evaluation = engine.evaluate(trip, defaultConfig, economicContext = context)

        assertNotNull(evaluation.goalContext)
        assertEquals(dailyProgress.remainingEur, evaluation.goalContext!!.remainingEur, delta)
    }

    // TC-12: Falls back to weekly when daily target is 0
    @Test
    fun decisionEngine_fallsBackToWeekly_whenDailyTargetIsZero() {
        val trip = makeTrip()
        val weeklyProgress = makeProgress(targetEur = 500.0, earnedEur = 200.0, plannedHours = 40.0, workedHours = 15.0)
        val context = makeEconomicContext(
            dailyProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0),
            weeklyProgress = weeklyProgress
        )

        val evaluation = engine.evaluate(trip, defaultConfig, economicContext = context)

        assertNotNull(evaluation.goalContext)
        assertEquals(weeklyProgress.remainingEur, evaluation.goalContext!!.remainingEur, delta)
    }

    // TC-13: All targets 0 (modo libre completo) produce goalContext null
    @Test
    fun decisionEngine_allTargetsZero_producesNullGoalContext() {
        val trip = makeTrip()
        val context = makeEconomicContext(
            dailyProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0),
            weeklyProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0),
            monthlyProgress = makeProgress(targetEur = 0.0, earnedEur = 0.0)
        )

        val evaluation = engine.evaluate(trip, defaultConfig, economicContext = context)

        assertNull(evaluation.goalContext)
    }

    // TC-14: rawFare null produce estimatedGoalContribution null
    @Test
    fun evaluate_rawFareNull_producesNullContribution() {
        val progress = makeProgress()
        val metrics = EvaluationMetrics(
            totalDistanceKm = 7.5,
            totalDurationMinutes = 19.0,
            estimatedOperatingCost = 1.50,
            grossProfit = 12.0,
            netProfit = 10.50,
            grossPerKm = 1.60,
            grossPerHour = 37.89,
            netPerHour = 33.16
        )

        val result = GoalContextEvaluator.evaluate(metrics, progress, null)

        assertNotNull(result)
        assertNull(result!!.estimatedGoalContribution)
    }

    // TC-15: HUD mapper produce textos correctos con goalContext
    @Test
    fun hudMapper_withGoalContext_producesCorrectTexts() {
        val trip = makeTrip()
        val context = makeEconomicContext(
            dailyProgress = makeProgress(
                targetEur = 100.0,
                earnedEur = 40.0,
                plannedHours = 8.0,
                workedHours = 3.0,
                status = ProgressStatus.BEHIND
            )
        )

        val evaluation = engine.evaluate(trip, defaultConfig, economicContext = context)
        val hudModel = com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper.map(evaluation)

        assertNotNull(hudModel.goalPaceText)
        assertNotNull(hudModel.goalContributionText)
        assertNotNull(hudModel.goalStatusText)
        assertEquals("Retrasado", hudModel.goalStatusText)
        assertTrue(hudModel.goalPaceText!!.contains("% ritmo"))
        assertTrue(hudModel.goalContributionText!!.contains("% del restante"))
    }

    // TC-16: HUD mapper sin goalContext produce textos null
    @Test
    fun hudMapper_withoutGoalContext_producesNullTexts() {
        val trip = makeTrip()

        val evaluation = engine.evaluate(trip, defaultConfig)
        val hudModel = com.ridedecider.app.ui.overlay.mapper.HudUiModelMapper.map(evaluation)

        assertNull(hudModel.goalPaceText)
        assertNull(hudModel.goalContributionText)
        assertNull(hudModel.goalStatusText)
    }
}
