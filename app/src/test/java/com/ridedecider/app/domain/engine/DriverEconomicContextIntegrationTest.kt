package com.ridedecider.app.domain.engine

import com.ridedecider.app.domain.model.Decision
import com.ridedecider.app.domain.model.DecisionReason
import com.ridedecider.app.domain.model.DriverEconomicContext
import com.ridedecider.app.domain.model.EarningsProgress
import com.ridedecider.app.domain.model.GoalPeriod
import com.ridedecider.app.domain.model.ProfitabilityConfig
import com.ridedecider.app.domain.model.ProgressStatus
import com.ridedecider.app.domain.model.Trip
import com.ridedecider.app.domain.model.TripOfferType
import com.ridedecider.app.domain.model.UberCategory
import com.ridedecider.app.domain.usecase.EvaluateIncomingTripUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverEconomicContextIntegrationTest {

    private val engine = DecisionEngine()
    private val useCase = EvaluateIncomingTripUseCase(engine)

    private val config = ProfitabilityConfig(
        costPerKm = 0.20,
        costPerHour = 5.0,
        minGrossHourlyRate = 25.0,
        minGrossPerKmRate = 1.20,
        minNetTripProfit = 2.0,
        minNetHourlyRate = 18.0,
        maxPickupDistanceKm = 5.0,
        maxPickupTimeMinutes = 10.0
    )

    @Test
    fun evaluateTrip_withoutContext_shouldPreserveStandardBehavior() {
        val trip = Trip(
            id = "trip_std",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.RADAR_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 9.01,
            currency = "EUR",
            pickupDistanceKm = 2.0,
            pickupDurationMinutes = 5.0,
            pickupAddress = null,
            tripDistanceKm = 7.6,
            tripDurationMinutes = 12.0,
            dropoffAddress = null
        )

        val eval = useCase(trip, config)

        assertEquals(Decision.REJECT, eval.decision)
        assertEquals(0.9385, eval.metrics?.grossPerKm ?: 0.0, 0.01) // 0.94 €/km
        assertEquals(31.80, eval.metrics?.grossPerHour ?: 0.0, 0.01) // 31.80 €/h
        assertTrue(eval.reasons.contains(DecisionReason.REJECT_LOW_KM_RATE))
    }

    @Test
    fun evaluateTrip_withContext_shouldAttachContextReasons() {
        val trip = Trip(
            id = "trip_ctx",
            timestamp = System.currentTimeMillis(),
            offerType = TripOfferType.TRIP_OFFER,
            category = UberCategory.UBER_X,
            rawFare = 27.02,
            currency = "EUR",
            pickupDistanceKm = 1.5,
            pickupDurationMinutes = 4.0,
            pickupAddress = null,
            tripDistanceKm = 10.9,
            tripDurationMinutes = 24.0,
            dropoffAddress = null
        )

        val dailyProgress = EarningsProgress(
            period = GoalPeriod.DAILY,
            targetEur = 150.0,
            earnedEur = 82.0,
            remainingEur = 68.0,
            completionPercentage = 54.67,
            plannedHours = 8.0,
            workedHours = 4.53,
            remainingHours = 3.47,
            currentHourlyRate = 18.09,
            requiredHourlyRate = 19.62,
            status = ProgressStatus.AHEAD
        )

        val dummyPeriodProgress = TargetProgressCalculator.calculateProgress(GoalPeriod.WEEKLY, 900.0, 82.0, 48.0, 4.53)
        val context = DriverEconomicContext(
            dailyProgress = dailyProgress,
            weeklyProgress = dummyPeriodProgress,
            monthlyProgress = dummyPeriodProgress
        )

        val eval = useCase(trip, config, economicContext = context)

        assertEquals(Decision.ACCEPT, eval.decision)
        assertTrue(eval.reasons.contains(DecisionReason.ACCEPT_HIGH_PROFITABILITY))
        // 57.90 €/h > 19.62 €/h -> CONTEXT_ABOVE_REQUIRED_HOURLY_RATE
        assertTrue(eval.reasons.contains(DecisionReason.CONTEXT_ABOVE_REQUIRED_HOURLY_RATE))
    }
}
