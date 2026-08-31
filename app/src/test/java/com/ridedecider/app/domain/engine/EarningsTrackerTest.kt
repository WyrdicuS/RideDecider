package com.ridedecider.app.domain.engine

import com.ridedecider.app.data.repository.InMemoryDriverGoalsRepository
import com.ridedecider.app.data.repository.InMemoryEarningsRepository
import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverGoals
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripEvaluation
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class EarningsTrackerTest {

    private lateinit var goalsRepository: InMemoryDriverGoalsRepository
    private lateinit var earningsRepository: InMemoryEarningsRepository
    private lateinit var tracker: EarningsTracker

    private val defaultConfig = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 5.0,
        minGrossHourlyRate = 25.0,
        minGrossPerKmRate = 1.20,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 5.0,
        maxPickupTimeMinutes = 10.0
    )

    @Before
    fun setUp() {
        goalsRepository = InMemoryDriverGoalsRepository(
            DriverGoals(
                dailyTargetEur = 150.0,
                weeklyTargetEur = 900.0,
                monthlyTargetEur = 3600.0,
                dailyPlannedHours = 8.0,
                weeklyPlannedHours = 48.0,
                monthlyPlannedHours = 192.0
            )
        )
        earningsRepository = InMemoryEarningsRepository()
        tracker = EarningsTracker(goalsRepository, earningsRepository)
    }

    private fun createDummyTrip(id: String, fare: Double, timestamp: Long): Pair<Trip, TripEvaluation> {
        val trip = Trip(
            id = id,
            timestamp = timestamp,
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = fare,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            pickupAddress = null,
            tripDistanceKm = 8.0,
            tripDurationMinutes = 15.0,
            dropoffAddress = null
        )
        val evaluation = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.ACCEPT,
            reasons = listOf(DecisionReason.ACCEPT_HIGH_PROFITABILITY),
            evaluationTimestamp = timestamp
        )
        return Pair(trip, evaluation)
    }

    // 17. Viaje evaluado pero no realizado NO suma automáticamente a ganancias
    @Test
    fun evaluatedTrip_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, eval) = createDummyTrip("eval_1", 25.0, now)

        tracker.recordEvaluatedOffer(trip, eval, now)

        val progress = tracker.getDailyProgress(now)
        assertEquals(0.0, progress.earnedEur, 0.001)
        assertEquals(150.0, progress.remainingEur, 0.001)
    }

    // 18. Viaje rechazado NO suma ganancias
    @Test
    fun rejectedTrip_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, _) = createDummyTrip("eval_rej", 5.0, now)
        val rejectedEval = TripEvaluation(
            trip = trip,
            configUsed = defaultConfig,
            metrics = null,
            decision = Decision.REJECT,
            reasons = listOf(DecisionReason.REJECT_LOW_KM_RATE),
            evaluationTimestamp = now
        )

        tracker.recordEvaluatedOffer(trip, rejectedEval, now)

        val progress = tracker.getDailyProgress(now)
        assertEquals(0.0, progress.earnedEur, 0.001)
    }

    // 19. Viaje aceptado por el conductor pero todavía no confirmado como realizado NO suma ganancias definitivas
    @Test
    fun acceptedTrip_withoutCompletion_doesNotCountTowardsEarnings() = runBlocking {
        val now = System.currentTimeMillis()
        val (trip, eval) = createDummyTrip("acc_1", 30.0, now)

        tracker.recordEvaluatedOffer(trip, eval, now)
        tracker.markTripAccepted(trip.id)

        val progress = tracker.getDailyProgress(now)
        assertEquals(0.0, progress.earnedEur, 0.001)

        // Ahora lo completamos: debe sumar
        tracker.completeTrip(trip.id, finalEarnings = 30.0, durationMinutes = 20.0, completedTimestamp = now)

        val updatedProgress = tracker.getDailyProgress(now)
        assertEquals(30.0, updatedProgress.earnedEur, 0.001)
        assertEquals(120.0, updatedProgress.remainingEur, 0.001)
        assertEquals((20.0 / 60.0), updatedProgress.workedHours, 0.001)
    }

    // 11. Progreso semanal: suma varios días de la misma semana
    @Test
    fun weeklyProgress_aggregatesAcrossDays() = runBlocking {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
        }
        val monday = cal.timeInMillis

        cal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
        val tuesday = cal.timeInMillis

        // Viaje lunes: 50 €
        val (trip1, eval1) = createDummyTrip("w_1", 50.0, monday)
        tracker.recordEvaluatedOffer(trip1, eval1, monday)
        tracker.completeTrip(trip1.id, 50.0, 60.0, monday)

        // Viaje martes: 70 €
        val (trip2, eval2) = createDummyTrip("w_2", 70.0, tuesday)
        tracker.recordEvaluatedOffer(trip2, eval2, tuesday)
        tracker.completeTrip(trip2.id, 70.0, 90.0, tuesday)

        val weekly = tracker.getWeeklyProgress(tuesday)
        assertEquals(120.0, weekly.earnedEur, 0.001)
        assertEquals(780.0, weekly.remainingEur, 0.001)
        assertEquals(2.5, weekly.workedHours, 0.001) // 150 min = 2.5 h
        assertEquals(48.0, weekly.currentHourlyRate, 0.001) // 120 / 2.5 = 48 €/h
    }

    // 12. Progreso mensual: suma los viajes del mes
    @Test
    fun monthlyProgress_aggregatesAcrossMonth() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 5)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val day5 = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, 15)
        val day15 = cal.timeInMillis

        val (t1, e1) = createDummyTrip("m_1", 200.0, day5)
        tracker.recordEvaluatedOffer(t1, e1, day5)
        tracker.completeTrip(t1.id, 200.0, 300.0, day5)

        val (t2, e2) = createDummyTrip("m_2", 350.0, day15)
        tracker.recordEvaluatedOffer(t2, e2, day15)
        tracker.completeTrip(t2.id, 350.0, 450.0, day15)

        val monthly = tracker.getMonthlyProgress(day15)
        assertEquals(550.0, monthly.earnedEur, 0.001)
        assertEquals(3050.0, monthly.remainingEur, 0.001)
        assertEquals(12.5, monthly.workedHours, 0.001) // 750 min = 12.5 h
    }

    // 13. Cambio de día: nuevo día no incluye ganancias del día anterior
    @Test
    fun dayRollover_doesNotMixDays() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 14)
        }
        val day10 = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, 11)
        val day11 = cal.timeInMillis

        // Viaje día 10: 100 €
        val (t1, e1) = createDummyTrip("d_10", 100.0, day10)
        tracker.recordEvaluatedOffer(t1, e1, day10)
        tracker.completeTrip(t1.id, 100.0, 120.0, day10)

        // El progreso del día 10 tiene 100 €
        val progressDay10 = tracker.getDailyProgress(day10)
        assertEquals(100.0, progressDay10.earnedEur, 0.001)

        // El progreso del día 11 tiene 0 €
        val progressDay11 = tracker.getDailyProgress(day11)
        assertEquals(0.0, progressDay11.earnedEur, 0.001)
        assertEquals(150.0, progressDay11.remainingEur, 0.001)
    }

    // 14. Cambio de semana: nueva semana no incluye ganancias de la semana anterior
    @Test
    fun weekRollover_doesNotMixWeeks() = runBlocking {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.WEEK_OF_YEAR, 10)
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
        }
        val week10 = cal.timeInMillis

        cal.set(Calendar.WEEK_OF_YEAR, 11)
        val week11 = cal.timeInMillis

        val (t1, e1) = createDummyTrip("w10", 300.0, week10)
        tracker.recordEvaluatedOffer(t1, e1, week10)
        tracker.completeTrip(t1.id, 300.0, 360.0, week10)

        val progressWeek10 = tracker.getWeeklyProgress(week10)
        assertEquals(300.0, progressWeek10.earnedEur, 0.001)

        val progressWeek11 = tracker.getWeeklyProgress(week11)
        assertEquals(0.0, progressWeek11.earnedEur, 0.001)
    }

    // 15. Cambio de mes: nuevo mes no incluye ganancias del mes anterior
    @Test
    fun monthRollover_doesNotMixMonths() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val jan = cal.timeInMillis

        cal.set(Calendar.MONTH, Calendar.FEBRUARY)
        val feb = cal.timeInMillis

        val (t1, e1) = createDummyTrip("jan_1", 800.0, jan)
        tracker.recordEvaluatedOffer(t1, e1, jan)
        tracker.completeTrip(t1.id, 800.0, 900.0, jan)

        val progressJan = tracker.getMonthlyProgress(jan)
        assertEquals(800.0, progressJan.earnedEur, 0.001)

        val progressFeb = tracker.getMonthlyProgress(feb)
        assertEquals(0.0, progressFeb.earnedEur, 0.001)
    }

    // 16. No mezclar ganancias entre períodos
    @Test
    fun periodsIsolation_verification() = runBlocking {
        val now = System.currentTimeMillis()
        val (t, e) = createDummyTrip("iso_1", 82.0, now)
        tracker.recordEvaluatedOffer(t, e, now)
        // 4h 32min = 272 min
        tracker.completeTrip(t.id, 82.0, 272.0, now)

        val context = tracker.getEconomicContext(now)

        // Daily
        assertEquals(82.0, context.dailyProgress.earnedEur, 0.001)
        assertEquals(68.0, context.dailyProgress.remainingEur, 0.001)
        assertEquals(4.5333, context.dailyProgress.workedHours, 0.01) // 4.53 h
        assertEquals(3.4667, context.dailyProgress.remainingHours, 0.01) // 3.47 h
        assertEquals(18.09, context.dailyProgress.currentHourlyRate, 0.02)
        assertEquals(19.62, context.dailyProgress.requiredHourlyRate, 0.02)

        // Weekly & Monthly also have the 82 € because they contain today
        assertEquals(82.0, context.weeklyProgress.earnedEur, 0.001)
        assertEquals(82.0, context.monthlyProgress.earnedEur, 0.001)
    }
}
