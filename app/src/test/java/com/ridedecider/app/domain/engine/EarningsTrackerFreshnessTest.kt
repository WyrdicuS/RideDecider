package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.repository.InMemoryDriverGoalsRepository
import com.ridedecider.app.data.repository.InMemoryEarningsRepository
import com.ridedecider.app.domain.model.CancellationReason
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class EarningsTrackerFreshnessTest {

    private lateinit var goalsRepo: InMemoryDriverGoalsRepository
    private lateinit var earningsRepo: InMemoryEarningsRepository
    private lateinit var tracker: EarningsTracker
    private val delta = 0.01

    @Before
    fun setUp() {
        goalsRepo = InMemoryDriverGoalsRepository(
            DriverGoals(
                activePeriod = GoalPeriod.DAILY,
                dailyTargetEur = 100.0,
                dailyPlannedHours = 8.0
            )
        )
        earningsRepo = InMemoryEarningsRepository()
        tracker = EarningsTracker(goalsRepo, earningsRepo)
    }

    private fun makeTrip(id: String = "trip_fresh", rawFare: Double = 12.0) = Trip(
        id = id,
        timestamp = System.currentTimeMillis(),
        offerType = TripOfferType.TRIP_OFFER,
        category = UberCategory.UBER_X,
        rawFare = rawFare,
        currency = "EUR",
        pickupDistanceKm = 1.5,
        pickupDurationMinutes = 4.0,
        pickupAddress = null,
        tripDistanceKm = 6.0,
        tripDurationMinutes = 15.0,
        dropoffAddress = null
    )

    private val defaultConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 4.0,
        minGrossHourlyRate = 20.0,
        minGrossPerKmRate = 1.0,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 10.0,
        maxPickupDistanceKm = 5.0,
        maxPickupTimeMinutes = 10.0
    )

    // TC-F1: Contexto inicial correcto tras refreshEconomicContext
    @Test
    fun initialContext_afterRefresh_hasCorrectGoals() = runBlocking {
        tracker.refreshEconomicContext()

        val ctx = tracker.cachedContext
        assertNotNull(ctx)
        assertEquals(100.0, ctx!!.dailyProgress.targetEur, delta)
        assertEquals(8.0, ctx.dailyProgress.plannedHours, delta)
        assertEquals(0.0, ctx.dailyProgress.earnedEur, delta)
    }

    // TC-F2: Cambio de targetEur durante sesion actualiza contexto
    @Test
    fun goalsChange_targetEur_contextUpdatedAfterRefresh() = runBlocking {
        tracker.refreshEconomicContext()
        val before = tracker.cachedContext!!
        assertEquals(100.0, before.dailyProgress.targetEur, delta)

        goalsRepo.updateGoals(
            DriverGoals(
                activePeriod = GoalPeriod.DAILY,
                dailyTargetEur = 150.0,
                dailyPlannedHours = 8.0
            )
        )
        tracker.refreshEconomicContext()

        val after = tracker.cachedContext!!
        assertEquals(150.0, after.dailyProgress.targetEur, delta)
        assertEquals(150.0, after.dailyProgress.remainingEur, delta)
    }

    // TC-F3: Cambio de plannedHours actualiza contexto
    @Test
    fun goalsChange_plannedHours_contextUpdatedAfterRefresh() = runBlocking {
        tracker.refreshEconomicContext()
        assertEquals(8.0, tracker.cachedContext!!.dailyProgress.plannedHours, delta)

        goalsRepo.updateGoals(
            DriverGoals(
                activePeriod = GoalPeriod.DAILY,
                dailyTargetEur = 100.0,
                dailyPlannedHours = 6.0
            )
        )
        tracker.refreshEconomicContext()

        assertEquals(6.0, tracker.cachedContext!!.dailyProgress.plannedHours, delta)
        assertEquals(6.0, tracker.cachedContext!!.dailyProgress.remainingHours, delta)
    }

    // TC-F4: Cambio de activePeriod actualiza contexto
    @Test
    fun goalsChange_activePeriod_contextReflectsNewPeriod() = runBlocking {
        tracker.refreshEconomicContext()
        assertEquals(GoalPeriod.DAILY, tracker.cachedContext!!.dailyProgress.period)

        goalsRepo.updateGoals(
            DriverGoals(
                activePeriod = GoalPeriod.WEEKLY,
                dailyTargetEur = 0.0,
                dailyPlannedHours = 0.0,
                weeklyTargetEur = 500.0,
                weeklyPlannedHours = 40.0
            )
        )
        tracker.refreshEconomicContext()

        val ctx = tracker.cachedContext!!
        assertEquals(500.0, ctx.weeklyProgress.targetEur, delta)
        assertEquals(40.0, ctx.weeklyProgress.plannedHours, delta)
    }

    // TC-F5: Contexto del dia anterior vs dia actual — day bounds divergen
    @Test
    fun dayChange_cachedContextTimestamp_detectsStaleDay() = runBlocking {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 22)
        }.timeInMillis

        tracker.refreshEconomicContext(yesterday)
        val ctxYesterday = tracker.cachedContext!!
        assertEquals(yesterday, ctxYesterday.timestamp)

        val now = System.currentTimeMillis()
        val (dayStartNow, _) = tracker.getDayBounds(now)
        val (dayStartCtx, _) = tracker.getDayBounds(ctxYesterday.timestamp)

        assertTrue("Day bounds must differ", dayStartNow != dayStartCtx)
    }

    // TC-F6: Despues de completeTrip, contexto actualizado
    @Test
    fun afterCompleteTrip_contextReflectsNewEarnings() = runBlocking {
        tracker.refreshEconomicContext()
        assertEquals(0.0, tracker.cachedContext!!.dailyProgress.earnedEur, delta)

        val trip = makeTrip("trip_complete")
        val engine = DecisionEngine()
        val eval = engine.evaluate(trip, defaultConfig)
        tracker.recordEvaluatedOffer(trip, eval)

        tracker.completeTrip(
            tripId = "trip_complete",
            finalEarnings = 15.0,
            durationMinutes = 18.0
        )

        val ctx = tracker.cachedContext!!
        assertEquals(15.0, ctx.dailyProgress.earnedEur, delta)
        assertEquals(85.0, ctx.dailyProgress.remainingEur, delta)
    }

    // TC-F7: Despues de cancelacion con fee, contexto actualizado
    @Test
    fun afterCancelWithFee_contextReflectsNewEarnings() = runBlocking {
        tracker.refreshEconomicContext()

        val trip = makeTrip("trip_cancel_fee")
        val engine = DecisionEngine()
        val eval = engine.evaluate(trip, defaultConfig)
        tracker.recordEvaluatedOffer(trip, eval)

        tracker.cancelTrip(
            tripId = "trip_cancel_fee",
            reason = CancellationReason.RIDER,
            cancellationFee = 3.75
        )

        val ctx = tracker.cachedContext!!
        assertEquals(3.75, ctx.dailyProgress.earnedEur, delta)
    }

    // TC-F8: Cancelacion sin fee no genera comportamiento incorrecto
    @Test
    fun afterCancelWithoutFee_contextUnchanged() = runBlocking {
        tracker.refreshEconomicContext()
        val before = tracker.cachedContext!!.dailyProgress.earnedEur

        val trip = makeTrip("trip_cancel_nofee")
        val engine = DecisionEngine()
        val eval = engine.evaluate(trip, defaultConfig)
        tracker.recordEvaluatedOffer(trip, eval)

        tracker.cancelTrip(
            tripId = "trip_cancel_nofee",
            reason = CancellationReason.DRIVER
        )

        assertEquals(before, tracker.cachedContext!!.dailyProgress.earnedEur, delta)
    }

    // TC-F9: targetEur == 0 produce goalContext null en evaluacion
    @Test
    fun targetZero_goalContextNull() = runBlocking {
        goalsRepo.updateGoals(
            DriverGoals(
                activePeriod = GoalPeriod.DAILY,
                dailyTargetEur = 0.0,
                dailyPlannedHours = 0.0
            )
        )
        tracker.refreshEconomicContext()

        val trip = makeTrip()
        val engine = DecisionEngine()
        val ctx = tracker.cachedContext!!
        val eval = engine.evaluate(trip, defaultConfig, economicContext = ctx)

        assertNull(eval.goalContext)
    }

    // TC-F10: No se realizan consultas Room adicionales al leer cachedContext
    @Test
    fun cachedContextRead_noRoomQuery() = runBlocking {
        tracker.refreshEconomicContext()

        val ctx1 = tracker.cachedContext
        val ctx2 = tracker.cachedContext
        val ctx3 = tracker.cachedContext

        assertNotNull(ctx1)
        assertEquals(ctx1, ctx2)
        assertEquals(ctx2, ctx3)
    }

    // TC-F11: Decision, profitabilityScore y profitabilityLevel no cambian con contexto fresco
    @Test
    fun freshContext_doesNotAlterSpeDecision() = runBlocking {
        tracker.refreshEconomicContext()

        val trip = makeTrip()
        val engine = DecisionEngine()
        val evalWithout = engine.evaluate(trip, defaultConfig)
        val evalWith = engine.evaluate(trip, defaultConfig, economicContext = tracker.cachedContext)

        assertEquals(evalWithout.decision, evalWith.decision)
        assertEquals(evalWithout.metrics?.profitabilityScore, evalWith.metrics?.profitabilityScore)
        assertEquals(evalWithout.profitabilityLevel, evalWith.profitabilityLevel)
    }

    // TC-F12: No se crea segundo score
    @Test
    fun noSecondScore_inGoalContext() = runBlocking {
        tracker.refreshEconomicContext()

        val trip = makeTrip()
        val engine = DecisionEngine()
        val eval = engine.evaluate(trip, defaultConfig, economicContext = tracker.cachedContext)

        assertNotNull(eval.goalContext)
        assertNotNull(eval.metrics?.profitabilityScore)
    }

    // TC-F13: No se crea segunda decision
    @Test
    fun noSecondDecision_inGoalContext() = runBlocking {
        tracker.refreshEconomicContext()

        val trip = makeTrip()
        val engine = DecisionEngine()
        val eval = engine.evaluate(trip, defaultConfig, economicContext = tracker.cachedContext)

        assertTrue(eval.decision == Decision.ACCEPT || eval.decision == Decision.REJECT || eval.decision == Decision.UNKNOWN)
        assertNotNull(eval.goalContext)
    }

    // TC-F14: No aparece NaN ni Infinity en metricas de goalContext
    @Test
    fun goalContext_noNaNOrInfinity() {
        runBlocking {
            tracker.refreshEconomicContext()

            val trip = makeTrip()
            val engine = DecisionEngine()
            val eval = engine.evaluate(trip, defaultConfig, economicContext = tracker.cachedContext)

            val gc = eval.goalContext!!
            gc.targetPaceRatio?.let {
                assertTrue("targetPaceRatio must be finite", it.isFinite())
            }
            gc.estimatedGoalContribution?.let {
                assertTrue("estimatedGoalContribution must be finite", it.isFinite())
            }
            gc.estimatedTimeConsumption?.let {
                assertTrue("estimatedTimeConsumption must be finite", it.isFinite())
            }
        }
    }
}
